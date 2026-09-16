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
    assessment = response.get("causalAssessment")
    if isinstance(assessment, dict):
        for assertion in assessment.get("evidenceAssertions", []):
            evidence_ref = assertion.get("evidenceReference", {})
            if isinstance(evidence_ref, dict) and evidence_ref.get("reference"):
                refs.add(str(evidence_ref["reference"]))
    return refs


def _causal_claims(response: dict[str, Any]) -> list[dict[str, Any]]:
    """Normalize V2's single assessment to the frozen evaluator's claim shape."""
    claims = response.get("causalClaims", [])
    if claims:
        return claims
    assessment = response.get("causalAssessment")
    if not isinstance(assessment, dict):
        return []
    question = assessment.get("question", {})
    assertions = assessment.get("evidenceAssertions", [])
    return [{
        "source": question.get("source", ""),
        "target": question.get("target", ""),
        "causalClassification": assessment.get("classification"),
        "evidenceReferences": [
            item.get("evidenceReference", {}) for item in assertions
            if isinstance(item, dict)
        ],
    }]


def adapt_production_story_context_result(
    case: dict[str, Any], result: dict[str, Any]
) -> dict[str, Any]:
    """Adapt production-shaped causal claims for the frozen evaluator only.

    Benchmark identifiers are introduced after generation and never enter the
    model-facing context. Claims are matched by their source and target; an
    unmatched claim cannot receive credit.
    """

    expected_links = case.get("expectedCausalLinks", [])
    expected_by_pair = {
        (_norm(link["from"]), _norm(link["to"])): f"claim-{index + 1}"
        for index, link in enumerate(expected_links)
    }
    expected_by_source: dict[str, list[str]] = {}
    for index, link in enumerate(expected_links):
        source = re.sub(r"[^a-z0-9]", "", _norm(link["from"]))
        expected_by_source.setdefault(source, []).append(f"claim-{index + 1}")
    adapted: list[dict[str, Any]] = []
    for index, claim in enumerate(_causal_claims(result)):
        source = str(claim.get("source", ""))
        target = str(claim.get("target", ""))
        claim_id = expected_by_pair.get((_norm(source), _norm(target)))
        if claim_id is None:
            source_key = re.sub(r"[^a-z0-9]", "", _norm(source))
            matches = [
                value for key, values in expected_by_source.items()
                if key and key in source_key
                for value in values
            ]
            if len(matches) == 1:
                claim_id = matches[0]
        if claim_id is None and case.get("negativeCase") and index == 0:
            claim_id = "claim-1"
        claim_id = claim_id or f"unmatched-{index + 1}"
        evidence_refs = claim.get("evidenceReferences", [])
        refs = [
            item.get("reference") if isinstance(item, dict) else str(item)
            for item in evidence_refs
        ]
        adapted.append({
            "claimId": claim_id,
            "classification": claim.get("causalClassification"),
            "evidenceRefs": [ref for ref in refs if ref],
            "evidenceRoles": [
                item.get("role") for item in evidence_refs
                if isinstance(item, dict) and item.get("role")
            ],
        })
    return {"causalClaims": adapted}


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

    original_response = response
    supplied_refs = {item.get("reference") for item in context.get("evidence", [])}
    supplied_refs.discard(None)
    referenced = _refs(response)
    response = {**response, "causalClaims": _causal_claims(response)}
    grounding = referenced <= supplied_refs

    tasks = _causal_tasks(case)
    adapted = response if any(item.get("claimId") for item in response.get("causalClaims", [])) \
        else adapt_production_story_context_result(case, response)
    by_claim = {item.get("claimId"): item for item in adapted.get("causalClaims", [])}
    causal_correct = [
        by_claim.get(task["claimId"], {}).get("classification") == task["expected"]
        for task in tasks
    ]
    causal_accuracy = None if not causal_correct else sum(causal_correct) / len(causal_correct)

    def role_admissible(claim: dict[str, Any] | None) -> bool:
        if not claim or not claim.get("evidenceRoles"):
            return False
        roles = list(claim["evidenceRoles"])
        if "CONTRADICTORY_EVIDENCE" in roles or "NON_CAUSAL_CONTEXT" in roles:
            return claim.get("classification") == "NOT_ESTABLISHED"
        classification = claim.get("classification")
        if classification == "EXPLICITLY_DOCUMENTED":
            return "DIRECT_RELATIONSHIP_STATEMENT" in roles
        if classification == "STRONGLY_SUPPORTED":
            refs = claim.get("evidenceRefs", [])
            material_refs = {
                ref for ref, role in zip(refs, roles)
                if role == "MATERIAL_RELATIONSHIP_SUPPORT"
            }
            return len(material_refs) >= 2
        return classification == "NOT_ESTABLISHED"

    negative_tasks = [task for task in tasks if task["expected"] == "NOT_ESTABLISHED"]
    overclaim_count = sum(
        by_claim.get(task["claimId"], {}).get("classification") in {
            "EXPLICITLY_DOCUMENTED", "STRONGLY_SUPPORTED"
        }
        for task in negative_tasks
    )
    abstention_count = sum(
        by_claim.get(task["claimId"], {}).get("classification") == "NOT_ESTABLISHED"
        for task in negative_tasks
    )
    role_admissible_count = sum(
        role_admissible(by_claim.get(task["claimId"])) for task in tasks
    )

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
    structured_fields_present = any(field in original_response for field in ("constraints", "causalClaims", "causalAssessment", "affectedComponents", "affectedTests"))
    unsupported_measurement = "MEASURED_STRUCTURED_FIELDS" if structured_fields_present else "NOT_MEASURED"
    unsupported_rate = (
        len(unsupported_values) / max(1, len(component_values | test_values | unsupported_values))
        if unsupported_measurement != "NOT_MEASURED" else None
    )

    return {
        "evidenceClaimGrounding": "MEASURED" if original_response.get("claims") is not None or original_response.get("causalClaims") is not None or original_response.get("causalAssessment") is not None else "NOT_MEASURED",
        "groundingValid": grounding,
        "referencedEvidence": sorted(referenced),
        "causalReasoningAccuracy": causal_accuracy,
        "causalClaimsMeasured": len(causal_correct),
        "causalOverclaimRate": (
            overclaim_count / len(negative_tasks) if negative_tasks else None
        ),
        "abstentionAccuracy": (
            abstention_count / len(negative_tasks) if negative_tasks else None
        ),
        "roleAdmissibilityRate": (
            role_admissible_count / len(tasks) if tasks else None
        ),
        "causalClaimSignature": [
            (
                task["claimId"],
                by_claim.get(task["claimId"], {}).get("classification"),
                tuple(by_claim.get(task["claimId"], {}).get("evidenceRoles", [])),
            )
            for task in tasks
        ],
        "contextConstraintRecall": context_constraint_recall,
        "interpretationConstraintRecall": interpretation_constraint_recall,
        "constraintAttribution": constraint_attribution.value,
        "contextImpactRecall": context_impact_recall,
        "interpretationImpactRecall": interpretation_impact_recall,
        "changeImpactAttribution": impact_attribution.value,
        "unsupportedInferenceRate": unsupported_rate,
        "unsupportedInferenceMeasurement": unsupported_measurement,
    }
