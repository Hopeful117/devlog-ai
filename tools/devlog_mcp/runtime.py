#!/usr/bin/env python3
"""Supervise one OpenCode-owned DevLog MCP STDIO instance."""

from __future__ import annotations

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
    }


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


def write_manifest(directory: Path, instance_id: str, parent_pid: int, parent_start: str | None) -> None:
    manifest = {
        "instanceId": instance_id,
        "launcherPid": os.getpid(),
        "launcherStartTime": process_start_time(os.getpid()),
        "parentOpenCodePid": parent_pid,
        "parentOpenCodeStartTime": parent_start,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "state": "STARTING",
    }
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
        self.renewal_condition = threading.Condition(self.lock)
        self.renewal_in_progress = False
        self.outstanding: dict[str, int] = {}
        self.request_payloads: dict[str, list[bytes]] = {}
        self.request_retries: dict[str, int] = {}
        self.last_renewal_failure = 0.0
        self.initialize_raw: bytes | None = None
        self.initialized_raw: bytes | None = None
        self.initialize_id: str | None = None
        self.suppress_initialize_response = False
        self.generation = 0

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
        child = self.child
        if child is None or child.poll() is not None:
            return
        with self.lock:
            self.generation += 1
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

    def observe_response(self, message: Any) -> None:
        if not isinstance(message, dict) or "id" not in message:
            return
        identifier = message["id"]
        if isinstance(identifier, (str, int, float)) and not isinstance(identifier, bool):
            with self.lock:
                key = json.dumps(identifier, sort_keys=True)
                if self.outstanding.get(key, 0) <= 1:
                    self.outstanding.pop(key, None)
                else:
                    self.outstanding[key] -= 1
                payloads = self.request_payloads.get(key)
                if payloads:
                    payloads.pop(0)
                    if not payloads:
                        self.request_payloads.pop(key, None)
                        self.request_retries.pop(key, None)

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
        with self.lock:
            payloads = self.request_payloads.get(identifier)
            retries = self.request_retries.get(identifier, 0)
            if not payloads or retries >= 1:
                self.observe_response(message)
                return False
            raw = payloads.pop(0)
            if not payloads:
                self.request_payloads.pop(identifier, None)
            self.request_retries[identifier] = retries + 1
            self.observe_response(message)
        try:
            self.renew_if_needed(force=True)
            self.forward(raw, json.loads(raw))
            self.writer.write("TOKEN_RENEWAL_SUCCEEDED", code="AUTHENTICATION_RETRY")
            return True
        except (OSError, RuntimeError, ValueError, urllib.error.URLError, TimeoutError):
            self.stop_requested.set()
            self.stop_child("MCP_REAUTH_REQUIRED")
            return False

    def consume_initialize_response(self, message: Any, generation: int | None = None) -> bool:
        if generation is not None and generation != self.generation:
            return True
        if not isinstance(message, dict):
            self.observe_response(message)
            return False
        identifier = self.request_id(message)
        is_initialize_response = identifier == self.initialize_id and isinstance(message.get("result"), dict)
        if not self.suppress_initialize_response:
            self.observe_response(message)
            if is_initialize_response and message["result"].get("protocolVersion"):
                self.ready.set()
                self.writer.write("MCP_INSTANCE_READY", code="INITIALIZE_RESPONSE")
            return False
        if identifier != self.initialize_id:
            self.observe_response(message)
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

    def renew_if_needed(self, force: bool = False) -> None:
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

    def forward(self, raw: bytes, message: Any) -> None:
        self.renew_if_needed()
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
            with self.lock:
                if self.outstanding.get(identifier, 0) > 0:
                    raise RuntimeError("MCP_PROTOCOL_INVALID")
                self.outstanding[identifier] = self.outstanding.get(identifier, 0) + 1
                self.request_payloads.setdefault(identifier, []).append(raw)
        try:
            child.stdin.write(raw)
            child.stdin.flush()
        except (BrokenPipeError, OSError) as exception:
            raise RuntimeError("MCP_PROCESS_FAILED") from exception

    def relay_stdin(self) -> None:
        buffer = b""
        try:
            while not self.stop_requested.is_set():
                chunk = sys.stdin.buffer.read1(65536)
                if not chunk:
                    self.stop_requested.set()
                    self.stop_child("STDIN_CLOSED")
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
    configuration = required_configuration()
    write_manifest(directory, instance_id, parent_pid, parent_start)
    writer.write("LAUNCHER_STARTED", launcherPid=os.getpid(), parentPid=parent_pid)

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
