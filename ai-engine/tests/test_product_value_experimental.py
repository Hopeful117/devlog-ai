from evaluations.product_value.experimental import (
    EvaluationCondition,
    build_ground_truth_context,
    serialize_context,
    validate_evidence_only_context,
    validate_comparable_metadata,
    validate_three_condition_capture,
)
from evaluations.product_value.scorer import evaluate_experimental_capture


def suite():
    return {
        "suiteId": "suite", "suiteVersion": "1", "project": "p", "projectId": "p", "repositoryId": "r",
        "repositoryRevision": "rev", "oracleStatus": "APPROVED", "frozenBeforeImplementation": True,
        "cases": [{
            "caseId": "CASE-04", "scope": {"revision": "rev"}, "question": "Did A cause B?",
            "expectedEvidence": ["a", "b"], "expectedConstraints": ["keep local authority"],
            "expectedConstraintIds": ["C-1"], "expectedCausalLinks": [],
            "expectedAffectedComponents": ["component"], "expectedAffectedComponentIds": ["COMP-1"],
            "expectedAffectedTests": ["test"], "expectedAffectedTestIds": ["TEST-1"],
            "negativeCase": True, "expectedOutcome": "NOT_ESTABLISHED",
        }],
    }


def condition_case(evidence, classification):
    return {
        "caseId": "CASE-04",
        "context": {"condition": "synthetic", "evidence": [{"reference": item} for item in evidence]},
        "interpretation": {
            "causalClaims": [{"claimId": "claim-1", "classification": classification, "evidenceRefs": evidence}],
            "constraints": [], "affectedComponents": [], "affectedTests": [],
        },
    }


def capture(devlog_evidence=("a",), devlog_classification="NOT_ESTABLISHED"):
    metadata = {
        "provider": "synthetic", "model": "synthetic-v1", "settings": {},
        "systemInstructions": "interpret engineering evidence", "taskPrompt": "answer the question",
        "contextSize": 2, "runTimestamp": "2026-09-15T00:00:00Z",
    }
    return {
        "actualRepositoryRevision": "rev",
        "conditions": {
            EvaluationCondition.DIRECT_REPOSITORY.value: {"metadata": metadata, "cases": [condition_case(["a", "b"], "NOT_ESTABLISHED")]},
            EvaluationCondition.DEVLOG_CONTEXT.value: {"metadata": metadata, "cases": [condition_case(list(devlog_evidence), devlog_classification)]},
            EvaluationCondition.GROUND_TRUTH_CONTEXT.value: {"metadata": metadata, "cases": [condition_case(["a", "b"], "NOT_ESTABLISHED")]},
        },
    }


def test_ground_truth_context_contains_evidence_but_no_answer_key():
    context = build_ground_truth_context(suite(), suite()["cases"][0], [{"reference": "a", "content": "ADR text"}])
    serialized = serialize_context(context)
    assert '"expected' not in serialized
    assert "NOT_ESTABLISHED" not in serialized
    assert "a" in serialized
    assert validate_evidence_only_context(context) == []


def test_answer_key_fields_thresholds_and_scoring_ids_are_rejected():
    context = {"evidence": [{"reference": "a", "content": "text"}], "oracle": "NOT_ESTABLISHED"}
    errors = validate_evidence_only_context(context)
    assert any("answer-key" in error or "oracle" in error for error in errors)
    assert validate_evidence_only_context({"evidence": [{"reference": "a", "content": "text"}], "threshold": 0.8})
    assert validate_evidence_only_context({"evidence": [{"reference": "a", "content": "text"}], "reference": "CL-01"})


def test_three_conditions_and_interpretation_attribution_are_separate():
    result = evaluate_experimental_capture(suite(), capture(), oracle_approved=True)
    assert result["status"] == "SCORED_EXPERIMENTAL"
    assert result["conditions"]["DEVLOG_CONTEXT"]["cases"][0]["contextEvidenceRecall"] == 0.5
    assert result["conditions"]["DEVLOG_CONTEXT"]["cases"][0]["failureAttribution"] == "CONTEXT_FAILURE"
    assert result["conditions"]["GROUND_TRUTH_CONTEXT"]["cases"][0]["causalReasoningAccuracy"] == 1.0


def test_present_complete_evidence_with_wrong_causal_answer_is_interpretation_failure():
    result = evaluate_experimental_capture(
        suite(), capture(devlog_evidence=("a", "b"), devlog_classification="STRONGLY_SUPPORTED"), oracle_approved=True
    )
    case = result["conditions"]["DEVLOG_CONTEXT"]["cases"][0]
    assert case["contextEvidenceRecall"] == 1.0
    assert case["causalReasoningAccuracy"] == 0.0
    assert case["failureAttribution"] == "INTERPRETATION_FAILURE"


def test_incomplete_condition_matrix_is_blocked():
    errors = validate_three_condition_capture({"conditions": {"DEVLOG_CONTEXT": {"cases": []}}})
    assert errors


def test_condition_metadata_must_be_comparable():
    capture_data = capture()
    del capture_data["conditions"]["DEVLOG_CONTEXT"]["metadata"]["model"]
    assert validate_comparable_metadata(capture_data)


def test_constraint_and_impact_interpretation_are_only_scored_when_support_map_exists():
    capture_data = capture(devlog_evidence=("a", "b"))
    capture_data["conditions"]["DEVLOG_CONTEXT"]["cases"][0]["interpretation"]["constraints"] = ["keep local authority"]
    result = evaluate_experimental_capture(
        suite(),
        capture_data,
        oracle_approved=True,
        constraint_support={"CASE-04": {"keep local authority": ["a"]}},
        impact_support={"CASE-04": {"component": ["b"], "test": ["b"]}},
    )
    case = result["conditions"]["DEVLOG_CONTEXT"]["cases"][0]
    assert case["contextConstraintRecall"] == 1.0
    assert case["interpretationConstraintRecall"] == 1.0
    assert case["contextImpactRecall"] == 1.0
    assert case["interpretationImpactRecall"] == 0.0


def test_candidate_oracle_blocks_all_three_condition_scoring():
    candidate = suite()
    candidate["oracleStatus"] = "CANDIDATE_REQUIRES_HUMAN_VALIDATION"
    assert evaluate_experimental_capture(candidate, capture(), oracle_approved=True)["status"] == "ORACLE_NOT_APPROVED"


def test_free_form_unstructured_claims_are_not_scored_as_unsupported_inference():
    from evaluations.product_value.interpretation import score_interpretation

    result = score_interpretation(
        suite()["cases"][0],
        {"evidence": [{"reference": "a"}, {"reference": "b"}]},
        {"summary": "The evidence suggests a conclusion."},
    )
    assert result["unsupportedInferenceMeasurement"] == "NOT_MEASURED"
    assert result["unsupportedInferenceRate"] is None


def test_production_causal_result_adapter_does_not_change_model_context():
    from evaluations.product_value.interpretation import adapt_production_story_context_result

    case = suite()["cases"][0]
    adapted = adapt_production_story_context_result(case, {
        "causalClaims": [{
            "source": "A", "target": "B",
            "causalClassification": "NOT_ESTABLISHED",
            "evidenceReferences": [],
            "explanation": "Chronology is insufficient.",
        }]
    })
    assert adapted["causalClaims"][0]["claimId"] == "claim-1"
    assert "expectedOutcome" not in adapted
