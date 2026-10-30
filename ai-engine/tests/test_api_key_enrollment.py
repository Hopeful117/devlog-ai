from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone
import hashlib
import hmac
from uuid import uuid4

import httpx
import pytest

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore
from app.services.api_key_enrollment import ApiKeyEnrollmentError, ApiKeyEnrollmentService


def _service(tmp_path, response_status=200):
    settings = Settings(
        service_auth_secret="service-secret",
        credential_store_path=str(tmp_path / "credentials.sqlite3"),
        binding_store_path=str(tmp_path / "bindings.sqlite3"),
        credential_encryption_key=urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode(),
    )
    async def handler(request):
        return httpx.Response(response_status, request=request, json={"data": []})
    client = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    return settings, ApiKeyEnrollmentService(
        settings, EncryptedCredentialStore(settings.credential_store_path,
                                           settings.credential_encryption_key),
        http_client=client,
    ), client


def _claims(settings, method="API_KEY", expires=None):
    intent_id = str(uuid4())
    issued_at = int(datetime.now(timezone.utc).timestamp())
    expires = expires or int((datetime.now(timezone.utc) + timedelta(minutes=5)).timestamp())
    binding = "principal-binding"
    payload = f"{intent_id}\nanthropic\n{method}\n{issued_at}\n{expires}\n{binding}".encode()
    signature = hmac.new(settings.service_auth_secret.encode(), payload, hashlib.sha256).hexdigest()
    return dict(intent_id=intent_id, provider="anthropic", authentication_method=method,
                issued_at=issued_at, expires_at=expires, principal_binding=binding, signature=signature)


@pytest.mark.asyncio
async def test_enrollment_encrypts_key_and_returns_only_handle(tmp_path):
    settings, service, client = _service(tmp_path)
    claims = _claims(settings)
    result = await service.enroll(**claims, api_key="sk-ant-secret")

    assert result["provider"] == "anthropic"
    assert "sk-ant-secret" not in str(result)
    assert b"sk-ant-secret" not in (tmp_path / "credentials.sqlite3").read_bytes()
    assert service._credentials.get(result["credentialHandle"], "anthropic").kind == "API_KEY"
    await client.aclose()


@pytest.mark.asyncio
async def test_intent_is_single_use_and_mismatch_is_rejected(tmp_path):
    settings, service, client = _service(tmp_path)
    claims = _claims(settings)
    await service.enroll(**claims, api_key="sk-ant-secret")
    with pytest.raises(ApiKeyEnrollmentError, match="already been consumed"):
        await service.enroll(**claims, api_key="sk-ant-secret")
    mismatch = _claims(settings, method="OAUTH")
    with pytest.raises(ApiKeyEnrollmentError, match="invalid enrollment intent"):
        await service.enroll(**mismatch, api_key="sk-ant-secret")
    await client.aclose()


@pytest.mark.asyncio
async def test_rejected_key_is_bounded_and_does_not_store(tmp_path):
    settings, service, client = _service(tmp_path, response_status=401)
    with pytest.raises(ApiKeyEnrollmentError, match="rejected"):
        await service.enroll(**_claims(settings), api_key="sk-ant-secret")
    await client.aclose()
