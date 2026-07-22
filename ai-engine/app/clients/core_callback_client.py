import asyncio
from collections.abc import Awaitable, Callable
import logging
from typing import Any
from uuid import UUID

import httpx

from app.core.logging import CORRELATION_HEADER, correlation_id_context

from app.schemas.ai_task_result import (
    AiTaskResultAcknowledgement,
    AiTaskResultRequest,
)


class CoreCallbackError(RuntimeError):
    """A callback response that the AI Engine cannot safely recover from."""


class CoreCallbackClient:
    def __init__(
        self,
        base_url: str,
        *,
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
        self._transport = transport
        self._timeout = timeout
        self._max_attempts = max_attempts
        self._initial_delay_ms = initial_delay_ms
        self._max_delay_ms = max_delay_ms
        self._sleep = sleep

    async def send_result(
        self,
        correlation_id: UUID,
        result: AiTaskResultRequest,
    ) -> AiTaskResultAcknowledgement:
        if correlation_id != result.correlation_id:
            raise ValueError("Path and payload correlation identifiers must match")

        payload = result.model_dump(mode="json", by_alias=True)
        logger = logging.getLogger(__name__)
        logger.info(
            "Sending AI task result callback",
            extra={
                "taskCorrelationId": str(correlation_id),
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
                    f"/api/v1/ai/tasks/{correlation_id}/result",
                    json=payload,
                    headers={CORRELATION_HEADER: correlation_id_context.get()},
                )
                if response.is_success:
                    logger.info(
                        "AI task result callback acknowledged",
                        extra={
                            "taskCorrelationId": str(correlation_id),
                            "status": response.status_code,
                            "attempt": attempt,
                        },
                    )
                    break
                if not self._is_transient_not_ready(response):
                    raise self._callback_error(response, correlation_id, attempt)
                if attempt == self._max_attempts:
                    raise self._callback_error(response, correlation_id, attempt)
                logger.warning(
                    "AI task result callback will be retried",
                    extra={
                        "taskCorrelationId": str(correlation_id),
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

    def _is_transient_not_ready(self, response: httpx.Response) -> bool:
        if response.status_code != 409:
            return False
        body = self._json_object(response)
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
        return CoreCallbackError(
            "Core callback failed: "
            f"status={response.status_code}, correlationId={correlation_id}, "
            f"attempts={attempts}"
        )

    def _json_object(self, response: httpx.Response) -> dict[str, Any]:
        try:
            body = response.json()
        except ValueError:
            return {}
        return body if isinstance(body, dict) else {}
