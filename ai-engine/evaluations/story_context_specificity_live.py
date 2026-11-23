"""Capture the bounded Story 0210 OAuth workflow and direct-context metrics.

The direct condition stops after Core-authorized context retrieval; it never
creates a second provider path or reads an API key.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import select
import subprocess
import sys
from pathlib import Path
from typing import Any


INTENT = "engineering-story-context-analysis"
TERMINAL_STATUSES = {"COMPLETED", "FAILED", "TIMED_OUT"}
CAPTURE_OUTCOMES = {"SUCCESS", "FAILED", "NOT_EVALUATED", "INVALID_CAPTURE"}
MAX_CAPTURE_BYTES = 64 * 1024
LIVE_CAMPAIGN_ID = "story-0210-live-v6-oauth-context"
DEFAULT_PROJECT = "devlog-ai"
DEFAULT_MCP_JAR = Path(__file__).parents[2] / "mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar"
DEFAULT_MCP_LAUNCHER = Path.home() / ".config/opencode/devlog-mcp-start.sh"
DEFAULT_OUTPUT = Path("/tmp/opencode/story0210-live")


class McpClient:
    def __init__(self, jar: Path, launcher: Path | None = None, read_timeout_seconds: float = 180.0,
                 elicitation_handler: Any | None = None):
        command = [str(launcher)] if launcher is not None else ["java", "-jar", str(jar)]
        self.process = subprocess.Popen(
            command,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            # The launcher records diagnostics in its bounded state directory.
            # Do not leave an unread stderr pipe that can block a long capture.
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )
        self.request_id = 0
        self.read_timeout_seconds = read_timeout_seconds
        self.elicitation_handler = elicitation_handler or self._prompt_elicitation
        self.call("initialize", {
            "protocolVersion": "2025-06-18",
            "capabilities": {"elicitation": {"form": {}}},
            "clientInfo": {"name": "story-0158-live-runner", "version": "1.0"},
        })
        self.notify("notifications/initialized", {})

    def notify(self, method: str, params: dict[str, Any]) -> None:
        self._send({"jsonrpc": "2.0", "method": method, "params": params})

    def call(self, method: str, params: dict[str, Any]) -> dict[str, Any]:
        self.request_id += 1
        self._send({"jsonrpc": "2.0", "id": self.request_id, "method": method, "params": params})
        while True:
            ready, _, _ = select.select(
                [self.process.stdout], [], [], self.read_timeout_seconds
            )
            if not ready:
                raise TimeoutError(f"MCP response timed out for {method}")
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError("MCP process exited before returning a response")
            value = json.loads(line)
            if value.get("method") == "elicitation/create" and "id" in value:
                self._respond_to_elicitation(value)
                continue
            if value.get("id") == self.request_id:
                if "error" in value:
                    raise RuntimeError(json.dumps(value["error"]))
                return value["result"]

    def tool(self, name: str, arguments: dict[str, Any]) -> Any:
        result = self.call("tools/call", {"name": name, "arguments": arguments})
        content = result.get("content", [])
        text = next(item["text"] for item in content if item.get("type") == "text")
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return {"mcpText": text}

    def close(self) -> None:
        self.process.terminate()
        self.process.wait(timeout=10)

    def _send(self, value: dict[str, Any]) -> None:
        self.process.stdin.write(json.dumps(value) + "\n")
        self.process.stdin.flush()

    def _respond_to_elicitation(self, request: dict[str, Any]) -> None:
        result = self.elicitation_handler(request.get("params", {}))
        self._send({"jsonrpc": "2.0", "id": request["id"], "result": result})

    @staticmethod
    def _prompt_elicitation(params: dict[str, Any]) -> dict[str, Any]:
        message = params.get("message", "Confirm the requested MCP operation")
        print(f"MCP confirmation required: {message}", file=sys.stderr, flush=True)
        answer = input("Type APPROVE or REJECT: ").strip().upper()
        if answer == "APPROVE":
            return {"action": "accept", "content": {"confirmation": "APPROVE"}}
        return {"action": "decline", "content": {"confirmation": "REJECT"}}


def stable_idempotency_key(
    scenario_id: str,
    question: str,
    files: list[str],
    repetition: int,
    target_revision: str = "",
) -> str:
    """Create a reproducible key without Python's process-randomized hash()."""
    identity = json.dumps(
        {
            "scenarioId": scenario_id,
            "question": question,
            "files": files,
            "repetition": repetition,
            "targetRevision": target_revision,
        },
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return f"{LIVE_CAMPAIGN_ID}-{hashlib.sha256(identity).hexdigest()[:24]}"


def digest_value(value: Any) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


def bounded_diagnostics(value: Any) -> dict[str, str]:
    if not isinstance(value, dict):
        return {}
    allowed = {"code", "status", "lastKnownStatus", "failureCode", "errorType"}
    return {
        key: str(value[key])[:120]
        for key in allowed
        if key in value and isinstance(value[key], (str, int, bool))
    }


def exception_outcome(error: Exception) -> str:
    message = str(error).lower()
    if (
        isinstance(error, (TimeoutError, subprocess.TimeoutExpired, OSError, ConnectionError))
        or "insufficient_quota" in message
        or "credit_balance_exhausted" in message
        or "rate limit" in message
        or "429" in message
    ):
        return "NOT_EVALUATED"
    return "FAILED"


def story_agent_run(
    mcp: McpClient,
    project: str,
    scenario_id: str,
    question: str,
    files: list[str],
    repetition: int,
    target_revision: str,
) -> dict[str, Any]:
    key = stable_idempotency_key(scenario_id, question, files, repetition, target_revision)
    execution = mcp.tool("devlog_story_agent", {
        "projectSlug": project,
        "storyId": None,
        "intent": INTENT,
        "question": question,
        "files": files,
        "guidance": {
            "captureCampaign": LIVE_CAMPAIGN_ID,
            "captureRepetition": repetition,
        },
        "idempotencyKey": key,
    })
    return {
        "execution": execution,
        "status": execution,
        "result": execution.get("result", {}),
        "idempotencyKey": key,
    }


def capture_envelope(
    scenario_id: str,
    question: str,
    condition: str,
    repetition: int,
    payload: dict[str, Any],
    *,
    execution_id: str,
    context_digest: str,
    projection_digest: str | None,
    repository_revision: str,
    terminal_status: str,
    evaluation_outcome: str = "SUCCESS",
    protocol_version: str = "story-0210-e2e/v2",
    evaluation_version: str = "story-0210-e2e-v2",
    expected_evidence: list[str] | None = None,
    expected_abstention: bool | None = None,
    manifest_digest: str = "",
) -> dict[str, Any]:
    if terminal_status not in TERMINAL_STATUSES | {"NOT_ESTABLISHED"}:
        raise ValueError(f"unsupported terminal status: {terminal_status}")
    if evaluation_outcome not in CAPTURE_OUTCOMES:
        raise ValueError(f"unsupported evaluation outcome: {evaluation_outcome}")
    envelope = {
        "scenarioId": scenario_id,
        "question": question,
        "condition": condition,
        "repetition": repetition,
        "status": terminal_status,
        "evaluationOutcome": evaluation_outcome,
        "executionId": execution_id,
        "contextDigest": context_digest,
        "projectionDigest": projection_digest,
        "repositoryRevision": repository_revision,
        "protocolVersion": protocol_version,
        "evaluationVersion": evaluation_version,
        "expectedEvidence": list(expected_evidence or []),
        "expectedAbstention": expected_abstention,
        "manifestDigest": manifest_digest,
    }
    diagnostics = bounded_diagnostics(payload.get("diagnostics"))
    if diagnostics:
        envelope["diagnostics"] = diagnostics
    if "analysisAvailable" in payload:
        envelope["analysisAvailable"] = bool(payload["analysisAvailable"])
    if "contextAvailable" in payload:
        envelope["contextAvailable"] = bool(payload["contextAvailable"])
    if "contextTruncated" in payload:
        envelope["contextTruncated"] = bool(payload["contextTruncated"])
    if "contextEvidenceCount" in payload and type(payload["contextEvidenceCount"]) is int:
        envelope["contextEvidenceCount"] = max(0, payload["contextEvidenceCount"])
    if "answerDigest" in payload:
        envelope["answerDigest"] = str(payload["answerDigest"])[:64]
    if "answerLength" in payload and type(payload["answerLength"]) is int:
        envelope["answerLength"] = max(0, payload["answerLength"])
    encoded = json.dumps(envelope, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    if len(encoded) > MAX_CAPTURE_BYTES:
        raise ValueError("capture envelope exceeds bounded size")
    envelope["captureDigest"] = hashlib.sha256(encoded).hexdigest()
    return envelope


def failure_envelope(
    scenario_id: str,
    question: str,
    condition: str,
    repetition: int,
    error: Exception,
    scenario: dict[str, Any],
    manifest_digest: str,
    target_revision: str,
) -> dict[str, Any]:
    """Represent a failed condition without claiming a valid analysis capture."""
    return capture_envelope(
        scenario_id,
        question,
        condition,
        repetition,
        {
            "diagnostics": {
                "code": "CAPTURE_EXECUTION_FAILED",
                "errorType": type(error).__name__,
            },
        },
        execution_id=f"failed-{scenario_id}-{repetition}",
        context_digest="",
        projection_digest=None,
        repository_revision=target_revision,
        terminal_status="FAILED",
        evaluation_outcome=exception_outcome(error),
        expected_evidence=scenario.get("expectedEvidence"),
        expected_abstention=scenario.get("expectedAbstention"),
        manifest_digest=manifest_digest,
    )


def write_capture(path: Path, capture: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(capture, sort_keys=True, separators=(",", ":"), ensure_ascii=False) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", default=DEFAULT_PROJECT)
    parser.add_argument("--mcp-jar", type=Path, default=DEFAULT_MCP_JAR)
    parser.add_argument(
        "--mcp-launcher",
        type=Path,
        default=Path(os.environ["DEVLOG_MCP_LAUNCHER"])
        if os.environ.get("DEVLOG_MCP_LAUNCHER")
        else (DEFAULT_MCP_LAUNCHER if DEFAULT_MCP_LAUNCHER.is_file() else None),
        help="Local MCP launcher that provisions authentication; defaults to the OpenCode launcher when present.",
    )
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--repetitions", type=int, default=None)
    parser.add_argument(
        "--target-revision",
        default=os.environ.get("DEVLOG_EVALUATION_REVISION"),
        help="Full repository revision expected in every capture.",
    )
    parser.add_argument("--direct-context-only", action="store_true")
    args = parser.parse_args()
    manifest_path = Path(__file__).parent / "scenarios/story-0210-e2e-v2/manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_repetitions = manifest["repetitions"]
    if args.repetitions is not None and args.repetitions != expected_repetitions:
        parser.error("--repetitions must match the frozen manifest")
    if not isinstance(args.target_revision, str) or not re.fullmatch(r"[0-9a-f]{40}", args.target_revision):
        parser.error("--target-revision must be a full 40-character lowercase revision")
    manifest_digest = digest_value(manifest)
    args.output.mkdir(parents=True, exist_ok=True)

    scenarios = manifest["scenarios"]
    mcp = McpClient(args.mcp_jar, args.mcp_launcher)
    try:
        for scenario in scenarios:
            for repetition in range(1, expected_repetitions + 1):
                scenario_id = scenario["scenarioId"]
                question = scenario["question"]
                files = scenario.get("files", [])
                if not args.direct_context_only:
                    try:
                        treatment = story_agent_run(
                            mcp, args.project, scenario_id, question, files, repetition,
                            args.target_revision,
                        )
                        story_result = treatment["result"]
                        story_freshness = story_result.get("freshness", {}).get("sourceRevision", {})
                        story_status = treatment["status"]
                        repository_revision = (
                            story_freshness.get("revision")
                            or story_result.get("contextFreshness", {}).get("sourceRevision", {}).get("revision")
                        )
                        story_status_value = story_status.get("status", "FAILED")
                        analysis_available = isinstance(
                            treatment.get("result", {}).get("analysis"), dict
                        )
                        outcome = (
                            "SUCCESS"
                            if story_status_value == "COMPLETED"
                            and analysis_available
                            and repository_revision == args.target_revision
                            else "INVALID_CAPTURE"
                        )
                        story_capture = capture_envelope(
                            scenario_id,
                            question,
                            "DEVLOG_ASSISTED_OAUTH",
                            repetition,
                            {
                                "diagnostics": story_status.get("diagnostics"),
                                "analysisAvailable": analysis_available,
                            },
                            execution_id=treatment["execution"].get("aiTaskId", "unknown"),
                            context_digest=story_status.get("contextDigest", ""),
                            projection_digest=story_status.get("projectionDigest"),
                            repository_revision=repository_revision or "",
                            terminal_status=story_status_value,
                            evaluation_outcome=outcome,
                            expected_evidence=scenario.get("expectedEvidence"),
                            expected_abstention=scenario.get("expectedAbstention"),
                            manifest_digest=manifest_digest,
                        )
                    except Exception as error:
                        story_capture = failure_envelope(
                            scenario_id, question, "DEVLOG_ASSISTED_OAUTH", repetition, error,
                            scenario, manifest_digest, args.target_revision,
                        )
                    write_capture(
                        args.output / f"{scenario_id}-DEVLOG_ASSISTED_OAUTH-r{repetition}.jsonl",
                        story_capture,
                    )
                try:
                    context = mcp.tool("get_engineering_context", {
                        "projectSlug": args.project,
                        "intent": question,
                        "files": files,
                        "storyId": None,
                    })
                    metadata = context.get("metadata", {})
                    repository_revision = metadata.get("freshness", {}).get("repositoryRevision", "")
                    context_available = isinstance(context, dict) and bool(metadata.get("contextDigest"))
                    baseline_capture = capture_envelope(
                        scenario_id, question, "DIRECT_CONTEXT_OAUTH",
                        repetition,
                        {
                            "contextAvailable": context_available,
                            "contextEvidenceCount": len(context.get("evidence", []))
                            if isinstance(context.get("evidence"), list) else 0,
                            "contextTruncated": bool(metadata.get("truncated", False)),
                        },
                        execution_id=f"direct-context-{scenario_id}-r{repetition}",
                        context_digest=metadata.get("contextDigest", ""),
                        projection_digest=None,
                        repository_revision=repository_revision,
                        terminal_status="COMPLETED",
                        evaluation_outcome=(
                            "SUCCESS"
                            if context_available and repository_revision == args.target_revision
                            else "INVALID_CAPTURE"
                        ),
                        expected_evidence=scenario.get("expectedEvidence"),
                        expected_abstention=scenario.get("expectedAbstention"),
                        manifest_digest=manifest_digest,
                    )
                except Exception as error:
                    baseline_capture = failure_envelope(
                        scenario_id, question, "DIRECT_CONTEXT_OAUTH", repetition, error,
                        scenario, manifest_digest, args.target_revision,
                    )
                write_capture(
                    args.output / f"{scenario_id}-DIRECT_CONTEXT_OAUTH-r{repetition}.jsonl",
                    baseline_capture,
                )
                print(f"captured {scenario_id} repetition {repetition}", flush=True)
    finally:
        mcp.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
