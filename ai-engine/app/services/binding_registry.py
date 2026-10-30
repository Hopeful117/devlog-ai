from datetime import datetime, timezone
import sqlite3
from pathlib import Path
from uuid import UUID

from app.schemas.binding import BindingRegistrationRequest
from app.services.provider_identity import engine_provider


class BindingRegistry:
    """Durable opaque binding registry; it stores no provider access token."""

    def __init__(self, path: str) -> None:
        self._path = path
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS ai_credential_bindings (
                    binding_id TEXT PRIMARY KEY,
                    task_id TEXT NOT NULL,
                    provider TEXT NOT NULL,
                    credential_handle TEXT NOT NULL,
                    expires_at TEXT NOT NULL
                )
                """
            )
            connection.execute(
                """
                CREATE TABLE IF NOT EXISTS revoked_credential_handles (
                    credential_handle TEXT PRIMARY KEY,
                    revoked_at TEXT NOT NULL
                )
                """
            )

    def register(self, binding: BindingRegistrationRequest) -> None:
        if binding.expires_at <= datetime.now(timezone.utc):
            raise ValueError("binding is already expired")
        with self._connect() as connection:
            existing = connection.execute(
                "SELECT task_id, provider, credential_handle, expires_at "
                "FROM ai_credential_bindings WHERE binding_id = ?",
                (str(binding.binding_id),),
            ).fetchone()
            values = (
                str(binding.task_id), engine_provider(binding.provider), binding.credential_handle,
                binding.expires_at.isoformat(),
            )
            if existing is not None and tuple(existing) != values:
                raise ValueError("binding id is already registered")
            connection.execute(
                "INSERT OR IGNORE INTO ai_credential_bindings "
                "(binding_id, task_id, provider, credential_handle, expires_at) "
                "VALUES (?, ?, ?, ?, ?)",
                (str(binding.binding_id), *values),
            )

    def resolve(self, binding_id: UUID, task_id: UUID, provider: str) -> str:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT task_id, provider, credential_handle, expires_at "
                "FROM ai_credential_bindings WHERE binding_id = ?",
                (str(binding_id),),
            ).fetchone()
            revoked = row is not None and connection.execute(
                "SELECT 1 FROM revoked_credential_handles WHERE credential_handle = ?",
                (row[2],),
            ).fetchone() is not None
        if row is None or revoked or row[0] != str(task_id) or row[1] != provider:
            raise ValueError("binding is not valid for this task and provider")
        if datetime.fromisoformat(row[3]) <= datetime.now(timezone.utc):
            raise ValueError("binding is expired")
        return row[2]

    def resolve_for_task(self, task_id: UUID, provider: str) -> str:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT credential_handle, expires_at FROM ai_credential_bindings "
                "WHERE task_id = ? AND provider = ? ORDER BY expires_at DESC LIMIT 1",
                (str(task_id), provider),
            ).fetchone()
            revoked = row is not None and connection.execute(
                "SELECT 1 FROM revoked_credential_handles WHERE credential_handle = ?",
                (row[0],),
            ).fetchone() is not None
        if row is None or revoked:
            raise ValueError("binding is not available for this task and provider")
        if datetime.fromisoformat(row[1]) <= datetime.now(timezone.utc):
            raise ValueError("binding is expired")
        return row[0]

    def revoke_credential(self, credential_handle: str) -> None:
        with self._connect() as connection:
            connection.execute(
                "INSERT OR IGNORE INTO revoked_credential_handles(credential_handle, revoked_at) "
                "VALUES (?, ?)",
                (credential_handle, datetime.now(timezone.utc).isoformat()),
            )

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self._path, timeout=5)
        connection.execute("PRAGMA foreign_keys = ON")
        return connection
