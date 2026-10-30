from __future__ import annotations

import base64
from dataclasses import dataclass
from datetime import datetime, timezone
import sqlite3
from pathlib import Path

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


class CredentialStoreError(RuntimeError):
    """Raised without exposing credential material."""


@dataclass(frozen=True)
class StoredCredential:
    handle: str
    provider: str
    access_token: str
    refresh_token: str | None
    access_token_expires_at: datetime | None
    scopes: str = ""
    client_id: str | None = None
    subject: str | None = None
    id_token: str | None = None
    kind: str = "OAUTH"


class EncryptedCredentialStore:
    def __init__(self, path: str, encoded_key: str | None) -> None:
        if not encoded_key:
            raise CredentialStoreError("credential encryption is not configured")
        try:
            key = base64.urlsafe_b64decode(encoded_key.encode("ascii"))
        except (ValueError, UnicodeError) as error:
            raise CredentialStoreError("credential encryption key is invalid") from error
        if len(key) not in {16, 24, 32}:
            raise CredentialStoreError("credential encryption key has invalid length")
        self._key = key
        self._path = path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS provider_credentials (
                    handle TEXT PRIMARY KEY,
                    provider TEXT NOT NULL,
                    access_token BLOB NOT NULL,
                    refresh_token BLOB,
                    access_token_expires_at TEXT,
                    created_at TEXT NOT NULL
                )
                """
            )
            try:
                connection.execute("ALTER TABLE provider_credentials ADD COLUMN scopes TEXT NOT NULL DEFAULT ''")
            except sqlite3.OperationalError:
                pass
            for column, definition in (("client_id", "TEXT"), ("subject", "TEXT"), ("id_token", "BLOB")):
                try:
                    connection.execute(
                        f"ALTER TABLE provider_credentials ADD COLUMN {column} {definition}"
                    )
                except sqlite3.OperationalError:
                    pass
            try:
                connection.execute("ALTER TABLE provider_credentials ADD COLUMN credential_kind TEXT NOT NULL DEFAULT 'OAUTH'")
            except sqlite3.OperationalError:
                pass

    def put(self, credential: StoredCredential) -> None:
        if not credential.handle or not credential.provider or not credential.access_token:
            raise CredentialStoreError("credential metadata is incomplete")
        if credential.kind not in {"OAUTH", "API_KEY"}:
            raise CredentialStoreError("credential kind is invalid")
        import secrets

        nonce = secrets.token_bytes(12)
        cipher = AESGCM(self._key)
        access = nonce + cipher.encrypt(nonce, credential.access_token.encode(), credential.handle.encode())
        refresh = None
        if credential.refresh_token is not None:
            refresh_nonce = secrets.token_bytes(12)
            refresh = refresh_nonce + cipher.encrypt(
                refresh_nonce, credential.refresh_token.encode(), credential.handle.encode()
            )
        id_token = None
        if credential.id_token is not None:
            id_token_nonce = secrets.token_bytes(12)
            id_token = id_token_nonce + cipher.encrypt(
                id_token_nonce, credential.id_token.encode(), credential.handle.encode()
            )
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO provider_credentials
                (handle, provider, access_token, refresh_token, access_token_expires_at,
                 scopes, client_id, subject, id_token, credential_kind, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(handle) DO UPDATE SET
                  provider=excluded.provider,
                  access_token=excluded.access_token,
                  refresh_token=excluded.refresh_token,
                  access_token_expires_at=excluded.access_token_expires_at,
                  scopes=excluded.scopes,
                  client_id=excluded.client_id,
                  subject=excluded.subject,
                  id_token=excluded.id_token,
                  credential_kind=excluded.credential_kind
                """,
                (credential.handle, credential.provider, access, refresh,
                 credential.access_token_expires_at.isoformat()
                 if credential.access_token_expires_at else None,
                   credential.scopes, credential.client_id, credential.subject, id_token,
                   credential.kind,
                 datetime.now(timezone.utc).isoformat()),
            )

    def get(self, handle: str, provider: str) -> StoredCredential:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT provider, access_token, refresh_token, access_token_expires_at, scopes, "
                "client_id, subject, id_token, credential_kind "
                "FROM provider_credentials WHERE handle = ? AND provider = ?",
                (handle, provider),
            ).fetchone()
        if row is None:
            raise CredentialStoreError("credential is not available")
        try:
            access = self._decrypt(handle, row[1])
            refresh = self._decrypt(handle, row[2]) if row[2] else None
            id_token = self._decrypt(handle, row[7]) if row[7] else None
        except Exception as error:
            raise CredentialStoreError("credential could not be decrypted") from error
        return StoredCredential(
            handle=handle,
            provider=row[0],
            access_token=access,
            refresh_token=refresh,
            access_token_expires_at=datetime.fromisoformat(row[3]) if row[3] else None,
            scopes=row[4] or "",
            client_id=row[5],
            subject=row[6],
            id_token=id_token,
            kind=row[8] or "OAUTH",
        )

    def delete(self, handle: str) -> None:
        with self._connect() as connection:
            connection.execute("DELETE FROM provider_credentials WHERE handle = ?", (handle,))

    def replace_tokens(self, handle: str, access_token: str,
                       refresh_token: str | None, expires_at: datetime | None) -> None:
        current = self.get(handle, "chatgpt")
        self.put(StoredCredential(
            handle=handle,
            provider=current.provider,
            access_token=access_token,
            refresh_token=refresh_token if refresh_token is not None else current.refresh_token,
            access_token_expires_at=expires_at,
            scopes=current.scopes,
            client_id=current.client_id,
            subject=current.subject,
            id_token=current.id_token,
            kind=current.kind,
        ))

    def _decrypt(self, handle: str, value: bytes) -> str:
        nonce, ciphertext = value[:12], value[12:]
        return AESGCM(self._key).decrypt(nonce, ciphertext, handle.encode()).decode()

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, timeout=5)
