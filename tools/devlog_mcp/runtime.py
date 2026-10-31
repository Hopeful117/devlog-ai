#!/usr/bin/env python3
"""Supervise one OpenCode-owned DevLog MCP STDIO instance."""

from __future__ import annotations

import json
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
)
MAX_EVENT_BYTES = 5 * 1024 * 1024
MAX_COMPLETED_INSTANCES = 20


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
        "actor_client_id": env("KEYCLOAK_MCP_ACTOR_CLIENT_ID", "devlog-mcp-opencode"),
        "secret_file": env(
            "DEVLOG_MCP_CLIENT_SECRET_FILE",
            str(Path.home() / ".config/opencode/devlog-mcp-client-secret"),
        ),
        "jar": env(
            "DEVLOG_MCP_JAR",
            "/home/ludo/workspace/devlog-ai/mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar",
        ),
    }


def acquire_token(configuration: dict[str, str]) -> str:
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
    with urllib.request.urlopen(request, timeout=10) as response:
        result = json.load(response)
    token = result.get("access_token")
    if not isinstance(token, str) or not token:
        raise RuntimeError("TOKEN_RESPONSE_INVALID")
    return token


def validate_configuration(configuration: dict[str, str]) -> None:
    if not Path(configuration["secret_file"]).is_file():
        raise RuntimeError("CONFIGURATION_INVALID: client secret file is missing")
    jar = Path(configuration["jar"])
    if not jar.is_file() or not os.access(jar, os.R_OK):
        raise RuntimeError("MCP_JAR_MISSING")


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


def relay_stdin(child: subprocess.Popen[bytes], writer: EventWriter) -> None:
    try:
        while True:
            # A pipe read() may wait for the full requested size. MCP messages
            # are small, so use read1() to forward bytes as soon as available.
            chunk = sys.stdin.buffer.read1(65536)
            if not chunk:
                if child.stdin:
                    child.stdin.close()
                writer.write("MCP_INSTANCE_STOPPED", code="STDIN_CLOSED")
                return
            if child.stdin:
                child.stdin.write(chunk)
                child.stdin.flush()
    except (BrokenPipeError, OSError):
        writer.write("MCP_INSTANCE_STOPPED", severity="WARN", code="STDIN_RELAY_FAILED")


def relay_stdout(child: subprocess.Popen[bytes], writer: EventWriter, ready: threading.Event) -> None:
    assert child.stdout is not None
    buffer = b""
    while chunk := child.stdout.read1(65536):
        sys.stdout.buffer.write(chunk)
        sys.stdout.buffer.flush()
        buffer += chunk
        while b"\n" in buffer:
            line, buffer = buffer.split(b"\n", 1)
            try:
                message = json.loads(line)
            except json.JSONDecodeError:
                continue
            if isinstance(message, dict) and isinstance(message.get("result"), dict):
                if message["result"].get("protocolVersion") and not ready.is_set():
                    ready.set()
                    writer.write("MCP_INSTANCE_READY", code="INITIALIZE_RESPONSE")


def relay_stderr(child: subprocess.Popen[bytes], writer: EventWriter) -> None:
    assert child.stderr is not None
    for raw_line in child.stderr:
        line = redact(raw_line.decode("utf-8", errors="replace").rstrip())
        if line:
            writer.write("MCP_PROCESS_LOG", severity="DEBUG", message=line)
            sys.stderr.write(line + "\n")
            sys.stderr.flush()


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
        token = acquire_token(configuration)
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

    child_env = os.environ.copy()
    child_env["DEVLOG_MCP_BEARER_TOKEN"] = token
    child_env.setdefault("KEYCLOAK_ISSUER_URI", "http://keycloak:8080/realms/devlog")
    child_env.setdefault("KEYCLOAK_AUDIENCE", "devlog-core")
    child_env.setdefault("KEYCLOAK_JWKS_URI", "http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/certs")
    try:
        child = subprocess.Popen(
            ["java", "-jar", configuration["jar"]],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=child_env,
        )
    except OSError as exception:
        writer.write("MCP_PROCESS_EXITED", severity="ERROR", code="MCP_PROCESS_FAILED", message=str(exception))
        update_manifest(directory, state="FAILED", failureCode="MCP_PROCESS_FAILED")
        return 1

    update_manifest(directory, state="RUNNING", mcpPid=child.pid, mcpStartTime=process_start_time(child.pid))
    writer.write("MCP_PROCESS_STARTED", mcpPid=child.pid)
    ready = threading.Event()
    threads = [
        threading.Thread(target=relay_stdin, args=(child, writer), daemon=True),
        threading.Thread(target=relay_stdout, args=(child, writer, ready), daemon=True),
        threading.Thread(target=relay_stderr, args=(child, writer), daemon=True),
    ]
    for thread in threads:
        thread.start()

    try:
        while child.poll() is None:
            if os.getppid() != parent_pid or process_start_time(parent_pid) != parent_start:
                writer.write("MCP_PARENT_DISAPPEARED", severity="WARN", code="PARENT_PROCESS_GONE")
                terminate_child(child, writer, "PARENT_PROCESS_GONE")
                break
            time.sleep(0.25)
    except KeyboardInterrupt:
        terminate_child(child, writer, "SIGNAL_RECEIVED")

    exit_code = child.wait()
    if not ready.is_set():
        writer.write("MCP_HANDSHAKE_FAILED", severity="WARN", code="MCP_HANDSHAKE_FAILED")
    writer.write("MCP_PROCESS_EXITED", code="MCP_PROCESS_FAILED" if exit_code else "NORMAL_EXIT", exitCode=exit_code)
    update_manifest(directory, state="STOPPED", exitCode=exit_code, stoppedAt=datetime.now(timezone.utc).isoformat())
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
