"""Pure, deterministic scoring and gate composition for product evaluation captures."""

from __future__ import annotations

from itertools import combinations
from typing import Any, Iterable

from .loader import BenchmarkValidationError, validate_benchmark
from .experimental import (
    EvaluationCondition,
    validate_evidence_only_context,
    validate_comparable_metadata,
    validate_three_condition_capture,
)
from .interpretation import score_interpretation


def _set(value: Iterable[Any] | None) -> set[str]:
    return {str(item) for item in (value or [])}


def _ratio(expected: set[str], returned: set[str]) -> float | None:
    if not expected and not returned:
        return None
    if not expected:
        return 0.0
    return len(expected & returned) / len(expected)


def _precision(expected: set[str], returned: set[str]) -> float | None:
    if not expected and not returned:
        return None
    if not returned:
        return 0.0
    return len(expected & returned) / len(returned)


def _jaccard(left: set[str], right: set[str]) -> float:
    union = left | right
    return 1.0 if not union else len(left & right) / len(union)


def evaluate_capture(benchmark: dict[str, Any], capture: dict[str, Any], *, oracle_approved: bool = False) -> dict[str, Any]:
    validate_benchmark(benchmark)
    if not oracle_approved or benchmark["oracleStatus"] != "APPROVED":
        return {"status": "ORACLE_NOT_APPROVED", "acceptanceAuthorized": False, "oracleStatus": benchmark["oracleStatus"]}
    if capture.get("actualRepositoryRevision") != benchmark["repositoryRevision"]:
        return {"status": "REVISION_MISMATCH", "acceptanceAuthorized": False}

    captures = capture.get("runs") or [capture]
    by_id = {case.get("caseId"): case for case in capture.get("cases", [])}
    case_results: list[dict[str, Any]] = []
    for expected in benchmark["cases"]:
        actual = by_id.get(expected["caseId"], {})
        evidence = _set(actual.get("returnedEvidence"))
        expected_evidence = _set(expected["expectedEvidence"])
        constraints = _set(actual.get("returnedConstraintIds"))
        causal = _set(actual.get("returnedCausalLinkIds"))
        components = _set(actual.get("returnedAffectedComponentIds"))
        tests = _set(actual.get("returnedAffectedTestIds"))
        unsupported = actual.get("unsupportedClaimRate")
        case_results.append({
            "caseId": expected["caseId"],
            "evidenceRecall": _ratio(expected_evidence, evidence),
            "evidencePrecision": _precision(expected_evidence, evidence),
            "constraintRecall": _ratio(_set(expected["expectedConstraintIds"]), constraints),
            "causalLinkRecall": _ratio(_set(item["id"] for item in expected["expectedCausalLinks"]), causal),
            "changeImpactRecall": _ratio(_set(expected["expectedAffectedComponentIds"] + expected["expectedAffectedTestIds"]), components | tests),
            "negativeControl": actual.get("outcome") == expected.get("expectedOutcome") if expected.get("negativeCase") else None,
            "unsupportedClaimRate": unsupported,
            "unsupportedClaimsMeasurement": actual.get("unsupportedClaimsMeasurement", "NOT_MEASURED"),
            "typedReferencesResolve": actual.get("typedReferencesResolve", False),
            "groundingValid": actual.get("groundingValid", False),
            "selectedEvidenceCount": actual.get("selectedEvidenceCount"),
            "estimatedTokens": actual.get("estimatedTokens"),
            "followUpSearches": actual.get("followUpSearches"),
        })

    stability_values: list[float] = []
    for expected in benchmark["cases"]:
        references = [_set(run_case.get("returnedEvidence")) for run in captures for run_case in run.get("cases", []) if run_case.get("caseId") == expected["caseId"]]
        pairs = list(combinations(references, 2))
        if pairs:
            stability_values.append(sum(_jaccard(a, b) for a, b in pairs) / len(pairs))
    stability = None if not stability_values else sum(stability_values) / len(stability_values)
    positive = [result for result, expected in zip(case_results, benchmark["cases"]) if not expected["negativeCase"]]
    mandatory = {
        "evidenceRecall": all((result["evidenceRecall"] or 0) >= (0.5 if result["caseId"] == "CASE-04" else 0.8) for result in case_results),
        "evidencePrecision": all((result["evidencePrecision"] or 0) >= 0.6 for result in positive),
        "constraintRecall": all((result["constraintRecall"] or 0) >= 0.8 for result in positive if result["caseId"] == "CASE-02" or result["constraintRecall"] is not None),
        "causalLinkRecall": all((result["causalLinkRecall"] or 0) >= 0.67 for result in positive),
        "unsupportedClaimRate": all(result["unsupportedClaimRate"] == 0 for result in case_results),
        "changeImpactRecall": all((result["changeImpactRecall"] or 0) >= 0.75 for result in positive if result["caseId"] == "CASE-03"),
        "followUpSearches": len([result for result in positive if result["followUpSearches"] is not None and result["followUpSearches"] <= 2]) == 3,
        "contextEfficiency": all(result["selectedEvidenceCount"] is not None and result["selectedEvidenceCount"] <= 60 and result["estimatedTokens"] is not None and result["estimatedTokens"] <= 6000 for result in case_results),
        "evidenceStability": stability is not None and stability >= 0.9,
    }
    ai_utility = all(mandatory.values())
    engineering = all(result["typedReferencesResolve"] and result["groundingValid"] for result in case_results)
    negative_ok = all(result["negativeControl"] for result, expected in zip(case_results, benchmark["cases"]) if expected["negativeCase"])
    return {
        "status": "SCORED",
        "acceptanceAuthorized": True,
        "engineeringCorrectnessPass": engineering,
        "aiUtilityPass": ai_utility and negative_ok,
        "aiUtilityThresholds": mandatory,
        "negativeControlAccuracy": negative_ok,
        "humanUtilityPass": None,
        "humanAiParityPass": None,
        "storyAccepted": False,
        "evidenceStability": stability,
        "cases": case_results,
    }


def evaluate_experimental_capture(
    benchmark: dict[str, Any],
    capture: dict[str, Any],
    *,
    oracle_approved: bool = False,
    constraint_support: dict[str, dict[str, list[str]]] | None = None,
    impact_support: dict[str, dict[str, list[str]]] | None = None,
) -> dict[str, Any]:
    """Evaluate the three conditions without conflating context and reasoning."""

    validate_benchmark(benchmark)
    if not oracle_approved or benchmark["oracleStatus"] != "APPROVED":
        return {"status": "ORACLE_NOT_APPROVED", "acceptanceAuthorized": False}
    if capture.get("actualRepositoryRevision") != benchmark["repositoryRevision"]:
        return {"status": "REVISION_MISMATCH", "acceptanceAuthorized": False}
    condition_errors = validate_three_condition_capture(capture)
    condition_errors.extend(validate_comparable_metadata(capture))
    if condition_errors:
        return {"status": "THREE_CONDITION_HARNESS_NOT_READY", "errors": condition_errors, "acceptanceAuthorized": False}

    results: dict[str, Any] = {}
    leakage_errors: list[str] = []
    for condition in EvaluationCondition:
        condition_capture = capture["conditions"][condition.value]
        by_case = {item.get("caseId"): item for item in condition_capture.get("cases", [])}
        condition_results: list[dict[str, Any]] = []
        for case in benchmark["cases"]:
            actual = by_case.get(case["caseId"], {})
            context = actual.get("context", {
                "condition": condition.value,
                "evidence": [{"reference": reference} for reference in actual.get("returnedEvidence", [])],
            })
            if condition is EvaluationCondition.GROUND_TRUTH_CONTEXT:
                leakage_errors.extend(
                    f"{condition.value}/{case['caseId']}: {error}"
                    for error in validate_evidence_only_context(context)
                )
            supplied = {item.get("reference") for item in context.get("evidence", [])}
            supplied.discard(None)
            expected = set(case.get("expectedEvidence", []))
            context_recall = None if not expected else len(expected & supplied) / len(expected)
            context_precision = None if not supplied else len(expected & supplied) / len(supplied)
            interpretation = score_interpretation(
                case,
                context,
                actual.get("interpretation", {}),
                constraint_support=(constraint_support or {}).get(case["caseId"]),
                impact_support=(impact_support or {}).get(case["caseId"]),
            )
            interpretation["contextEvidenceRecall"] = context_recall
            interpretation["contextEvidencePrecision"] = context_precision
            interpretation["contextSelectedEvidenceCount"] = actual.get("selectedEvidenceCount", len(supplied))
            interpretation["contextEstimatedTokens"] = actual.get("estimatedTokens")
            interpretation["contextFollowUpSearches"] = actual.get("followUpSearches")
            interpretation["failureAttribution"] = (
                "CONTEXT_FAILURE" if context_recall is not None and context_recall < 1.0 and interpretation["causalReasoningAccuracy"] == 1.0
                else "INTERPRETATION_FAILURE" if context_recall == 1.0 and interpretation["causalReasoningAccuracy"] is not None and interpretation["causalReasoningAccuracy"] < 1.0
                else "BOTH" if context_recall is not None and context_recall < 1.0 and interpretation["causalReasoningAccuracy"] is not None and interpretation["causalReasoningAccuracy"] < 1.0
                else "NOT_ATTRIBUTABLE"
            )
            condition_results.append({"caseId": case["caseId"], **interpretation})
        results[condition.value] = {"cases": condition_results}

    if leakage_errors:
        return {
            "status": "GROUND_TRUTH_CONTEXT_LEAKAGE",
            "errors": leakage_errors,
            "acceptanceAuthorized": False,
            "conditions": results,
        }
    return {
        "status": "SCORED_EXPERIMENTAL",
        "acceptanceAuthorized": True,
        "conditions": results,
        "contextQuality": "MEASURED",
        "interpretationQuality": "MEASURED_STRUCTURED_FIELDS",
        "humanUtility": "NOT_MEASURED",
        "formalBaselineExecuted": False,
    }
