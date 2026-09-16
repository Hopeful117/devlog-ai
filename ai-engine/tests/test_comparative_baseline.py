import copy
import json
from pathlib import Path

import pytest

from evaluations.comparative_baseline.infrastructure import (
    ERROR_TAXONOMY,
    MISSING_STATES,
    assignment_matrix,
    assert_official_baseline_eligible,
    build_raw_artifact,
    canonical,
    classify_historical_case01,
    compare_pair,
    completeness,
    condition_input,
    load_manifest,
    load_policies,
    manifest_identity,
    policy_identities,
    primary_outcome_snapshot,
    raw_output_hash,
    validate_manifest,
    validate_raw_artifact,
    validate_raw_observation,
    write_immutable_json,
)


ROOT = Path(__file__).resolve().parents[2]


def _observation(condition="DEVLOG", question_id="CASE-03", repetition=1, **overrides):
    manifest = load_manifest()
    source_question_id = question_id if question_id in {item["questionId"] for item in manifest["questions"]} else "CASE-03"
    source_condition = condition if condition in manifest["conditionPolicyVersions"] else "DEVLOG"
    question = next(item for item in manifest["questions"] if item["questionId"] == source_question_id)
    raw_output = {"answer": "fixture", "references": []}
    observation = {
        "observationId": f"obs-{condition}-{question_id}-{repetition}",
        "benchmarkVersion": manifest["benchmarkVersion"],
        "questionId": question_id,
        "questionVersion": question["questionVersion"],
        "caseId": question["caseId"],
        "condition": condition,
        "conditionPolicyVersion": manifest["conditionPolicyVersions"][source_condition],
        "conditionPolicyCompatibilityKey": "story0133-ai-pairing-policy-1.0.0",
        "repetition": repetition,
        "repositoryId": manifest["repositoryId"],
        "repositoryRevision": manifest["repositoryRevision"],
        "oracleVersion": manifest["oracleVersion"],
        "scoringContractVersion": manifest["scoringContractVersion"],
        "model": "fixture-model",
        "modelConfiguration": {"temperature": 0},
        "executionStatus": "EXECUTED",
        "semanticOutcome": "YES",
        "primaryError": "CORRECT_GROUNDED",
        "exactInput": condition_input(source_condition, source_question_id, repetition),
        "rawOutput": raw_output,
        "rawOutputSha256": raw_output_hash(raw_output),
        "validationResults": {"structural": "PASS"},
        "runId": "fixture-run",
        "artifactReference": "fixture.json",
    }
    observation.update(overrides)
    return observation


def test_manifest_freezes_three_questions_and_eighteen_assignments():
    manifest = load_manifest()
    validate_manifest(manifest)
    assignments = assignment_matrix(manifest)
    assert len(assignments) == 18
    assert {(item["questionId"], item["condition"], item["repetition"]) for item in assignments}.__len__() == 18
    assert manifest_identity(manifest)["repositoryRevision"] == "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"


def test_question_identities_and_case01_adaptation_are_frozen():
    questions = {item["questionId"]: item for item in load_manifest()["questions"]}
    case01 = questions["CASE-01-COMPARATIVE"]
    assert case01["questionVersion"] == "1.0.0"
    assert case01["category"] == "CAUSAL_RELATIONSHIP"
    assert case01["adaptation"] == {
        "originalQuestionId": "CASE-01",
        "originalQuestionVersion": "1.0.0",
        "mappingClaimId": "CL-01",
        "oracleCompatibility": "UNCHANGED",
        "humanApproval": "YES",
    }
    assert case01["question"] == (
        "Does the available evidence establish a causal relationship between ADR-042 "
        "and Story-0039's persisted account identity and explicit PAPER provisioning?"
    )


def test_condition_policies_are_distinct_and_human_is_non_executable():
    policies = load_policies()
    identities = policy_identities()
    assert set(policies) == {"DEVLOG", "AGENT_DIRECT", "HUMAN_DIRECT"}
    assert len({item["policySha256"] for item in identities.values()}) == 3
    assert policies["DEVLOG"]["repositoryAccess"] == "NONE"
    assert policies["AGENT_DIRECT"]["repositoryAccess"] == "READ_ONLY_PINNED_REVISION"
    assert policies["HUMAN_DIRECT"]["mode"] == "DESIGNED_NOT_EXECUTABLE"


@pytest.mark.parametrize("condition", ["DEVLOG", "AGENT_DIRECT"])
def test_condition_input_excludes_oracle_and_other_condition_data(condition):
    payload = condition_input(condition, "CASE-01-COMPARATIVE", 1)
    serialized = canonical(payload)
    assert "STRONGLY_SUPPORTED" not in serialized
    assert "expectedEvidence" not in serialized
    assert "expectedClassification" not in serialized
    assert "DIRECT_REQUIRED" not in serialized
    assert "AGENT_DIRECT" not in serialized if condition == "DEVLOG" else "DEVLOG" not in serialized


def test_condition_input_rejects_non_executable_human_condition():
    with pytest.raises(ValueError, match="not executable"):
        condition_input("HUMAN_DIRECT", "CASE-03", 1)


def test_case01_evidence_subset_invariants_are_frozen():
    case01 = next(item for item in load_manifest()["questions"] if item["questionId"] == "CASE-01-COMPARATIVE")
    assert len(case01["authorizedEvidence"]) == 14
    assert len(case01["providerVisibleEvidence"]) == 4
    assert len(case01["expectedGroundingEvidence"]) == 2
    assert set(case01["expectedGroundingEvidence"]).issubset(case01["providerVisibleEvidence"])
    assert set(case01["providerVisibleEvidence"]).issubset(case01["authorizedEvidence"])


def test_valid_raw_observations_and_missing_states_are_distinct():
    valid = _observation()
    validate_raw_observation(valid)
    assert len(MISSING_STATES) == 5
    assert 0 != "NOT_MEASURED"
    invalid = copy.deepcopy(valid)
    invalid["executionStatus"] = "INVALID"
    invalid["semanticOutcome"] = "NOT_EVALUATED"
    invalid["primaryError"] = "INFRASTRUCTURE_FAILURE"
    invalid["rawOutput"] = "NOT_AVAILABLE"
    invalid["rawOutputSha256"] = "NOT_APPLICABLE"
    validate_raw_observation(invalid)


def test_invalid_condition_question_and_revision_fail_closed():
    for overrides, message in [
        ({"condition": "UNKNOWN"}, "invalid condition"),
        ({"questionId": "CASE-99"}, "question identity"),
        ({"repositoryRevision": "other"}, "repository identity"),
    ]:
        candidate = _observation(**overrides)
        with pytest.raises(ValueError, match=message):
            validate_raw_observation(candidate)


@pytest.mark.parametrize("field", ["model", "modelConfiguration", "repetition", "conditionPolicyCompatibilityKey", "oracleVersion", "scoringContractVersion"])
def test_pairing_mismatch_fails_closed(field):
    left = _observation("DEVLOG")
    right = _observation("AGENT_DIRECT")
    if field == "model":
        left["model"] = "model-a"
        right["model"] = "model-b"
    elif field == "modelConfiguration":
        left["modelConfiguration"] = {"temperature": 0}
        right["modelConfiguration"] = {"temperature": 1}
    elif field == "repetition":
        right = _observation("AGENT_DIRECT", repetition=2)
    elif field == "conditionPolicyCompatibilityKey":
        right["conditionPolicyCompatibilityKey"] = "other"
    elif field == "oracleVersion":
        right["oracleVersion"] = "other"
    else:
        right["scoringContractVersion"] = "other"
    result = compare_pair(left, right)
    assert result["status"] == "INVALID_COMPARISON"
    assert result["reasons"]


def test_compatible_pair_is_reported_without_dropping_identity():
    left = _observation("DEVLOG")
    right = _observation("AGENT_DIRECT")
    left["model"] = right["model"] = "fixture-model"
    left["modelConfiguration"] = right["modelConfiguration"] = {"temperature": 0}
    result = compare_pair(left, right)
    assert result["status"] == "COMPATIBLE"
    assert result["pairingKey"]


def test_completeness_distinguishes_missing_invalid_executed_and_eligible():
    observations = [
        _observation("DEVLOG", repetition=1),
        _observation("AGENT_DIRECT", repetition=1),
    ]
    observations[0]["semanticOutcome"] = "NOT_EVALUATED"
    observations[0]["primaryError"] = "EVIDENCE_EXCERPT_MISMATCH"
    invalid = _observation("DEVLOG", question_id="CASE-03", repetition=2)
    invalid["executionStatus"] = "INVALID"
    invalid["semanticOutcome"] = "NOT_EVALUATED"
    invalid["primaryError"] = "INFRASTRUCTURE_FAILURE"
    invalid["rawOutput"] = "NOT_APPLICABLE"
    invalid["rawOutputSha256"] = "NOT_APPLICABLE"
    observations.append(invalid)
    view = completeness(observations)
    assert view["assignedObservationCount"] == 18
    assert view["executedObservationCount"] == 2
    assert view["semanticEligibleCount"] == 1
    assert view["invalidObservationCount"] == 1
    assert view["missingObservationCount"] == 15
    assert view["pairedCompleteCount"] == 1
    assert view["pairedIncompleteCount"] == 8
    assert view["executionCompleteness"] == 1 / 18


def test_infrastructure_failure_is_not_semantic_failure_and_response_failure_is_captured():
    infrastructure = _observation(executionStatus="INVALID", semanticOutcome="NOT_EVALUATED", primaryError="INFRASTRUCTURE_FAILURE", rawOutput="NOT_APPLICABLE", rawOutputSha256="NOT_APPLICABLE")
    validate_raw_observation(infrastructure)
    response_failure = _observation(semanticOutcome="NOT_EVALUATED", primaryError="EVIDENCE_EXCERPT_MISMATCH")
    validate_raw_observation(response_failure)
    assert response_failure["executionStatus"] == "EXECUTED"


def test_historical_case01_is_excluded_from_official_baseline():
    historical = classify_historical_case01("v3/design-c-smoke/historical.json")
    assert historical["classification"] == "HISTORICAL_PRE_BASELINE"
    assert historical["baselineEligible"] is False
    with pytest.raises(ValueError, match="historical observation"):
        assert_official_baseline_eligible({"historicalClassification": "HISTORICAL_PRE_BASELINE"})


def test_raw_artifact_hash_is_write_once_and_tamper_evident(tmp_path):
    artifact = build_raw_artifact(_observation())
    validate_raw_artifact(artifact)
    path = tmp_path / "observation.json"
    write_immutable_json(path, artifact)
    with pytest.raises(FileExistsError):
        write_immutable_json(path, artifact)
    tampered = copy.deepcopy(artifact)
    tampered["observation"]["rawOutput"]["answer"] = "changed"
    with pytest.raises(ValueError, match="hash mismatch"):
        validate_raw_artifact(tampered)


def test_primary_outcome_cannot_be_reported_without_completeness_fields():
    with pytest.raises(ValueError, match="completeness"):
        primary_outcome_snapshot([], {"semanticEligibleCount": 0})
    view = completeness([_observation()])
    snapshot = primary_outcome_snapshot(
        [{"semanticEligible": True, "semanticCorrect": True, "groundingValid": True, "structuralValid": True}],
        view,
    )
    assert snapshot["assignedObservationCount"] == 18
    assert snapshot["correctGroundedAnswerRate"] == 1.0
    assert snapshot["executionCompleteness"] == 1 / 18


def test_error_taxonomy_is_frozen():
    assert ERROR_TAXONOMY == {
        "CORRECT_GROUNDED", "CORRECT_POOR_GROUNDING", "WRONG_RELATIONSHIP",
        "OVERCLAIM", "FALSE_ABSTENTION", "FAILED_TO_ABSTAIN", "FABRICATED_EVIDENCE",
        "EVIDENCE_EXCERPT_MISMATCH", "UNAUTHORIZED_REFERENCE", "STRUCTURAL_FAILURE",
        "INFRASTRUCTURE_FAILURE", "INVALID_COMPARISON", "NOT_EVALUATED",
    }
