#!/usr/bin/env python3
"""Supervise one OpenCode-owned DevLog MCP STDIO instance."""

import json
import base64
import binascii
import io
import math
import os
import re
import select
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import threading
import time
from dataclasses import dataclass
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable


SECRET_PATTERNS = (
    re.compile(r"(?i)(authorization\s*:\s*bearer\s+)[^\s,}]+"),
    re.compile(r"(?i)((?:token|secret|password|api[_-]?key)\s*[=:]\s*)[^\s,}]+"),
    re.compile(r"(?i)(client_secret=)[^&\s]+"),
    re.compile(
        r'(?i)("?(?:access_token|refresh_token|id_token|client_secret|delegation_token|authorization_code|prompt|provider_payload)"?\s*:\s*")((?:\\.|[^"\\])*)(?=")'
    ),
)
MAX_EVENT_BYTES = 5 * 1024 * 1024
MAX_COMPLETED_INSTANCES = 20
DEFAULT_RENEWAL_MARGIN_SECONDS = 30
DEFAULT_RENEWAL_COOLDOWN_SECONDS = 10
DEFAULT_MINIMUM_LEASE_SECONDS = 5
DEFAULT_TOKEN_RETRY_BACKOFF_SECONDS = 1
MAX_TOKEN_RETRY_BACKOFF_SECONDS = 60
DEFAULT_RATE_LIMIT_RETRY_BACKOFF_SECONDS = 1
MAX_RATE_LIMIT_RETRY_SECONDS = 60
TOOL_SCHEMA_VERSION = "mcp-tool-observability-v1"
TOOL_ACTIVE = "ACTIVE"
TOOL_RESPONDED = "RESPONDED"
TOOL_FAILED = "FAILED"
TOOL_INTERRUPTED = "INTERRUPTED"

DIAGNOSTIC_CATALOG: dict[str, dict[str, Any]] = {
    "CORE_AUTHENTICATION_FAILED": {
        "category": "CORE_AUTHENTICATION", "layer": "CORE", "retryable": False,
        "action": "CHECK_CORE_AUTHORIZATION",
    },
    "CORE_AUTHORIZATION_FAILED": {
        "category": "CORE_AUTHORIZATION", "layer": "CORE", "retryable": False,
        "action": "CHECK_PROJECT_ACCESS",
    },
    "CORE_CONFLICT": {
        "category": "CORE_CONFLICT", "layer": "CORE", "retryable": False,
        "action": "RECONCILE_OPERATION_STATUS",
    },
    "DELEGATION_FAILED": {
        "category": "DELEGATION_AUTHENTICATION", "layer": "DELEGATION", "retryable": False,
        "action": "RENEW_OR_REVOKE_DELEGATION",
    },
    "PROVIDER_FAILED": {
        "category": "PROVIDER_AUTHENTICATION", "layer": "PROVIDER", "retryable": False,
        "action": "CHECK_PROVIDER_CONFIGURATION",
    },
    "TRANSIENT_INFRASTRUCTURE": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "CORE", "retryable": False,
        "action": "RETRY_ONLY_IF_OPERATION_SAFE",
    },
    "RATE_LIMITED": {
        "category": "RATE_LIMITED", "layer": "CORE", "retryable": False,
        "action": "WAIT_OR_RETRY_WITH_POLICY",
    },
    "MCP_REAUTH_REQUIRED": {
        "category": "TRANSPORT_AUTH_REJECTED", "layer": "TRANSPORT", "retryable": False,
        "action": "REAUTHENTICATE_RUNTIME",
    },
    "MCP_RETRY_NOT_IDEMPOTENT": {
        "category": "UNKNOWN", "layer": "TRANSPORT", "retryable": False,
        "action": "RECONCILE_OPERATION_STATUS",
    },
    "MCP_JSONRPC_ERROR": {
        "category": "UNKNOWN", "layer": "PROTOCOL", "retryable": False,
        "action": "ESCALATE_WITH_CORRELATION",
    },
    "MCP_PROCESS_FAILED": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "TRANSPORT", "retryable": False,
        "action": "RESTART_RUNTIME",
    },
    "MCP_HANDSHAKE_FAILED": {
        "category": "PROTOCOL", "layer": "PROTOCOL", "retryable": False,
        "action": "CHECK_MCP_CLIENT",
    },
    "KEYCLOAK_UNREACHABLE": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "TRANSPORT", "retryable": False,
        "action": "CHECK_TOKEN_ENDPOINT",
    },
    "MCP_JAR_MISSING": {
        "category": "CONFIGURATION_INVALID", "layer": "TRANSPORT", "retryable": False,
        "action": "FIX_RUNTIME_CONFIGURATION",
    },
    "PARENT_PROCESS_GONE": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "TRANSPORT", "retryable": False,
        "action": "RESTART_RUNTIME",
    },
    "TOKEN_ENDPOINT_TIMEOUT": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "TRANSPORT", "retryable": True,
        "action": "RETRY_TOKEN_ACQUISITION_ONCE",
    },
    "TOKEN_ENDPOINT_5XX": {
        "category": "TRANSIENT_INFRASTRUCTURE", "layer": "TRANSPORT", "retryable": True,
        "action": "RETRY_TOKEN_ACQUISITION_ONCE",
    },
    "TOKEN_ENDPOINT_REJECTED": {
        "category": "TRANSPORT_AUTH_REJECTED", "layer": "TRANSPORT", "retryable": False,
        "action": "REAUTHENTICATE_RUNTIME",
    },
    "TOKEN_RESPONSE_INVALID": {
        "category": "PROTOCOL", "layer": "TRANSPORT", "retryable": False,
        "action": "FIX_RUNTIME_CONFIGURATION",
    },
    "PROTOCOL_INVALID": {
        "category": "PROTOCOL", "layer": "PROTOCOL", "retryable": False,
        "action": "CHECK_MCP_CLIENT",
    },
    "FUNCTIONAL_ERROR": {
        "category": "FUNCTIONAL", "layer": "CORE", "retryable": False,
        "action": "CHECK_OPERATION_PARAMETERS",
    },
    "CORE_INTERNAL_ERROR": {
        "category": "UNKNOWN", "layer": "CORE", "retryable": False,
        "action": "ESCALATE_WITH_CORRELATION",
    },
    "CORE_RESOURCE_NOT_FOUND": {
        "category": "FUNCTIONAL", "layer": "CORE", "retryable": False,
        "action": "CHECK_RESOURCE_IDENTIFIER",
    },
    "MCP_UNKNOWN_DIAGNOSTIC": {
        "category": "UNKNOWN", "layer": "UNKNOWN", "retryable": False,
        "action": "ESCALATE_WITH_CORRELATION",
    },
    "CONFIGURATION_INVALID": {
        "category": "CONFIGURATION_INVALID", "layer": "TRANSPORT", "retryable": False,
        "action": "FIX_RUNTIME_CONFIGURATION",
    },
}

NON_DIAGNOSTIC_CODES = frozenset({
    "NORMAL_EXIT", "STDIN_CLOSED", "TOKEN_RENEWAL_REQUIRED",
    "INITIALIZE_RESPONSE", "RENEWAL_INITIALIZE_RESPONSE", "TOKEN_RENEWAL",
    "AUTHENTICATION_RETRY", "CHILD_KILL_REQUIRED", "MCP_INSTANCE_STOPPING",
})


DEVLOG_CORRELATION_TOOLS = frozenset({
    "devlog_story_agent",
    "story_context_agent_follow_up_authenticated",
})

RETRY_SAFE_TOOLS = frozenset({
    "get_engineering_context",
    "search_project_history",
    "story_context_agent_get_projection",
    "story_context_agent_get_snapshot_authenticated",
})

IDEMPOTENT_RETRY_TOOLS = frozenset({
    "devlog_story_agent",
})


def _tool_has_idempotency_key(message: Any, tool_name: str | None) -> bool:
    if tool_name not in IDEMPOTENT_RETRY_TOOLS or not isinstance(message, dict):
        return False
    params = message.get("params")
    arguments = params.get("arguments") if isinstance(params, dict) else None
    key = arguments.get("idempotencyKey") if isinstance(arguments, dict) else None
    return isinstance(key, str) and bool(key.strip())


def _is_retry_safe_tool(message: Any, tool_name: str | None) -> bool:
    return tool_name is None or tool_name in RETRY_SAFE_TOOLS or _tool_has_idempotency_key(message, tool_name)


def _extract_devlog_correlation_id(message: Any) -> str | None:
    """Best-effort extraction of aiTaskId from a DevLog tool MCP response."""
    try:
        result = message.get("result")
        if not isinstance(result, dict):
            return None
        content = result.get("content")
        if not isinstance(content, list) or not content:
            return None
        text = content[0].get("text") if isinstance(content[0], dict) else None
        if not isinstance(text, str):
            return None
        payload = json.loads(text)
        task_id = payload.get("aiTaskId") or payload.get("snapshotId")
        if isinstance(task_id, str) and len(task_id) == 36:
            return task_id
    except Exception:
        pass
    return None


@dataclass
class ToolOperation:
    operation_id: str
    request_id: str
    initial_generation: int
    current_generation: int
    state: str
    retry_count: int
    raw_payload: bytes
    started_at: float
    retry_safe: bool
    tool_name: str | None = None
    response_correlation_id: str | None = None
    rate_limit_retry_count: int = 0
    rate_limit_retry_in_progress: bool = False
    rate_limit_retry_sent: bool = False


def state_root() -> Path:
    return Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local/state")) / "devlog/mcp"


def process_start_time(pid: int) -> str | None:
    """Return Linux process start ticks, or None when the process is gone."""
    try:
        stat = Path(f"/proc/{pid}/stat").read_text(encoding="utf-8")
    except (FileNotFoundError, PermissionError, OSError):
        return None
    closing = stat.rfind(")")
    if closing < 0:
        return None
    fields = stat[closing + 2 :].split()
    return fields[19] if len(fields) > 19 else None


def redact(value: str) -> str:
    result = value
    for pattern in SECRET_PATTERNS:
        result = pattern.sub(r"\1<redacted>", result)
    return result[:1000]


def diagnostic_for_code(code: str | None) -> dict[str, Any]:
    """Return only allowlisted, non-secret diagnostic metadata."""
    if not code:
        return {}
    diagnostic = DIAGNOSTIC_CATALOG.get(code)
    if diagnostic is None:
        return {
            "code": "MCP_UNKNOWN_DIAGNOSTIC",
            "category": "UNKNOWN",
            "layer": "UNKNOWN",
            "retryable": False,
            "action": "ESCALATE_WITH_CORRELATION",
        }
    return {"code": code, **diagnostic}


class EventWriter:
    def __init__(self, directory: Path, instance_id: str):
        directory.mkdir(parents=True, exist_ok=True)
        self.instance_id = instance_id
        self.path = directory / "events.jsonl"
        self._lock = threading.Lock()
        self._truncated = False

    def write(self, event: str, severity: str = "INFO", code: str | None = None, **fields: Any) -> None:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "instanceId": self.instance_id,
            "event": event,
            "severity": severity,
        }
        if code:
            if code in DIAGNOSTIC_CATALOG or code not in NON_DIAGNOSTIC_CODES:
                payload.update(diagnostic_for_code(code))
            else:
                payload["code"] = code
        for key, value in fields.items():
            if value is not None:
                payload[key] = redact(str(value)) if isinstance(value, str) else value
        with self._lock:
            if self.path.exists() and self.path.stat().st_size >= MAX_EVENT_BYTES:
                if not self._truncated:
                    marker = {
                        "timestamp": datetime.now(timezone.utc).isoformat(),
                        "instanceId": self.instance_id,
                        "event": "LOG_TRUNCATED",
                        "severity": "WARN",
                        "code": "EVENT_LOG_SIZE_LIMIT",
                    }
                    self.path.write_text(json.dumps(marker, sort_keys=True) + "\n", encoding="utf-8")
                    self._truncated = True
                return
            with self.path.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(payload, sort_keys=True) + "\n")


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def required_configuration() -> dict[str, str]:
    return {
        "token_endpoint": env(
            "KEYCLOAK_TOKEN_ENDPOINT",
            "http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/token",
        ),
        "issuer_uri": env(
            "KEYCLOAK_ISSUER_URI", "http://127.0.0.1:18084/realms/devlog"
        ),
        "jwks_uri": env(
            "KEYCLOAK_JWKS_URI",
            "http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/certs",
        ),
        "actor_client_id": env("KEYCLOAK_MCP_ACTOR_CLIENT_ID", "devlog-mcp-opencode"),
        "secret_file": env(
            "DEVLOG_MCP_CLIENT_SECRET_FILE",
            str(Path.home() / ".config/opencode/devlog-mcp-client-secret"),
        ),
        "jar": env(
            "DEVLOG_MCP_JAR",
            "/home/ludo/workspace/devlog-ai/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar",
        ),
        "renewal_margin_seconds": env(
            "DEVLOG_MCP_RENEWAL_MARGIN_SECONDS", str(DEFAULT_RENEWAL_MARGIN_SECONDS)
        ),
        "renewal_cooldown_seconds": env(
            "DEVLOG_MCP_RENEWAL_COOLDOWN_SECONDS", str(DEFAULT_RENEWAL_COOLDOWN_SECONDS)
        ),
        "minimum_lease_seconds": env(
            "DEVLOG_MCP_MINIMUM_LEASE_SECONDS", str(DEFAULT_MINIMUM_LEASE_SECONDS)
        ),
        "token_retry_backoff_seconds": env(
            "DEVLOG_MCP_TOKEN_RETRY_BACKOFF_SECONDS",
            str(DEFAULT_TOKEN_RETRY_BACKOFF_SECONDS),
        ),
        "core_base_url": env("DEVLOG_BACKEND_BASE_URL", "http://localhost:18080"),
        "project_slug": env("DEVLOG_MCP_PROJECT_SLUG", ""),
    }


class ToolEventPusher:
    """Best-effort HTTP push of tool-call events to the Core backend."""

    def __init__(
        self,
        base_url: str,
        project_slug: str,
        instance_id: str,
        token_getter: Any,
        agent_session_id: str | None = None,
        agent_provider: str | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._project_slug = project_slug
        self._instance_id = instance_id
        self._token_getter = token_getter
        self._agent_session_id = agent_session_id
        self._agent_provider = agent_provider

    def push(self, fields: dict[str, Any]) -> None:
        if not self._project_slug:
            return
        body_data: dict[str, Any] = {
            **fields,
            "instanceId": self._instance_id,
            "projectSlug": self._project_slug,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        if self._agent_session_id:
            body_data["agentSessionId"] = self._agent_session_id
        if self._agent_provider:
            body_data["agentProvider"] = self._agent_provider
        body = json.dumps(body_data).encode("utf-8")
        threading.Thread(target=self._post, args=(body,), daemon=True).start()

    def _post(self, body: bytes) -> None:
        try:
            token = self._token_getter()
            url = f"{self._base_url}/api/v1/mcp/tool-events"
            request = urllib.request.Request(
                url,
                data=body,
                method="POST",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {token}",
                },
            )
            with urllib.request.urlopen(request, timeout=5):
                pass
        except Exception:
            try:
                sys.stderr.write("MCP_COMPANION_PUSH_FAILED\n")
                sys.stderr.flush()
            except Exception:
                pass

    def push_session_lifecycle_event(self, event_type: str) -> threading.Thread | None:
        if not self._project_slug or not self._agent_session_id:
            return None
        body_data: dict[str, Any] = {
            "projectSlug": self._project_slug,
            "agentSessionId": self._agent_session_id,
            "eventType": event_type,
            "occurredAt": datetime.now(timezone.utc).isoformat(),
        }
        if self._agent_provider:
            body_data["agentProvider"] = self._agent_provider
        body = json.dumps(body_data).encode("utf-8")
        t = threading.Thread(target=self._post_session_event, args=(body,), daemon=True)
        t.start()
        return t

    def _post_session_event(self, body: bytes) -> None:
        try:
            token = self._token_getter()
            url = f"{self._base_url}/api/v1/mcp/session-events"
            request = urllib.request.Request(
                url,
                data=body,
                method="POST",
                headers={
                    "Content-Type": "application/json",
                    "Authorization": f"Bearer {token}",
                },
            )
            with urllib.request.urlopen(request, timeout=5):
                pass
        except Exception:
            try:
                sys.stderr.write("MCP_COMPANION_PUSH_FAILED\n")
                sys.stderr.flush()
            except Exception:
                pass


class TokenLease:
    def __init__(self, token: str, expires_at: float):
        self.token = token
        self.expires_at = expires_at


def _jwt_expiry(token: str) -> float | None:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        claims = json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
        if not isinstance(claims, dict):
            return None
        expiry = claims.get("exp")
        return float(expiry) if isinstance(expiry, (int, float)) else None
    except (
        IndexError,
        ValueError,
        TypeError,
        json.JSONDecodeError,
        UnicodeDecodeError,
        binascii.Error,
        OverflowError,
    ):
        return None


def acquire_token(configuration: dict[str, str]) -> TokenLease:
    secret = Path(configuration["secret_file"]).read_text(encoding="utf-8").strip()
    body = urllib.parse.urlencode(
        {
            "grant_type": "client_credentials",
            "client_id": configuration["actor_client_id"],
            "client_secret": secret,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        configuration["token_endpoint"],
        data=body,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded", "Host": "keycloak:8080"},
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            result = json.load(response)
    except (TypeError, ValueError, UnicodeDecodeError, OverflowError) as exception:
        raise RuntimeError("TOKEN_RESPONSE_INVALID") from exception
    if not isinstance(result, dict):
        raise RuntimeError("TOKEN_RESPONSE_INVALID")
    token = result.get("access_token")
    if not isinstance(token, str) or not token:
        raise RuntimeError("TOKEN_RESPONSE_INVALID")
    if "expires_in" not in result:
        expires_at = _jwt_expiry(token)
        if expires_at is None:
            raise RuntimeError("TOKEN_RESPONSE_INVALID")
    else:
        try:
            expires_at = time.time() + float(result["expires_in"])
        except (TypeError, ValueError, OverflowError) as exception:
            raise RuntimeError("TOKEN_RESPONSE_INVALID") from exception
    try:
        minimum_lease = float(configuration.get("minimum_lease_seconds", DEFAULT_MINIMUM_LEASE_SECONDS))
    except (TypeError, ValueError, OverflowError) as exception:
        raise RuntimeError("TOKEN_RESPONSE_INVALID") from exception
    if (
        not math.isfinite(expires_at)
        or not math.isfinite(minimum_lease)
        or expires_at - time.time() < minimum_lease
    ):
        raise RuntimeError("TOKEN_RESPONSE_INVALID")
    return TokenLease(token, expires_at)


def validate_configuration(configuration: dict[str, str]) -> None:
    if not Path(configuration["secret_file"]).is_file():
        raise RuntimeError("CONFIGURATION_INVALID: client secret file is missing")
    jar = Path(configuration["jar"])
    if not jar.is_file() or not os.access(jar, os.R_OK):
        raise RuntimeError("MCP_JAR_MISSING")
    for name in (
        "renewal_margin_seconds",
        "renewal_cooldown_seconds",
        "minimum_lease_seconds",
        "token_retry_backoff_seconds",
    ):
        try:
            value = float(configuration[name])
            if not math.isfinite(value) or value < 0:
                raise ValueError
            if name == "token_retry_backoff_seconds" and value > MAX_TOKEN_RETRY_BACKOFF_SECONDS:
                raise ValueError
        except (KeyError, TypeError, ValueError):
            raise RuntimeError("CONFIGURATION_INVALID: renewal timing is invalid")


def write_manifest(
    directory: Path,
    instance_id: str,
    parent_pid: int,
    parent_start: str | None,
    agent_session_id: str | None = None,
    agent_provider: str | None = None,
) -> None:
    manifest: dict[str, Any] = {
        "instanceId": instance_id,
        "launcherPid": os.getpid(),
        "launcherStartTime": process_start_time(os.getpid()),
        "parentOpenCodePid": parent_pid,
        "parentOpenCodeStartTime": parent_start,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "state": "STARTING",
    }
    if agent_session_id:
        manifest["agentSessionId"] = agent_session_id
    if agent_provider:
        manifest["agentProvider"] = agent_provider
    (directory / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def update_manifest(directory: Path, **updates: Any) -> None:
    path = directory / "manifest.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update(updates)
    path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")


class RuntimeSession:
    def __init__(self, configuration: dict[str, str], directory: Path, writer: EventWriter, lease: TokenLease):
        self.configuration = configuration
        self.directory = directory
        self.writer = writer
        self.lease = lease
        self.child: subprocess.Popen[bytes] | None = None
        self.ready = threading.Event()
        self.initialize_received = threading.Event()
        self.handshake_deadline = 0.0
        self.stop_requested = threading.Event()
        self.explicit_stop = threading.Event()
        self.lock = threading.RLock()
        # Lifecycle decisions never wait on protocol I/O. When both locks
        # are needed, acquire the session lock before the lifecycle lock.
        self.lifecycle_lock = threading.Lock()
        self.spawn_done = threading.Event()
        self.spawn_done.set()
        self.unpublished_child: subprocess.Popen[bytes] | None = None
        self.child_stop_lock = threading.Lock()
        self.stdout_lock = threading.Lock()
        self.stdout_done = threading.Event()
        self.stdout_done.set()
        self.terminalize_lock = threading.Lock()
        self.tool_event_lock = threading.Lock()
        self.stdin_write_lock = threading.Lock()
        self.renewal_condition = threading.Condition(self.lock)
        self.renewal_in_progress = False
        self.replacement_failed = threading.Event()
        self.outstanding: dict[str, ToolOperation | int] = {}
        self.operations: dict[str, ToolOperation] = {}
        self.pending_operation_events: list[tuple[ToolOperation, str, str, int, str | None]] = []
        self.last_renewal_failure = 0.0
        self.initialize_raw: bytes | None = None
        self.initialized_raw: bytes | None = None
        self.initialize_id: str | None = None
        self.suppress_initialize_response = False
        self.generation = 0
        self.pusher = ToolEventPusher(
            base_url=configuration.get("core_base_url", "http://localhost:18080"),
            project_slug=configuration.get("project_slug", ""),
            instance_id=getattr(writer, "instance_id", ""),
            token_getter=lambda: self.lease.token,
            agent_session_id=configuration.get("agent_session_id"),
            agent_provider=configuration.get("agent_provider"),
        )

    def _new_operation_id(self) -> str:
        operation_id = secrets.token_hex(16)
        while operation_id in self.operations:
            operation_id = secrets.token_hex(16)
        return operation_id

    def _emit_tool_event(
        self,
        operation: ToolOperation,
        event_type: str,
        status: str,
        generation: int,
        *,
        reason_code: str | None = None,
    ) -> None:
        fields: dict[str, Any] = {
            "schemaVersion": TOOL_SCHEMA_VERSION,
            "eventType": event_type,
            "operationId": operation.operation_id,
            "generation": generation,
            "status": status,
        }
        if operation.tool_name is not None:
            fields["toolName"] = operation.tool_name
        if reason_code is not None:
            fields["reasonCode"] = reason_code
            fields.update(diagnostic_for_code(reason_code))
        if operation.response_correlation_id is not None and event_type != "MCP_TOOL_STARTED":
            fields["correlationId"] = operation.response_correlation_id
        duration_ms = max(0, int((time.monotonic() - operation.started_at) * 1000))
        if event_type != "MCP_TOOL_STARTED":
            fields["durationMs"] = duration_ms
        push_fields = dict(fields)
        with self.tool_event_lock:
            try:
                severity = "ERROR" if status == TOOL_FAILED else "WARN" if status == TOOL_INTERRUPTED else "INFO"
                self.writer.write(event_type, severity=severity, **fields)
            except Exception:
                # Observability must not corrupt MCP STDOUT or change transport state.
                try:
                    sys.stderr.write("MCP_OBSERVABILITY_WRITE_FAILED\n")
                    sys.stderr.flush()
                except Exception:
                    pass
        self.pusher.push(push_fields)

    def _finish_operation_locked(
        self,
        operation: ToolOperation,
        state: str,
        generation: int,
        reason_code: str | None = None,
    ) -> bool:
        if operation.state != TOOL_ACTIVE:
            return False
        operation.state = state
        if self.outstanding.get(operation.request_id) is operation:
            self.outstanding.pop(operation.request_id, None)
        self.operations.pop(operation.operation_id, None)
        event_type = {
            TOOL_RESPONDED: "MCP_TOOL_RESPONDED",
            TOOL_FAILED: "MCP_TOOL_FAILED",
            TOOL_INTERRUPTED: "MCP_TOOL_INTERRUPTED",
        }[state]
        self.pending_operation_events.append((operation, event_type, state, generation, reason_code))
        return True

    def _finish_operation(
        self,
        operation: ToolOperation,
        state: str,
        generation: int,
        reason_code: str | None = None,
    ) -> bool:
        with self.lock:
            finished = self._finish_operation_locked(operation, state, generation, reason_code)
        if finished:
            self.flush_terminal_events()
        return finished

    def flush_terminal_events(self) -> None:
        # Reservation and retirement queue their events under the session
        # lock. Finalization flushes them even if a relay thread is paused.
        with self.terminalize_lock:
            with self.lock:
                pending, self.pending_operation_events = self.pending_operation_events, []
            for operation, event_type, status, generation, reason in pending:
                self._emit_tool_event(operation, event_type, status, generation, reason_code=reason)

    @property
    def renewal_margin(self) -> float:
        return float(self.configuration["renewal_margin_seconds"])

    @property
    def renewal_cooldown(self) -> float:
        return float(self.configuration["renewal_cooldown_seconds"])

    @property
    def minimum_lease(self) -> float:
        return float(self.configuration.get("minimum_lease_seconds", DEFAULT_MINIMUM_LEASE_SECONDS))

    @property
    def token_retry_backoff(self) -> float:
        value = float(
            self.configuration.get("token_retry_backoff_seconds", DEFAULT_TOKEN_RETRY_BACKOFF_SECONDS)
        )
        if not math.isfinite(value) or not 0 <= value <= MAX_TOKEN_RETRY_BACKOFF_SECONDS:
            raise RuntimeError("CONFIGURATION_INVALID")
        return value

    @staticmethod
    def is_transient_token_error(exception: BaseException) -> bool:
        if isinstance(exception, TimeoutError):
            return True
        if isinstance(exception, urllib.error.HTTPError):
            return exception.code in {502, 503, 504}
        if isinstance(exception, urllib.error.URLError):
            return isinstance(exception.reason, (TimeoutError, socket.timeout))
        return False

    @staticmethod
    def token_retry_code(exception: BaseException) -> str:
        if isinstance(exception, urllib.error.HTTPError):
            return "TOKEN_ENDPOINT_5XX"
        return "TOKEN_ENDPOINT_TIMEOUT"

    def acquire_token_with_retry(self) -> TokenLease:
        try:
            return acquire_token(self.configuration)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exception:
            if not self.is_transient_token_error(exception):
                raise
            self.writer.write(
                "TOKEN_RENEWAL_RETRY",
                severity="WARN",
                code=self.token_retry_code(exception),
                attempt=1,
                maxAttempts=1,
            )
            with self.lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
            try:
                if self.stop_requested.wait(timeout=self.token_retry_backoff):
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
            except OverflowError as exception:
                raise RuntimeError("CONFIGURATION_INVALID") from exception
            with self.lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
            return acquire_token(self.configuration)

    def start_child(self, replay_initialize: bool = False) -> None:
        with self.lock:
            with self.lifecycle_lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                self.spawn_done.clear()
            try:
                child, initialize = self._spawn_child(replay_initialize)
            finally:
                self.spawn_done.set()
        if initialize is not None:
            self.write_child_handshake(child.stdin, initialize)

    def _spawn_child(self, replay_initialize: bool) -> tuple[subprocess.Popen[bytes], bytes | None]:
        # Called with the session lock. Do not include handshake I/O in the
        # spawn reservation: shutdown may need to kill the child to unblock it.
        self.generation += 1
        generation = self.generation
        child_env = os.environ.copy()
        child_env["DEVLOG_MCP_BEARER_TOKEN"] = self.lease.token
        child_env["KEYCLOAK_ISSUER_URI"] = self.configuration["issuer_uri"]
        child_env.setdefault("KEYCLOAK_AUDIENCE", "devlog-core")
        child_env["KEYCLOAK_JWKS_URI"] = self.configuration["jwks_uri"]
        child_env["KEYCLOAK_MCP_ACTOR_CLIENT_ID"] = self.configuration["actor_client_id"]
        try:
            child = subprocess.Popen(
                ["java", "-jar", self.configuration["jar"]],
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=child_env,
            )
        except OSError as exception:
            raise RuntimeError("MCP_PROCESS_FAILED") from exception
        with self.lifecycle_lock:
            publish = not self.stop_requested.is_set()
            if publish:
                self.child = child
                self.ready = threading.Event()
                self.initialize_received = threading.Event()
                self.stdout_done = threading.Event()
            else:
                self.unpublished_child = child
        if not publish:
            self.terminate_owned_child(child, "MCP_INSTANCE_STOPPING")
            self.unpublished_child = None
            raise RuntimeError("MCP_INSTANCE_STOPPING")
        initialize = self.initialize_raw if replay_initialize else None
        self.suppress_initialize_response = initialize is not None
        update_manifest(self.directory, state="RUNNING", mcpPid=child.pid, mcpStartTime=process_start_time(child.pid))
        self.writer.write("MCP_PROCESS_STARTED", mcpPid=child.pid)
        threading.Thread(target=self.run_stdout_relay, args=(child, generation, self.stdout_done), daemon=True).start()
        threading.Thread(target=self.relay_stderr, args=(child,), daemon=True).start()
        return child, initialize

    def request_stop(self) -> None:
        with self.lifecycle_lock:
            self.explicit_stop.set()
            self.stop_requested.set()

    def stop_child(self, code: str) -> None:
        if code != "TOKEN_RENEWAL":
            self.request_stop()
            self.spawn_done.wait()
        with self.lifecycle_lock:
            child = self.child
        if child is not None:
            self.terminate_owned_child(child, code)
        if code == "TOKEN_RENEWAL":
            return
        self.interrupt_pending_operations()

    def interrupt_pending_operations(self) -> None:
        with self.lock:
            for operation in list(self.operations.values()):
                self._finish_operation_locked(operation, TOOL_INTERRUPTED, self.generation, "MCP_INSTANCE_STOPPING")
        self.flush_terminal_events()

    def terminate_owned_child(self, child: subprocess.Popen[bytes], code: str) -> None:
        # Never close a buffered stdin before signalling: close can wait on
        # a writer whose pipe is full. Signal/reap without the protocol locks.
        with self.child_stop_lock:
            terminate_child(child, self.writer, code)

    def write_stdout(self, payload: bytes, generation: int, message: Any = None) -> bool:
        with self.stdout_lock:
            with self.lock:
                if generation != self.generation or self.explicit_stop.is_set():
                    return False
                operation = self.response_operation_locked(message, generation)
                if operation is not None:
                    # The server answered. Release the wire request before the
                    # client can read the response, but log delivery only after
                    # the write. The operation remains visible to shutdown.
                    self.outstanding.pop(operation.request_id)
            # An admitted frame may finish, but cannot be overtaken by a
            # newer generation. No lifecycle lock is held during the write.
            output = sys.stdout.buffer
            # The stdio launcher owns stdout. Do not pin Python's buffered
            # stdout lock in a daemon thread during interpreter shutdown.
            if isinstance(output, io.BufferedWriter) and isinstance(output.raw, io.FileIO):
                remaining = memoryview(payload)
                while remaining:
                    written = os.write(output.fileno(), remaining)
                    if not written:
                        raise BrokenPipeError("MCP stdout write made no progress")
                    remaining = remaining[written:]
            else:
                output.write(payload)
                output.flush()
            if operation is not None:
                self.finish_response(operation, message, generation)
            return True

    def run_stdout_relay(self, child: subprocess.Popen[bytes], generation: int, done: threading.Event) -> None:
        try:
            self.relay_stdout(child, generation)
        finally:
            done.set()

    def relay_stdout(self, child: subprocess.Popen[bytes], generation: int) -> None:
        assert child.stdout is not None
        buffer = b""
        while chunk := child.stdout.read1(65536):
            buffer += chunk
            while b"\n" in buffer:
                line, buffer = buffer.split(b"\n", 1)
                try:
                    message = json.loads(line)
                except json.JSONDecodeError:
                    message = None
                with self.lock:
                    if generation != self.generation:
                        continue
                auth_failure = self.is_transport_auth_failure(message)
                if not self.stop_requested.is_set() and auth_failure and self.retry_authentication(message):
                    continue
                if not self.stop_requested.is_set() and self.is_rate_limited(message) and self.retry_rate_limited(message):
                    continue
                with self.lock:
                    if generation != self.generation:
                        continue
                # Publication de disponibilité hors du verrou de session :
                # l'arrêt peut détenir child_stop_lock pendant un réap.
                internal = self.consume_initialize_response(message, generation)
                if internal:
                    continue
                self.write_stdout(line + b"\n", generation, message)
        if buffer:
            self.write_stdout(buffer, generation)

    def relay_stderr(self, child: subprocess.Popen[bytes]) -> None:
        assert child.stderr is not None
        for raw_line in child.stderr:
            line = redact(raw_line.decode("utf-8", errors="replace").rstrip())
            if line:
                self.writer.write("MCP_PROCESS_LOG", severity="DEBUG", message=line)
                sys.stderr.write(line + "\n")
                sys.stderr.flush()

    def observe_response(self, message: Any, generation: int | None = None) -> None:
        with self.lock:
            operation = self.response_operation_locked(message, generation)
        if operation is not None:
            self.finish_response(operation, message, generation)

    def response_operation_locked(self, message: Any, generation: int | None) -> ToolOperation | None:
        identifier = self.request_id(message)
        if identifier is None:
            return None
        operation = self.outstanding.get(identifier)
        if not isinstance(operation, ToolOperation):
            return None
        if generation is not None and generation != operation.current_generation:
            return None
        return operation

    def finish_response(self, operation: ToolOperation, message: dict, generation: int | None) -> None:
        with self.lock:
            if operation.state != TOOL_ACTIVE or (
                generation is not None and generation != operation.current_generation
            ):
                return
            terminal_generation = operation.current_generation
            if isinstance(message.get("error"), dict):
                self._finish_operation_locked(operation, TOOL_FAILED, terminal_generation, self.jsonrpc_diagnostic(message)["code"])
            else:
                if operation.tool_name in DEVLOG_CORRELATION_TOOLS:
                    operation.response_correlation_id = _extract_devlog_correlation_id(message)
                self._finish_operation_locked(operation, TOOL_RESPONDED, terminal_generation)
        self.flush_terminal_events()

    @staticmethod
    def jsonrpc_error_reason(message: Any) -> str:
        if not isinstance(message, dict) or not isinstance(message.get("error"), dict):
            return "MCP_JSONRPC_ERROR"
        data = message["error"].get("data")
        if not isinstance(data, dict):
            return "MCP_JSONRPC_ERROR"
        category = data.get("category")
        return {
            "CORE_AUTHENTICATION": "CORE_AUTHENTICATION_FAILED",
            "CORE_AUTHORIZATION": "CORE_AUTHORIZATION_FAILED",
            "CORE_CONFLICT": "CORE_CONFLICT",
            "DELEGATION_AUTHENTICATION": "DELEGATION_FAILED",
            "PROVIDER_AUTHENTICATION": "PROVIDER_FAILED",
            "TRANSIENT_INFRASTRUCTURE": "TRANSIENT_INFRASTRUCTURE",
            "RATE_LIMITED": "RATE_LIMITED",
            "PROTOCOL": "PROTOCOL_INVALID",
            "FUNCTIONAL": "FUNCTIONAL_ERROR",
        }.get(category, "MCP_JSONRPC_ERROR")

    @staticmethod
    def jsonrpc_diagnostic(message: Any) -> dict[str, Any]:
        """Project structured JSON-RPC error data without backend details."""
        if not isinstance(message, dict) or not isinstance(message.get("error"), dict):
            return diagnostic_for_code("MCP_JSONRPC_ERROR")
        data = message["error"].get("data")
        if not isinstance(data, dict):
            return diagnostic_for_code("MCP_JSONRPC_ERROR")
        code = data.get("code") if isinstance(data.get("code"), str) else None
        if code is None:
            code = RuntimeSession.jsonrpc_error_reason(message)
        return diagnostic_for_code(code)

    @staticmethod
    def is_transport_auth_failure(message: Any) -> bool:
        if not isinstance(message, dict) or not isinstance(message.get("error"), dict):
            return False
        error = message["error"]
        data = error.get("data")
        if data is not None and (not isinstance(data, dict) or data.get("source") != "TRANSPORT"):
            return False
        return error.get("code") == -32600 and error.get("message") == "Authentication is required"

    def retry_authentication(self, message: Any) -> bool:
        if not self.is_transport_auth_failure(message):
            return False
        identifier = self.request_id(message)
        if identifier is None:
            return False
        terminal_event = False
        raw: bytes | None = None
        operation_id: str | None = None
        with self.lock:
            operation = self.outstanding.get(identifier)
            if not isinstance(operation, ToolOperation):
                return False
            if not operation.retry_safe:
                terminal_event = self._finish_operation_locked(
                    operation, TOOL_FAILED, operation.current_generation, "MCP_RETRY_NOT_IDEMPOTENT"
                )
                raw = None
                operation_id = None
            elif operation.retry_count >= 1:
                terminal_event = self._finish_operation_locked(
                    operation, TOOL_FAILED, operation.current_generation, "MCP_REAUTH_REQUIRED"
                )
            else:
                operation.retry_count += 1
                raw = operation.raw_payload
                operation_id = operation.operation_id
        if terminal_event:
            self.flush_terminal_events()
            return False
        if raw is None or operation_id is None:
            return False
        try:
            self.renew_if_needed(force=True, retry_operation_id=operation_id)
            self.forward(raw, json.loads(raw), retry_operation_id=operation_id)
            self.writer.write("TOKEN_RENEWAL_SUCCEEDED", code="AUTHENTICATION_RETRY")
            return True
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, TimeoutError):
            terminal_event = False
            with self.lock:
                current = self.operations.get(operation_id)
                if current is not None:
                    if self.stop_requested.is_set():
                        state = TOOL_INTERRUPTED
                        reason_code = "MCP_INSTANCE_STOPPING"
                    else:
                        state = TOOL_FAILED
                        reason_code = "MCP_REAUTH_REQUIRED"
                    terminal_event = self._finish_operation_locked(current, state, current.current_generation, reason_code)
            if terminal_event:
                self.flush_terminal_events()
            self.stop_child("MCP_REAUTH_REQUIRED")
            return False

    @staticmethod
    def is_rate_limited(message: Any) -> bool:
        if not isinstance(message, dict) or not isinstance(message.get("error"), dict):
            return False
        data = message["error"].get("data")
        return isinstance(data, dict) and data.get("source") == "CORE" and data.get("category") == "RATE_LIMITED"

    @staticmethod
    def rate_limit_retry_delay(message: Any) -> float:
        data = message.get("error", {}).get("data", {}) if isinstance(message, dict) else {}
        value = data.get("retryAfterSeconds") if isinstance(data, dict) else None
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or value <= 0:
            return DEFAULT_RATE_LIMIT_RETRY_BACKOFF_SECONDS
        return min(float(value), MAX_RATE_LIMIT_RETRY_SECONDS)

    def retry_rate_limited(self, message: Any) -> bool:
        if not self.is_rate_limited(message):
            return False
        identifier = self.request_id(message)
        if identifier is None:
            return False
        terminal_event = False
        raw: bytes | None = None
        operation_id: str | None = None
        delay = self.rate_limit_retry_delay(message)
        with self.lock:
            operation = self.outstanding.get(identifier)
            if not isinstance(operation, ToolOperation):
                return False
            if operation.rate_limit_retry_in_progress:
                if not operation.rate_limit_retry_sent:
                    return True
                terminal_event = self._finish_operation_locked(operation, TOOL_FAILED, operation.current_generation, "RATE_LIMITED")
                raw = None
                operation_id = None
            elif (
                not operation.retry_safe
                or operation.tool_name is None
                or operation.rate_limit_retry_count >= 1
            ):
                terminal_event = self._finish_operation_locked(
                    operation, TOOL_FAILED, operation.current_generation,
                    "MCP_RETRY_NOT_IDEMPOTENT" if not operation.retry_safe else "RATE_LIMITED",
                )
            else:
                operation.rate_limit_retry_count += 1
                operation.rate_limit_retry_in_progress = True
                operation.rate_limit_retry_sent = False
                raw = operation.raw_payload
                operation_id = operation.operation_id
        if terminal_event:
            self.flush_terminal_events()
            return False
        if raw is None or operation_id is None:
            return False
        self.writer.write(
            "MCP_RATE_LIMIT_RETRY",
            severity="WARN",
            code="RATE_LIMITED",
            attempt=1,
            maxAttempts=1,
            delaySeconds=delay,
        )
        try:
            if self.stop_requested.wait(timeout=delay):
                raise RuntimeError("MCP_INSTANCE_STOPPING")
            with self.lock:
                current = self.operations.get(operation_id)
                if current is None or current.state != TOOL_ACTIVE or current.operation_id != operation_id:
                    return False
                if current.current_generation != self.generation or self.stop_requested.is_set():
                    state = TOOL_INTERRUPTED if self.stop_requested.is_set() else TOOL_FAILED
                    reason_code = "MCP_INSTANCE_STOPPING" if state == TOOL_INTERRUPTED else "RATE_LIMITED"
                    terminal_event = self._finish_operation_locked(current, state, current.current_generation, reason_code)
                    raw = None
                    operation_id = None
            if terminal_event:
                self.flush_terminal_events()
                return False
            if raw is None or operation_id is None:
                return False
            with self.lock:
                current = self.operations.get(operation_id)
                if current is None or current.state != TOOL_ACTIVE:
                    return False
                current.rate_limit_retry_sent = True
            self.forward(raw, json.loads(raw), retry_operation_id=operation_id, skip_renewal=True)
            with self.lock:
                current = self.operations.get(operation_id)
                if current is not None:
                    current.rate_limit_retry_in_progress = False
            return True
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, TimeoutError):
            terminal_event = False
            with self.lock:
                current = self.operations.get(operation_id)
                if current is not None:
                    state = TOOL_INTERRUPTED if self.stop_requested.is_set() else TOOL_FAILED
                    reason_code = "MCP_INSTANCE_STOPPING" if state == TOOL_INTERRUPTED else "RATE_LIMITED"
                    terminal_event = self._finish_operation_locked(current, state, current.current_generation, reason_code)
            if terminal_event:
                self.flush_terminal_events()
            return False

    def consume_initialize_response(self, message: Any, generation: int | None = None) -> bool:
        if generation is not None and generation != self.generation:
            return True
        if not isinstance(message, dict):
            return False
        identifier = self.request_id(message)
        is_initialize_response = identifier == self.initialize_id and isinstance(message.get("result"), dict)
        if not self.suppress_initialize_response:
            if is_initialize_response and message["result"].get("protocolVersion"):
                self.publish_ready("INITIALIZE_RESPONSE", generation)
            return False
        if identifier != self.initialize_id:
            return False
        if is_initialize_response and message["result"].get("protocolVersion"):
            self.initialize_received.set()
        return True

    def request_id(self, message: Any) -> str | None:
        if not isinstance(message, dict) or "id" not in message:
            return None
        identifier = message["id"]
        if isinstance(identifier, (str, int, float)) and not isinstance(identifier, bool):
            return json.dumps(identifier, sort_keys=True)
        return None

    def ensure_ready(self) -> None:
        if self.initialize_received.wait(timeout=max(0, self.handshake_deadline - time.monotonic())):
            return
        raise RuntimeError("MCP_HANDSHAKE_FAILED")

    def publish_ready(self, code: str, generation: int | None = None) -> bool:
        # Decide under the session lock, but never keep it while acquiring
        # child_stop_lock: child termination can wait on a process reap.
        with self.lock:
            if generation is not None and generation != self.generation:
                return False
            if self.stop_requested.is_set():
                return False
        # Order readiness ahead of the STOPPED diagnostic without holding a
        # lifecycle lock during event I/O or any handshake write.
        with self.child_stop_lock:
            with self.lifecycle_lock:
                if self.stop_requested.is_set():
                    return False
                if generation is not None and generation != self.generation:
                    return False
                self.ready.set()
            self.writer.write("MCP_INSTANCE_READY", code=code)
            return True

    def write_child_handshake(self, stream: Any, payload: bytes) -> None:
        if self.stop_requested.is_set():
            raise RuntimeError("MCP_INSTANCE_STOPPING")
        if stream is None or time.monotonic() >= self.handshake_deadline:
            raise RuntimeError("MCP_HANDSHAKE_FAILED")
        # Popen owns a blocking buffered pipe; bypass buffering for the
        # replay and bound both writes with the same deadline as the reply.
        # Share stdin_write_lock with the ordinary relay so a handshake can
        # never interleave a frame with a concurrent request.
        with self.stdin_write_lock:
            if isinstance(stream, io.BufferedWriter) and isinstance(stream.raw, io.FileIO):
                fd = stream.fileno()
                blocking = os.get_blocking(fd)
                os.set_blocking(fd, False)
                remaining = memoryview(payload)
                poller = select.poll()
                poller.register(fd, select.POLLOUT)
                try:
                    while remaining:
                        if self.stop_requested.is_set():
                            raise RuntimeError("MCP_INSTANCE_STOPPING")
                        deadline_left = self.handshake_deadline - time.monotonic()
                        if deadline_left <= 0:
                            raise RuntimeError("MCP_HANDSHAKE_FAILED")
                        if not poller.poll(math.ceil(min(deadline_left, 0.25) * 1000)):
                            continue
                        try:
                            written = os.write(fd, remaining)
                        except BlockingIOError:
                            continue
                        if not written:
                            raise RuntimeError("MCP_HANDSHAKE_FAILED")
                        remaining = remaining[written:]
                finally:
                    os.set_blocking(fd, blocking)
            else:
                stream.write(payload)
                stream.flush()

    def renew_if_needed(self, force: bool = False, retry_operation_id: str | None = None) -> None:
        if self.stop_requested.is_set():
            raise RuntimeError("MCP_INSTANCE_STOPPING")
        with self.lock:
            while self.renewal_in_progress:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                self.renewal_condition.wait()
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                if self.replacement_failed.is_set():
                    raise RuntimeError("MCP_REAUTH_REQUIRED")
                if time.time() + self.renewal_margin < self.lease.expires_at:
                    return
                if time.time() - self.last_renewal_failure < self.renewal_cooldown:
                    raise RuntimeError("MCP_REAUTH_REQUIRED")
            if self.replacement_failed.is_set():
                raise RuntimeError("MCP_REAUTH_REQUIRED")
            if not force and time.time() + self.renewal_margin < self.lease.expires_at:
                return
            if self.outstanding:
                if retry_operation_id is None or len(self.outstanding) != 1:
                    raise RuntimeError("MCP_REAUTH_REQUIRED")
                operation = next(iter(self.outstanding.values()))
                if not isinstance(operation, ToolOperation) or operation.operation_id != retry_operation_id:
                    raise RuntimeError("MCP_REAUTH_REQUIRED")
            if time.time() - self.last_renewal_failure < self.renewal_cooldown:
                raise RuntimeError("MCP_REAUTH_REQUIRED")
            with self.lifecycle_lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                self.renewal_in_progress = True
        try:
            self._renew(force)
        finally:
            with self.lock:
                with self.lifecycle_lock:
                    self.renewal_in_progress = False
                self.renewal_condition.notify_all()

    def _renew(self, force: bool) -> None:
        replacing = False
        try:
            if self.stop_requested.is_set():
                raise RuntimeError("MCP_INSTANCE_STOPPING")
            self.writer.write("TOKEN_EXPIRED", severity="INFO", code="TOKEN_RENEWAL_REQUIRED")
            self.writer.write("TOKEN_RENEWAL_STARTED")
            if self.stop_requested.is_set():
                raise RuntimeError("MCP_INSTANCE_STOPPING")
            lease = self.acquire_token_with_retry()
            if lease.expires_at - time.time() < self.minimum_lease:
                raise RuntimeError("TOKEN_RESPONSE_INVALID")
            if self.stop_requested.is_set():
                raise RuntimeError("MCP_INSTANCE_STOPPING")
            replacing = True
            self.stop_child("TOKEN_RENEWAL")
            with self.lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                self.lease = lease
                has_initialize = self.initialize_raw is not None
                self.handshake_deadline = time.monotonic() + 10
            self.start_child(replay_initialize=has_initialize)
            if has_initialize:
                self.ensure_ready()
                with self.lock:
                    if self.stop_requested.is_set():
                        raise RuntimeError("MCP_INSTANCE_STOPPING")
                    child, initialized = self.child, self.initialized_raw
                if initialized is not None:
                    self.write_child_handshake(child.stdin if child is not None else None, initialized)
                with self.lock:
                    if self.stop_requested.is_set():
                        raise RuntimeError("MCP_INSTANCE_STOPPING")
                    if time.monotonic() >= self.handshake_deadline:
                        raise RuntimeError("MCP_HANDSHAKE_FAILED")
                    current_generation = self.generation
                if not self.publish_ready("RENEWAL_INITIALIZE_RESPONSE", current_generation):
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                self.suppress_initialize_response = False
            if self.stop_requested.is_set():
                raise RuntimeError("MCP_INSTANCE_STOPPING")
            self.writer.write("TOKEN_RENEWAL_SUCCEEDED")
        except (OSError, RuntimeError, urllib.error.URLError, TimeoutError) as exception:
            with self.lock:
                if replacing:
                    # The new lease alone cannot make a failed generation
                    # usable. Publish the failure before releasing waiters.
                    self.replacement_failed.set()
                self.last_renewal_failure = time.time()
            code = str(exception).split(":", 1)[0]
            if isinstance(exception, urllib.error.HTTPError):
                code = "TOKEN_ENDPOINT_5XX" if exception.code in {500, 502, 503, 504} else "TOKEN_ENDPOINT_REJECTED"
            elif isinstance(exception, (urllib.error.URLError, TimeoutError)):
                code = "TOKEN_ENDPOINT_TIMEOUT"
            self.writer.write("TOKEN_RENEWAL_FAILED", severity="ERROR", code=code)
            raise RuntimeError("MCP_REAUTH_REQUIRED") from exception

    def forward(
        self,
        raw: bytes,
        message: Any,
        retry_operation_id: str | None = None,
        skip_renewal: bool = False,
    ) -> None:
        if not skip_renewal:
            self.renew_if_needed(retry_operation_id=retry_operation_id)
        if self.replacement_failed.is_set():
            raise RuntimeError("MCP_REAUTH_REQUIRED")
        child = self.child
        if child is None or child.stdin is None:
            raise RuntimeError("MCP_PROCESS_FAILED")
        identifier = self.request_id(message)
        if isinstance(message, dict) and "id" in message and identifier is None:
            raise RuntimeError("MCP_PROTOCOL_INVALID")
        if isinstance(message, dict) and message.get("method") == "initialize":
            self.initialize_raw = raw
            self.initialize_id = identifier
        if isinstance(message, dict) and message.get("method") == "notifications/initialized":
            self.initialized_raw = raw
        if identifier is not None:
            started_operation: ToolOperation | None = None
            tool_name: str | None = None
            if isinstance(message, dict) and message.get("method") == "tools/call":
                params = message.get("params")
                if isinstance(params, dict):
                    raw_name = params.get("name")
                    if isinstance(raw_name, str) and raw_name:
                        tool_name = raw_name[:128]
            with self.lock:
                if self.stop_requested.is_set():
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
                existing = self.outstanding.get(identifier)
                if existing is not None:
                    if retry_operation_id is None or not isinstance(existing, ToolOperation):
                        raise RuntimeError("MCP_PROTOCOL_INVALID")
                    if existing.operation_id != retry_operation_id or existing.state != TOOL_ACTIVE:
                        raise RuntimeError("MCP_PROTOCOL_INVALID")
                    existing.current_generation = self.generation
                    operation = existing
                else:
                    if retry_operation_id is not None:
                        raise RuntimeError("MCP_PROTOCOL_INVALID")
                    operation = ToolOperation(
                        operation_id=self._new_operation_id(),
                        request_id=identifier,
                        initial_generation=self.generation,
                        current_generation=self.generation,
                        state=TOOL_ACTIVE,
                        retry_count=0,
                        raw_payload=raw,
                        started_at=time.monotonic(),
                        retry_safe=_is_retry_safe_tool(message, tool_name),
                        tool_name=tool_name,
                    )
                    self.outstanding[identifier] = operation
                    self.operations[operation.operation_id] = operation
                    self.pending_operation_events.append((operation, "MCP_TOOL_STARTED", TOOL_ACTIVE, self.generation, None))
                    started_operation = operation
            if started_operation is not None:
                self.flush_terminal_events()
        with self.stdin_write_lock:
            with self.lock:
                if self.replacement_failed.is_set():
                    raise RuntimeError("MCP_REAUTH_REQUIRED")
                if self.stop_requested.is_set() or (identifier is not None and operation.state != TOOL_ACTIVE):
                    raise RuntimeError("MCP_INSTANCE_STOPPING")
            try:
                child.stdin.write(raw)
                child.stdin.flush()
            except (BrokenPipeError, OSError) as exception:
                if identifier is not None:
                    self._finish_operation(
                        operation,
                        TOOL_FAILED,
                        operation.current_generation,
                        reason_code="MCP_PROCESS_FAILED",
                    )
                raise RuntimeError("MCP_PROCESS_FAILED") from exception

    def relay_stdin(self) -> None:
        buffer = b""
        try:
            while not self.stop_requested.is_set():
                chunk = sys.stdin.buffer.read1(65536)
                if not chunk:
                    push_thread = self.pusher.push_session_lifecycle_event("SESSION_LIFECYCLE_ENDED")
                    self.stop_child("STDIN_CLOSED")
                    if push_thread is not None:
                        push_thread.join(timeout=6)
                    return
                buffer += chunk
                while b"\n" in buffer:
                    line, buffer = buffer.split(b"\n", 1)
                    raw = line + b"\n"
                    try:
                        message = json.loads(line)
                    except json.JSONDecodeError as exception:
                        raise RuntimeError("MCP_PROTOCOL_INVALID") from exception
                    if not isinstance(message, dict):
                        raise RuntimeError("MCP_PROTOCOL_INVALID")
                    self.forward(raw, message)
        except RuntimeError as exception:
            self.writer.write("MCP_INSTANCE_STOPPED", severity="ERROR", code=str(exception))
            self.stop_child(str(exception))
        except (BrokenPipeError, OSError):
            self.writer.write("MCP_INSTANCE_STOPPED", severity="WARN", code="STDIN_RELAY_FAILED")
            self.stop_child("STDIN_RELAY_FAILED")


def terminate_child(child: subprocess.Popen[bytes], writer: EventWriter, code: str) -> None:
    if child.poll() is not None:
        return
    writer.write("MCP_INSTANCE_STOPPED", code=code)
    child.terminate()
    try:
        child.wait(timeout=5)
    except subprocess.TimeoutExpired:
        writer.write("MCP_INSTANCE_STOPPED", severity="WARN", code="CHILD_KILL_REQUIRED")
        child.kill()
        child.wait(timeout=5)


def prune_completed_instances(root: Path) -> None:
    if not root.is_dir():
        return
    completed = []
    for directory in root.iterdir():
        if not directory.is_dir():
            continue
        try:
            manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        if manifest.get("state") in {"STOPPED", "FAILED"}:
            completed.append((directory.stat().st_mtime, directory))
    for _, directory in sorted(completed, reverse=True)[MAX_COMPLETED_INSTANCES:]:
        shutil.rmtree(directory, ignore_errors=True)


def supervise_child(
    session: RuntimeSession, parent_pid: int, parent_start: str | None,
    interrupted: Callable[[], bool] = lambda: False,
) -> subprocess.Popen[bytes] | None:
    try:
        while True:
            if interrupted() and not session.stop_requested.is_set():
                session.stop_child("SIGNAL_RECEIVED")
            if not session.stop_requested.is_set() and (
                os.getppid() != parent_pid or process_start_time(parent_pid) != parent_start
            ):
                session.request_stop()
                session.writer.write("MCP_PARENT_DISAPPEARED", severity="WARN", code="PARENT_PROCESS_GONE")
                session.stop_child("PARENT_PROCESS_GONE")
            with session.lifecycle_lock:
                child = session.child
                if session.stop_requested.is_set():
                    return child
                if (child is None or child.poll() is not None) and not session.renewal_in_progress:
                    # Seal the terminal decision before another thread can start a replacement.
                    session.stop_requested.set()
                    return child
            time.sleep(0.25)
    except KeyboardInterrupt:
        session.stop_child("SIGNAL_RECEIVED")
        return session.child


def run_owned_session(session: RuntimeSession, parent_pid: int, parent_start: str | None) -> int:
    interrupted = False

    def record_interrupt(_signal, _frame):
        # Python can invoke this while the main thread owns any of our
        # locks. Record intent only; do not raise out of ownership cleanup.
        nonlocal interrupted
        interrupted = True

    previous = None
    if threading.current_thread() is threading.main_thread():
        previous = signal.signal(signal.SIGINT, record_interrupt)
    try:
        try:
            session.start_child()
        except RuntimeError as exception:
            session.writer.write("MCP_PROCESS_EXITED", severity="ERROR", code=str(exception))
            update_manifest(session.directory, state="FAILED", failureCode="MCP_PROCESS_FAILED")
            return 1
        threading.Thread(target=session.relay_stdin, daemon=True).start()
        child = supervise_child(session, parent_pid, parent_start, lambda: interrupted)
        return finalize_owned_session(session, child, lambda: interrupted)
    finally:
        if previous is not None:
            signal.signal(signal.SIGINT, previous)


def finalize_owned_session(
    session: RuntimeSession, child: subprocess.Popen[bytes] | None, interrupted: Callable[[], bool],
) -> int:
    # No new spawn may be reserved after the terminal decision. An already
    # reserved Popen must hand off or reap its exact child before finalization.
    session.spawn_done.wait()
    if session.unpublished_child is not None:
        session.terminate_owned_child(session.unpublished_child, "MCP_INSTANCE_STOPPING")
    if child is None:
        update_manifest(session.directory, state="FAILED", failureCode="MCP_PROCESS_FAILED")
        return 1
    session.terminate_owned_child(child, "MCP_INSTANCE_STOPPING")
    exit_code = child.wait(timeout=5)
    if not session.explicit_stop.is_set() and not interrupted():
        # Drain already-produced frames on natural exit, but never wait
        # indefinitely for a backpressured or disconnected client.
        session.stdout_done.wait(timeout=1)
    session.request_stop()
    session.interrupt_pending_operations()
    if not session.ready.is_set():
        session.writer.write("MCP_HANDSHAKE_FAILED", severity="WARN", code="MCP_HANDSHAKE_FAILED")
    session.writer.write("MCP_PROCESS_EXITED", code="MCP_PROCESS_FAILED" if exit_code else "NORMAL_EXIT", exitCode=exit_code)
    update_manifest(session.directory, state="STOPPED", exitCode=exit_code, stoppedAt=datetime.now(timezone.utc).isoformat())
    return exit_code


def main() -> int:
    instance_id = secrets.token_hex(12)
    root = state_root()
    root.mkdir(parents=True, exist_ok=True)
    prune_completed_instances(root)
    directory = root / instance_id
    writer = EventWriter(directory, instance_id)
    parent_pid = os.getppid()
    parent_start = process_start_time(parent_pid)
    agent_session_id = env("CLAUDE_CODE_SESSION_ID") or None
    agent_provider = env("AI_AGENT") or None
    configuration = required_configuration()
    configuration["agent_session_id"] = agent_session_id or ""
    configuration["agent_provider"] = agent_provider or ""
    write_manifest(directory, instance_id, parent_pid, parent_start,
                   agent_session_id=agent_session_id, agent_provider=agent_provider)
    launcher_fields: dict[str, Any] = {"launcherPid": os.getpid(), "parentPid": parent_pid}
    if agent_session_id:
        launcher_fields["agentSessionId"] = agent_session_id
    if agent_provider:
        launcher_fields["agentProvider"] = agent_provider
    writer.write("LAUNCHER_STARTED", **launcher_fields)

    try:
        validate_configuration(configuration)
        writer.write("CONFIGURATION_VALIDATED")
        writer.write("TOKEN_ACQUISITION_STARTED")
        lease = acquire_token(configuration)
        writer.write("TOKEN_ACQUISITION_SUCCEEDED")
    except urllib.error.HTTPError as exception:
        writer.write("TOKEN_ACQUISITION_FAILED", severity="ERROR", code="TOKEN_ENDPOINT_REJECTED", httpStatus=exception.code)
        update_manifest(directory, state="FAILED", failureCode="TOKEN_ENDPOINT_REJECTED")
        return 1
    except (urllib.error.URLError, TimeoutError) as exception:
        writer.write(
            "TOKEN_ACQUISITION_FAILED",
            severity="ERROR",
            code="KEYCLOAK_UNREACHABLE",
            errorType=type(exception).__name__,
        )
        update_manifest(directory, state="FAILED", failureCode="KEYCLOAK_UNREACHABLE")
        return 1
    except (OSError, RuntimeError) as exception:
        message = str(exception)
        if message.startswith("MCP_JAR_MISSING"):
            code = "MCP_JAR_MISSING"
        elif message.startswith("TOKEN_RESPONSE_INVALID"):
            code = "TOKEN_RESPONSE_INVALID"
        else:
            code = message.split(":", 1)[0] if ":" in message else "CONFIGURATION_INVALID"
        event = "DEPENDENCY_CHECK_FAILED" if code in {"CONFIGURATION_INVALID", "MCP_JAR_MISSING"} else "TOKEN_ACQUISITION_FAILED"
        writer.write(event, severity="ERROR", code=code, errorType=type(exception).__name__)
        update_manifest(directory, state="FAILED", failureCode=code)
        return 1

    session = RuntimeSession(configuration, directory, writer, lease)
    return run_owned_session(session, parent_pid, parent_start)


if __name__ == "__main__":
    raise SystemExit(main())
