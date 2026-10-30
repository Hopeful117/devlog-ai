from datetime import datetime, timezone
import sqlite3
from pathlib import Path
from uuid import UUID

from app.schemas.binding import BindingRegistrationRequest


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
                str(binding.task_id), binding.provider, binding.credential_handle,
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
        if row is None or row[0] != str(task_id) or row[1] != provider:
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
        if row is None:
            raise ValueError("binding is not available for this task and provider")
        if datetime.fromisoformat(row[1]) <= datetime.now(timezone.utc):
            raise ValueError("binding is expired")
        return row[0]

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self._path, timeout=5)
        connection.execute("PRAGMA foreign_keys = ON")
        return connection
