#!/usr/bin/env python3
"""Supervise one OpenCode-owned DevLog MCP STDIO instance."""

import json
import base64
import binascii
import math
import os
import re
import secrets
import shutil
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
from typing import Any


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
TOOL_SCHEMA_VERSION = "mcp-tool-observability-v1"
TOOL_ACTIVE = "ACTIVE"
TOOL_RESPONDED = "RESPONDED"
TOOL_FAILED = "FAILED"
TOOL_INTERRUPTED = "INTERRUPTED"


DEVLOG_CORRELATION_TOOLS = frozenset({
    "devlog_story_agent",
    "story_context_agent_follow_up_authenticated",
})


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
    tool_name: str | None = None
    response_correlation_id: str | None = None


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
    for name in ("renewal_margin_seconds", "renewal_cooldown_seconds", "minimum_lease_seconds"):
        try:
            if not math.isfinite(float(configuration[name])) or float(configuration[name]) < 0:
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
        self.stop_requested = threading.Event()
        self.lock = threading.RLock()
        self.tool_event_lock = threading.Lock()
        self.stdin_write_lock = threading.Lock()
        self.renewal_condition = threading.Condition(self.lock)
        self.renewal_in_progress = False
        self.outstanding: dict[str, ToolOperation | int] = {}
        self.operations: dict[str, ToolOperation] = {}
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
        return True

    def _emit_terminal_event(
        self,
        operation: ToolOperation,
        state: str,
        generation: int,
        reason_code: str | None = None,
    ) -> None:
        self._emit_tool_event(
            operation,
            {
                TOOL_RESPONDED: "MCP_TOOL_RESPONDED",
                TOOL_FAILED: "MCP_TOOL_FAILED",
                TOOL_INTERRUPTED: "MCP_TOOL_INTERRUPTED",
            }[state],
            state,
            generation,
            reason_code=reason_code,
        )

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
            self._emit_terminal_event(operation, state, generation, reason_code)
        return finished

    @property
    def renewal_margin(self) -> float:
        return float(self.configuration["renewal_margin_seconds"])

    @property
    def renewal_cooldown(self) -> float:
        return float(self.configuration["renewal_cooldown_seconds"])

    @property
    def minimum_lease(self) -> float:
        return float(self.configuration.get("minimum_lease_seconds", DEFAULT_MINIMUM_LEASE_SECONDS))

    def start_child(self, replay_initialize: bool = False) -> None:
        with self.lock:
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
        self.child = child
        self.ready = threading.Event()
        self.suppress_initialize_response = replay_initialize and self.initialize_raw is not None
        update_manifest(self.directory, state="RUNNING", mcpPid=child.pid, mcpStartTime=process_start_time(child.pid))
        self.writer.write("MCP_PROCESS_STARTED", mcpPid=child.pid)
        threading.Thread(target=self.relay_stdout, args=(child, generation), daemon=True).start()
        threading.Thread(target=self.relay_stderr, args=(child,), daemon=True).start()
        if replay_initialize and self.initialize_raw is not None and child.stdin is not None:
            child.stdin.write(self.initialize_raw)
            child.stdin.flush()

    def stop_child(self, code: str) -> None:
        terminal_events: list[tuple[ToolOperation, int]] = []
        with self.lock:
            child = self.child
            if code != "TOKEN_RENEWAL":
                self.stop_requested.set()
                self.generation += 1
                for operation in list(self.operations.values()):
                    if self._finish_operation_locked(operation, TOOL_INTERRUPTED, self.generation):
                        terminal_events.append((operation, self.generation))
            child_running = child is not None and child.poll() is None
        for terminal_operation, terminal_generation in terminal_events:
            self._emit_terminal_event(
                terminal_operation,
                TOOL_INTERRUPTED,
                terminal_generation,
                reason_code="MCP_INSTANCE_STOPPING",
            )
        if not child_running:
            return
        self.writer.write("MCP_INSTANCE_STOPPED", code=code)
        if child.stdin:
            try:
                child.stdin.close()
            except OSError:
                pass
        child.terminate()
        try:
            child.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.writer.write("MCP_INSTANCE_STOPPED", severity="WARN", code="CHILD_KILL_REQUIRED")
            child.kill()
            child.wait(timeout=5)

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
                if auth_failure and self.retry_authentication(message):
                    continue
                with self.lock:
                    if generation != self.generation:
                        continue
                    internal = self.consume_initialize_response(message, generation)
                if internal:
                    continue
                with self.lock:
                    if generation == self.generation:
                        sys.stdout.buffer.write(line + b"\n")
                        sys.stdout.buffer.flush()
        with self.lock:
            if buffer and generation == self.generation:
                sys.stdout.buffer.write(buffer)
                sys.stdout.buffer.flush()

    def relay_stderr(self, child: subprocess.Popen[bytes]) -> None:
        assert child.stderr is not None
        for raw_line in child.stderr:
            line = redact(raw_line.decode("utf-8", errors="replace").rstrip())
            if line:
                self.writer.write("MCP_PROCESS_LOG", severity="DEBUG", message=line)
                sys.stderr.write(line + "\n")
                sys.stderr.flush()

    def observe_response(self, message: Any, generation: int | None = None) -> None:
        if not isinstance(message, dict) or "id" not in message:
            return
        identifier = message["id"]
        if isinstance(identifier, (str, int, float)) and not isinstance(identifier, bool):
            terminal_event: tuple[ToolOperation, str, int, str | None] | None = None
            with self.lock:
                key = json.dumps(identifier, sort_keys=True)
                operation = self.outstanding.get(key)
                if not isinstance(operation, ToolOperation):
                    return
                if generation is not None and generation != operation.current_generation:
                    return
                if isinstance(message.get("error"), dict):
                    terminal_generation = generation if generation is not None else operation.current_generation
                    if self._finish_operation_locked(operation, TOOL_FAILED, terminal_generation):
                        terminal_event = (operation, TOOL_FAILED, terminal_generation, "MCP_JSONRPC_ERROR")
                else:
                    terminal_generation = generation if generation is not None else operation.current_generation
                    if operation.tool_name in DEVLOG_CORRELATION_TOOLS:
                        correlation_id = _extract_devlog_correlation_id(message)
                        if correlation_id:
                            operation.response_correlation_id = correlation_id
                    if self._finish_operation_locked(operation, TOOL_RESPONDED, terminal_generation):
                        terminal_event = (operation, TOOL_RESPONDED, terminal_generation, None)
            if terminal_event is not None:
                self._emit_terminal_event(*terminal_event)

    def is_transport_auth_failure(self, message: Any) -> bool:
        if not isinstance(message, dict) or not isinstance(message.get("error"), dict):
            return False
        error = message["error"]
        return error.get("code") == -32600 and error.get("message") == "Authentication is required"

    def retry_authentication(self, message: Any) -> bool:
        if not self.is_transport_auth_failure(message):
            return False
        identifier = self.request_id(message)
        if identifier is None:
            return False
        terminal_event: tuple[ToolOperation, str, int, str | None] | None = None
        with self.lock:
            operation = self.outstanding.get(identifier)
            if not isinstance(operation, ToolOperation):
                return False
            if operation.retry_count >= 1:
                if self._finish_operation_locked(operation, TOOL_FAILED, operation.current_generation):
                    terminal_event = (operation, TOOL_FAILED, operation.current_generation, "MCP_REAUTH_REQUIRED")
            else:
                operation.retry_count += 1
                raw = operation.raw_payload
                operation_id = operation.operation_id
        if terminal_event is not None:
            self._emit_terminal_event(*terminal_event)
            return False
        try:
            self.renew_if_needed(force=True, retry_operation_id=operation_id)
            self.forward(raw, json.loads(raw), retry_operation_id=operation_id)
            self.writer.write("TOKEN_RENEWAL_SUCCEEDED", code="AUTHENTICATION_RETRY")
            return True
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, TimeoutError):
            terminal_event = None
            with self.lock:
                current = self.operations.get(operation_id)
                if current is not None:
                    if self.stop_requested.is_set():
                        state = TOOL_INTERRUPTED
                        reason_code = "MCP_INSTANCE_STOPPING"
                    else:
                        state = TOOL_FAILED
                        reason_code = "MCP_REAUTH_REQUIRED"
                    if self._finish_operation_locked(current, state, current.current_generation):
                        terminal_event = (current, state, current.current_generation, reason_code)
            if terminal_event is not None:
                self._emit_terminal_event(*terminal_event)
            self.stop_requested.set()
            self.stop_child("MCP_REAUTH_REQUIRED")
            return False

    def consume_initialize_response(self, message: Any, generation: int | None = None) -> bool:
        if generation is not None and generation != self.generation:
            return True
        if not isinstance(message, dict):
            self.observe_response(message, generation)
            return False
        identifier = self.request_id(message)
        is_initialize_response = identifier == self.initialize_id and isinstance(message.get("result"), dict)
        if not self.suppress_initialize_response:
            self.observe_response(message, generation)
            if is_initialize_response and message["result"].get("protocolVersion"):
                self.ready.set()
                self.writer.write("MCP_INSTANCE_READY", code="INITIALIZE_RESPONSE")
            return False
        if identifier != self.initialize_id:
            self.observe_response(message, generation)
            return False
        if is_initialize_response and message["result"].get("protocolVersion"):
            self.ready.set()
            self.writer.write("MCP_INSTANCE_READY", code="RENEWAL_INITIALIZE_RESPONSE")
        return True

    def request_id(self, message: Any) -> str | None:
        if not isinstance(message, dict) or "id" not in message:
            return None
        identifier = message["id"]
        if isinstance(identifier, (str, int, float)) and not isinstance(identifier, bool):
            return json.dumps(identifier, sort_keys=True)
        return None

    def ensure_ready(self) -> None:
        if self.ready.wait(timeout=10):
            return
        raise RuntimeError("MCP_HANDSHAKE_FAILED")

    def renew_if_needed(self, force: bool = False, retry_operation_id: str | None = None) -> None:
        with self.lock:
            while self.renewal_in_progress:
                self.renewal_condition.wait()
                if time.time() + self.renewal_margin < self.lease.expires_at:
                    return
                if time.time() - self.last_renewal_failure < self.renewal_cooldown:
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
            self.renewal_in_progress = True
        try:
            self._renew(force)
        finally:
            with self.lock:
                self.renewal_in_progress = False
                self.renewal_condition.notify_all()

    def _renew(self, force: bool) -> None:
        with self.lock:
            self.writer.write("TOKEN_EXPIRED", severity="INFO", code="TOKEN_RENEWAL_REQUIRED")
            self.writer.write("TOKEN_RENEWAL_STARTED")
        try:
            lease = acquire_token(self.configuration)
            if lease.expires_at - time.time() < self.minimum_lease:
                raise RuntimeError("TOKEN_RESPONSE_INVALID")
            self.stop_child("TOKEN_RENEWAL")
            with self.lock:
                self.lease = lease
                self.start_child(replay_initialize=self.initialize_raw is not None)
                has_initialize = self.initialize_raw is not None
            if has_initialize:
                self.ensure_ready()
                with self.lock:
                    if self.initialized_raw is not None and self.child and self.child.stdin:
                        self.child.stdin.write(self.initialized_raw)
                        self.child.stdin.flush()
                    self.suppress_initialize_response = False
            self.writer.write("TOKEN_RENEWAL_SUCCEEDED")
        except (OSError, RuntimeError, urllib.error.URLError, TimeoutError) as exception:
            with self.lock:
                self.last_renewal_failure = time.time()
            code = str(exception).split(":", 1)[0]
            if isinstance(exception, urllib.error.HTTPError):
                code = "TOKEN_RENEWAL_FAILED"
            self.writer.write("TOKEN_RENEWAL_FAILED", severity="ERROR", code=code)
            raise RuntimeError("MCP_REAUTH_REQUIRED") from exception

    def forward(self, raw: bytes, message: Any, retry_operation_id: str | None = None) -> None:
        self.renew_if_needed(retry_operation_id=retry_operation_id)
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
                if self.stop_requested.is_set() and retry_operation_id is None:
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
                    operation = ToolOperation(
                        operation_id=self._new_operation_id(),
                        request_id=identifier,
                        initial_generation=self.generation,
                        current_generation=self.generation,
                        state=TOOL_ACTIVE,
                        retry_count=0,
                        raw_payload=raw,
                        started_at=time.monotonic(),
                        tool_name=tool_name,
                    )
                    self.outstanding[identifier] = operation
                    self.operations[operation.operation_id] = operation
                    started_operation = operation
            if started_operation is not None:
                self._emit_tool_event(
                    started_operation,
                    "MCP_TOOL_STARTED",
                    TOOL_ACTIVE,
                    started_operation.current_generation,
                )
        with self.stdin_write_lock:
            if identifier is not None:
                with self.lock:
                    if operation.state != TOOL_ACTIVE:
                        raise RuntimeError("MCP_INSTANCE_STOPPING")
                    if self.stop_requested.is_set() and retry_operation_id is None:
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
                    self.stop_requested.set()
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
            self.stop_requested.set()
            self.stop_child(str(exception))
        except (BrokenPipeError, OSError):
            self.writer.write("MCP_INSTANCE_STOPPED", severity="WARN", code="STDIN_RELAY_FAILED")
            self.stop_requested.set()


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
        writer.write("TOKEN_ACQUISITION_FAILED", severity="ERROR", code="KEYCLOAK_UNREACHABLE", message=str(exception))
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
        writer.write(event, severity="ERROR", code=code, message=message)
        update_manifest(directory, state="FAILED", failureCode=code)
        return 1

    session = RuntimeSession(configuration, directory, writer, lease)
    try:
        session.start_child()
    except RuntimeError as exception:
        writer.write("MCP_PROCESS_EXITED", severity="ERROR", code=str(exception))
        update_manifest(directory, state="FAILED", failureCode="MCP_PROCESS_FAILED")
        return 1

    threading.Thread(target=session.relay_stdin, daemon=True).start()

    try:
        while session.child is not None and session.child.poll() is None and not session.stop_requested.is_set():
            if os.getppid() != parent_pid or process_start_time(parent_pid) != parent_start:
                writer.write("MCP_PARENT_DISAPPEARED", severity="WARN", code="PARENT_PROCESS_GONE")
                session.stop_requested.set()
                session.stop_child("PARENT_PROCESS_GONE")
                break
            time.sleep(0.25)
    except KeyboardInterrupt:
        session.stop_requested.set()
        session.stop_child("SIGNAL_RECEIVED")

    child = session.child
    if child is None:
        update_manifest(directory, state="FAILED", failureCode="MCP_PROCESS_FAILED")
        return 1
    exit_code = child.wait()
    if not session.ready.is_set():
        writer.write("MCP_HANDSHAKE_FAILED", severity="WARN", code="MCP_HANDSHAKE_FAILED")
    writer.write("MCP_PROCESS_EXITED", code="MCP_PROCESS_FAILED" if exit_code else "NORMAL_EXIT", exitCode=exit_code)
    update_manifest(directory, state="STOPPED", exitCode=exit_code, stoppedAt=datetime.now(timezone.utc).isoformat())
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
