from __future__ import annotations

import json
import logging
from datetime import datetime, timedelta, timezone
from typing import Annotated

import httpx
from fastapi import APIRouter, Depends, Request, status
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.core.config import get_settings
from app.schemas.evaluation_provider import EvaluationProviderRequest
from app.security.credential_resolver import ProviderCredentialResolver
from app.security.service_auth import HmacServiceAuthenticator, ServiceAuthenticationError
from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthSessionStore, required_scopes_granted
from app.security.credential_store import EncryptedCredentialStore

router = APIRouter(prefix="/evaluations", tags=["evaluation-provider"])
logger = logging.getLogger(__name__)


def _authenticator() -> HmacServiceAuthenticator | None:
    settings = get_settings()
    if not settings.service_auth_secret:
        return None
    return HmacServiceAuthenticator(
        settings.service_auth_secret,
        max_clock_skew_seconds=settings.service_auth_max_clock_skew_seconds,
    )


def _error(code: str, status_code: int = status.HTTP_401_UNAUTHORIZED) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"code": code})


@router.post("/chatgpt/responses", response_model=None)
async def complete_chatgpt(
        request: Request,
        authenticator: Annotated[HmacServiceAuthenticator | None, Depends(_authenticator)],
) -> dict | JSONResponse:
    if authenticator is None:
        return _error("SERVICE_AUTHENTICATION_UNAVAILABLE", status.HTTP_503_SERVICE_UNAVAILABLE)
    raw_body = await request.body()
    try:
        authenticator.verify(
            method=request.method,
            path=request.url.path,
            body=raw_body,
            headers=request.headers,
        )
        payload = EvaluationProviderRequest.model_validate_json(raw_body)
        return await _complete(payload)
    except ServiceAuthenticationError:
        return _error("INVALID_SERVICE_AUTHENTICATION")
    except ValidationError as error:
        logger.warning(
            "Remote provider request validation failed",
            extra={
                "event": "REMOTE_PROVIDER_REQUEST_VALIDATION_FAILED",
                "fields": [
                    {"location": list(item.get("loc", ())), "type": item.get("type")}
                    for item in error.errors()
                ],
            },
        )
        return JSONResponse(
            status_code=status.HTTP_400_BAD_REQUEST,
            content={
                "code": "INVALID_PROVIDER_REQUEST",
                "fields": [
                    {"location": list(item.get("loc", ())), "type": item.get("type")}
                    for item in error.errors()
                ],
            },
        )
    except httpx.HTTPError:
        return _error("PROVIDER_CONNECTION_ERROR", status.HTTP_502_BAD_GATEWAY)


async def _complete(payload: EvaluationProviderRequest) -> dict:
    settings = get_settings()
    resolver = ProviderCredentialResolver(
        bindings=_binding_registry(),
        credentials=EncryptedCredentialStore(
            settings.credential_store_path, settings.credential_encryption_key
        ),
    )
    credential = resolver.resolve_for_task(payload.task_id, "chatgpt")
    if credential.access_token_expires_at and credential.access_token_expires_at <= datetime.now(timezone.utc):
        credential = await ChatGptOAuthService(
            settings,
            OAuthSessionStore(settings.oauth_store_path),
            EncryptedCredentialStore(settings.credential_store_path, settings.credential_encryption_key),
        ).refresh_if_expired(credential.handle)
    if not required_scopes_granted(credential.scopes):
        return {"status": "FAILED", "code": "OAUTH_SCOPES_INCOMPLETE"}

    body = payload.model_dump(mode="json", by_alias=True, exclude={"task_id"})
    body["model"] = payload.model
    chunks: list[str] = []
    function_arguments: dict[str, str] = {}
    function_call: dict[str, str] | None = None
    usage: dict[str, int | None] = {}
    completed = False
    async with httpx.AsyncClient(timeout=settings.llm_timeout_seconds) as client:
        async with client.stream(
                "POST", settings.chatgpt_responses_endpoint,
                headers={"Authorization": f"Bearer {credential.access_token}"},
                json=body,
        ) as response:
            if response.status_code >= 400:
                await response.aread()
                try:
                    error_body = response.json()
                except ValueError:
                    error_body = {}
                error = error_body.get("error", {}) if isinstance(error_body, dict) else {}
                response_text = response.text[:500]
                return {
                    "status": "FAILED",
                    "code": f"PROVIDER_HTTP_{response.status_code}",
                    "providerError": {
                        "type": error.get("type"),
                        "code": error.get("code"),
                        "param": error.get("param"),
                        "message": str(error.get("message", ""))[:500],
                        "rawBody": response_text,
                    },
                }
            event_type = None
            data_lines: list[str] = []
            async for line in response.aiter_lines():
                if line.startswith("event:"):
                    event_type = line[6:].strip()
                elif line.startswith("data:"):
                    data_lines.append(line[5:].strip())
                elif not line and data_lines:
                    event = json.loads("\n".join(data_lines))
                    received_type = event_type or event.get("type")
                    if received_type == "response.output_text.delta":
                        chunks.append(event.get("delta", ""))
                    elif received_type == "response.function_call_arguments.delta":
                        item_id = event.get("item_id")
                        if isinstance(item_id, str):
                            function_arguments[item_id] = function_arguments.get(item_id, "") + event.get("delta", "")
                    elif received_type == "response.output_item.done":
                        item = event.get("item", {})
                        if item.get("type") == "function_call" and function_call is None:
                            item_id = item.get("id")
                            function_call = {
                                "operation": item.get("name", ""),
                                "arguments": item.get("arguments") or function_arguments.get(item_id, "{}"),
                            }
                    elif received_type == "response.completed":
                        completed = True
                        response_payload = event.get("response", event)
                        response_usage = response_payload.get("usage", {}) if isinstance(response_payload, dict) else {}
                        usage = {
                            "inputTokens": response_usage.get("input_tokens"),
                            "outputTokens": response_usage.get("output_tokens"),
                            "totalTokens": response_usage.get("total_tokens"),
                        }
                    elif received_type in {"response.failed", "response.incomplete", "error"}:
                        return {"status": "FAILED", "code": "PROVIDER_INCOMPLETE_RESPONSE"}
                    event_type, data_lines = None, []
    if not completed:
        return {"status": "FAILED", "code": "PROVIDER_INCOMPLETE_RESPONSE"}
    if function_call is not None:
        return {"status": "COMPLETED", "kind": "TOOL_CALL", "functionCall": function_call, "usage": usage}
    return {"status": "COMPLETED", "kind": "FINAL", "outputText": "".join(chunks), "usage": usage}


def _binding_registry():
    from app.api.bindings import get_binding_registry

    return get_binding_registry()
