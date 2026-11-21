import json
from pathlib import Path

import pytest

from evaluations.story_context_runtime_loss import (
    CAPTURE_VERSION,
    CaptureValidationError,
    evaluate_capture,
    load_captures,
    normalize_terminal_outcome,
    stable_digest,
    summarize_captures,
    validate_capture,
)


FIXTURE_DIR = Path(__file__).parents[1] / "evaluations" / "scenarios" / "story-0220-runtime-loss-v1"
EXPECTED_REVISION = "abcdef1"
DIGEST_KWARGS = {
    "expected_context_digest": "a" * 64,
    "expected_projection_digest": "b" * 64,
    "expected_ledger_digest": "c" * 64,
}
CAPTURE_KWARGS = {
    **DIGEST_KWARGS,
    "expected_project": "devlog-ai",
    "expected_project_id": "project-devlog-ai",
    "expected_source_ids": {"source-main", "source-main-and-docs"},
    "expected_revision_source": "STORY_TARGET_COMMIT",
}


def captures() -> list[dict]:
    return load_captures(FIXTURE_DIR / "captures.jsonl")


def test_provider_free_fixture_set_has_expected_terminal_distribution() -> None:
    result = summarize_captures(captures(), EXPECTED_REVISION, **CAPTURE_KWARGS)

    assert result["captureVersion"] == CAPTURE_VERSION
    assert result["captureCount"] == 8
    assert result["semanticEligibleCount"] == 4
    assert result["semanticNotEvaluatedCount"] == 4
    assert result["semanticDenominator"] == 4
    assert result["technicalStatusCounts"] == {
        "COMPLETED": 3,
        "NOT_EVALUATED": 1,
        "PROJECTION_REDUCED": 1,
        "PROVIDER_INCOMPLETE": 1,
        "PROVIDER_TIMEOUT": 1,
        "REJECTED": 1,
    }


def test_repeated_summary_is_byte_stable() -> None:
    first = summarize_captures(captures(), EXPECTED_REVISION, **CAPTURE_KWARGS)
    second = summarize_captures(captures(), EXPECTED_REVISION, **CAPTURE_KWARGS)

    assert first == second
    assert first["reportDigest"] == stable_digest(
        [evaluate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS) for capture in captures()]
    )


def test_invalid_revision_is_not_evaluated() -> None:
    capture = captures()[-1]

    result = evaluate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)

    assert result["status"] == "NOT_EVALUATED"
    assert result["semanticEligibility"] == "NOT_EVALUATED"
    assert "freshness revision" in result["failureCode"]


def test_provider_visibility_remains_unknown_without_attribution() -> None:
    capture = captures()[0]

    validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)

    assert evaluate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)["providerVisibility"] == "UNKNOWN"
    assert capture["lossAccounting"]["counts"]["providerVisibleCount"] == 0


@pytest.mark.parametrize(
    ("kwargs", "expected"),
    [
        (
            {"callback_status": "COMPLETED"},
            {"status": "COMPLETED", "semanticEligibility": "ELIGIBLE"},
        ),
        (
            {
                "callback_status": "COMPLETED",
                "interaction_traces": [{"failureCategory": "PROVIDER_INCOMPLETE"}],
            },
            {"status": "PROVIDER_INCOMPLETE", "semanticEligibility": "NOT_EVALUATED"},
        ),
        (
            {"callback_status": "COMPLETED", "preflight_outcome": "REJECTED"},
            {"status": "REJECTED", "semanticEligibility": "NOT_EVALUATED"},
        ),
        (
            {"callback_status": "COMPLETED", "projection_reduced": True},
            {"status": "PROJECTION_REDUCED", "semanticEligibility": "ELIGIBLE"},
        ),
    ],
)
def test_terminal_normalization_preserves_failure_precedence(kwargs: dict, expected: dict) -> None:
    assert normalize_terminal_outcome(**kwargs) == expected


def test_completed_capture_requires_analysis() -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture["analysisAvailable"] = False

    with pytest.raises(CaptureValidationError, match="completed capture"):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_unknown_provider_visibility_cannot_claim_visible_evidence() -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture["lossAccounting"]["counts"]["providerVisibleCount"] = 1

    with pytest.raises(CaptureValidationError, match="unknown provider visibility"):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_story_0219_execution_scope_is_the_authoritative_identity() -> None:
    capture = captures()[0]

    assert capture["executionScope"] == {
        "project": "devlog-ai",
        "projectId": "project-devlog-ai",
        "sourceId": "source-main",
        "resolvedRevision": EXPECTED_REVISION,
        "revisionSource": "STORY_TARGET_COMMIT",
        "resolutionState": "RESOLVED",
    }
    validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_authorized_and_validation_failure_are_not_semantically_eligible() -> None:
    assert normalize_terminal_outcome(callback_status="AUTHORIZED") == {
        "status": "AUTHORIZED",
        "semanticEligibility": "NOT_EVALUATED",
    }
    assert normalize_terminal_outcome(
        callback_status="COMPLETED", interaction_traces=[{"validationStatus": "FAILED"}]
    ) == {
        "status": "VALIDATION_FAILURE",
        "semanticEligibility": "NOT_EVALUATED",
    }


@pytest.mark.parametrize("field", ["prompt", "apiToken", "repositoryPayload"])
def test_forbidden_or_unknown_capture_fields_are_rejected(field: str) -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture[field] = "sensitive"

    with pytest.raises(CaptureValidationError):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_expected_digests_are_checked() -> None:
    capture = captures()[0]

    with pytest.raises(CaptureValidationError, match="contextDigest"):
        validate_capture(capture, EXPECTED_REVISION, **(CAPTURE_KWARGS | {"expected_context_digest": "0" * 64}))
    with pytest.raises(CaptureValidationError, match="projectionDigest"):
        validate_capture(capture, EXPECTED_REVISION, **(CAPTURE_KWARGS | {"expected_projection_digest": "0" * 64}))
    with pytest.raises(CaptureValidationError, match="lossAccounting.digest"):
        validate_capture(capture, EXPECTED_REVISION, **(CAPTURE_KWARGS | {"expected_ledger_digest": "0" * 64}))


def test_explicit_measured_provider_visibility_is_supported() -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture["lossAccounting"]["counts"]["providerVisibleCount"] = 1
    capture["lossAccounting"]["providerOutcome"]["visibility"] = "MEASURED"

    validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_evaluation_boundary_requires_expected_identity_inputs() -> None:
    with pytest.raises(TypeError):
        validate_capture(captures()[0], EXPECTED_REVISION)  # type: ignore[call-arg]


@pytest.mark.parametrize(
    "mutation",
    [
        lambda capture: capture["freshness"].update(extra="unsupported"),
        lambda capture: capture["lossAccounting"]["providerOutcome"].update(extra="unsupported"),
        lambda capture: capture["lossAccounting"]["lossReasons"].update(apiToken=1),
        lambda capture: capture["lossAccounting"]["lossReasons"].update(API_TOKEN=1),
        lambda capture: capture["lossAccounting"]["lossReasons"].update(PROJECTION={"apiToken": "secret"}),
    ],
)
def test_nested_unknown_or_sensitive_fields_are_rejected(mutation) -> None:
    capture = json.loads(json.dumps(captures()[0]))
    mutation(capture)

    with pytest.raises(CaptureValidationError):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_project_and_source_identity_mismatches_are_rejected() -> None:
    for field, value in (("project", "other-project"), ("sourceId", "other-source")):
        capture = json.loads(json.dumps(captures()[0]))
        capture["executionScope"][field] = value

        with pytest.raises(CaptureValidationError):
            validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_measured_provider_visibility_requires_approved_attribution() -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture["lossAccounting"]["counts"]["providerVisibleCount"] = 1
    capture["lossAccounting"]["providerOutcome"]["visibility"] = "MEASURED"
    capture["lossAccounting"]["providerOutcome"]["outcomeSource"] = "unknown-source"

    with pytest.raises(CaptureValidationError, match="unsupported"):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


def test_missing_or_malformed_expected_inputs_are_rejected() -> None:
    with pytest.raises(CaptureValidationError, match="expected capture identity"):
        validate_capture(
            captures()[0],
            EXPECTED_REVISION,
            expected_context_digest=None,
            **{key: value for key, value in CAPTURE_KWARGS.items()
               if key != "expected_context_digest"},
        )


def test_terminal_outcome_cannot_contradict_provider_outcome() -> None:
    capture = json.loads(json.dumps(captures()[0]))
    capture["lossAccounting"]["providerOutcome"]["status"] = "INCOMPLETE"

    with pytest.raises(CaptureValidationError, match="contradicts"):
        validate_capture(capture, EXPECTED_REVISION, **CAPTURE_KWARGS)


@pytest.mark.parametrize(
    ("callback_status", "expected_status"),
    [
        ("REJECTED", "REJECTED"),
        ("VALIDATION_FAILURE", "VALIDATION_FAILURE"),
        ("PROVIDER_INCOMPLETE", "PROVIDER_INCOMPLETE"),
        ("PROVIDER_TIMEOUT", "PROVIDER_TIMEOUT"),
    ],
)
def test_direct_terminal_statuses_are_preserved(callback_status: str, expected_status: str) -> None:
    assert normalize_terminal_outcome(callback_status=callback_status)["status"] == expected_status


@pytest.mark.parametrize("callback_status", ["REJECTED", "VALIDATION_FAILURE"])
def test_direct_terminal_failures_precede_projection_reduction(callback_status: str) -> None:
    result = normalize_terminal_outcome(callback_status=callback_status, projection_reduced=True)

    assert result == {"status": callback_status, "semanticEligibility": "NOT_EVALUATED"}
