"""Story0132 evaluation helpers kept outside production services."""

from __future__ import annotations

from copy import deepcopy
from itertools import combinations
from typing import Any

from .interpretation import adapt_production_story_context_result, score_interpretation


STORY0132_THRESHOLDS = {
    "thresholdVersion": "1.0.0",
    "positiveCausalAccuracyMin": 0.80,
    "case04NotEstablishedRuns": 3,
    "groundingRequired": True,
    "unsupportedInferenceRateMax": 0.0,
    "stabilityMin": 0.90,
}


def causal_diagnostics(runs: list[dict[str, Any]]) -> dict[str, Any]:
    """Compute versioned causal diagnostics without changing historical metrics."""

    def average(key: str) -> float | None:
        values = [run[key] for run in runs if run.get(key) is not None]
        return sum(values) / len(values) if values else None

    signatures_by_question: dict[str, list[tuple[Any, ...]]] = {}
    for run in runs:
        signature = run.get("causalClaimSignature")
        if signature is not None:
            # V2 records one assessment per question. Legacy captures have no
            # question identity and retain their historical case-level fallback.
            identity = str(run.get("questionId") or run.get("claimId") or run.get("caseId"))
            signatures_by_question.setdefault(identity, []).append(tuple(map(tuple, signature)))
    stability_values: list[float] = []
    for signatures in signatures_by_question.values():
        pairs = list(combinations(signatures, 2))
        if pairs:
            stability_values.append(sum(left == right for left, right in pairs) / len(pairs))
    return {
        "causalOverclaimRate": average("causalOverclaimRate"),
        "abstentionAccuracy": average("abstentionAccuracy"),
        "roleAdmissibilityRate": average("roleAdmissibilityRate"),
        "causalClaimStability": (
            sum(stability_values) / len(stability_values) if stability_values else None
        ),
    }


def score_production_result(
    case: dict[str, Any],
    context: dict[str, Any],
    production_result: dict[str, Any],
) -> dict[str, Any]:
    """Score a production-shaped result without exposing benchmark fields."""

    return score_interpretation(
        case,
        context,
        adapt_production_story_context_result(case, production_result),
    )


def remove_causal_evidence(
    context: dict[str, Any], references: set[str]
) -> dict[str, Any]:
    """Remove only selected causal evidence while retaining other evidence."""

    reduced = deepcopy(context)
    reduced["evidence"] = [
        item for item in reduced.get("evidence", [])
        if item.get("reference") not in references
    ]
    return reduced


def remove_causal_evidence_for_question(
    context: dict[str, Any], *, case_id: str, question_id: str,
    references: set[str],
) -> dict[str, Any]:
    """Build an explicitly traceable evidence-removal variant for one question."""

    if not case_id or not question_id:
        raise ValueError("evidence removal requires case and question identity")
    return {
        "caseId": case_id,
        "questionId": question_id,
        "removedEvidenceReferences": sorted(references),
        "context": remove_causal_evidence(context, references),
    }


def evaluate_thresholds(
    runs: list[dict[str, Any]],
    *,
    case04_id: str = "CASE-04",
    stability: float | None = None,
    expected_positive_total: int | None = None,
) -> dict[str, Any]:
    """Apply frozen-before-run Story0132 thresholds to raw run scores."""

    positive = [
        run for run in runs
        if run.get("caseId") != case04_id
        and (
            expected_positive_total is not None
            or run.get("causalReasoningAccuracy") is not None
        )
    ]
    case04 = [run for run in runs if run.get("caseId") == case04_id]
    positive_accuracy = (
        sum(run.get("causalReasoningAccuracy") == 1.0 for run in positive)
        / (expected_positive_total or len(positive))
        if positive and (expected_positive_total or len(positive)) > 0 else None
    )
    diagnostics = causal_diagnostics(runs)
    result = {
        "thresholdVersion": STORY0132_THRESHOLDS["thresholdVersion"],
        "positiveCausalAccuracy": positive_accuracy,
        "positiveCausalAccuracyDenominator": expected_positive_total or len(positive),
        "case04NotEstablishedRuns": sum(
            run.get("causalReasoningAccuracy") == 1.0
            or run.get("actualClassification") == "NOT_ESTABLISHED"
            for run in case04
        ),
        "case04Runs": len(case04),
        "groundingPass": all(run.get("groundingValid") for run in runs),
        "unsupportedInferencePass": all(
            run.get("unsupportedInferenceRate") == 0 for run in runs
        ),
        "stability": stability if stability is not None else diagnostics["causalClaimStability"],
        "diagnostics": diagnostics,
    }
    result["pass"] = (
        result["positiveCausalAccuracy"] is not None
        and result["positiveCausalAccuracy"] >= STORY0132_THRESHOLDS["positiveCausalAccuracyMin"]
        and result["case04NotEstablishedRuns"] >= STORY0132_THRESHOLDS["case04NotEstablishedRuns"]
        and result["case04Runs"] >= STORY0132_THRESHOLDS["case04NotEstablishedRuns"]
        and result["groundingPass"]
        and result["unsupportedInferencePass"]
        and result["stability"] is not None
        and result["stability"] >= STORY0132_THRESHOLDS["stabilityMin"]
    )
    return result
