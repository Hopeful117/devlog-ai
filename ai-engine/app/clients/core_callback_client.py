import asyncio
from collections.abc import Awaitable, Callable
import hashlib
import hmac
import json
import logging
import secrets
import time
from typing import Any
from uuid import UUID

import httpx

from app.core.logging import CORRELATION_HEADER, correlation_id_context

from app.schemas.ai_task_result import (
    AiTaskResultAcknowledgement,
    AiTaskResultRequest,
)
from app.schemas.story_context_investigation import InvestigationActionResponse


class CoreCallbackError(RuntimeError):
    """A callback response that the AI Engine cannot safely recover from."""

    def __init__(
        self,
        message: str,
        *,
        status_code: int | None = None,
        code: str | None = None,
        current_status: str | None = None,
        attempts: int | None = None,
        correlation_id: UUID | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.current_status = current_status
        self.attempts = attempts
        self.correlation_id = correlation_id


class CoreCallbackClient:
    def __init__(
        self,
        base_url: str,
        *,
        callback_secret: str,
        transport: httpx.AsyncBaseTransport | None = None,
        timeout: float = 5.0,
        max_attempts: int = 5,
        initial_delay_ms: int = 100,
        max_delay_ms: int = 1_000,
        sleep: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        if max_attempts <= 0:
            raise ValueError("max_attempts must be positive")
        if initial_delay_ms < 0 or max_delay_ms < initial_delay_ms:
            raise ValueError("callback retry delays are invalid")
        self._base_url = base_url
        if not callback_secret:
            raise ValueError("callback_secret must not be blank")
        self._callback_secret = callback_secret.encode("utf-8")
        self._transport = transport
        self._timeout = timeout
        self._max_attempts = max_attempts
        self._initial_delay_ms = initial_delay_ms
        self._max_delay_ms = max_delay_ms
        self._sleep = sleep

    async def send_result(
        self,
        ai_task_id: UUID,
        correlation_id: UUID,
        result: AiTaskResultRequest,
    ) -> AiTaskResultAcknowledgement:
        if correlation_id != result.correlation_id:
            raise ValueError("Path and payload correlation identifiers must match")

        payload = result.model_dump(mode="json", by_alias=True, exclude_none=True)
        body = json.dumps(
            payload, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")
        path = f"/api/v1/story-context-agent/tasks/{ai_task_id}/callback"
        signature = hmac.new(
            self._callback_secret,
            (
                "POST\n" + path + "\n"
            ).encode("utf-8") + body,
            hashlib.sha256,
        ).hexdigest()
        logger = logging.getLogger(__name__)
        logger.info(
            "Sending AI task result callback",
            extra={
                "event": "AI_CALLBACK_STARTED",
                "taskId": str(ai_task_id),
                "taskCorrelationId": str(correlation_id),
                "externalJobId": result.external_job_id,
                "proposalCount": len(result.proposals),
            },
        )
        response: httpx.Response | None = None
        async with httpx.AsyncClient(
            base_url=self._base_url,
            transport=self._transport,
            timeout=self._timeout,
        ) as client:
            for attempt in range(1, self._max_attempts + 1):
                response = await client.post(
                    path,
                    content=body,
                    headers={
                        CORRELATION_HEADER: correlation_id_context.get(),
                        "X-SCA-Signature": f"sha256={signature}",
                    },
                )
                if response.is_success:
                    logger.info(
                        "AI task result callback acknowledged",
                        extra={
                            "event": "AI_CALLBACK_SUCCEEDED",
                            "taskId": str(ai_task_id),
                            "taskCorrelationId": str(correlation_id),
                            "externalJobId": result.external_job_id,
                            "status": response.status_code,
                            "attempt": attempt,
                        },
                    )
                    break
                if not self._is_transient_not_ready(response):
                    error = self._callback_error(response, correlation_id, attempt)
                    logger.error(
                        "AI task result callback failed",
                        extra={
                            "event": "AI_CALLBACK_FAILED",
                            "taskId": str(ai_task_id),
                            "taskCorrelationId": str(correlation_id),
                            "externalJobId": result.external_job_id,
                            "status": response.status_code,
                            "attempt": attempt,
                            "outcome": error.code,
                        },
                    )
                    raise error
                if attempt == self._max_attempts:
                    error = self._callback_error(response, correlation_id, attempt)
                    logger.error(
                        "AI task result callback failed after retries",
                        extra={
                            "event": "AI_CALLBACK_FAILED",
                            "taskId": str(ai_task_id),
                            "taskCorrelationId": str(correlation_id),
                            "externalJobId": result.external_job_id,
                            "status": response.status_code,
                            "attempt": attempt,
                            "outcome": error.code,
                        },
                    )
                    raise error
                logger.warning(
                    "AI task result callback will be retried",
                    extra={
                        "event": "AI_CALLBACK_RETRY",
                        "taskId": str(ai_task_id),
                        "taskCorrelationId": str(correlation_id),
                        "externalJobId": result.external_job_id,
                        "status": response.status_code,
                        "attempt": attempt,
                    },
                )
                await self._sleep(self._delay_seconds(attempt))

        if response is None:
            raise RuntimeError("Callback did not produce an HTTP response")
        acknowledgement = AiTaskResultAcknowledgement.model_validate(
            response.json()
        )
        if acknowledgement.correlation_id != correlation_id:
            raise ValueError("Core returned a different correlation identifier")
        if not acknowledgement.acknowledged:
            raise ValueError("Core did not acknowledge the AI task result")
        return acknowledgement

    async def execute_investigation_action(
        self,
        ai_task_id: UUID,
        correlation_id: UUID,
        action: dict[str, Any],
    ) -> dict[str, Any]:
        """Execute one Core-authorized action using the callback authentication seam."""
        path = f"/api/v1/story-context-agent/tasks/{ai_task_id}/investigation/actions"
        body = json.dumps(action, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        signature = hmac.new(
            self._callback_secret, ("POST\n" + path + "\n").encode("utf-8") + body,
            hashlib.sha256,
        ).hexdigest()
        async with httpx.AsyncClient(
            base_url=self._base_url, transport=self._transport, timeout=self._timeout
        ) as client:
            response = await client.post(
                path,
                content=body,
                headers={
                    CORRELATION_HEADER: correlation_id_context.get(),
                    "X-SCA-Signature": f"sha256={signature}",
                },
            )
        if not response.is_success:
            raise self._callback_error(response, correlation_id, 1)
        value = response.json()
        if not isinstance(value, dict):
            raise CoreCallbackError("Core investigation action returned a non-object response")
        try:
            return InvestigationActionResponse.model_validate(value).model_dump(
                mode="json", by_alias=True, exclude_none=True
            )
        except Exception as error:
            raise CoreCallbackError(
                "Core investigation action returned an invalid v2 response: "
                f"{error}"
            ) from error

    def _is_transient_not_ready(self, response: httpx.Response) -> bool:
        body = self._json_object(response)
        if response.status_code == 404:
            return body.get("message", "").startswith("AI task not found with identifier:")
        if response.status_code != 409:
            return False
        return (
            body.get("code") == "AI_TASK_NOT_READY"
            and body.get("currentStatus") == "CREATED"
        )

    def _delay_seconds(self, failed_attempt: int) -> float:
        delay_ms = min(
            self._initial_delay_ms * (2 ** (failed_attempt - 1)),
            self._max_delay_ms,
        )
        return delay_ms / 1_000

    def _callback_error(
        self,
        response: httpx.Response,
        correlation_id: UUID,
        attempts: int,
    ) -> CoreCallbackError:
        body = self._json_object(response)
        detail = body.get("message") or body.get("detail") or body.get("code")
        suffix = f", detail={detail}" if detail else ""
        return CoreCallbackError(
            "Core callback failed: "
            f"status={response.status_code}, correlationId={correlation_id}, "
            f"attempts={attempts}{suffix}",
            status_code=response.status_code,
            code=body.get("code"),
            current_status=body.get("currentStatus"),
            attempts=attempts,
            correlation_id=correlation_id,
        )

    def _json_object(self, response: httpx.Response) -> dict[str, Any]:
        try:
            body = response.json()
        except ValueError:
            return {}
        return body if isinstance(body, dict) else {}
