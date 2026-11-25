"""Deterministic provider-free tests for the A/B/C evaluation adapters."""

from copy import deepcopy
import json
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_normalization import (
    normalize_direct_investigator,
    normalize_story_context,
    render_evaluation_display,
)
from evaluations.devlog_ai_abc_normalization.normalizer import (
    NormalizationError,
    sha256_json,
)


REVISION = "0673d1d32433cdaff054d5f1defd1bedd01c0039"


def raw(payload: dict, *, treatment: str, raw_hash: bool = True) -> dict:
    result = {
        "observationId": f"obs-{treatment.lower()}",
        "caseId": "Q1",
        "treatment": treatment,
        "repositoryRevision": REVISION,
        "rawOutput": deepcopy(payload),
    }
    if raw_hash:
        result["rawSha256"] = sha256_json(result["rawOutput"])
    return result


def story_payload() -> dict:
    return {
        "executionStatus": "COMPLETED",
        "finalResponse": {
            "causalClaims": [{
                "explanation": "The repository does not establish the causal link.",
                "causalClassification": "NOT_ESTABLISHED",
                "evidenceBasis": "INSUFFICIENT",
                "evidenceReferences": [{"reference": "file:docs/ADR.md#L1-L3", "role": "NON_CAUSAL_CONTEXT"}],
            }],
            "uncertainties": [{"description": "The direct relationship statement is absent."}],
        },
        "evidence": [{"reference": "file:docs/ADR.md#L1-L3"}],
    }


def test_a_and_c_use_the_same_structured_story_adapter_without_changing_raw() -> None:
    payload = story_payload()
    original = deepcopy(payload)
    a = normalize_story_context(raw(payload, treatment="A"))
    c = normalize_story_context(raw(payload, treatment="C"))

    assert payload == original
    assert a["claims"] == c["claims"]
    assert a["treatment"] == "A_DEVLOG_CURRENT"
    assert c["treatment"] == "C"
    assert a["answerText"] is None
    assert "ANSWER_TEXT_NOT_AVAILABLE" in a["normalizationWarnings"]
    assert a["rawArtifact"]["rawPayloadPreserved"] is True
    assert render_evaluation_display(a) == "The repository does not establish the causal link.\nThe direct relationship statement is absent."


def test_b_preserves_common_answer_claims_evidence_and_locator() -> None:
    answer = {
        "executionStatus": "COMPLETED",
        "finalResponse": {
            "answerText": "The evidence does not establish causality.",
            "claims": [{"text": "The causal relationship is not established.", "claimType": "INTERPRETATION", "references": ["file:docs/ADR.md#L1-L3"]}],
            "evidence": [{"reference": "file:docs/ADR.md#L1-L3", "locator": {"kind": "LINE_RANGE", "startLine": 1, "endLine": 3}, "excerpt": "No causal statement.", "role": "DIRECT"}],
            "abstention": True,
        },
    }
    normalized = normalize_direct_investigator(raw(answer, treatment="B"))

    assert normalized["answerText"] == answer["finalResponse"]["answerText"]
    assert normalized["claims"][0]["statement"] == answer["finalResponse"]["claims"][0]["text"]
    assert normalized["evidenceReferences"][0]["locator"]["startLine"] == 1
    assert normalized["evidenceReferences"][0]["citationStatus"] == "RESOLVED_IN_RAW_LEDGER"
    assert normalized["abstention"] is True


def test_normalization_preserves_explicit_ordinal_confidence() -> None:
    answer = {
        "executionStatus": "COMPLETED",
        "finalResponse": {
            "answerText": "The evidence supports the bounded conclusion.",
            "confidence": "HIGH",
            "abstention": False,
        },
    }

    normalized = normalize_direct_investigator(raw(answer, treatment="B"))

    assert normalized["confidence"] == "HIGH"
    assert "CONFIDENCE_INVALID" not in normalized["normalizationWarnings"]


def test_invalid_confidence_is_not_reinterpreted() -> None:
    normalized = normalize_direct_investigator(raw({
        "finalResponse": {"confidence": "VERY_HIGH"},
    }, treatment="B"))

    assert normalized["confidence"] is None
    assert "CONFIDENCE_INVALID" in normalized["normalizationWarnings"]


def test_normalization_is_deterministic_and_keeps_raw_hash_identity() -> None:
    value = raw(story_payload(), treatment="C")
    first = normalize_story_context(value)
    second = normalize_story_context(value)

    assert first == second
    assert first["rawArtifact"]["rawSha256"] == value["rawSha256"]
    assert first["derivedSha256"] == sha256_json({key: item for key, item in first.items() if key != "derivedSha256"})


def test_oracle_is_not_an_input_and_cannot_enrich_the_derived_answer() -> None:
    value = raw({"executionStatus": "COMPLETED", "finalResponse": {"answerText": "Observed answer."}}, treatment="B")
    normalized = normalize_direct_investigator(value)

    assert "oracle" not in str(normalized).lower()
    assert normalized["claims"] == []
    assert "CLAIMS_NOT_AVAILABLE" in normalized["normalizationWarnings"]


@pytest.mark.parametrize(
    ("payload", "expected_status"),
    [
        ({"executionStatus": "PROVIDER_FAILURE"}, "PROVIDER_FAILURE"),
        ({"executionStatus": "PROVIDER_INCOMPLETE"}, "PROVIDER_INCOMPLETE_RESPONSE"),
        ({"executionStatus": "RUNTIME_FAILURE"}, "RUNTIME_FAILURE"),
        ({}, "NOT_EVALUATED"),
    ],
)
def test_technical_statuses_are_not_converted_into_semantic_failure(payload: dict, expected_status: str) -> None:
    normalized = normalize_direct_investigator(raw(payload, treatment="B"))

    assert normalized["technicalStatus"] == expected_status
    assert normalized["semanticEligibility"] == "NOT_EVALUATED"


def test_missing_citation_is_explicit_and_not_reconstructed() -> None:
    normalized = normalize_direct_investigator(raw({"finalResponse": {"claims": [{"text": "Claim", "claimType": "FACT"}]}}, treatment="B"))

    assert normalized["claims"][0]["evidenceReferences"] == []
    assert "CLAIM_WITHOUT_CITATION" in normalized["normalizationWarnings"]
    assert normalized["evidenceReferences"] == []


def test_invalid_and_unresolved_citations_are_preserved_as_statuses() -> None:
    payload = {
        "finalResponse": {"claims": [{"text": "Claim", "claimType": "FACT", "references": ["unknown:source"]}]},
        "evidence": [{"reference": "unknown:source"}],
    }
    normalized = normalize_direct_investigator(raw(payload, treatment="B"))

    assert normalized["evidenceReferences"][0]["sourceIdentity"] == "unknown:source"
    assert normalized["evidenceReferences"][0]["citationStatus"] == "RESOLVED_IN_RAW_LEDGER"
    assert normalized["evidenceReferences"][0]["locator"] is None


def test_revision_mismatch_is_not_silently_accepted() -> None:
    payload = {
        "finalResponse": {"claims": [{"text": "Claim", "claimType": "FACT", "references": ["file:old"]}]},
        "evidence": [{"reference": "file:old", "revision": "old-revision"}],
    }
    normalized = normalize_direct_investigator(raw(payload, treatment="B"))

    assert normalized["evidenceReferences"][0]["citationStatus"] == "REVISION_MISMATCH"


def test_missing_provenance_is_classified_and_raw_hash_is_computed() -> None:
    normalized = normalize_direct_investigator(raw({"finalResponse": {"answerText": "Answer"}}, treatment="B", raw_hash=False))

    assert normalized["rawArtifact"]["rawHashSource"] == "COMPUTED_FROM_RAW_PAYLOAD"
    assert "RAW_HASH_NOT_SUPPLIED_COMPUTED_FOR_PROVENANCE" in normalized["normalizationWarnings"]


def test_display_projection_does_not_invent_text_for_empty_structured_output() -> None:
    assert render_evaluation_display({"answerText": None, "claims": [], "limitations": []}) is None


def test_inconsistent_raw_hash_is_rejected() -> None:
    value = raw({"finalResponse": {"answerText": "Answer"}}, treatment="B")
    value["rawSha256"] = "0" * 64

    with pytest.raises(NormalizationError, match="RAW hash"):
        normalize_direct_investigator(value)


def test_partially_structured_story_output_keeps_unknowns_explicit() -> None:
    normalized = normalize_story_context(raw({"executionStatus": "COMPLETED", "finalResponse": {"architectureFindings": [{"description": "Observed architecture."}]}}, treatment="A"))

    assert normalized["claims"][0]["claimType"] == "UNCLASSIFIED"
    assert normalized["claims"][0]["evidenceReferences"] == []
    assert "CLAIM_WITHOUT_CITATION" in normalized["normalizationWarnings"]


def test_story_context_normalization_exposes_evaluation_only_acceptability() -> None:
    payload = {
        "executionStatus": "COMPLETED",
        "semanticEligibility": "SEMANTICALLY_ELIGIBLE",
        "groundingStatus": "GROUNDED",
        "freshness": "FRESH",
        "acceptability": {
            "groundingStatus": "GROUNDED",
            "freshness": "FRESH",
            "revisionPinned": True,
            "expectedEvidenceCount": 1,
            "coveredEvidenceCount": 1,
            "contradictionCount": 0,
            "truncated": False,
            "principalConclusionCovered": True,
            "nextStepPresent": True,
        },
        "finalResponse": {
            "answerText": "The evidence establishes the bounded conclusion.",
            "abstention": False,
            "claims": [{
                "text": "The bounded conclusion is established.",
                "claimType": "FACT",
                "references": ["file:docs/ADR.md#L1-L3"],
            }],
        },
        "evidence": [{"reference": "file:docs/ADR.md#L1-L3"}],
    }

    normalized = normalize_story_context(raw(payload, treatment="A"))

    assert normalized["acceptability"] == {
        "status": "ACCEPTABLE",
        "reason": "EVIDENCE_AND_LIMITS_SUFFICIENT",
        "semanticEvaluated": True,
    }


def test_story_0210_acceptability_baseline_is_replayable() -> None:
    fixture_path = Path(__file__).parents[1] / "evaluations" / "acceptability-baseline.json"
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    results = []

    for case in fixture["cases"]:
        payload = case["rawOutput"]
        normalized = normalize_story_context(raw({**payload}, treatment="A"))
        results.append(normalized["acceptability"]["status"])
        assert normalized["acceptability"]["status"] == case["expected"]

    assert {status: results.count(status) for status in set(results)} == {
        "ACCEPTABLE": 1,
        "BOUNDED": 1,
        "INSUFFICIENT": 3,
        "STALE_OR_UNKNOWN": 1,
        "NOT_EVALUATED": 1,
    }
