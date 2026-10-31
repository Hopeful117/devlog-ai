from __future__ import annotations

from enum import StrEnum
import json
import httpx

from app.core.config import Settings
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore
from app.services.provider_identity import engine_provider


class ConfigurationTestStatus(StrEnum):
    SUCCESS = "SUCCESS"
    CREDENTIAL_INVALID = "CREDENTIAL_INVALID"
    PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE"
    MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE"
    TIMEOUT = "TIMEOUT"
    CONFIGURATION_INVALID = "CONFIGURATION_INVALID"


class ConfigurationTestService:
    _MAX_MODEL_RESPONSE_BYTES = 64 * 1024
    def __init__(self, settings: Settings, credentials: EncryptedCredentialStore,
                 http_client: httpx.AsyncClient | None = None) -> None:
        self._settings = settings
        self._credentials = credentials
        self._http_client = http_client

    async def test(self, *, provider: str, authentication_method: str,
                   model: str, credential_handle: str | None) -> dict[str, str]:
        expected_authentication = {"anthropic": "API_KEY", "openai_chatgpt": "OAUTH"}
        if provider not in expected_authentication or authentication_method != expected_authentication[provider]:
            return self._result(ConfigurationTestStatus.CONFIGURATION_INVALID, "UNSUPPORTED_CONFIGURATION")
        if not model or not credential_handle:
            return self._result(ConfigurationTestStatus.CREDENTIAL_INVALID, "CREDENTIAL_MISSING")
        try:
            credential = self._credentials.get(credential_handle, engine_provider(provider))
        except CredentialStoreError:
            return self._result(ConfigurationTestStatus.CREDENTIAL_INVALID, "CREDENTIAL_INVALID")
        expected_kind = "API_KEY" if provider == "anthropic" else "OAUTH"
        if credential.kind != expected_kind:
            return self._result(ConfigurationTestStatus.CREDENTIAL_INVALID, "CREDENTIAL_KIND_MISMATCH")
        if provider == "anthropic":
            return await self._test_anthropic(model, credential.access_token)
        return await self._test_chatgpt(model, credential.access_token)

    async def _test_anthropic(self, model: str, api_key: str) -> dict[str, str]:
        client = self._http_client or httpx.AsyncClient(
            timeout=self._settings.configuration_test_timeout_seconds
        )
        try:
            async with client.stream(
                "GET",
                "https://api.anthropic.com/v1/models",
                headers={"x-api-key": api_key, "anthropic-version": "2023-06-01"},
            ) as response:
                if response.status_code in {401, 403}:
                    return self._result(ConfigurationTestStatus.CREDENTIAL_INVALID, "CREDENTIAL_REJECTED")
                if response.status_code >= 500:
                    return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_ERROR")
                if not 200 <= response.status_code < 300:
                    return self._result(ConfigurationTestStatus.CONFIGURATION_INVALID, "PROVIDER_REQUEST_REJECTED")
                body = await self._read_bounded(response)
                if body is None:
                    return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_RESPONSE_TOO_LARGE")
                payload = json.loads(body)
            if payload.get("has_more"):
                return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "MODEL_LIST_INCOMPLETE")
            models = payload.get("data", [])
            available = {item.get("id") for item in models if isinstance(item, dict)}
            if model not in available:
                return self._result(ConfigurationTestStatus.MODEL_UNAVAILABLE, "MODEL_UNAVAILABLE")
            return self._result(ConfigurationTestStatus.SUCCESS, "CONFIGURATION_VALID")
        except httpx.TimeoutException:
            return self._result(ConfigurationTestStatus.TIMEOUT, "PROVIDER_TIMEOUT")
        except (httpx.HTTPError, ValueError, TypeError):
            return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_UNAVAILABLE")
        finally:
            if self._http_client is None:
                await client.aclose()

    async def _test_chatgpt(self, model: str, access_token: str) -> dict[str, str]:
        client = self._http_client or httpx.AsyncClient(
            timeout=self._settings.configuration_test_timeout_seconds
        )
        try:
            async with client.stream(
                "GET",
                self._settings.chatgpt_models_endpoint,
                headers={"Authorization": f"Bearer {access_token}"},
            ) as response:
                if response.status_code in {401, 403}:
                    return self._result(ConfigurationTestStatus.CREDENTIAL_INVALID, "CREDENTIAL_REJECTED")
                if response.status_code == 404:
                    return self._result(ConfigurationTestStatus.MODEL_UNAVAILABLE, "MODEL_UNAVAILABLE")
                if response.status_code >= 500:
                    return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_ERROR")
                if not 200 <= response.status_code < 300:
                    return self._result(ConfigurationTestStatus.CONFIGURATION_INVALID, "PROVIDER_REQUEST_REJECTED")
                body = await self._read_bounded(response)
                if body is None:
                    return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_RESPONSE_TOO_LARGE")
                payload = json.loads(body)
            if payload.get("has_more"):
                return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "MODEL_LIST_INCOMPLETE")
            models = payload.get("data", [])
            available = {item.get("id") for item in models if isinstance(item, dict)}
            if model not in available:
                return self._result(ConfigurationTestStatus.MODEL_UNAVAILABLE, "MODEL_UNAVAILABLE")
            return self._result(ConfigurationTestStatus.SUCCESS, "CONFIGURATION_VALID")
        except httpx.TimeoutException:
            return self._result(ConfigurationTestStatus.TIMEOUT, "PROVIDER_TIMEOUT")
        except (httpx.HTTPError, ValueError, TypeError):
            return self._result(ConfigurationTestStatus.PROVIDER_UNAVAILABLE, "PROVIDER_UNAVAILABLE")
        finally:
            if self._http_client is None:
                await client.aclose()

    @staticmethod
    async def _read_bounded(response: httpx.Response) -> bytes | None:
        content_length = response.headers.get("content-length")
        if content_length is not None and int(content_length) > ConfigurationTestService._MAX_MODEL_RESPONSE_BYTES:
            return None
        body = bytearray()
        async for chunk in response.aiter_bytes():
            body.extend(chunk)
            if len(body) > ConfigurationTestService._MAX_MODEL_RESPONSE_BYTES:
                return None
        return bytes(body)

    @staticmethod
    def _result(status: ConfigurationTestStatus, diagnostic_code: str) -> dict[str, str]:
        return {"status": status.value, "diagnosticCode": diagnostic_code}
