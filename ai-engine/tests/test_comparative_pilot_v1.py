import hashlib
import json
from pathlib import Path

import pytest

from evaluations.comparative_pilot_v1 import (
    TREATMENTS,
    Observation,
    assignment_matrix,
    build_report,
    condition_input,
    completeness,
    load_manifest,
    pairing,
    validate_manifest,
    validate_observation,
    ProofThresholds,
    evaluate_proof_gate,
)


MANIFEST = Path(__file__).parents[1] / "evaluations/comparative_pilot_v1/pilot-manifest.json"


def _observation(assignment: dict, *, status: str = "COMPLETED", eligible: str = "SEMANTICALLY_ELIGIBLE") -> Observation:
    manifest = load_manifest()
    exact_input = condition_input(assignment["treatment"], assignment["questionId"], assignment["repetition"])
    raw_output = {"answer": "bounded fixture output"}
    return Observation(
        observation_id=f"obs-{assignment['assignmentId']}",
        assignment_id=assignment["assignmentId"],
        question_id=assignment["questionId"],
        question_version=assignment["questionVersion"],
        treatment=assignment["treatment"],
        repetition=assignment["repetition"],
        benchmark_version=manifest["benchmarkVersion"],
        repository_id=manifest["repositoryId"],
        repository_revision=manifest["repositoryRevision"],
        policy_version=f"{assignment['treatment']}-fixture-v1",
        policy_compatibility_key="story0223-three-treatment-pairing-v1",
        oracle_version=manifest["oracleVersion"],
        scoring_contract_version=manifest["scoringContractVersion"],
        model="fixture-model",
        model_configuration={"provider": "fixture"},
        execution_status=status,
        semantic_eligibility=eligible,
        primary_error=None if status == "COMPLETED" else status,
        exact_input=exact_input,
        raw_output=raw_output,
        raw_input_sha256=hashlib.sha256(json.dumps(exact_input, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
        raw_output_sha256=hashlib.sha256(json.dumps(raw_output, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest(),
        validation_results={"isolation": "PASS", "redaction": "PASS"},
        run_id="provider-free-fixture",
        artifact_reference="fixture://story-0223/observation",
    )


def test_manifest_has_accepted_q1_oracle_and_frozen_three_treatment_population() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))

    validate_manifest(manifest)
    assert manifest["oracleStatus"] == "Q1_ORACLE_ACCEPTED"
    assert tuple(manifest["treatments"]) == TREATMENTS
    assert len(manifest["questions"]) == 8
    assert len(assignment_matrix(manifest)) == 72


@pytest.mark.parametrize("treatment", TREATMENTS)
def test_condition_inputs_are_isolated_and_oracle_free(treatment: str) -> None:
    value = condition_input(treatment, "Q1", 1)

    assert value["treatment"] == treatment
    assert value["repositoryRevision"] == load_manifest()["repositoryRevision"]
    assert "expectedEvidence" not in json.dumps(value)
    assert "oracle" not in json.dumps(value).lower()


def test_every_assignment_is_deterministic_and_unique() -> None:
    first = assignment_matrix()
    second = assignment_matrix()

    assert first == second
    assert len({item["assignmentId"] for item in first}) == 72
    assert first[0]["assignmentId"] == "Q1:DEVLOG_CONTEXT:r1"
    assert first[-1]["assignmentId"] == "Q8:STORY_CONTEXT_INVESTIGATION:r3"


def test_completeness_distinguishes_eligible_invalid_and_missing() -> None:
    assignments = assignment_matrix()
    observations = [
        _observation(assignments[0]),
        _observation(assignments[1], status="PROVIDER_INCOMPLETE_RESPONSE", eligible="NOT_EVALUATED"),
    ]

    result = completeness(observations)

    assert result["assignedCount"] == 72
    assert result["executedCount"] == 2
    assert result["eligibleCount"] == 1
    assert result["invalidCount"] == 0
    assert result["missingCount"] == 70
    assert result["states"]["SEMANTIC_ELIGIBLE"] == 1
    assert result["states"]["EXECUTED"] == 1


def test_pairing_reports_complete_and_incomplete_question_repetitions() -> None:
    assignments = assignment_matrix()
    observations = [_observation(assignments[index]) for index in (0, 1, 3, 6)]

    result = pairing(observations)

    assert result["expectedPairCount"] == 24
    assert result["observedPairCount"] == 2
    assert result["completePairCount"] == 1
    assert result["incompletePairCount"] == 1


def test_observation_digest_mismatch_fails_closed() -> None:
    observation = _observation(assignment_matrix()[0]).as_dict()
    observation["rawOutput"] = {"answer": "tampered"}

    with pytest.raises(ValueError, match="raw output digest mismatch"):
        validate_observation(observation)


def test_observation_secret_is_rejected() -> None:
    observation = _observation(assignment_matrix()[0]).as_dict()
    observation["rawOutput"] = {"authorization": "Bearer secret"}
    observation["rawOutputSha256"] = hashlib.sha256(
        json.dumps(observation["rawOutput"], sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()

    with pytest.raises(ValueError, match="secret-like field"):
        validate_observation(observation)


def test_report_is_provisional_until_oracles_are_reviewed() -> None:
    report = build_report([_observation(assignment_matrix()[0])])

    assert report["oracleReviewed"] is False
    assert report["qualityMetricsAvailable"] is False
    assert report["qualityConclusion"] == "NOT_AVAILABLE"
    assert report["completeness"]["executionCompleteness"] == pytest.approx(1 / 72)


def _approved_report() -> dict:
    return {
        "oracleReviewed": True,
        "qualityMetricsAvailable": True,
        "completeness": {"missingCount": 0, "executionCompleteness": 1},
        "pairing": {"incompletePairCount": 0},
    }


def _metrics(value: float) -> dict[str, float]:
    return {metric: value for metric in (
        "groundedCorrectnessRate",
        "evidenceSupportRate",
        "specificityRate",
        "abstentionCorrectnessRate",
    )}


def _thresholds() -> ProofThresholds:
    return ProofThresholds(
        minimum_execution_completeness=1,
        minimum_human_utility=0.7,
        non_inferiority_margin=0.05,
        minimum_advantage_delta=0.1,
        advantage_metric="specificityRate",
    )


def test_proof_gate_fails_closed_without_human_approval_evidence() -> None:
    result = evaluate_proof_gate({}, {}, {}, None)

    assert result["verdict"] == "NOT_PROVEN"
    assert result["closureAllowed"] is False


def test_proof_gate_requires_a_real_advantage_and_human_utility() -> None:
    metrics = {
        "AGENT_DIRECT": _metrics(0.7),
        "DEVLOG_CONTEXT": _metrics(0.8),
        "STORY_CONTEXT_INVESTIGATION": _metrics(0.75),
    }
    metrics["DEVLOG_CONTEXT"]["specificityRate"] = 0.85

    result = evaluate_proof_gate(
        _approved_report(),
        metrics,
        {"DEVLOG_CONTEXT": 0.8, "STORY_CONTEXT_INVESTIGATION": 0.75},
        _thresholds(),
    )

    assert result == {
        "verdict": "PROVEN",
        "closureAllowed": True,
        "reasons": [],
        "baselineTreatment": "AGENT_DIRECT",
        "targetTreatments": ["DEVLOG_CONTEXT", "STORY_CONTEXT_INVESTIGATION"],
    }


def test_proof_gate_rejects_inferior_target_even_with_high_utility() -> None:
    metrics = {
        "AGENT_DIRECT": _metrics(0.8),
        "DEVLOG_CONTEXT": _metrics(0.7),
        "STORY_CONTEXT_INVESTIGATION": _metrics(0.7),
    }

    result = evaluate_proof_gate(
        _approved_report(),
        metrics,
        {"DEVLOG_CONTEXT": 1, "STORY_CONTEXT_INVESTIGATION": 1},
        _thresholds(),
    )

    assert result["verdict"] == "NOT_PROVEN"
    assert any("inferior" in reason for reason in result["reasons"])
