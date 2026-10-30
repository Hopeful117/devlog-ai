from base64 import urlsafe_b64encode
from datetime import datetime, timedelta, timezone

import pytest

from app.security.credential_store import (
    CredentialStoreError,
    EncryptedCredentialStore,
    StoredCredential,
)


def key() -> str:
    return urlsafe_b64encode(b"0123456789abcdef0123456789abcdef").decode()


def credential() -> StoredCredential:
    return StoredCredential(
        handle="vault-ref-123",
        provider="chatgpt",
        access_token="access-secret",
        refresh_token="refresh-secret",
        access_token_expires_at=datetime.now(timezone.utc) + timedelta(minutes=5),
        scopes="openid resource.invoke chatgpt.tokens.use.direct",
        client_id="oaiapp_test",
        subject="openai-subject",
        id_token="id-token-secret",
    )


def test_credential_round_trip_is_encrypted(tmp_path):
    path = tmp_path / "credentials.sqlite3"
    store = EncryptedCredentialStore(str(path), key())
    value = credential()
    store.put(value)

    assert store.get("vault-ref-123", "chatgpt") == value
    assert b"access-secret" not in path.read_bytes()
    assert b"refresh-secret" not in path.read_bytes()
    assert b"id-token-secret" not in path.read_bytes()


def test_wrong_key_fails_closed(tmp_path):
    path = tmp_path / "credentials.sqlite3"
    EncryptedCredentialStore(str(path), key()).put(credential())
    wrong = EncryptedCredentialStore(
        str(path), urlsafe_b64encode(b"fedcba9876543210fedcba9876543210").decode()
    )

    with pytest.raises(CredentialStoreError, match="could not be decrypted"):
        wrong.get("vault-ref-123", "chatgpt")


def test_missing_key_is_rejected(tmp_path):
    with pytest.raises(CredentialStoreError, match="not configured"):
        EncryptedCredentialStore(str(tmp_path / "credentials.sqlite3"), None)
