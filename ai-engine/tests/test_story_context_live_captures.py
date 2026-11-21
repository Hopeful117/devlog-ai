import copy
from pathlib import Path

import pytest

from evaluations.story_context_runtime_loss import (
    CaptureValidationError,
    evaluate_live_capture,
    project_live_capture,
    load_captures,
    summarize_live_captures,
    validate_live_capture,
)


REVISION = "abcdef1"
CONTEXT_DIGEST = "a" * 64
PROJECTION_DIGEST = "b" * 64
LEDGER_DIGEST = "c" * 64
IDENTITY = {
    "expected_revision": REVISION,
    "expected_context_digest": CONTEXT_DIGEST,
    "expected_projection_digest": PROJECTION_DIGEST,
    "expected_ledger_digest": LEDGER_DIGEST,
    "expected_project": "devlog-ai",
    "expected_project_id": "project-devlog-ai",
    "expected_source_ids": {"source-main"},
    "expected_revision_source": "STORY_TARGET_COMMIT",
}
FIXTURE = Path(__file__).parents[1] / "evaluations" / "scenarios" / "story-0221-live-provider-captures-v1"


def core_snapshot() -> dict:
    return {
        "taskId": "task-live-1",
        "correlationId": "correlation-live-1",
        "executionScope": {
            "project": "devlog-ai",
            "projectId": "project-devlog-ai",
            "sourceId": "source-main",
            "resolvedRevision": REVISION,
            "revisionSource": "STORY_TARGET_COMMIT",
            "resolutionState": "RESOLVED",
        },
        "freshness": {
            "state": "CURRENT",
            "sourceRevision": {
                "kind": "PROJECT_REVISION",
                "revision": REVISION,
                "project": "devlog-ai",
            },
        },
        "contextDigest": CONTEXT_DIGEST,
        "projectionDigest": PROJECTION_DIGEST,
        "lossAccounting": {
            "version": "story-context-loss-ledger-v1",
            "digest": LEDGER_DIGEST,
            "counts": {
                "candidateCount": 10,
                "selectedCount": 4,
                "projectedCount": 4,
                "groundedCount": 4,
                "providerVisibleCount": 0,
            },
            "lossReasons": {},
            "providerOutcome": {
                "status": "UNKNOWN",
                "visibility": "UNKNOWN",
                "outcomeSource": "AI_INTERACTION_TRACE",
            },
        },
    }


def trace(**overrides: object) -> dict:
    value = {
        "taskId": "task-live-1",
        "correlationId": "correlation-live-1",
        "provider": "openai_chatgpt",
        "modelIdentifier": "gpt-5.6-luna",
        "validationStatus": "SUCCEEDED",
        "providerStatus": "COMPLETED",
        "inputTokens": 100,
        "outputTokens": 25,
        "totalTokens": 125,
        "effectiveOutputLimit": 2000,
        "durationMs": 420,
        "traceId": "trace-live-1",
    }
    value.update(overrides)
    return value


def test_complete_live_capture_is_measured_and_bounded() -> None:
    capture = project_live_capture(
        core_snapshot(), trace(), scenario="live-complete", callback_status="COMPLETED", analysis_available=True
    )

    validate_live_capture(capture, **IDENTITY)
    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == "COMPLETED"
    assert result["captureSource"] == "LIVE_PROVIDER"
    assert result["providerVisibility"] == "MEASURED"
    assert result["usage"] == {"inputTokens": 100, "outputTokens": 25, "totalTokens": 125}


@pytest.mark.parametrize(
    ("provider_status", "failure_category", "expected_status"),
    [
        ("INCOMPLETE", "PROVIDER_INCOMPLETE", "PROVIDER_INCOMPLETE"),
        ("TIMEOUT", "PROVIDER_TIMEOUT", "PROVIDER_TIMEOUT"),
    ],
)
def test_provider_terminal_outcomes_are_not_successful(
    provider_status: str, failure_category: str, expected_status: str
) -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(providerStatus=provider_status, failureCategory=failure_category),
        scenario="live-failure",
        callback_status="COMPLETED",
    )

    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == expected_status
    assert result["semanticEligibility"] == "NOT_EVALUATED"
    assert result["providerVisibility"] == "MEASURED"


def test_missing_attribution_keeps_visibility_unknown() -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(provider=None, modelIdentifier=None),
        scenario="live-unattributed",
        callback_status="COMPLETED",
        analysis_available=True,
    )

    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == "COMPLETED"
    assert result["providerVisibility"] == "UNKNOWN"
    assert capture["providerOutcome"]["outcomeSource"] is None


def test_missing_measurements_remain_unknown() -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(inputTokens=None, outputTokens=None, totalTokens=None, effectiveOutputLimit=None, durationMs=None),
        scenario="live-missing-measurements",
        callback_status="COMPLETED",
        analysis_available=True,
    )

    validate_live_capture(capture, **IDENTITY)

    assert capture["providerOutcome"]["usage"] is None
    assert capture["providerOutcome"]["latencyMs"] is None


def test_trace_identity_mismatch_is_not_evaluated() -> None:
    with pytest.raises(CaptureValidationError, match="identity"):
        project_live_capture(
            core_snapshot(), trace(correlationId="other-correlation"), scenario="live-mismatch"
        )


def test_real_interaction_trace_shape_uses_explicit_binding() -> None:
    actual_trace = trace()
    actual_trace.pop("taskId")
    actual_trace.pop("correlationId")
    actual_trace.pop("providerStatus")

    capture = project_live_capture(
        core_snapshot(),
        actual_trace,
        trace_binding={
            "taskId": "task-live-1",
            "correlationId": "correlation-live-1",
            "traceId": "trace-live-1",
        },
        scenario="live-real-trace-shape",
        callback_status="COMPLETED",
        analysis_available=True,
    )

    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == "COMPLETED"
    assert result["providerVisibility"] == "MEASURED"


def test_evaluation_rejects_contradictory_provider_and_terminal_state() -> None:
    capture = project_live_capture(
        core_snapshot(), trace(), scenario="live-contradiction", callback_status="COMPLETED", analysis_available=True
    )
    contradictory = copy.deepcopy(capture)
    contradictory["providerOutcome"]["status"] = "INCOMPLETE"

    result = evaluate_live_capture(contradictory, **IDENTITY)

    assert result["status"] == "NOT_EVALUATED"
    assert "contradict" in result["failureCode"]


def test_malformed_live_capture_is_not_evaluated() -> None:
    result = evaluate_live_capture(
        {"captureVersion": "story-context-live-provider-v1"}, **IDENTITY
    )

    assert result["status"] == "NOT_EVALUATED"
    assert result["semanticEligibility"] == "NOT_EVALUATED"


def test_forbidden_trace_payload_is_not_projected() -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(prompt="do not export", rawOutput="do not export", repositoryPayload="do not export"),
        scenario="live-redaction",
        callback_status="COMPLETED",
        analysis_available=True,
    )

    serialized = repr(capture)
    assert "prompt" not in serialized
    assert "rawOutput" not in serialized
    assert "repositoryPayload" not in serialized


def test_sensitive_metadata_value_is_rejected() -> None:
    with pytest.raises(CaptureValidationError, match="sensitive"):
        project_live_capture(
            core_snapshot(), trace(modelIdentifier="api_key=secret-value"), scenario="live-secret"
        )


def test_ledger_digest_and_contents_are_preserved() -> None:
    snapshot = core_snapshot()
    expected_ledger = copy.deepcopy(snapshot["lossAccounting"])
    capture = project_live_capture(
        snapshot, trace(), scenario="live-ledger", callback_status="COMPLETED", analysis_available=True
    )

    assert capture["lossAccounting"] == expected_ledger


def test_transport_failure_is_distinct_and_not_eligible() -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(providerStatus="FAILED", failureCategory="TRANSPORT_FAILURE"),
        scenario="live-transport",
    )

    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == "PROVIDER_TRANSPORT_FAILURE"
    assert result["semanticEligibility"] == "NOT_EVALUATED"


def test_projection_reduction_and_callback_validation_remain_distinct() -> None:
    reduced = project_live_capture(
        core_snapshot(), trace(), scenario="live-reduced", projection_reduced=True, analysis_available=True
    )
    validation = project_live_capture(
        core_snapshot(), trace(validationStatus="FAILED"), scenario="live-validation"
    )

    assert evaluate_live_capture(reduced, **IDENTITY)["status"] == "PROJECTION_REDUCED"
    assert evaluate_live_capture(validation, **IDENTITY)["status"] == "VALIDATION_FAILURE"


@pytest.mark.parametrize("validation_status", ["VALIDATION_FAILED", "PARSING_FAILED"])
def test_real_validation_statuses_are_not_transport_failures(validation_status: str) -> None:
    capture = project_live_capture(
        core_snapshot(),
        trace(validationStatus=validation_status),
        scenario="live-real-validation",
    )

    result = evaluate_live_capture(capture, **IDENTITY)

    assert result["status"] == "VALIDATION_FAILURE"
    assert result["semanticEligibility"] == "NOT_EVALUATED"


@pytest.mark.parametrize(
    "identity_override",
    [
        {"expected_revision": "1234567"},
        {"expected_context_digest": "0" * 64},
        {"expected_projection_digest": "0" * 64},
        {"expected_ledger_digest": "0" * 64},
    ],
)
def test_revision_and_digest_mismatches_are_not_evaluated(identity_override: dict) -> None:
    capture = project_live_capture(
        core_snapshot(), trace(), scenario="live-identity", callback_status="COMPLETED", analysis_available=True
    )

    result = evaluate_live_capture(capture, **(IDENTITY | identity_override))

    assert result["status"] == "NOT_EVALUATED"
    assert result["semanticEligibility"] == "NOT_EVALUATED"


def test_live_fixture_summary_keeps_measurements_and_denominators_separate() -> None:
    summary = summarize_live_captures(
        load_captures(FIXTURE / "captures.jsonl"), **IDENTITY
    )

    assert summary["captureSource"] == "LIVE_PROVIDER"
    assert summary["technicalStatusCounts"] == {
        "COMPLETED": 3,
        "PROVIDER_INCOMPLETE": 1,
        "PROVIDER_TIMEOUT": 1,
    }
    assert summary["measuredProviderCount"] == 4
    assert summary["semanticDenominator"] == 3
