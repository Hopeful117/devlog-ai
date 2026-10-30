from base64 import urlsafe_b64encode
from urllib.parse import parse_qs, urlparse

import pytest

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore, StoredCredential
from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthError, OAuthSessionStore


def make_service(tmp_path, enabled=True):
    settings = Settings(
        chatgpt_oauth_enabled=enabled,
        chatgpt_client_id="client-id",
        chatgpt_authorization_endpoint="https://auth.example/authorize",
        chatgpt_token_endpoint="https://auth.example/token",
        chatgpt_redirect_uri="https://app.example/callback",
        chatgpt_issuer="https://auth.example",
        chatgpt_jwks_url="https://auth.example/jwks",
        chatgpt_agent_name="DevLog Test",
        chatgpt_ext_agent_host_id="urn:uuid:test-host",
        credential_store_path=str(tmp_path / "credentials.sqlite3"),
        credential_encryption_key=urlsafe_b64encode(
            b"0123456789abcdef0123456789abcdef"
        ).decode(),
        oauth_store_path=str(tmp_path / "oauth.sqlite3"),
    )
    return ChatGptOAuthService(
        settings,
        OAuthSessionStore(settings.oauth_store_path),
        EncryptedCredentialStore(settings.credential_store_path,
                                 settings.credential_encryption_key),
    )


def test_start_creates_pkce_transaction(tmp_path):
    service = make_service(tmp_path)

    location = service.start("browser-session")
    query = parse_qs(urlparse(location).query)

    assert query["response_type"] == ["code"]
    assert query["code_challenge_method"] == ["S256"]
    assert query["state"]
    assert query["nonce"]
    assert query["code_challenge"]
    assert query["redirect_uri"] == ["https://app.example/callback"]
    assert query["client_id"] == ["dynamic_agent_client"]
    assert query["agent_name_hint"] == ["DevLog Test"]
    assert query["ext_agent_host_id"] == ["urn:uuid:test-host"]


@pytest.mark.asyncio
async def test_dynamic_registration_requires_issued_client_id(tmp_path):
    service = make_service(tmp_path)
    location = service.start("browser-session")
    state = parse_qs(urlparse(location).query)["state"][0]

    with pytest.raises(OAuthError, match="authorization could not be completed"):
        await service.callback("browser-session", state, "authorization-code")


def test_disabled_authorization_fails_closed(tmp_path):
    with pytest.raises(OAuthError, match="disabled"):
        make_service(tmp_path, enabled=False).start("browser-session")


def test_start_reuses_issued_client_id(tmp_path):
    service = make_service(tmp_path)
    service._credentials.put(StoredCredential(
        handle="chatgpt-account",
        provider="chatgpt",
        access_token="access",
        refresh_token="refresh",
        access_token_expires_at=None,
        client_id="oaiapp_issued",
        subject="subject",
        id_token="id-token",
    ))
    service._sessions.set_session("browser-session", "chatgpt-account", "IDENTITY_AUTHORIZED")

    query = parse_qs(urlparse(service.start("browser-session")).query)

    assert query["client_id"] == ["oaiapp_issued"]
    assert "agent_name_hint" not in query
    assert query["id_token_hint"] == ["id-token"]
