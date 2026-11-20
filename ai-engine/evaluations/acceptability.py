"""Evaluation-only acceptability classification for agent responses.

This module deliberately consumes explicit observation metadata. It never infers
quality from answer prose, confidence text, or citation counts alone.
"""

from __future__ import annotations

from enum import Enum
from typing import Any, Mapping


class AcceptabilityStatus(str, Enum):
    ACCEPTABLE = "ACCEPTABLE"
    BOUNDED = "BOUNDED"
    INSUFFICIENT = "INSUFFICIENT"
    STALE_OR_UNKNOWN = "STALE_OR_UNKNOWN"
    FAILED = "FAILED"
    NOT_EVALUATED = "NOT_EVALUATED"


def classify_acceptability(observation: Mapping[str, Any]) -> dict[str, Any]:
    """Classify one normalized observation using explicit metadata only."""

    if observation.get("evaluationFailure"):
        return _result(
            AcceptabilityStatus.FAILED,
            "EVALUATION_FAILURE",
            semantic_evaluated=False,
        )

    if observation.get("technicalStatus") != "COMPLETED":
        return _result(
            AcceptabilityStatus.NOT_EVALUATED,
            "TECHNICAL_RESULT_NOT_EVALUABLE",
            semantic_evaluated=False,
        )
    if observation.get("semanticEligibility") != "SEMANTICALLY_ELIGIBLE":
        return _result(
            AcceptabilityStatus.NOT_EVALUATED,
            "SEMANTIC_RESULT_NOT_EVALUABLE",
            semantic_evaluated=False,
        )
    if observation.get("metadataComplete") is not True:
        return _result(
            AcceptabilityStatus.NOT_EVALUATED,
            "ACCEPTABILITY_METADATA_INCOMPLETE",
            semantic_evaluated=False,
        )

    if observation.get("abstention") is True:
        return _result(AcceptabilityStatus.INSUFFICIENT, "ABSTENTION_DECLARED")

    if (
        observation.get("unauthorizedEvidenceCount", 0) > 0
        or observation.get("revisionMismatchCount", 0) > 0
    ):
        return _result(AcceptabilityStatus.INSUFFICIENT, "EVIDENCE_SCOPE_INCONSISTENT")

    if observation.get("groundingStatus") in {"UNSUPPORTED", "CONTRADICTED"}:
        return _result(AcceptabilityStatus.INSUFFICIENT, "GROUNDING_NOT_SUFFICIENT")

    if observation.get("groundingStatus") == "REVIEW_REQUIRED":
        return _result(AcceptabilityStatus.BOUNDED, "GROUNDING_REVIEW_REQUIRED")

    if observation.get("contradictionCount", 0) > 0:
        return _result(AcceptabilityStatus.INSUFFICIENT, "CONTRADICTION_PRESENT")

    if (
        observation.get("expectedEvidenceCount", 0)
        > observation.get("coveredEvidenceCount", 0)
    ):
        return _result(AcceptabilityStatus.INSUFFICIENT, "EVIDENCE_COVERAGE_INCOMPLETE")

    freshness = observation.get("freshness")
    temporal_scope = observation.get("temporalScope", "CURRENT")
    if freshness in {"STALE", "UNKNOWN"} and not (
        temporal_scope == "HISTORICAL" and observation.get("revisionPinned") is True
    ):
        return _result(AcceptabilityStatus.STALE_OR_UNKNOWN, "FRESHNESS_NOT_CURRENT")

    if observation.get("truncated") and not observation.get("principalConclusionCovered"):
        return _result(AcceptabilityStatus.INSUFFICIENT, "PRINCIPAL_CONCLUSION_NOT_COVERED")

    if observation.get("nextStepPresent") is False:
        return _result(AcceptabilityStatus.BOUNDED, "NEXT_STEP_NOT_EXPLICIT")

    if observation.get("limitations"):
        return _result(AcceptabilityStatus.BOUNDED, "KNOWN_LIMITATION")

    return _result(AcceptabilityStatus.ACCEPTABLE, "EVIDENCE_AND_LIMITS_SUFFICIENT")


def _result(
    status: AcceptabilityStatus,
    reason: str,
    *,
    semantic_evaluated: bool = True,
) -> dict[str, Any]:
    return {
        "status": status.value,
        "reason": reason,
        "semanticEvaluated": semantic_evaluated,
    }
