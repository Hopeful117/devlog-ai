#!/usr/bin/env python3
"""Read-only status and doctor commands for local DevLog MCP instances."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

from runtime import process_start_time, state_root


def read_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def last_event(directory: Path) -> dict | None:
    try:
        lines = (directory / "events.jsonl").read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    if not lines:
        return None
    try:
        return json.loads(lines[-1])
    except json.JSONDecodeError:
        return {"event": "INVALID_EVENT_RECORD"}


def last_renewal_event(directory: Path) -> dict | None:
    try:
        lines = (directory / "events.jsonl").read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    renewal_events = {
        "TOKEN_EXPIRED",
        "TOKEN_RENEWAL_STARTED",
        "TOKEN_RENEWAL_SUCCEEDED",
        "TOKEN_RENEWAL_FAILED",
    }
    for line in reversed(lines):
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            continue
        if event.get("event") in renewal_events:
            return {
                key: event[key]
                for key in ("timestamp", "event", "severity", "code")
                if key in event
            }
    return None


def instance_status(directory: Path) -> dict:
    manifest = read_json(directory / "manifest.json")
    parent_pid = manifest.get("parentOpenCodePid")
    child_pid = manifest.get("mcpPid")
    parent_alive = bool(parent_pid and process_start_time(int(parent_pid)) == manifest.get("parentOpenCodeStartTime"))
    child_alive = bool(child_pid and process_start_time(int(child_pid)) == manifest.get("mcpStartTime"))
    state = manifest.get("state", "UNKNOWN")
    if state == "RUNNING" and not parent_alive:
        state = "ORPHANED"
    return {
        "instanceId": manifest.get("instanceId", directory.name),
        "state": state,
        "parentAlive": parent_alive,
        "childAlive": child_alive,
        "lastEvent": last_event(directory),
        "lastRenewalEvent": last_renewal_event(directory),
        "manifest": manifest,
    }


def endpoint_status(url: str) -> dict:
    try:
        request = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(request, timeout=3) as response:
            return {"url": url, "httpStatus": response.status, "reachable": True}
    except urllib.error.HTTPError as exception:
        return {"url": url, "httpStatus": exception.code, "reachable": True}
    except (urllib.error.URLError, TimeoutError, OSError) as exception:
        return {"url": url, "reachable": False, "error": type(exception).__name__}


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only DevLog MCP runtime diagnostics")
    parser.add_argument("command", choices=("status", "doctor"), nargs="?", default="status")
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args()
    root = state_root()
    instances = [instance_status(path) for path in sorted(root.iterdir()) if path.is_dir()] if root.is_dir() else []
    result = {"runtimeRoot": str(root), "pid": os.getpid(), "instances": instances}
    if args.command == "doctor":
        backend = os.environ.get("DEVLOG_BACKEND_BASE_URL", "http://localhost:18080")
        token = os.environ.get(
            "KEYCLOAK_TOKEN_ENDPOINT",
            "http://127.0.0.1:18084/realms/devlog/protocol/openid-connect/token",
        )
        result["checks"] = [endpoint_status(backend + "/actuator/health"), endpoint_status(token)]
    if args.as_json:
        print(json.dumps(result, indent=2, sort_keys=True))
    else:
        print(f"runtimeRoot={root}")
        for instance in instances:
            print(f"{instance['instanceId']} state={instance['state']} parentAlive={instance['parentAlive']} childAlive={instance['childAlive']}")
        if not instances:
            print("No MCP instances found")
    return 0


if __name__ == "__main__":
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    raise SystemExit(main())
