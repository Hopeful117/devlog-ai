from __future__ import annotations

import json
import logging
import re
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from uuid import UUID

import httpx
from openai.lib._pydantic import to_strict_json_schema
from pydantic import BaseModel

from app.providers.base import (
    OutputAllowanceError,
    Prompt,
    ProviderGenerationResult,
    StructuredOutput,
    effective_output_tokens,
)
from app.security.credential_resolver import ProviderCredentialResolver
from app.services.chatgpt_oauth import required_scopes_granted

logger = logging.getLogger(__name__)

class ChatGptProviderError(RuntimeError):
    """Bounded provider failure without remote response disclosure."""

    def __init__(self, message: str, *, status: str = "FAILED", reason: str | None = None) -> None:
        super().__init__(message)
        self.provider_status = status
        self.incomplete_reason = reason
        incomplete = status == "RESPONSE.INCOMPLETE"
        self.failure_category = "PROVIDER_INCOMPLETE" if incomplete else "PROVIDER_ERROR"
        self.failure_code = "PROVIDER_INCOMPLETE_RESPONSE" if incomplete else "PROVIDER_ERROR"


class OpenAiChatGptProvider:
    def __init__(self, *, resolver: ProviderCredentialResolver, endpoint: str,
                 model: str, timeout_seconds: float, token_endpoint: str | None,
                 client_id: str | None, resource: str | None,
                 max_output_tokens: int) -> None:
        self._resolver = resolver
        self._endpoint = endpoint
        self._model = model
        self._timeout_seconds = timeout_seconds
        self._max_output_tokens = max_output_tokens
        self._token_endpoint = token_endpoint
        self._client_id = client_id
        self._resource = resource
        self._last_usage: dict[str, int | None] = {}

    async def embed(self, texts: list[str], model: str) -> list[list[float]]:
        """Keep OAuth chat credentials from being misused as embedding credentials."""
        raise ChatGptProviderError(
            "ChatGPT OAuth does not provide an embeddings operation; configure a separate embedding provider",
            status="UNSUPPORTED",
        )

    async def generate_structured(self, request: Prompt,
                                  response_model: type[StructuredOutput]) -> StructuredOutput:
        return (await self.generate_structured_with_trace(request, response_model)).output

    async def generate_structured_with_trace(
        self, request: Prompt, response_model: type[StructuredOutput]
    ) -> ProviderGenerationResult:
        started_at = datetime.now(timezone.utc)
        try:
            task_id = UUID(request.traceability.ai_task_id)
            credential = self._resolver.resolve_for_task(task_id, "chatgpt")
            if credential.access_token_expires_at and credential.access_token_expires_at <= started_at:
                credential = await self._refresh(task_id, credential)
            if not required_scopes_granted(credential.scopes):
                raise ChatGptProviderError("ChatGPT plan authorization is incomplete")
            effective_tokens = effective_output_tokens(request, self._max_output_tokens)
            logger.info(
                "Sending ChatGPT provider request",
                extra={
                    "event": "provider_request_output_allowance",
                    "taskId": request.traceability.ai_task_id,
                    "intent": request.intent_id,
                    "provider": "openai_chatgpt",
                    "model": request.model_identifier or self._model,
                    "effectiveOutputTokens": effective_tokens,
                    "outputParameterSource": "CORE_AUTHORIZED"
                    if request.intent_id == "engineering-story-context-analysis"
                    else "REQUEST_OR_ENGINE_CONFIG",
                },
            )
            schema, wrapped_root = self._provider_schema(response_model)
            body = {
                "model": request.model_identifier or self._model,
                "store": False,
                "stream": True,
                "max_output_tokens": effective_tokens,
                "input": [
                    {"role": "system", "content": request.system_message},
                    {"role": "user", "content": request.user_message},
                ],
                "text": {"format": {
                    "type": "json_schema", "name": "devlog_output", "strict": True,
                    "schema": schema,
                }},
            }
            output_text = await self._stream(credential.access_token, body)
            completed_at = datetime.now(timezone.utc)
            logger.info(
                "ChatGPT provider usage measured",
                extra={
                    "event": "provider_usage_measured",
                    "taskId": request.traceability.ai_task_id,
                    "intent": request.intent_id,
                    "durationMs": int((completed_at - started_at).total_seconds() * 1000),
                    "inputTokens": self._last_usage.get("input_tokens"),
                    "outputTokens": self._last_usage.get("output_tokens"),
                    "reasoningTokens": self._last_usage.get("reasoning_tokens"),
                },
            )
            payload = json.loads(output_text)
            if wrapped_root:
                payload = payload["root"]
            parsed = response_model.model_validate(payload)
            return ProviderGenerationResult(
                output=parsed,
                raw_output=output_text,
                started_at=started_at,
                completed_at=completed_at,
                input_tokens=self._last_usage.get("input_tokens"),
                output_tokens=self._last_usage.get("output_tokens"),
                total_tokens=self._last_usage.get("total_tokens"),
                response_status="COMPLETED",
            )
        except OutputAllowanceError:
            raise
        except (ValueError, KeyError, json.JSONDecodeError, httpx.HTTPError) as exception:
            raise ChatGptProviderError("ChatGPT provider request failed") from exception

    async def _refresh(self, task_id: UUID, credential):
        if not credential.refresh_token or not self._token_endpoint or not credential.client_id:
            raise ChatGptProviderError("ChatGPT credential requires reauthorization")
        try:
            data = {
                "grant_type": "refresh_token",
                "client_id": credential.client_id,
                "refresh_token": credential.refresh_token,
            }
            if self._resource:
                data["resource"] = self._resource
            async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
                response = await client.post(self._token_endpoint, data=data)
            if response.status_code >= 400:
                self._resolver.invalidate(credential.handle)
                raise ChatGptProviderError("ChatGPT credential requires reauthorization")
            payload = response.json()
            access_token = payload.get("access_token")
            if not isinstance(access_token, str) or not access_token:
                raise ChatGptProviderError("ChatGPT credential requires reauthorization")
            expires_at = datetime.now(timezone.utc) + timedelta(
                seconds=int(payload.get("expires_in", 0))
            )
            self._resolver.replace_tokens(
                credential.handle, access_token, payload.get("refresh_token"), expires_at
            )
            return self._resolver.resolve_for_task(task_id, "chatgpt")
        except (httpx.HTTPError, ValueError, KeyError) as exception:
            self._resolver.invalidate(credential.handle)
            raise ChatGptProviderError("ChatGPT credential requires reauthorization") from exception

    async def _stream(self, access_token: str, body: dict) -> str:
        chunks: list[str] = []
        completed = False
        self._last_usage = {}
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            async with client.stream(
                    "POST", self._endpoint,
                    headers={"Authorization": f"Bearer {access_token}"},
                    json=body,
            ) as response:
                if response.status_code >= 400:
                    await response.aread()
                    raise ChatGptProviderError(
                        self._provider_error_message(response.status_code, response)
                    )
                response.raise_for_status()
                event_type = None
                data_lines: list[str] = []
                async for line in response.aiter_lines():
                    if line.startswith("event:"):
                        event_type = line[6:].strip()
                    elif line.startswith("data:"):
                        data_lines.append(line[5:].strip())
                    elif not line and data_lines:
                        data = json.loads("\n".join(data_lines))
                        received_type = event_type or data.get("type")
                        if received_type == "response.output_text.delta":
                            chunks.append(data.get("delta", ""))
                        elif received_type in {"response.failed", "response.incomplete", "error"}:
                            reason = self._incomplete_reason(data)
                            logger.warning(
                                "ChatGPT provider response incomplete",
                                extra={
                                    "event": "provider_response_incomplete",
                                    "provider": "openai_chatgpt",
                                    "providerStatus": received_type.upper(),
                                    "incompleteReason": reason,
                                },
                            )
                            raise ChatGptProviderError(
                                self._incomplete_response_message(received_type, data),
                                status=received_type.upper(), reason=reason,
                            )
                        elif received_type == "response.completed":
                            completed = True
                            response = data.get("response") if isinstance(data.get("response"), dict) else data
                            usage = response.get("usage") if isinstance(response, dict) else None
                            if isinstance(usage, dict):
                                details = usage.get("output_tokens_details")
                                self._last_usage = {
                                    "input_tokens": self._non_negative_int(usage.get("input_tokens")),
                                    "output_tokens": self._non_negative_int(usage.get("output_tokens")),
                                    "total_tokens": self._non_negative_int(usage.get("total_tokens")),
                                    "reasoning_tokens": self._non_negative_int(
                                        details.get("reasoning_tokens") if isinstance(details, dict) else None
                                    ),
                                }
                        event_type, data_lines = None, []
        if not completed:
            raise ChatGptProviderError("ChatGPT response did not complete")
        return "".join(chunks)

    @staticmethod
    def _non_negative_int(value: object) -> int | None:
        return value if isinstance(value, int) and value >= 0 else None

    @staticmethod
    def _provider_schema(response_model: type[StructuredOutput]) -> tuple[dict, bool]:
        schema = deepcopy(to_strict_json_schema(response_model))
        wrapped_root = "oneOf" in schema or "anyOf" in schema or schema.get("type") != "object"
        OpenAiChatGptProvider._replace_one_of(schema)
        if not wrapped_root:
            return schema, False
        definitions = schema.pop("$defs", None)
        wrapped = {
            "type": "object",
            "properties": {"root": schema},
            "required": ["root"],
            "additionalProperties": False,
        }
        if definitions:
            wrapped["$defs"] = definitions
        return wrapped, True

    @staticmethod
    def _replace_one_of(value: object) -> None:
        if isinstance(value, dict):
            if "oneOf" in value:
                value["anyOf"] = value.pop("oneOf")
            for child in value.values():
                OpenAiChatGptProvider._replace_one_of(child)
        elif isinstance(value, list):
            for child in value:
                OpenAiChatGptProvider._replace_one_of(child)

    @staticmethod
    def _incomplete_response_message(event_type: str, data: dict) -> str:
        """Return bounded terminal diagnostics without exposing provider content."""
        response = data.get("response") if isinstance(data.get("response"), dict) else data
        details = response.get("incomplete_details") if isinstance(response, dict) else None
        reason = details.get("reason") if isinstance(details, dict) else None
        if isinstance(reason, str) and reason:
            reason = re.sub(r"[^A-Za-z0-9_.-]", "_", reason)[:80]
            return f"ChatGPT response did not complete ({event_type}; reason={reason})"
        error = response.get("error") if isinstance(response, dict) else None
        code = error.get("code") if isinstance(error, dict) else None
        if isinstance(code, str) and code:
            code = re.sub(r"[^A-Za-z0-9_.-]", "_", code)[:80]
            return f"ChatGPT response did not complete ({event_type}; code={code})"
        return f"ChatGPT response did not complete ({event_type})"

    @staticmethod
    def _incomplete_reason(data: dict) -> str | None:
        response = data.get("response") if isinstance(data.get("response"), dict) else data
        details = response.get("incomplete_details") if isinstance(response, dict) else None
        reason = details.get("reason") if isinstance(details, dict) else None
        return re.sub(r"[^A-Za-z0-9_.-]", "_", reason)[:80] if isinstance(reason, str) and reason else None

    @staticmethod
    def _provider_error_message(status_code: int, response: httpx.Response) -> str:
        """Expose bounded provider diagnostics without returning remote content."""
        labels: list[str] = []
        try:
            error = response.json().get("error", {})
            for key in ("code", "type"):
                value = error.get(key) if isinstance(error, dict) else None
                if isinstance(value, str) and value:
                    labels.append(f"{key}={re.sub(r'[^A-Za-z0-9_.-]', '_', value)[:80]}")
        except (ValueError, TypeError):
            pass
        detail = f"; {', '.join(labels)}" if labels else ""
        return f"ChatGPT provider request failed (HTTP {status_code}{detail})"

    @property
    def provider_name(self) -> str:
        return "openai_chatgpt"

    @property
    def model_identifier(self) -> str:
        return self._model
