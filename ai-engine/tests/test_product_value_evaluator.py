from pathlib import Path

import pytest

from evaluations.product_value.loader import BenchmarkValidationError, apply_frozen_oracle, load_benchmark, validate_artifact_manifest
from evaluations.product_value.scorer import evaluate_capture


def benchmark():
    return {
        "suiteId": "suite", "suiteVersion": "1", "projectId": "p", "repositoryId": "r",
        "repositoryRevision": "rev", "oracleStatus": "APPROVED", "frozenBeforeImplementation": True,
        "cases": [{
            "caseId": "CASE-01", "scope": {"revision": "rev"}, "expectedEvidence": ["a", "b"],
            "expectedConstraintIds": ["c"], "expectedCausalLinks": [{"id": "l"}],
            "expectedAffectedComponentIds": ["comp"], "expectedAffectedTestIds": ["test"], "negativeCase": False,
        }, {
            "caseId": "CASE-04", "scope": {"revision": "rev"}, "expectedEvidence": ["n"],
            "expectedConstraintIds": [], "expectedCausalLinks": [], "expectedAffectedComponentIds": ["x"],
            "expectedAffectedTestIds": [], "negativeCase": True, "expectedOutcome": "NOT_ESTABLISHED",
        }],
    }


def capture(revision="rev"):
    return {"actualRepositoryRevision": revision, "cases": [
        {"caseId": "CASE-01", "returnedEvidence": ["a", "extra"], "returnedConstraintIds": ["c"],
         "returnedCausalLinkIds": ["l"], "returnedAffectedComponentIds": ["comp"], "returnedAffectedTestIds": ["test"],
         "typedReferencesResolve": True, "groundingValid": True},
        {"caseId": "CASE-04", "returnedEvidence": [], "outcome": "NOT_ESTABLISHED",
         "typedReferencesResolve": True, "groundingValid": True},
    ]}


def test_candidate_oracle_cannot_produce_acceptance_score():
    suite = benchmark()
    suite["oracleStatus"] = "CANDIDATE_REQUIRES_HUMAN_VALIDATION"
    result = evaluate_capture(suite, capture(), oracle_approved=True)
    assert result["status"] == "ORACLE_NOT_APPROVED"
    assert result["acceptanceAuthorized"] is False


def test_revision_mismatch_is_fail_closed():
    result = evaluate_capture(benchmark(), capture("other"), oracle_approved=True)
    assert result["status"] == "REVISION_MISMATCH"


def test_scoring_is_exact_set_based_and_negative_control_is_successful():
    result = evaluate_capture(benchmark(), capture(), oracle_approved=True)
    case = result["cases"][0]
    assert case["evidenceRecall"] == 0.5
    assert case["evidencePrecision"] == 0.5
    assert result["cases"][1]["negativeControl"] is True
    assert result["aiUtilityPass"] is False
    assert result["humanUtilityPass"] is None


def test_manifest_must_match_revision_and_resolve_every_artifact():
    suite = benchmark()
    errors = validate_artifact_manifest(suite, {"repositoryRevision": "other", "resolvedArtifacts": ["a"]})
    assert "artifact manifest revision differs from benchmark revision" in errors
    assert "unresolved expected artifact: b" in errors


def test_invalid_benchmark_is_rejected():
    suite = benchmark()
    suite["cases"][0]["scope"]["revision"] = "other"
    with pytest.raises(BenchmarkValidationError):
        evaluate_capture(suite, capture(), oracle_approved=True)


def test_story_0130_frozen_benchmark_loads_without_mutation():
    path = Path(__file__).parents[2] / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"
    suite = load_benchmark(path)
    assert suite["suiteId"] == "devlog-product-value"
    assert suite["oracleStatus"] == "CANDIDATE_REQUIRES_HUMAN_VALIDATION"
    assert [case["caseId"] for case in suite["cases"]] == ["CASE-01", "CASE-02", "CASE-03", "CASE-04"]


def test_story_0131_frozen_oracle_overlays_without_mutating_benchmark():
    source = benchmark()
    source["oracleStatus"] = "CANDIDATE_REQUIRES_HUMAN_VALIDATION"
    oracle = {
        "oracleVersion": "1", "oracleStatus": "HUMAN_APPROVED", "oracleFrozen": True,
        "explicitApproval": True, "suiteId": "suite", "suiteVersion": "1", "projectId": "p",
        "repositoryId": "r", "repositoryRevision": "rev",
        "causalClassifications": {"l": "STRONGLY_SUPPORTED", "CASE-04": "NOT_ESTABLISHED"},
    }
    frozen = apply_frozen_oracle(source, oracle)
    assert source["oracleStatus"] == "CANDIDATE_REQUIRES_HUMAN_VALIDATION"
    assert frozen["oracleStatus"] == "APPROVED"
    assert frozen["oracleVersion"] == "1"
