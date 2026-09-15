"""Deterministic scoring of structured, evaluation-only interpretation output."""

from __future__ import annotations

import re
from typing import Any

from .experimental import FailureAttribution, attribute_failure


def _norm(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value).strip()).casefold()


def _refs(response: dict[str, Any]) -> set[str]:
    refs: set[str] = set()
    for claim in response.get("claims", []):
        refs.update(str(ref) for ref in claim.get("evidenceRefs", []))
    for claim in response.get("causalClaims", []):
        refs.update(str(ref) for ref in claim.get("evidenceRefs", []))
    return refs


def _causal_tasks(case: dict[str, Any]) -> list[dict[str, str]]:
    links = case.get("expectedCausalLinks", [])
    if links:
        return [{"claimId": f"claim-{index + 1}", "expected": item["strength"]} for index, item in enumerate(links)]
    if case.get("negativeCase"):
        return [{"claimId": "claim-1", "expected": case["expectedOutcome"]}]
    return []


def _exact_set(values: list[Any], allowed: list[str]) -> set[str]:
    allowed_by_norm = {_norm(value): value for value in allowed}
    return {allowed_by_norm[_norm(value)] for value in values if _norm(value) in allowed_by_norm}


def score_interpretation(
    case: dict[str, Any],
    context: dict[str, Any],
    response: dict[str, Any],
    *,
    constraint_support: dict[str, list[str]] | None = None,
    impact_support: dict[str, list[str]] | None = None,
) -> dict[str, Any]:
    """Score only exact structured fields; arbitrary prose remains unmeasured."""

    supplied_refs = {item.get("reference") for item in context.get("evidence", [])}
    supplied_refs.discard(None)
    referenced = _refs(response)
    grounding = referenced <= supplied_refs

    tasks = _causal_tasks(case)
    by_claim = {item.get("claimId"): item for item in response.get("causalClaims", [])}
    causal_correct = [
        by_claim.get(task["claimId"], {}).get("classification") == task["expected"]
        for task in tasks
    ]
    causal_accuracy = None if not causal_correct else sum(causal_correct) / len(causal_correct)

    constraints = _exact_set(response.get("constraints", []), case.get("expectedConstraints", []))
    expected_constraints = set(case.get("expectedConstraints", []))
    if constraint_support:
        present_constraints = {
            constraint for constraint, refs in constraint_support.items() if set(refs) <= supplied_refs
        }
        context_constraint_recall = (
            None if not expected_constraints else len(present_constraints & expected_constraints) / len(expected_constraints)
        )
        interpretation_constraint_recall = (
            None if not present_constraints else len(constraints & present_constraints) / len(present_constraints)
        )
        constraint_attribution = attribute_failure(
            context_recall=context_constraint_recall,
            interpretation_recall=interpretation_constraint_recall,
        )
    else:
        present_constraints = None
        context_constraint_recall = None
        interpretation_constraint_recall = None
        constraint_attribution = FailureAttribution.NOT_ATTRIBUTABLE

    component_values = _exact_set(response.get("affectedComponents", []), case.get("expectedAffectedComponents", []))
    test_values = _exact_set(response.get("affectedTests", []), case.get("expectedAffectedTests", []))
    if impact_support:
        expected_impact = set(case.get("expectedAffectedComponents", [])) | set(case.get("expectedAffectedTests", []))
        present_impact = {item for item, refs in impact_support.items() if set(refs) <= supplied_refs}
        returned_impact = component_values | test_values
        context_impact_recall = None if not expected_impact else len(present_impact & expected_impact) / len(expected_impact)
        interpretation_impact_recall = None if not present_impact else len(returned_impact & present_impact) / len(present_impact)
        impact_attribution = attribute_failure(
            context_recall=context_impact_recall,
            interpretation_recall=interpretation_impact_recall,
        )
    else:
        context_impact_recall = None
        interpretation_impact_recall = None
        impact_attribution = FailureAttribution.NOT_ATTRIBUTABLE

    expected_components = set(case.get("expectedAffectedComponents", []))
    expected_tests = set(case.get("expectedAffectedTests", []))
    unsupported_values = (
        set(str(value) for value in response.get("affectedComponents", [])) - expected_components
    ) | (set(str(value) for value in response.get("affectedTests", [])) - expected_tests)
    unsupported_values |= {
        str(value) for value in response.get("constraints", [])
        if _norm(value) not in {_norm(expected) for expected in expected_constraints}
    }
    unsupported_values |= {
        str(value) for value in response.get("causalClaims", [])
        if value.get("claimId") not in {task["claimId"] for task in tasks}
    }
    structured_fields_present = any(field in response for field in ("constraints", "causalClaims", "affectedComponents", "affectedTests"))
    unsupported_measurement = "MEASURED_STRUCTURED_FIELDS" if structured_fields_present else "NOT_MEASURED"
    unsupported_rate = (
        len(unsupported_values) / max(1, len(component_values | test_values | unsupported_values))
        if unsupported_measurement != "NOT_MEASURED" else None
    )

    return {
        "evidenceClaimGrounding": "MEASURED" if response.get("claims") is not None or response.get("causalClaims") is not None else "NOT_MEASURED",
        "groundingValid": grounding,
        "referencedEvidence": sorted(referenced),
        "causalReasoningAccuracy": causal_accuracy,
        "causalClaimsMeasured": len(causal_correct),
        "contextConstraintRecall": context_constraint_recall,
        "interpretationConstraintRecall": interpretation_constraint_recall,
        "constraintAttribution": constraint_attribution.value,
        "contextImpactRecall": context_impact_recall,
        "interpretationImpactRecall": interpretation_impact_recall,
        "changeImpactAttribution": impact_attribution.value,
        "unsupportedInferenceRate": unsupported_rate,
        "unsupportedInferenceMeasurement": unsupported_measurement,
    }
