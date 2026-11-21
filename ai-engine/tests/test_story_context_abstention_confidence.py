import json
import hashlib
from pathlib import Path

import pytest

from evaluations.story_context_abstention_confidence import (
    AbstentionOutcome,
    EvaluationObservation,
    ScenarioOracle,
    evaluate_observation,
    evaluate_report,
)


REVISION = "a" * 40
CAPTURE_PAYLOAD = {"capture": "bounded", "scenarioId": "case-1"}
CAPTURE = hashlib.sha256(
    json.dumps(CAPTURE_PAYLOAD, sort_keys=True, separators=(",", ":")).encode()
).hexdigest()
MANIFEST = Path(__file__).parents[1] / "evaluations/scenarios/story-0222-abstention-confidence-v1/manifest.json"


def oracle(
    *,
    expected_abstention: bool,
    reviewed: bool = True,
    required_evidence: list[str] | None = None,
) -> ScenarioOracle:
    return ScenarioOracle(
        scenarioId="case-1",
        scenarioVersion="1.0.0",
        repositoryRevision=REVISION,
        expectedAbstention=expected_abstention,
        expectedAnswerClass="INSUFFICIENT" if expected_abstention else "SUPPORTED",
        reviewed=reviewed,
        requiredEvidence=required_evidence or [],
    )


def observation(
    *,
    abstention: bool | None,
    confidence: str | None = "MEDIUM",
    grounded_correctness: bool | None = True,
    evidence_supported: bool | None = True,
    answer_class: str = "SUPPORTED",
    evidence_references: list[str] | None = None,
    capture_digest: str = CAPTURE,
    technical_status: str = "COMPLETED",
    semantic_eligibility: str = "SEMANTICALLY_ELIGIBLE",
    acceptability_status: str = "ACCEPTABLE",
) -> EvaluationObservation:
    return EvaluationObservation(
        scenarioId="case-1",
        scenarioVersion="1.0.0",
        repositoryRevision=REVISION,
        captureDigest=capture_digest,
        capturePayload=CAPTURE_PAYLOAD,
        technicalStatus=technical_status,
        semanticEligibility=semantic_eligibility,
        acceptabilityStatus=acceptability_status,
        answerClass=answer_class,
        evidenceReferences=evidence_references or [],
        abstention=abstention,
        confidence=confidence,
        groundedCorrectness=grounded_correctness,
        evidenceSupported=evidence_supported,
    )


def test_manifest_is_proposed_and_covers_required_scenarios() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    assert manifest["protocolVersion"] == "story-0222-abstention-confidence/v1"
    assert manifest["oracleStatus"] == "PROPOSED_REQUIRES_HUMAN_REVIEW"
    assert {item["expectedAbstention"] for item in manifest["scenarios"]} == {True, False}
    assert manifest["scenarios"][0]["requiredEvidence"] == ["file:docker-compose.yml"]
    assert len(manifest["scenarios"]) == 8


@pytest.mark.parametrize(
    ("expected", "actual", "outcome"),
    [
        (True, True, AbstentionOutcome.CORRECT_ABSTENTION),
        (True, False, AbstentionOutcome.MISSED_ABSTENTION),
        (False, True, AbstentionOutcome.UNNECESSARY_ABSTENTION),
        (False, False, AbstentionOutcome.NO_ABSTENTION_REQUIRED),
    ],
)
def test_abstention_outcomes_are_explicit(expected, actual, outcome) -> None:
    result = evaluate_observation(
        oracle(expected_abstention=expected),
        observation(
            abstention=actual,
            answer_class="INSUFFICIENT" if expected else "SUPPORTED",
        ),
    )

    assert result.abstention_outcome is outcome
    assert result.semantic_evaluated is True


def test_technical_failure_is_not_evaluated() -> None:
    result = evaluate_observation(
        oracle(expected_abstention=True),
        observation(
            abstention=True,
            answer_class="INSUFFICIENT",
            technical_status="PROVIDER_INCOMPLETE_RESPONSE",
        ),
    )

    assert result.abstention_outcome is AbstentionOutcome.ABSTENTION_NOT_EVALUATED
    assert result.semantic_evaluated is False
    assert result.diagnostics == ["TECHNICAL_RESULT_NOT_EVALUABLE"]


def test_high_confidence_unsupported_answer_fails_safety_gate() -> None:
    result = evaluate_observation(
        oracle(expected_abstention=False),
        observation(
            abstention=False,
            confidence="HIGH",
            answer_class="SUPPORTED",
            grounded_correctness=False,
            evidence_supported=False,
        ),
    )

    assert result.confidence_safety_passed is False
    assert "HIGH_CONFIDENCE_UNSUPPORTED" in result.diagnostics


def test_missing_abstention_is_not_evaluated() -> None:
    result = evaluate_observation(
        oracle(expected_abstention=True),
        observation(abstention=None, answer_class="INSUFFICIENT"),
    )

    assert result.abstention_outcome is AbstentionOutcome.ABSTENTION_NOT_EVALUATED
    assert result.semantic_evaluated is False


def test_report_excludes_non_evaluable_observations_from_confidence_buckets() -> None:
    report = evaluate_report(
        [oracle(expected_abstention=False)],
        [
            observation(abstention=False, confidence="HIGH"),
            observation(
                abstention=False,
                confidence="HIGH",
                technical_status="PROVIDER_INCOMPLETE_RESPONSE",
            ),
        ],
    )

    assert report.semantic_observation_count == 1
    assert report.not_evaluated_count == 1
    assert report.confidence_buckets["HIGH"].eligible_count == 1
    assert report.confidence_buckets["HIGH"].grounded_correct_rate == 1.0
    assert report.oracle_reviewed is True
    assert report.quality_metrics_available is True


def test_report_marks_unreviewed_oracles_without_blocking_development_replay() -> None:
    report = evaluate_report(
        [oracle(expected_abstention=False, reviewed=False)],
        [observation(abstention=False)],
    )

    assert report.oracle_reviewed is False
    assert report.quality_metrics_available is False
    assert report.abstention_counts == {}
    assert report.confidence_buckets == {}
    assert report.semantic_observation_count == 1


def test_identity_mismatch_fails_closed() -> None:
    value = observation(abstention=True).model_copy(update={"repository_revision": "c" * 40})
    result = evaluate_observation(oracle(expected_abstention=True), value)

    assert result.semantic_evaluated is False
    assert result.diagnostics == ["REPOSITORY_REVISION_MISMATCH"]


def test_capture_digest_mismatch_fails_closed() -> None:
    result = evaluate_observation(
        oracle(expected_abstention=False),
        observation(abstention=False, capture_digest="c" * 64),
    )

    assert result.semantic_evaluated is False
    assert result.diagnostics == ["CAPTURE_DIGEST_MISMATCH"]


def test_answer_class_and_required_evidence_are_enforced() -> None:
    value = oracle(expected_abstention=False).model_copy(update={
        "expected_answer_class": "SUPPORTED",
        "required_evidence": ["docker-compose.yml"],
    })
    result = evaluate_observation(
        value,
        observation(
            abstention=False,
            answer_class="BOUNDED",
            evidence_references=[],
        ),
    )

    assert result.semantic_evaluated is True
    assert result.answer_class_passed is False
    assert result.required_evidence_passed is False
    assert "ANSWER_CLASS_MISMATCH" in result.diagnostics
    assert "REQUIRED_EVIDENCE_MISSING" in result.diagnostics


def test_canonical_required_evidence_can_pass() -> None:
    result = evaluate_observation(
        oracle(expected_abstention=False, required_evidence=["file:docker-compose.yml"]),
        observation(
            abstention=False,
            evidence_references=["file:docker-compose.yml"],
        ),
    )

    assert result.answer_class_passed is True
    assert result.required_evidence_passed is True
    assert result.oracle_validation_passed is True


def test_oracle_failures_are_excluded_from_quality_aggregates() -> None:
    report = evaluate_report(
        [oracle(expected_abstention=False, required_evidence=["file:docker-compose.yml"])],
        [observation(abstention=False, evidence_references=[])],
    )

    assert report.semantic_observation_count == 0
    assert report.oracle_excluded_count == 1
    assert report.abstention_counts == {}


def test_non_acceptable_observation_is_excluded_from_confidence_bucket() -> None:
    report = evaluate_report(
        [oracle(expected_abstention=False)],
        [observation(abstention=False, acceptability_status="BOUNDED")],
    )

    assert report.confidence_buckets["MEDIUM"].eligible_count == 0


def test_high_confidence_incorrect_grounding_is_overconfidence() -> None:
    report = evaluate_report(
        [oracle(expected_abstention=False)],
        [
            observation(
                abstention=False,
                confidence="HIGH",
                grounded_correctness=False,
                evidence_supported=True,
            )
        ],
    )

    assert report.overconfidence_count == 1
    assert report.confidence_buckets["HIGH"].incorrect_count == 1
