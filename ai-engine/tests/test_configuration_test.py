from base64 import urlsafe_b64encode

import httpx
import pytest

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore, StoredCredential
from app.services.configuration_test import ConfigurationTestService


def _service(tmp_path, handler):
    settings = Settings(
        service_auth_secret="service-secret",
        credential_store_path=str(tmp_path / "credentials.sqlite3"),
        binding_store_path=str(tmp_path / "bindings.sqlite3"),
        credential_encryption_key=urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode(),
        chatgpt_models_endpoint="https://example.test/models",
    )
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    store = EncryptedCredentialStore(settings.credential_store_path, settings.credential_encryption_key)
    store.put(StoredCredential("handle", "anthropic", "secret", None, None, kind="API_KEY"))
    store.put(StoredCredential("oauth-handle", "chatgpt", "oauth-secret", None, None, kind="OAUTH"))
    return ConfigurationTestService(settings, store, client), client


@pytest.mark.asyncio
async def test_anthropic_configuration_test_returns_success_without_response_body(tmp_path):
    async def handler(request):
        return httpx.Response(200, request=request, json={"data": [{"id": "claude-opus-5-5"}]})

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="API_KEY",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result == {"status": "SUCCESS", "diagnosticCode": "CONFIGURATION_VALID"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_distinguishes_model_and_credential_failures(tmp_path):
    async def handler(request):
        return httpx.Response(200, request=request, json={"data": []})

    service, client = _service(tmp_path, handler)
    unavailable = await service.test(provider="anthropic", authentication_method="API_KEY",
                                     model="missing", credential_handle="handle")
    invalid = await service.test(provider="anthropic", authentication_method="API_KEY",
                                 model="claude-opus-5-5", credential_handle="unknown")
    assert unavailable["status"] == "MODEL_UNAVAILABLE"
    assert invalid["status"] == "CREDENTIAL_INVALID"
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_maps_provider_timeout(tmp_path):
    async def handler(request):
        raise httpx.ReadTimeout("timed out", request=request)

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="API_KEY",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result["status"] == "TIMEOUT"
    assert "secret" not in str(result)
    await client.aclose()


@pytest.mark.asyncio
async def test_chatgpt_configuration_test_uses_oauth_without_persisting_prompt_data(tmp_path):
    async def handler(request):
        assert request.method == "GET"
        assert request.headers["authorization"] == "Bearer oauth-secret"
        assert request.content == b""
        return httpx.Response(200, request=request, json={"data": [{"id": "gpt-5.6-luna"}]})

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="openai_chatgpt", authentication_method="OAUTH",
                                model="gpt-5.6-luna", credential_handle="oauth-handle")
    assert result == {"status": "SUCCESS", "diagnosticCode": "CONFIGURATION_VALID"}
    await client.aclose()


@pytest.mark.asyncio
async def test_chatgpt_configuration_test_maps_missing_model(tmp_path):
    async def handler(request):
        return httpx.Response(200, request=request, json={"data": [{"id": "other-model"}]})

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="openai_chatgpt", authentication_method="OAUTH",
                                model="gpt-5.6-luna", credential_handle="oauth-handle")
    assert result == {"status": "MODEL_UNAVAILABLE", "diagnosticCode": "MODEL_UNAVAILABLE"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_maps_provider_failure(tmp_path):
    async def handler(request):
        return httpx.Response(503, request=request)

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="API_KEY",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result == {"status": "PROVIDER_UNAVAILABLE", "diagnosticCode": "PROVIDER_ERROR"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_rejects_oversized_model_listing(tmp_path):
    async def handler(request):
        return httpx.Response(200, request=request, content=b"{" + b"x" * (65 * 1024) + b"}")

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="API_KEY",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result == {"status": "PROVIDER_UNAVAILABLE", "diagnosticCode": "PROVIDER_RESPONSE_TOO_LARGE"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_rejects_incomplete_model_listing(tmp_path):
    async def handler(request):
        return httpx.Response(200, request=request, json={"has_more": True, "data": []})

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="API_KEY",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result == {"status": "PROVIDER_UNAVAILABLE", "diagnosticCode": "MODEL_LIST_INCOMPLETE"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_maps_provider_credential_rejection(tmp_path):
    async def handler(request):
        return httpx.Response(401, request=request)

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="openai_chatgpt", authentication_method="OAUTH",
                                model="gpt-5.6-luna", credential_handle="oauth-handle")
    assert result == {"status": "CREDENTIAL_INVALID", "diagnosticCode": "CREDENTIAL_REJECTED"}
    await client.aclose()


@pytest.mark.asyncio
async def test_configuration_test_rejects_provider_authentication_mismatch(tmp_path):
    async def handler(request):
        raise AssertionError("provider must not be contacted")

    service, client = _service(tmp_path, handler)
    result = await service.test(provider="anthropic", authentication_method="OAUTH",
                                model="claude-opus-5-5", credential_handle="handle")
    assert result == {"status": "CONFIGURATION_INVALID", "diagnosticCode": "UNSUPPORTED_CONFIGURATION"}
    await client.aclose()
