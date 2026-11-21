"""Evaluation-only abstention and ordinal confidence reporting.

The evaluator consumes explicit normalized metadata and reviewed scenario
oracles. It never infers abstention or correctness from prose, citation counts,
or confidence alone.
"""

from __future__ import annotations

from collections import Counter
from enum import Enum
import hashlib
import json
from typing import Literal

from pydantic import Field

from evaluations.models import EvaluationModel


ConfidenceLevel = Literal["HIGH", "MEDIUM", "LOW"]


class AbstentionOutcome(str, Enum):
    CORRECT_ABSTENTION = "CORRECT_ABSTENTION"
    MISSED_ABSTENTION = "MISSED_ABSTENTION"
    UNNECESSARY_ABSTENTION = "UNNECESSARY_ABSTENTION"
    NO_ABSTENTION_REQUIRED = "NO_ABSTENTION_REQUIRED"
    ABSTENTION_NOT_EVALUATED = "ABSTENTION_NOT_EVALUATED"


class ScenarioOracle(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId", min_length=1)
    scenario_version: str = Field(alias="scenarioVersion", min_length=1)
    repository_revision: str = Field(alias="repositoryRevision", min_length=1)
    expected_abstention: bool = Field(alias="expectedAbstention")
    expected_answer_class: Literal["SUPPORTED", "BOUNDED", "INSUFFICIENT", "NOT_ESTABLISHED"] = Field(
        alias="expectedAnswerClass"
    )
    reviewed: bool = False
    required_evidence: list[str] = Field(alias="requiredEvidence", default_factory=list)


class EvaluationObservation(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId", min_length=1)
    scenario_version: str = Field(alias="scenarioVersion", min_length=1)
    repository_revision: str = Field(alias="repositoryRevision", min_length=1)
    capture_digest: str = Field(alias="captureDigest", pattern=r"^[0-9a-f]{64}$")
    capture_payload: dict[str, object] | None = Field(default=None, alias="capturePayload")
    technical_status: str = Field(alias="technicalStatus", min_length=1)
    semantic_eligibility: str = Field(alias="semanticEligibility", min_length=1)
    acceptability_status: str = Field(alias="acceptabilityStatus", min_length=1)
    answer_class: Literal["SUPPORTED", "BOUNDED", "INSUFFICIENT", "NOT_ESTABLISHED"] | None = Field(
        default=None, alias="answerClass"
    )
    evidence_references: list[str] = Field(alias="evidenceReferences", default_factory=list)
    abstention: bool | None = None
    confidence: ConfidenceLevel | None = None
    grounded_correctness: bool | None = Field(default=None, alias="groundedCorrectness")
    evidence_supported: bool | None = Field(default=None, alias="evidenceSupported")


class AbstentionConfidenceResult(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId")
    abstention_outcome: AbstentionOutcome = Field(alias="abstentionOutcome")
    semantic_evaluated: bool = Field(alias="semanticEvaluated")
    confidence_evaluated: bool = Field(alias="confidenceEvaluated")
    confidence: ConfidenceLevel | None = None
    confidence_safety_passed: bool | None = Field(alias="confidenceSafetyPassed")
    answer_class_passed: bool | None = Field(alias="answerClassPassed")
    required_evidence_passed: bool | None = Field(alias="requiredEvidencePassed")
    oracle_validation_passed: bool | None = Field(alias="oracleValidationPassed")
    diagnostics: list[str] = Field(default_factory=list)


class ConfidenceBucket(EvaluationModel):
    eligible_count: int = Field(alias="eligibleCount", ge=0)
    grounded_correct_count: int = Field(alias="groundedCorrectCount", ge=0)
    evidence_supported_count: int = Field(alias="evidenceSupportedCount", ge=0)
    incorrect_count: int = Field(alias="incorrectCount", ge=0)
    grounded_correct_rate: float | None = Field(alias="groundedCorrectRate", default=None)
    evidence_support_rate: float | None = Field(alias="evidenceSupportRate", default=None)


class AbstentionConfidenceReport(EvaluationModel):
    protocol_version: str = Field(alias="protocolVersion")
    oracle_reviewed: bool = Field(alias="oracleReviewed")
    quality_metrics_available: bool = Field(alias="qualityMetricsAvailable")
    semantic_observation_count: int = Field(alias="semanticObservationCount", ge=0)
    not_evaluated_count: int = Field(alias="notEvaluatedCount", ge=0)
    oracle_excluded_count: int = Field(alias="oracleExcludedCount", ge=0)
    abstention_counts: dict[str, int] = Field(alias="abstentionCounts")
    confidence_buckets: dict[str, ConfidenceBucket] = Field(alias="confidenceBuckets")
    overconfidence_count: int = Field(alias="overconfidenceCount", ge=0)
    underconfidence_count: int = Field(alias="underconfidenceCount", ge=0)
    results: list[AbstentionConfidenceResult]


PROTOCOL_VERSION = "story-0222-abstention-confidence/v1"
_NON_EVALUABLE_STATUSES = {"FAILED", "NOT_EVALUATED"}
_MAX_CAPTURE_BYTES = 64 * 1024


def evaluate_observation(
    oracle: ScenarioOracle,
    observation: EvaluationObservation,
) -> AbstentionConfidenceResult:
    diagnostics: list[str] = []
    if observation.scenario_id != oracle.scenario_id:
        return _not_evaluated(observation, "SCENARIO_ID_MISMATCH")
    if observation.scenario_version != oracle.scenario_version:
        return _not_evaluated(observation, "SCENARIO_VERSION_MISMATCH")
    if observation.repository_revision != oracle.repository_revision:
        return _not_evaluated(observation, "REPOSITORY_REVISION_MISMATCH")
    if observation.capture_payload is None:
        return _not_evaluated(observation, "CAPTURE_PAYLOAD_NOT_AVAILABLE")
    encoded_capture = json.dumps(
        observation.capture_payload,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")
    if len(encoded_capture) > _MAX_CAPTURE_BYTES:
        return _not_evaluated(observation, "CAPTURE_PAYLOAD_TOO_LARGE")
    if hashlib.sha256(encoded_capture).hexdigest() != observation.capture_digest:
        return _not_evaluated(observation, "CAPTURE_DIGEST_MISMATCH")
    if observation.technical_status != "COMPLETED":
        return _not_evaluated(observation, "TECHNICAL_RESULT_NOT_EVALUABLE")
    if observation.semantic_eligibility != "SEMANTICALLY_ELIGIBLE":
        return _not_evaluated(observation, "SEMANTIC_RESULT_NOT_EVALUABLE")
    if observation.acceptability_status in _NON_EVALUABLE_STATUSES:
        return _not_evaluated(observation, "ACCEPTABILITY_NOT_EVALUABLE")
    if observation.abstention is None:
        return _not_evaluated(observation, "ABSTENTION_NOT_EXPLICIT")

    answer_class_passed = observation.answer_class == oracle.expected_answer_class
    required_evidence_passed = set(oracle.required_evidence).issubset(
        set(observation.evidence_references)
    )
    if not answer_class_passed:
        diagnostics.append("ANSWER_CLASS_MISMATCH")
    if not required_evidence_passed:
        diagnostics.append("REQUIRED_EVIDENCE_MISSING")

    if oracle.expected_abstention and observation.abstention:
        abstention_outcome = AbstentionOutcome.CORRECT_ABSTENTION
    elif oracle.expected_abstention:
        abstention_outcome = AbstentionOutcome.MISSED_ABSTENTION
    elif observation.abstention:
        abstention_outcome = AbstentionOutcome.UNNECESSARY_ABSTENTION
    else:
        abstention_outcome = AbstentionOutcome.NO_ABSTENTION_REQUIRED

    confidence_safety_passed: bool | None = None
    confidence_evaluated = (
        observation.confidence is not None
        and observation.grounded_correctness is not None
        and observation.evidence_supported is not None
        and observation.acceptability_status == "ACCEPTABLE"
        and answer_class_passed
        and required_evidence_passed
    )
    if observation.confidence is not None:
        unsafe_reasons = []
        if observation.acceptability_status != "ACCEPTABLE":
            unsafe_reasons.append("CONFIDENCE_ACCEPTABILITY_NOT_ACCEPTABLE")
        if not answer_class_passed:
            unsafe_reasons.append("CONFIDENCE_ANSWER_CLASS_INVALID")
        if not required_evidence_passed:
            unsafe_reasons.append("CONFIDENCE_REQUIRED_EVIDENCE_MISSING")
        if observation.grounded_correctness is False:
            unsafe_reasons.append("CONFIDENCE_GROUNDED_CORRECTNESS_FAILED")
        if observation.evidence_supported is False:
            unsafe_reasons.append("CONFIDENCE_EVIDENCE_SUPPORT_FAILED")
        if observation.grounded_correctness is None:
            unsafe_reasons.append("GROUNDED_CORRECTNESS_NOT_AVAILABLE")
        if observation.evidence_supported is None:
            unsafe_reasons.append("EVIDENCE_SUPPORT_NOT_AVAILABLE")
        if unsafe_reasons:
            if observation.confidence == "HIGH":
                diagnostics.append("HIGH_CONFIDENCE_UNSUPPORTED")
            confidence_safety_passed = False
            diagnostics.extend(unsafe_reasons)
        else:
            confidence_safety_passed = True

    return AbstentionConfidenceResult(
        scenarioId=oracle.scenario_id,
        abstentionOutcome=abstention_outcome,
        semanticEvaluated=True,
        confidenceEvaluated=confidence_evaluated,
        confidence=observation.confidence,
        confidenceSafetyPassed=confidence_safety_passed,
        answerClassPassed=answer_class_passed,
        requiredEvidencePassed=required_evidence_passed,
        oracleValidationPassed=answer_class_passed and required_evidence_passed,
        diagnostics=diagnostics,
    )


def evaluate_report(
    oracles: list[ScenarioOracle],
    observations: list[EvaluationObservation],
) -> AbstentionConfidenceReport:
    oracle_by_id = {oracle.scenario_id: oracle for oracle in oracles}
    if len(oracle_by_id) != len(oracles):
        raise ValueError("duplicate scenario oracle")

    results: list[AbstentionConfidenceResult] = []
    for observation in observations:
        oracle = oracle_by_id.get(observation.scenario_id)
        if oracle is None:
            results.append(_not_evaluated(observation, "UNKNOWN_SCENARIO"))
        else:
            results.append(evaluate_observation(oracle, observation))

    semantic_results = [result for result in results if result.semantic_evaluated]
    quality_results = [
        result
        for result in semantic_results
        if result.oracle_validation_passed is True
    ]
    oracle_reviewed = bool(oracles) and all(oracle.reviewed for oracle in oracles)
    confidence_buckets: dict[str, ConfidenceBucket] = {}
    abstention_counts: dict[str, int] = {}
    overconfidence_count = 0
    underconfidence_count = 0
    if oracle_reviewed:
        for level in ("HIGH", "MEDIUM", "LOW"):
            eligible = [
                (result, observation)
                for result, observation in zip(results, observations)
                if result.confidence == level
                and result.confidence_evaluated
                and result.semantic_evaluated
                and result.oracle_validation_passed is True
            ]
            grounded_correct = sum(
                observation.grounded_correctness is True for _, observation in eligible
            )
            evidence_supported = sum(
                observation.evidence_supported is True for _, observation in eligible
            )
            incorrect = sum(
                observation.grounded_correctness is False for _, observation in eligible
            )
            count = len(eligible)
            confidence_buckets[level] = ConfidenceBucket(
                eligibleCount=count,
                groundedCorrectCount=grounded_correct,
                evidenceSupportedCount=evidence_supported,
                incorrectCount=incorrect,
                groundedCorrectRate=_rate(grounded_correct, count),
                evidenceSupportRate=_rate(evidence_supported, count),
            )

        abstention_counts = dict(
            sorted(Counter(result.abstention_outcome.value for result in quality_results).items())
        )
        overconfidence_count = sum(
            result.confidence == "HIGH"
            and result.oracle_validation_passed is True
            and result.confidence_safety_passed is False
            for result in results
        )
        underconfidence_count = sum(
            result.confidence == "LOW"
            and result.confidence_evaluated
            and observation.grounded_correctness is True
            for result, observation in zip(results, observations)
        )
    return AbstentionConfidenceReport(
        protocolVersion=PROTOCOL_VERSION,
        oracleReviewed=oracle_reviewed,
        qualityMetricsAvailable=oracle_reviewed,
        semanticObservationCount=len(quality_results),
        notEvaluatedCount=len(results) - len(quality_results),
        oracleExcludedCount=len(semantic_results) - len(quality_results),
        abstentionCounts=abstention_counts,
        confidenceBuckets=confidence_buckets,
        overconfidenceCount=overconfidence_count,
        underconfidenceCount=underconfidence_count,
        results=results,
    )


def _not_evaluated(
    observation: EvaluationObservation,
    reason: str,
) -> AbstentionConfidenceResult:
    return AbstentionConfidenceResult(
        scenarioId=observation.scenario_id,
        abstentionOutcome=AbstentionOutcome.ABSTENTION_NOT_EVALUATED,
        semanticEvaluated=False,
        confidenceEvaluated=False,
        confidence=observation.confidence,
        confidenceSafetyPassed=None,
        answerClassPassed=None,
        requiredEvidencePassed=None,
        oracleValidationPassed=None,
        diagnostics=[reason],
    )


def _rate(numerator: int, denominator: int) -> float | None:
    return round(numerator / denominator, 3) if denominator else None
