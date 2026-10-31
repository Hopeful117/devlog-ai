from base64 import urlsafe_b64encode
from urllib.parse import parse_qs, urlparse
from datetime import datetime, timedelta, timezone
from concurrent.futures import ThreadPoolExecutor
import httpx

import pytest

from app.core.config import Settings
from app.security.credential_store import CredentialStoreError, EncryptedCredentialStore, StoredCredential
from app.services.chatgpt_oauth import (
    ChatGptOAuthService, OAuthError, OAuthSessionStore, OAuthTransaction,
    validate_connection_intent,
)


def make_service(tmp_path, enabled=True, callback_attempts=5):
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
        service_auth_secret="service-secret",
        core_callback_max_attempts=callback_attempts,
        core_callback_initial_delay_ms=0,
        core_callback_max_delay_ms=0,
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


@pytest.mark.asyncio
async def test_refresh_if_expired_replaces_access_token(tmp_path):
    class Response:
        status_code = 200

        def json(self):
            return {"access_token": "refreshed", "expires_in": 3600}

    class Client:
        async def post(self, url, data):
            assert url == "https://auth.example/token"
            assert data["grant_type"] == "refresh_token"
            return Response()

    service = make_service(tmp_path)
    service._http_client = Client()
    service._credentials.put(StoredCredential(
        "chatgpt-expired", "chatgpt", "old", "refresh", datetime.now(timezone.utc) - timedelta(seconds=1),
        "resource.invoke chatgpt.tokens.use.direct", "client-id", "subject",
    ))

    refreshed = await service.refresh_if_expired("chatgpt-expired")

    assert refreshed.access_token == "refreshed"
    assert refreshed.refresh_token == "refresh"


@pytest.mark.asyncio
async def test_refresh_if_expired_requires_reauthorization_without_refresh_token(tmp_path):
    service = make_service(tmp_path)
    service._credentials.put(StoredCredential(
        "chatgpt-expired", "chatgpt", "old", None, datetime.now(timezone.utc) - timedelta(seconds=1),
        "resource.invoke chatgpt.tokens.use.direct", "client-id", "subject",
    ))

    with pytest.raises(OAuthError, match="requires reauthorization"):
        await service.refresh_if_expired("chatgpt-expired")


def test_oauth_transaction_can_be_released_after_a_failed_exchange(tmp_path):
    service = make_service(tmp_path)
    transaction = OAuthTransaction(
        "state", "browser-session", "verifier", "nonce", "https://app.example/callback",
        "scope", "client", datetime.now(timezone.utc),
    )
    service._sessions.save_transaction(transaction)

    consumed = service._sessions.consume_transaction("state", "browser-session", 300)
    assert consumed.state == "state"
    with pytest.raises(OAuthError, match="invalid OAuth transaction"):
        service._sessions.consume_transaction("state", "browser-session", 300)

    service._sessions.release_transaction("state", "browser-session")
    assert service._sessions.consume_transaction("state", "browser-session", 300).state == "state"


def test_oauth_transaction_claim_is_single_use_under_concurrent_callbacks(tmp_path):
    service = make_service(tmp_path)
    transaction = OAuthTransaction(
        "state", "browser-session", "verifier", "nonce", "https://app.example/callback",
        "scope", "client", datetime.now(timezone.utc),
    )
    service._sessions.save_transaction(transaction)

    def claim() -> bool:
        try:
            service._sessions.consume_transaction("state", "browser-session", 300)
            return True
        except OAuthError:
            return False

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(lambda _: claim(), range(2)))

    assert sorted(results) == [False, True]


@pytest.mark.asyncio
async def test_core_callback_retries_with_fresh_replay_identity(tmp_path, monkeypatch):
    service = make_service(tmp_path, callback_attempts=2)

    class Response:
        def __init__(self, status_code):
            self.status_code = status_code

        @property
        def is_success(self):
            return self.status_code < 400

    class Client:
        def __init__(self, *args, **kwargs):
            self.headers = []
            self.responses = iter((Response(503), Response(204)))

        async def __aenter__(self):
            return self

        async def __aexit__(self, *args):
            return None

        async def post(self, path, *, content, headers):
            self.headers.append(headers)
            return next(self.responses)

    client = Client()
    monkeypatch.setattr("app.services.chatgpt_oauth.httpx.AsyncClient", lambda **kwargs: client)

    await service.complete_core_configuration("intent-id", "completion-code")

    assert client.headers[0]["X-DevLog-Service-Nonce"] != client.headers[1]["X-DevLog-Service-Nonce"]
    assert client.headers[0]["X-DevLog-Service-Request-Id"] != client.headers[1]["X-DevLog-Service-Request-Id"]


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


@pytest.mark.asyncio
async def test_callback_does_not_trust_browser_supplied_scopes(tmp_path, monkeypatch):
    service = make_service(tmp_path)
    transaction = OAuthTransaction(
        "state", "browser-session", "verifier", "nonce", "https://app.example/callback",
        "requested.scope", "client", datetime.now(timezone.utc),
    )
    service._sessions.save_transaction(transaction)

    class Response:
        status_code = 200

        def json(self):
            return {"access_token": "access", "id_token": "id-token"}

    class Client:
        async def post(self, *args, **kwargs):
            return Response()

    service._http_client = Client()
    monkeypatch.setattr(service, "_validate_id_token", lambda *args: {"sub": "subject"})

    with pytest.raises(OAuthError, match="did not grant scopes"):
        await service.callback("browser-session", "state", "authorization-code",
                               granted_scopes="forged.scope")


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


def test_connection_intent_signature_is_required_and_expiry_bound(tmp_path):
    service = make_service(tmp_path)
    expires = int((datetime.now(timezone.utc) + timedelta(minutes=5)).timestamp())
    import uuid
    intent_id = str(uuid.uuid4())
    import hmac
    import hashlib
    signature = hmac.new(
        b"", b"", hashlib.sha256
    ).hexdigest()

    assert not validate_connection_intent(None, intent_id, "openai_chatgpt", expires, "principal-1", signature)
    assert not validate_connection_intent("secret", intent_id, "openai_chatgpt", expires, "principal-1", signature)
    with pytest.raises(OAuthError, match="invalid OAuth connection intent"):
        service.start("browser-session", intent_id, "openai_chatgpt", expires, signature, "principal-1")


def test_valid_connection_intent_is_bound_to_oauth_transaction(tmp_path):
    service = make_service(tmp_path)
    expires = int((datetime.now(timezone.utc) + timedelta(minutes=5)).timestamp())
    import uuid
    intent_id = str(uuid.uuid4())
    import hmac
    import hashlib
    principal_binding = hmac.new(
        b"service-secret", b"principal-1", hashlib.sha256
    ).hexdigest()
    signature = hmac.new(
        b"service-secret", f"{intent_id}\nopenai_chatgpt\n{expires}\n{principal_binding}".encode(), hashlib.sha256
    ).hexdigest()
    location = service.start("browser-session", intent_id, "openai_chatgpt", expires, signature, principal_binding)
    state = parse_qs(urlparse(location).query)["state"][0]
    transaction = service._sessions.consume_transaction(state, "browser-session", 300)

    assert transaction.intent_id == intent_id
    assert transaction.intent_signature == signature
    assert transaction.intent_principal_id == principal_binding


def test_completion_is_stored_without_secret_material(tmp_path):
    service = make_service(tmp_path)
    service._sessions.save_completion(
        "completion-code", "intent-id", "opaque-handle", "subject", "resource.invoke", "client"
    )

    result = service.redeem_completion("intent-id", "completion-code")
    assert service.redeem_completion("intent-id", "completion-code") == result

    assert result == {
        "credentialHandle": "opaque-handle",
        "subject": "subject",
        "scopes": "resource.invoke",
        "clientId": "client",
    }
    service.acknowledge_completion("intent-id", "completion-code")
    with pytest.raises(OAuthError, match="invalid OAuth completion"):
        service.redeem_completion("intent-id", "completion-code")


def test_completion_compensation_deletes_the_pending_credential(tmp_path):
    service = make_service(tmp_path)
    service._credentials.put(StoredCredential(
        handle="opaque-handle", provider="chatgpt", access_token="access",
        refresh_token=None, access_token_expires_at=None,
    ))
    service._sessions.save_completion(
        "completion-code", "intent-id", "opaque-handle", "subject", "resource.invoke", "client"
    )

    service.compensate_completion("intent-id", "completion-code")

    with pytest.raises(CredentialStoreError):
        service._credentials.get("opaque-handle", "chatgpt")
    service.compensate_completion("intent-id", "completion-code")


def test_completion_redeem_is_idempotent_under_concurrent_requests(tmp_path):
    service = make_service(tmp_path)
    service._sessions.save_completion(
        "completion-code", "intent-id", "opaque-handle", "subject", "resource.invoke", "client"
    )

    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(
            lambda _: service.redeem_completion("intent-id", "completion-code"), range(2)
        ))

    assert results[0] == results[1]


def test_disconnect_deletes_local_credential_when_core_notification_fails(tmp_path, monkeypatch):
    service = make_service(tmp_path)
    service._credentials.put(StoredCredential(
        handle="opaque-handle", provider="chatgpt", access_token="access",
        refresh_token=None, access_token_expires_at=None,
    ))
    service._sessions.set_session("browser-session", "opaque-handle", "IDENTITY_AUTHORIZED")
    monkeypatch.setattr(
        service, "_notify_core_revocation",
        lambda _: (_ for _ in ()).throw(OAuthError("disconnect unavailable")),
    )

    with pytest.raises(OAuthError, match="disconnect could not be completed"):
        service.disconnect("browser-session")

    assert service._sessions.get_session("browser-session")[1] == "DISCONNECTED"
    with pytest.raises(CredentialStoreError):
        service._credentials.get("opaque-handle", "chatgpt")
