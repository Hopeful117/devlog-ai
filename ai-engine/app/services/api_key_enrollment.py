from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import hmac
import secrets
import sqlite3
from pathlib import Path
from uuid import UUID

import httpx

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore, StoredCredential


class ApiKeyEnrollmentError(ValueError):
    """Safe enrollment failure; never includes the submitted key or provider body."""


class ApiKeyEnrollmentService:
    def __init__(self, settings: Settings, credentials: EncryptedCredentialStore,
                 intent_store_path: str | None = None,
                 http_client: httpx.AsyncClient | None = None) -> None:
        self._settings = settings
        self._credentials = credentials
        self._path = intent_store_path or settings.binding_store_path
        self._http_client = http_client
        if self._path != ":memory:":
            Path(self._path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                "CREATE TABLE IF NOT EXISTS api_key_enrollment_intents "
                "(intent_id TEXT PRIMARY KEY, consumed_at TEXT NOT NULL)"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS api_key_credential_intents "
                "(handle TEXT PRIMARY KEY, intent_id TEXT NOT NULL, principal_binding TEXT NOT NULL)"
            )

    async def enroll(self, *, intent_id: str, provider: str, authentication_method: str,
                     issued_at: int, expires_at: int, principal_binding: str, signature: str,
                     api_key: str) -> dict[str, str]:
        if provider != "anthropic" or authentication_method != "API_KEY":
            raise ApiKeyEnrollmentError("invalid enrollment intent")
        now = int(datetime.now(timezone.utc).timestamp())
        if issued_at > now + 30 or expires_at <= now or expires_at <= issued_at \
                or now - issued_at > 600 \
                or not self._valid_signature(intent_id, provider, authentication_method,
                                              issued_at, expires_at, principal_binding, signature):
            raise ApiKeyEnrollmentError("invalid enrollment intent")
        try:
            UUID(intent_id)
        except ValueError as error:
            raise ApiKeyEnrollmentError("invalid enrollment intent") from error
        if not api_key or len(api_key) > 4096:
            raise ApiKeyEnrollmentError("invalid API key")
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            try:
                connection.execute(
                    "INSERT INTO api_key_enrollment_intents(intent_id, consumed_at) VALUES (?, ?)",
                    (intent_id, datetime.now(timezone.utc).isoformat()),
                )
            except sqlite3.IntegrityError as error:
                raise ApiKeyEnrollmentError("enrollment intent has already been consumed") from error
        await self._validate_provider_key(api_key)
        handle = "anthropic-key-" + secrets.token_urlsafe(24)
        try:
            self._credentials.put(StoredCredential(
                handle=handle, provider="anthropic", access_token=api_key,
                refresh_token=None, access_token_expires_at=None, kind="API_KEY",
            ))
            with self._connect() as connection:
                connection.execute(
                    "INSERT INTO api_key_credential_intents(handle, intent_id, principal_binding) "
                    "VALUES (?, ?, ?)", (handle, intent_id, principal_binding),
                )
        except Exception as error:
            self._credentials.delete(handle)
            raise ApiKeyEnrollmentError("API key could not be stored") from error
        return {"credentialHandle": handle, "provider": "anthropic", "authenticationMethod": "API_KEY"}

    def compensate(self, handle: str) -> None:
        self._credentials.delete(handle)

    async def _validate_provider_key(self, api_key: str) -> None:
        client = self._http_client or httpx.AsyncClient(timeout=min(self._settings.llm_timeout_seconds, 10.0))
        try:
            response = await client.get(
                "https://api.anthropic.com/v1/models",
                headers={"x-api-key": api_key, "anthropic-version": "2023-06-01"},
            )
            if not 200 <= response.status_code < 300:
                raise ApiKeyEnrollmentError("API key was rejected by Anthropic")
        except httpx.TimeoutException as error:
            raise ApiKeyEnrollmentError("API key validation timed out") from error
        except httpx.HTTPError as error:
            raise ApiKeyEnrollmentError("API key validation failed") from error
        finally:
            if self._http_client is None:
                await client.aclose()

    def _valid_signature(self, intent_id: str, provider: str, method: str,
                         issued_at: int, expires_at: int, principal_binding: str, signature: str) -> bool:
        secret = self._settings.service_auth_secret or self._settings.sca_callback_secret
        if not secret or len(signature) != 64:
            return False
        payload = f"{intent_id}\n{provider}\n{method}\n{issued_at}\n{expires_at}\n{principal_binding}".encode()
        expected = hmac.new(secret.encode(), payload, hashlib.sha256).hexdigest()
        return hmac.compare_digest(expected, signature)

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, timeout=5)
