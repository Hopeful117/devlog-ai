from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
import asyncio
import hashlib
import hmac
import json
import httpx
import jwt
import secrets
import sqlite3
from pathlib import Path
from urllib.parse import urlencode

from app.core.config import Settings
from app.security.credential_store import EncryptedCredentialStore, StoredCredential
from app.security.service_auth import HmacServiceAuthenticator


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
    intent_id: str | None = None
    intent_signature: str | None = None
    intent_principal_id: str | None = None


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
                "intent_id TEXT, intent_signature TEXT, intent_principal_id TEXT, "
                "created_at TEXT NOT NULL, consumed_at TEXT)"
            )
            try:
                connection.execute(
                    "ALTER TABLE oauth_transactions ADD COLUMN client_id TEXT NOT NULL "
                    "DEFAULT 'dynamic_agent_client'"
                )
            except sqlite3.OperationalError:
                pass
            for column in ("intent_id", "intent_signature"):
                try:
                    connection.execute(f"ALTER TABLE oauth_transactions ADD COLUMN {column} TEXT")
                except sqlite3.OperationalError:
                    pass
            try:
                connection.execute("ALTER TABLE oauth_transactions ADD COLUMN intent_principal_id TEXT")
            except sqlite3.OperationalError:
                pass
            try:
                connection.execute("ALTER TABLE oauth_transactions ADD COLUMN consumed_at TEXT")
            except sqlite3.OperationalError:
                pass
            connection.execute(
                "CREATE TABLE IF NOT EXISTS oauth_sessions ("
                "session_id TEXT PRIMARY KEY, credential_handle TEXT, status TEXT NOT NULL, "
                "scopes TEXT NOT NULL DEFAULT '')"
            )
            connection.execute(
                "CREATE TABLE IF NOT EXISTS oauth_completions ("
                "completion_code TEXT PRIMARY KEY, intent_id TEXT NOT NULL, "
                "credential_handle TEXT NOT NULL, subject TEXT NOT NULL, scopes TEXT NOT NULL, "
                "client_id TEXT NOT NULL, created_at TEXT NOT NULL, redeemed_at TEXT, consumed_at TEXT)"
            )
            try:
                connection.execute("ALTER TABLE oauth_completions ADD COLUMN redeemed_at TEXT")
            except sqlite3.OperationalError:
                pass
            connection.execute(
                "CREATE TABLE IF NOT EXISTS oauth_revocations ("
                "credential_handle TEXT PRIMARY KEY, queued_at TEXT NOT NULL)"
            )

    def save_transaction(self, transaction: OAuthTransaction) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT INTO oauth_transactions "
                "(state, session_id, verifier, nonce, redirect_uri, scopes, client_id, created_at, "
                "intent_id, intent_signature, intent_principal_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (transaction.state, transaction.session_id, transaction.verifier,
                 transaction.nonce, transaction.redirect_uri, transaction.scopes,
                 transaction.client_id, transaction.created_at.isoformat(),
                  transaction.intent_id, transaction.intent_signature, transaction.intent_principal_id),
            )

    def consume_transaction(self, state: str, session_id: str,
                            ttl_seconds: int) -> OAuthTransaction:
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                "SELECT state, session_id, verifier, nonce, redirect_uri, scopes, client_id, created_at, "
                "intent_id, intent_signature, intent_principal_id "
                "FROM oauth_transactions WHERE state = ?", (state,)
            ).fetchone()
            if row is None or row[1] != session_id:
                raise OAuthError("invalid OAuth transaction")
            consumed = connection.execute(
                "SELECT consumed_at FROM oauth_transactions WHERE state = ?", (state,)
            ).fetchone()[0]
            if consumed is not None:
                raise OAuthError("invalid OAuth transaction")
            updated = connection.execute(
                "UPDATE oauth_transactions SET consumed_at = ? "
                "WHERE state = ? AND session_id = ? AND consumed_at IS NULL",
                (datetime.now(timezone.utc).isoformat(), state, session_id),
            )
            if updated.rowcount != 1:
                raise OAuthError("invalid OAuth transaction")
        transaction = OAuthTransaction(*row[:7], datetime.fromisoformat(row[7]), row[8], row[9], row[10])
        if datetime.now(timezone.utc) - transaction.created_at > timedelta(seconds=ttl_seconds):
            raise OAuthError("expired OAuth transaction")
        return transaction

    def release_transaction(self, state: str, session_id: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "UPDATE oauth_transactions SET consumed_at = NULL "
                "WHERE state = ? AND session_id = ? AND consumed_at IS NOT NULL",
                (state, session_id),
            )

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

    def save_completion(self, completion_code: str, intent_id: str, credential_handle: str,
                        subject: str, scopes: str, client_id: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT INTO oauth_completions "
                "(completion_code, intent_id, credential_handle, subject, scopes, client_id, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                (completion_code, intent_id, credential_handle, subject, scopes, client_id,
                 datetime.now(timezone.utc).isoformat()),
            )

    def get_completion(self, completion_code: str, intent_id: str, ttl_seconds: int) -> tuple[str, str, str, str]:
        with self._connect() as connection:
            connection.execute("BEGIN IMMEDIATE")
            row = connection.execute(
                "SELECT credential_handle, subject, scopes, client_id, created_at, redeemed_at, consumed_at "
                "FROM oauth_completions WHERE completion_code = ? AND intent_id = ?",
                (completion_code, intent_id),
            ).fetchone()
            if row is None or row[6] is not None:
                raise OAuthError("invalid OAuth completion")
            if datetime.now(timezone.utc) - datetime.fromisoformat(row[4]) > timedelta(seconds=ttl_seconds):
                raise OAuthError("expired OAuth completion")
            if row[5] is None:
                connection.execute(
                    "UPDATE oauth_completions SET redeemed_at = ? "
                    "WHERE completion_code = ? AND intent_id = ? AND redeemed_at IS NULL",
                    (datetime.now(timezone.utc).isoformat(), completion_code, intent_id),
                )
        return tuple(row[:4])

    def acknowledge_completion(self, completion_code: str, intent_id: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "UPDATE oauth_completions SET consumed_at = ? "
                "WHERE completion_code = ? AND intent_id = ? AND consumed_at IS NULL",
                (datetime.now(timezone.utc).isoformat(), completion_code, intent_id),
            )

    def discard_completion(self, completion_code: str, intent_id: str) -> str | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT credential_handle FROM oauth_completions "
                "WHERE completion_code = ? AND intent_id = ? AND consumed_at IS NULL",
                (completion_code, intent_id),
            ).fetchone()
            if row is None:
                return None
            connection.execute(
                "UPDATE oauth_completions SET consumed_at = ? "
                "WHERE completion_code = ? AND intent_id = ? AND consumed_at IS NULL",
                (datetime.now(timezone.utc).isoformat(), completion_code, intent_id),
            )
            return str(row[0])

    def queue_revocation(self, credential_handle: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT OR IGNORE INTO oauth_revocations(credential_handle, queued_at) VALUES (?, ?)",
                (credential_handle, datetime.now(timezone.utc).isoformat()),
            )

    def pending_revocations(self) -> list[str]:
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT credential_handle FROM oauth_revocations ORDER BY queued_at"
            ).fetchall()
        return [str(row[0]) for row in rows]

    def complete_revocation(self, credential_handle: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "DELETE FROM oauth_revocations WHERE credential_handle = ?",
                (credential_handle,),
            )

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

    def start(self, session_id: str, intent_id: str | None = None,
              intent_provider: str | None = None, intent_expires_at: int | None = None,
              intent_signature: str | None = None,
              intent_principal_id: str | None = None) -> str:
        self._require_enabled()
        self.retry_pending_revocations()
        if any(value is not None for value in
               (intent_id, intent_provider, intent_expires_at, intent_signature)):
            if not intent_id or intent_provider != "openai_chatgpt" or intent_expires_at is None \
                    or not intent_principal_id \
                    or not validate_connection_intent(self._settings.service_auth_secret,
                                                       intent_id, intent_provider, intent_expires_at,
                                                       intent_principal_id, intent_signature):
                raise OAuthError("invalid OAuth connection intent")
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
            scopes, client_id, datetime.now(timezone.utc), intent_id, intent_signature,
            intent_principal_id,
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
                       granted_scopes: str | None = None) -> str | dict[str, str]:
        self._require_enabled()
        if error or not code:
            raise OAuthError("authorization was not completed")
        transaction = self._sessions.consume_transaction(
            state, session_id, self._settings.oauth_transaction_ttl_seconds
        )
        effective_client_id = client_id or transaction.client_id
        if transaction.client_id == "dynamic_agent_client":
            if not client_id or client_id == "dynamic_agent_client":
                self._sessions.release_transaction(state, session_id)
                raise OAuthError("authorization could not be completed")
        elif effective_client_id != transaction.client_id:
            self._sessions.release_transaction(state, session_id)
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
            scopes = token.get("scope")
            if not isinstance(scopes, str) or not scopes.strip():
                raise OAuthError("authorization did not grant scopes")
            handle = "chatgpt-" + secrets.token_urlsafe(24)
            expires_at = None
            if isinstance(token.get("expires_in"), int):
                expires_at = datetime.now(timezone.utc) + timedelta(seconds=token["expires_in"])
            self._credentials.put(StoredCredential(
                handle, "chatgpt", access_token, token.get("refresh_token"), expires_at,
                scopes, effective_client_id, str(claims["sub"]), id_token,
            ))
            try:
                if transaction.intent_id:
                    completion_code = secrets.token_urlsafe(32)
                    self._sessions.save_completion(
                        completion_code, transaction.intent_id, handle, str(claims["sub"]),
                        scopes, effective_client_id,
                    )
            except Exception:
                self._credentials.delete(handle)
                raise OAuthError("authorization could not be completed")
            session_status = "PENDING_CORE_CONFIRMATION" if transaction.intent_id else "IDENTITY_AUTHORIZED"
            self._sessions.set_session(session_id, handle, session_status, scopes)
            if not transaction.intent_id and not required_scopes_granted(scopes):
                self._sessions.set_session(session_id, handle, "REAUTH_REQUIRED", scopes)
            if transaction.intent_id:
                return {
                    "status": "PENDING_CORE_CONFIRMATION",
                    "completionCode": completion_code,
                    "intentId": transaction.intent_id,
                }
            return self._sessions.get_session(session_id)[1]
        except OAuthError:
            self._sessions.release_transaction(state, session_id)
            raise
        except Exception as exception:
            self._sessions.release_transaction(state, session_id)
            raise OAuthError("authorization could not be completed") from exception

    async def complete_core_configuration(self, intent_id: str, completion_code: str) -> None:
        path = "/api/v1/ai/configuration/oauth/chatgpt/callback"
        body = json.dumps(
            {"completionCode": completion_code, "intentId": intent_id},
            ensure_ascii=False,
            separators=(",", ":"),
            sort_keys=True,
        ).encode("utf-8")
        secret = self._settings.service_auth_secret
        if not secret:
            raise OAuthError("authorization could not be completed")
        try:
            async with httpx.AsyncClient(
                base_url=self._settings.core_base_url,
                timeout=self._settings.core_callback_timeout_seconds,
            ) as client:
                for attempt in range(1, self._settings.core_callback_max_attempts + 1):
                    headers = HmacServiceAuthenticator(secret).sign(
                        method="POST",
                        path=path,
                        body=body,
                        timestamp=int(datetime.now(timezone.utc).timestamp()),
                        nonce=secrets.token_urlsafe(16),
                        request_id=secrets.token_urlsafe(16),
                    )
                    try:
                        response = await client.post(path, content=body, headers=headers)
                    except httpx.HTTPError as exception:
                        if attempt == self._settings.core_callback_max_attempts:
                            raise OAuthError("authorization could not be completed") from exception
                        await asyncio.sleep(self._callback_delay(attempt))
                        continue
                    if response.is_success:
                        return
                    if response.status_code < 500 or attempt == self._settings.core_callback_max_attempts:
                        raise OAuthError("authorization could not be completed")
                    await asyncio.sleep(self._callback_delay(attempt))
        except (httpx.HTTPError, OAuthError) as exception:
            if isinstance(exception, OAuthError):
                raise
            raise OAuthError("authorization could not be completed") from exception

    def _callback_delay(self, failed_attempt: int) -> float:
        delay_ms = min(
            self._settings.core_callback_initial_delay_ms * (2 ** (failed_attempt - 1)),
            self._settings.core_callback_max_delay_ms,
        )
        return delay_ms / 1_000

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
        self.retry_pending_revocations()
        _, status, scopes = self._sessions.get_session(session_id)
        return status, scopes

    def disconnect(self, session_id: str) -> None:
        handle, _, _ = self._sessions.get_session(session_id)
        notification_error: OAuthError | None = None
        if handle:
            self._sessions.queue_revocation(handle)
            self._credentials.delete(handle)
            try:
                self.retry_pending_revocations()
            except OAuthError as exception:
                notification_error = exception
        self._sessions.set_session(session_id, None, "DISCONNECTED")
        if notification_error is not None:
            raise notification_error

    def redeem_completion(self, intent_id: str, completion_code: str) -> dict[str, str]:
        handle, subject, scopes, client_id = self._sessions.get_completion(
            completion_code, intent_id, self._settings.oauth_transaction_ttl_seconds
        )
        return {
            "credentialHandle": handle,
            "subject": subject,
            "scopes": scopes,
            "clientId": client_id,
        }

    def acknowledge_completion(self, intent_id: str, completion_code: str) -> None:
        self._sessions.acknowledge_completion(completion_code, intent_id)

    def compensate_completion(self, intent_id: str, completion_code: str) -> None:
        handle = self._sessions.discard_completion(completion_code, intent_id)
        if handle:
            self._credentials.delete(handle)

    def retry_pending_revocations(self) -> None:
        failures: list[OAuthError] = []
        for handle in self._sessions.pending_revocations():
            try:
                self._notify_core_revocation(handle)
                self._sessions.complete_revocation(handle)
            except OAuthError as exception:
                failures.append(exception)
        if failures:
            raise OAuthError("disconnect could not be completed") from failures[0]

    def _notify_core_revocation(self, handle: str) -> None:
        secret = self._settings.service_auth_secret or self._settings.sca_callback_secret
        if not secret:
            raise OAuthError("disconnect is unavailable")
        path = "/api/v1/ai-credential-associations/revoked"
        body = json.dumps(
            {"credentialHandle": handle, "provider": "chatgpt"},
            ensure_ascii=False, separators=(",", ":"), sort_keys=True,
        ).encode()
        headers = HmacServiceAuthenticator(secret).sign(
            method="POST", path=path, body=body,
            timestamp=int(datetime.now(timezone.utc).timestamp()),
            nonce=secrets.token_urlsafe(16), request_id=secrets.token_urlsafe(16),
        )
        try:
            with httpx.Client(base_url=self._settings.core_base_url,
                              timeout=self._settings.llm_timeout_seconds) as client:
                response = client.post(path, content=body, headers={
                    "Content-Type": "application/json",
                    **headers,
                })
            if not response.is_success:
                raise OAuthError("disconnect could not be completed")
        except (httpx.HTTPError, OAuthError) as exception:
            if isinstance(exception, OAuthError):
                raise
            raise OAuthError("disconnect could not be completed") from exception

    def _require_enabled(self) -> None:
        if not self._settings.chatgpt_oauth_enabled:
            raise OAuthError("ChatGPT authorization is disabled")


def required_scopes_granted(scopes: str) -> bool:
    granted = set(scopes.split())
    return {"resource.invoke", "chatgpt.tokens.use.direct"}.issubset(granted)


def validate_connection_intent(secret: str | None, intent_id: str, provider: str,
                               expires_at: int, principal_id: str,
                               signature: str | None) -> bool:
    if not secret or not signature or expires_at <= int(datetime.now(timezone.utc).timestamp()):
        return False
    if not re_uuid(intent_id) or not principal_id or not signature or len(signature) != 64:
        return False
    payload = f"{intent_id}\n{provider}\n{expires_at}\n{principal_id}".encode()
    expected = hmac.new(secret.encode(), payload, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, signature)


def re_uuid(value: str) -> bool:
    import uuid
    try:
        uuid.UUID(value)
        return True
    except (ValueError, AttributeError):
        return False


def base64url(value: bytes) -> str:
    import base64
    return base64.urlsafe_b64encode(value).rstrip(b"=").decode("ascii")
