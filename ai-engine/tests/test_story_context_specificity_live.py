import hashlib
import json
from pathlib import Path

from evaluations.story_context_specificity_live import (
    capture_envelope,
    stable_idempotency_key,
)


def test_idempotency_key_is_stable_across_processes():
    first = stable_idempotency_key(
        "targeted", "Which component owns this?", ["docker-compose.yml"], 1
    )
    second = stable_idempotency_key(
        "targeted", "Which component owns this?", ["docker-compose.yml"], 1
    )

    assert first == second
    assert first.startswith("story-0210-live-v6-oauth-context-")


def test_idempotency_key_uses_canonical_sha256_identity():
    scenario_id = "targeted"
    question = "Which component owns this?"
    files = ["docker-compose.yml"]
    repetition = 1
    identity = json.dumps(
        {
            "scenarioId": scenario_id,
            "question": question,
            "files": files,
            "repetition": repetition,
            "targetRevision": "",
        },
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")

    assert stable_idempotency_key(scenario_id, question, files, repetition) == (
        f"story-0210-live-v6-oauth-context-{hashlib.sha256(identity).hexdigest()[:24]}"
    )


def test_capture_preserves_terminal_failure_status():
    capture = capture_envelope(
        "insufficient",
        "What incident caused this?",
        "DEVLOG_ASSISTED_OAUTH",
        1,
        {"diagnostics": {"code": "PROVIDER_INCOMPLETE"}},
        execution_id="task-1",
        context_digest="",
        projection_digest=None,
        repository_revision="",
        terminal_status="TIMED_OUT",
    )

    assert capture["status"] == "TIMED_OUT"
    assert "taskStatus" not in capture


def test_story_0210_manifest_has_required_scenario_classes():
    manifest_path = (
        Path(__file__).parents[1]
        / "evaluations/scenarios/story-0210-e2e-v2/manifest.json"
    )
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))

    assert manifest["repetitions"] == 3
    assert len(manifest["scenarios"]) == 5
    assert {
        scenario["questionKind"] for scenario in manifest["scenarios"]
    } == {
        "TARGETED",
        "MULTI_SOURCE_ARCHITECTURE",
        "HISTORICAL_DECISION",
        "INSUFFICIENT_EVIDENCE",
        "TRUNCATED_CONTEXT",
    }
