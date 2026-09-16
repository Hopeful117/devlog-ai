import json
from pathlib import Path

from evaluations.product_value.causal_evaluation import (
    causal_diagnostics,
    evaluate_thresholds,
    remove_causal_evidence_for_question,
)
from evaluations.product_value.causal_mapping import (
    CASE04_QUESTION_ID,
    build_frozen_causal_questions,
    expected_assessment_slots,
    validate_assessment_slots,
)
from evaluations.product_value.loader import apply_frozen_oracle, load_benchmark


ROOT = Path(__file__).parents[2]
BENCHMARK = ROOT / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"
ORACLE = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"


def frozen_benchmark() -> dict:
    benchmark = load_benchmark(BENCHMARK)
    return apply_frozen_oracle(benchmark, json.loads(ORACLE.read_text()))


def test_frozen_mapping_preserves_all_questions_and_human_freeze():
    questions = build_frozen_causal_questions(frozen_benchmark())

    assert [len(questions[case_id]) for case_id in ("CASE-01", "CASE-02", "CASE-03", "CASE-04")] == [5, 3, 2, 1]
    assert sum(len(items) for items in questions.values()) == 11
    assert questions["CASE-01"][0].question_id == "CASE-01::CL-01"
    assert questions["CASE-03"][1].question_id == "CASE-03::CL-10"
    case04 = questions["CASE-04"][0]
    assert case04.question_id == CASE04_QUESTION_ID
    assert case04.source == "ADR-043"
    assert case04.target == "ExecutionConfiguration refactor delivered by Story 0042"
    assert case04.relation_asked == "CAUSAL"
    assert case04.answer_required is True
    assert case04.expected_classification == "NOT_ESTABLISHED"
    assert case04.mapping_authority == "EXPLICIT_HUMAN_FREEZE"
    assert all(
        question.mapping_authority == "DETERMINISTIC_FROM_FROZEN_BENCHMARK"
        for case_id, items in questions.items() if case_id != "CASE-04" for question in items
    )


def test_case_repetition_slots_are_derived_and_relation_level():
    slots = expected_assessment_slots(frozen_benchmark(), 3)

    assert slots == {"CASE-01": 15, "CASE-02": 9, "CASE-03": 6, "CASE-04": 3}
    assert sum(slots.values()) == 33


def test_positive_accuracy_uses_question_assessments_and_excludes_case04():
    runs = [
        {
            "caseId": case_id,
            "questionId": f"{case_id}::Q-{index}",
            "causalReasoningAccuracy": 1.0,
            "groundingValid": True,
            "unsupportedInferenceRate": 0,
        }
        for case_id, count in (("CASE-01", 5), ("CASE-02", 3), ("CASE-03", 2))
        for index in range(count * 3)
    ]
    runs.extend(
        {
            "caseId": "CASE-04",
            "questionId": CASE04_QUESTION_ID,
            "causalReasoningAccuracy": 1.0,
            "actualClassification": "NOT_ESTABLISHED",
            "groundingValid": True,
            "unsupportedInferenceRate": 0,
        }
        for _ in range(3)
    )

    result = evaluate_thresholds(runs, stability=1.0, expected_positive_total=30)

    assert result["positiveCausalAccuracy"] == 1.0
    assert result["positiveCausalAccuracyDenominator"] == 30
    assert result["case04NotEstablishedRuns"] == 3
    assert result["case04Runs"] == 3
    assert result["pass"] is True


def test_positive_accuracy_keeps_frozen_denominator_when_slots_are_invalid():
    result = evaluate_thresholds([
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-01",
            "causalReasoningAccuracy": None,
            "groundingValid": False,
            "unsupportedInferenceRate": None,
        }
    ], stability=0.0, expected_positive_total=30)

    assert result["positiveCausalAccuracy"] == 0.0
    assert result["positiveCausalAccuracyDenominator"] == 30


def test_stability_groups_by_question_not_case():
    runs = [
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-01",
            "causalClaimSignature": [("CASE-01::CL-01", "NOT_ESTABLISHED", ())],
        },
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-01",
            "causalClaimSignature": [("CASE-01::CL-01", "NOT_ESTABLISHED", ())],
        },
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-01",
            "causalClaimSignature": [("CASE-01::CL-01", "NOT_ESTABLISHED", ())],
        },
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-02",
            "causalClaimSignature": [("CASE-01::CL-02", "EXPLICITLY_DOCUMENTED", ())],
        },
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-02",
            "causalClaimSignature": [("CASE-01::CL-02", "STRONGLY_SUPPORTED", ())],
        },
        {
            "caseId": "CASE-01",
            "questionId": "CASE-01::CL-02",
            "causalClaimSignature": [("CASE-01::CL-02", "NOT_ESTABLISHED", ())],
        },
    ]

    diagnostics = causal_diagnostics(runs)

    assert diagnostics["causalClaimStability"] == 0.5


def test_assessment_slots_reject_missing_duplicate_and_wrong_case_records():
    benchmark = frozen_benchmark()
    valid = [
        {"caseId": case_id, "questionId": question.question_id, "repetition": repetition}
        for case_id, questions in build_frozen_causal_questions(benchmark).items()
        for question in questions
        for repetition in range(1, 4)
    ]
    assert validate_assessment_slots(benchmark, valid, 3) == []

    duplicate = valid + [valid[0]]
    errors = validate_assessment_slots(benchmark, duplicate, 3)
    assert any("duplicate assessment slot" in error for error in errors)

    missing = valid[1:]
    errors = validate_assessment_slots(benchmark, missing, 3)
    assert any("missing assessment slot" in error for error in errors)

    wrong_case = [*valid]
    wrong_case[0] = {**wrong_case[0], "caseId": "CASE-02"}
    errors = validate_assessment_slots(benchmark, wrong_case, 3)
    assert any("unknown assessment slot" in error for error in errors)


def test_evidence_removal_is_traceable_to_one_question():
    variant = remove_causal_evidence_for_question(
        {"evidence": [{"reference": "causal"}, {"reference": "other"}]},
        case_id="CASE-01",
        question_id="CASE-01::CL-03",
        references={"causal"},
    )

    assert variant["caseId"] == "CASE-01"
    assert variant["questionId"] == "CASE-01::CL-03"
    assert variant["removedEvidenceReferences"] == ["causal"]
    assert [item["reference"] for item in variant["context"]["evidence"]] == ["other"]
