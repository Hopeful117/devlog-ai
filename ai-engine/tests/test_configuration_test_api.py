import time
from types import SimpleNamespace

import httpx
import pytest

from app.api.bindings import get_authenticator
from app.main import create_app
from app.security.service_auth import HmacServiceAuthenticator


class StubConfigurationTestService:
    async def test(self, **kwargs):
        assert kwargs["provider"] == "anthropic"
        assert kwargs["model"] == "claude-opus-5-5"
        return {"status": "SUCCESS", "diagnosticCode": "CONFIGURATION_VALID"}


def _client(monkeypatch):
    app = create_app()
    authenticator = HmacServiceAuthenticator("service-secret")
    app.dependency_overrides[get_authenticator] = lambda: authenticator
    monkeypatch.setattr(
        "app.api.configuration_test.get_configuration_test_service",
        lambda: StubConfigurationTestService(),
    )
    return app, authenticator


def test_default_service_authenticator_is_process_scoped(monkeypatch):
    settings = SimpleNamespace(
        service_auth_secret="service-secret",
        sca_callback_secret=None,
        service_auth_max_clock_skew_seconds=60,
    )
    monkeypatch.setattr("app.api.bindings.get_settings", lambda: settings)
    get_authenticator.cache_clear()
    try:
        assert get_authenticator() is get_authenticator()
    finally:
        get_authenticator.cache_clear()


@pytest.mark.asyncio
async def test_configuration_test_endpoint_requires_valid_service_authentication(monkeypatch):
    app, authenticator = _client(monkeypatch)
    body = b'{"provider":"anthropic","authenticationMethod":"API_KEY","model":"claude-opus-5-5","credentialHandle":"opaque-handle"}'
    headers = authenticator.sign(
        method="POST", path="/api/v1/ai/configuration/test", body=body,
        timestamp=int(time.time()), nonce="nonce-1", request_id="request-1",
    )
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post("/api/v1/ai/configuration/test", content=body, headers=headers)
        invalid = await client.post("/api/v1/ai/configuration/test", content=body,
                                    headers={**headers, "X-DevLog-Service-Signature": "sha256=" + "0" * 64,
                                             "X-DevLog-Service-Nonce": "nonce-2", "X-DevLog-Service-Request-Id": "request-2"})
    assert response.status_code == 200
    assert response.json() == {"status": "SUCCESS", "diagnosticCode": "CONFIGURATION_VALID"}
    assert invalid.status_code == 401


@pytest.mark.asyncio
async def test_configuration_test_endpoint_returns_service_unavailable_without_authenticator(monkeypatch):
    app = create_app()
    app.dependency_overrides[get_authenticator] = lambda: None
    monkeypatch.setattr(
        "app.api.configuration_test.get_configuration_test_service",
        lambda: StubConfigurationTestService(),
    )
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
        response = await client.post(
            "/api/v1/ai/configuration/test",
            json={"provider": "anthropic", "authenticationMethod": "API_KEY",
                  "model": "claude-opus-5-5", "credentialHandle": "opaque-handle"},
        )
    assert response.status_code == 503
    assert response.json() == {"code": "SERVICE_AUTHENTICATION_UNAVAILABLE"}
