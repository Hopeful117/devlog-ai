from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import hashlib
import httpx
import jwt
import secrets
import sqlite3
from pathlib import Path
from urllib.parse import urlencode

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore, StoredCredential


class OAuthError(ValueError):
    """Public-safe OAuth failure without provider payloads or secrets."""


@dataclass(frozen=True)
class OAuthTransaction:
    state: str
    session_id: str
    verifier: str
    nonce: str
    redirect_uri: str
    scopes: str
    client_id: str
    created_at: datetime


class OAuthSessionStore:
    def __init__(self, path: str) -> None:
        self._path = path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                "CREATE TABLE IF NOT EXISTS oauth_transactions ("
                "state TEXT PRIMARY KEY, session_id TEXT NOT NULL, verifier TEXT NOT NULL, "
                "nonce TEXT NOT NULL, redirect_uri TEXT NOT NULL, scopes TEXT NOT NULL, "
                "client_id TEXT NOT NULL, "
                "created_at TEXT NOT NULL)"
            )
            try:
                connection.execute(
                    "ALTER TABLE oauth_transactions ADD COLUMN client_id TEXT NOT NULL "
                    "DEFAULT 'dynamic_agent_client'"
                )
            except sqlite3.OperationalError:
                pass
            connection.execute(
                "CREATE TABLE IF NOT EXISTS oauth_sessions ("
                "session_id TEXT PRIMARY KEY, credential_handle TEXT, status TEXT NOT NULL, "
                "scopes TEXT NOT NULL DEFAULT '')"
            )

    def save_transaction(self, transaction: OAuthTransaction) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT INTO oauth_transactions "
                "(state, session_id, verifier, nonce, redirect_uri, scopes, client_id, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                (transaction.state, transaction.session_id, transaction.verifier,
                 transaction.nonce, transaction.redirect_uri, transaction.scopes,
                 transaction.client_id,
                 transaction.created_at.isoformat()),
            )

    def consume_transaction(self, state: str, session_id: str,
                            ttl_seconds: int) -> OAuthTransaction:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT state, session_id, verifier, nonce, redirect_uri, scopes, client_id, created_at "
                "FROM oauth_transactions WHERE state = ?", (state,)
            ).fetchone()
            if row is None or row[1] != session_id:
                raise OAuthError("invalid OAuth transaction")
            connection.execute("DELETE FROM oauth_transactions WHERE state = ?", (state,))
        transaction = OAuthTransaction(*row[:7], datetime.fromisoformat(row[7]))
        if datetime.now(timezone.utc) - transaction.created_at > timedelta(seconds=ttl_seconds):
            raise OAuthError("expired OAuth transaction")
        return transaction

    def set_session(self, session_id: str, credential_handle: str | None,
                    status: str, scopes: str = "") -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT INTO oauth_sessions(session_id, credential_handle, status, scopes) "
                "VALUES (?, ?, ?, ?) ON CONFLICT(session_id) DO UPDATE SET "
                "credential_handle=excluded.credential_handle, status=excluded.status, "
                "scopes=excluded.scopes",
                (session_id, credential_handle, status, scopes),
            )

    def get_session(self, session_id: str) -> tuple[str | None, str, str]:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT credential_handle, status, scopes FROM oauth_sessions WHERE session_id = ?",
                (session_id,),
            ).fetchone()
        return (None, "DISCONNECTED", "") if row is None else tuple(row)

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self._path, timeout=5)


class ChatGptOAuthService:
    def __init__(self, settings: Settings, sessions: OAuthSessionStore,
                 credentials: EncryptedCredentialStore,
                 http_client: httpx.AsyncClient | None = None) -> None:
        self._settings = settings
        self._sessions = sessions
        self._credentials = credentials
        self._http_client = http_client

    def start(self, session_id: str) -> str:
        self._require_enabled()
        verifier = secrets.token_urlsafe(64)
        challenge = base64url(hashlib.sha256(verifier.encode()).digest())
        state = secrets.token_urlsafe(32)
        nonce = secrets.token_urlsafe(32)
        scopes = " ".join(self._settings.chatgpt_scopes.split())
        client_id = "dynamic_agent_client"
        handle, _, _ = self._sessions.get_session(session_id)
        if handle:
            try:
                existing = self._credentials.get(handle, "chatgpt")
                client_id = existing.client_id or client_id
            except Exception:
                client_id = "dynamic_agent_client"
        transaction = OAuthTransaction(
            state, session_id, verifier, nonce, self._settings.chatgpt_redirect_uri,
            scopes, client_id, datetime.now(timezone.utc),
        )
        self._sessions.save_transaction(transaction)
        params = {
            "response_type": "code", "client_id": client_id,
            "redirect_uri": transaction.redirect_uri, "scope": scopes,
            "state": state, "code_challenge": challenge, "code_challenge_method": "S256",
            "nonce": nonce,
        }
        if client_id == "dynamic_agent_client":
            params["agent_name_hint"] = self._settings.chatgpt_agent_name
            params["ext_agent_host_id"] = self._settings.chatgpt_ext_agent_host_id
        else:
            try:
                existing = self._credentials.get(handle, "chatgpt")
                if existing.id_token:
                    params["id_token_hint"] = existing.id_token
            except Exception:
                pass
        if self._settings.chatgpt_resource:
            params["resource"] = self._settings.chatgpt_resource
        return self._settings.chatgpt_authorization_endpoint + "?" + urlencode(params)

    async def callback(self, session_id: str, state: str, code: str,
                       error: str | None = None, client_id: str | None = None,
                       granted_scopes: str | None = None) -> str:
        self._require_enabled()
        if error or not code:
            raise OAuthError("authorization was not completed")
        transaction = self._sessions.consume_transaction(
            state, session_id, self._settings.oauth_transaction_ttl_seconds
        )
        effective_client_id = client_id or transaction.client_id
        if transaction.client_id == "dynamic_agent_client":
            if not client_id or client_id == "dynamic_agent_client":
                raise OAuthError("authorization could not be completed")
        elif effective_client_id != transaction.client_id:
            raise OAuthError("authorization could not be completed")
        payload = {
            "grant_type": "authorization_code", "client_id": effective_client_id,
            "code": code, "redirect_uri": transaction.redirect_uri,
            "code_verifier": transaction.verifier,
        }
        if self._settings.chatgpt_resource:
            payload["resource"] = self._settings.chatgpt_resource
        try:
            client = self._http_client or httpx.AsyncClient(timeout=self._settings.llm_timeout_seconds)
            response = await client.post(self._settings.chatgpt_token_endpoint, data=payload)
            if self._http_client is None:
                await client.aclose()
            if response.status_code >= 400:
                raise OAuthError("authorization could not be completed")
            token = response.json()
            access_token = token.get("access_token")
            id_token = token.get("id_token")
            if not isinstance(access_token, str) or not access_token or not isinstance(id_token, str):
                raise OAuthError("authorization could not be completed")
            claims = self._validate_id_token(id_token, transaction.nonce, effective_client_id)
            scopes = str(token.get("scope") or granted_scopes or "")
            handle = "chatgpt-" + secrets.token_urlsafe(24)
            expires_at = None
            if isinstance(token.get("expires_in"), int):
                expires_at = datetime.now(timezone.utc) + timedelta(seconds=token["expires_in"])
            self._credentials.put(StoredCredential(
                handle, "chatgpt", access_token, token.get("refresh_token"), expires_at,
                scopes, effective_client_id, str(claims["sub"]), id_token,
            ))
            self._sessions.set_session(session_id, handle, "IDENTITY_AUTHORIZED", scopes)
            if not required_scopes_granted(scopes):
                self._sessions.set_session(session_id, handle, "REAUTH_REQUIRED", scopes)
            return self._sessions.get_session(session_id)[1]
        except (httpx.HTTPError, ValueError, KeyError) as exception:
            raise OAuthError("authorization could not be completed") from exception

    def _validate_id_token(self, id_token: str, nonce: str, client_id: str) -> dict:
        try:
            signing_key = jwt.PyJWKClient(self._settings.chatgpt_jwks_url).get_signing_key_from_jwt(id_token)
            claims = jwt.decode(
                id_token,
                signing_key.key,
                algorithms=["RS256", "ES256"],
                audience=client_id,
                issuer=self._settings.chatgpt_issuer,
                options={"require": ["sub", "nonce"]},
            )
            if claims.get("nonce") != nonce:
                raise OAuthError("authorization could not be completed")
            return claims
        except Exception as exception:
            if isinstance(exception, OAuthError):
                raise
            raise OAuthError("authorization could not be completed") from exception

    def status(self, session_id: str) -> tuple[str, str]:
        _, status, scopes = self._sessions.get_session(session_id)
        return status, scopes

    def disconnect(self, session_id: str) -> None:
        handle, _, _ = self._sessions.get_session(session_id)
        if handle:
            self._credentials.delete(handle)
        self._sessions.set_session(session_id, None, "DISCONNECTED")

    def _require_enabled(self) -> None:
        if not self._settings.chatgpt_oauth_enabled:
            raise OAuthError("ChatGPT authorization is disabled")


def required_scopes_granted(scopes: str) -> bool:
    granted = set(scopes.split())
    return {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(granted)


def base64url(value: bytes) -> str:
    import base64
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
