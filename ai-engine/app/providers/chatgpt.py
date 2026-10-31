from __future__ import annotations

import json
import re
from datetime import datetime, timedelta, timezone
from uuid import UUID

import httpx
from openai.lib._pydantic import to_strict_json_schema
from pydantic import BaseModel

from app.providers.base import Prompt, ProviderGenerationResult, StructuredOutput
from app.security.credential_resolver import ProviderCredentialResolver
from app.services.chatgpt_oauth import required_scopes_granted


class ChatGptProviderError(RuntimeError):
    """Bounded provider failure without remote response disclosure."""


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

    async def generate_structured(self, request: Prompt,
                                  response_model: type[StructuredOutput]) -> StructuredOutput:
        started_at = datetime.now(timezone.utc)
        try:
            task_id = UUID(request.traceability.ai_task_id)
            credential = self._resolver.resolve_for_task(task_id, "chatgpt")
            if credential.access_token_expires_at and credential.access_token_expires_at <= started_at:
                credential = await self._refresh(task_id, credential)
            if not required_scopes_granted(credential.scopes):
                raise ChatGptProviderError("ChatGPT plan authorization is incomplete")
            body = {
                "model": request.model_identifier or self._model,
                "store": False,
                "stream": True,
                "max_output_tokens": request.generation_parameters.get(
                    "maxOutputTokens", self._max_output_tokens
                ),
                "input": [
                    {"role": "system", "content": request.system_message},
                    {"role": "user", "content": request.user_message},
                ],
                "text": {"format": {
                    "type": "json_schema", "name": "devlog_output", "strict": True,
                    "schema": to_strict_json_schema(response_model),
                }},
            }
            output_text = await self._stream(credential.access_token, body)
            return response_model.model_validate(json.loads(output_text))
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
                        if event_type == "response.output_text.delta":
                            chunks.append(data.get("delta", ""))
                        elif event_type in {"response.failed", "response.incomplete", "error"}:
                            raise ChatGptProviderError("ChatGPT response did not complete")
                        elif event_type == "response.completed":
                            completed = True
                        event_type, data_lines = None, []
        if not completed:
            raise ChatGptProviderError("ChatGPT response did not complete")
        return "".join(chunks)

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
