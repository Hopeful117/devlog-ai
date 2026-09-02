import ast
import copy
import json
from pathlib import Path

import pytest

from app.schemas.insight import KnowledgeDeltaType
from evaluations.evaluator import evaluate_replay
from evaluations.loader import (
    SCENARIOS_DIR,
    ArtifactValidationError,
    load_replay,
    load_scenario,
    resolve_scenario_directory,
)
from evaluations.models import (
    Correctness,
    EvaluationReplay,
    EvaluationScenario,
    ExecutionStatus,
    GateResult,
    GroundingQuality,
    OverallQuality,
    StructuralValidity,
    TargetCorrectness,
    TrustSafety,
)
from evaluations.runner import format_text_report, main, run_replay


SCENARIO_ID = "architecture-overview-v2-enriches-v1"
TARGET_A = "00000000-0000-0000-0000-000000000301"
TARGET_B = "00000000-0000-0000-0000-000000000302"
FOREIGN_FACT = "00000000-0000-0000-0000-000000000999"


def _canonical_models() -> tuple[EvaluationScenario, EvaluationReplay]:
    scenario = load_scenario(SCENARIO_ID)
    return scenario, load_replay(scenario)


def _scenario_data() -> dict:
    path = SCENARIOS_DIR / SCENARIO_ID / "scenario.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _replay_data() -> dict:
    path = SCENARIOS_DIR / SCENARIO_ID / "replay.json"
    return json.loads(path.read_text(encoding="utf-8"))


def _models_from_data(
    scenario_data: dict,
    replay_data: dict,
) -> tuple[EvaluationScenario, EvaluationReplay]:
    return (
        EvaluationScenario.model_validate(scenario_data),
        EvaluationReplay.model_validate(replay_data),
    )


def _run_artifacts(
    tmp_path: Path,
    *,
    mutate_scenario=None,
    mutate_replay=None,
):
    scenario_data = _scenario_data()
    replay_data = _replay_data()
    if mutate_scenario:
        mutate_scenario(scenario_data)
    if mutate_replay:
        mutate_replay(replay_data)
    scenario_dir = tmp_path / SCENARIO_ID
    scenario_dir.mkdir()
    (scenario_dir / "scenario.json").write_text(
        json.dumps(scenario_data), encoding="utf-8"
    )
    (scenario_dir / "replay.json").write_text(
        json.dumps(replay_data), encoding="utf-8"
    )
    return run_replay(SCENARIO_ID, tmp_path)


def _with_expectation(
    scenario_data: dict,
    *,
    delta: str | None,
    target: str | None,
) -> None:
    scenario_data["expectation"]["expectedDelta"] = delta
    scenario_data["expectation"]["expectedTargetId"] = target


def _without_proposals(replay_data: dict) -> None:
    replay_data["output"]["proposals"] = []
    replay_data["output"]["synthesis"]["deltaConclusion"] = "NO_MATERIAL_DELTA"


def _as_new(replay_data: dict) -> None:
    proposal = replay_data["output"]["proposals"][0]
    proposal["deltaType"] = "NEW"
    proposal.pop("targetInsightId")


def test_canonical_replay_integration_passes() -> None:
    result = run_replay(SCENARIO_ID)

    assert result.execution_status == ExecutionStatus.EVALUATED
    assert result.structural_validity == StructuralValidity.VALID
    assert result.proposal_correctness == Correctness.CORRECT
    assert result.delta_correctness == Correctness.CORRECT
    assert result.target_correctness == TargetCorrectness.CORRECT_TARGET
    assert result.grounding_quality == GroundingQuality.GROUNDED
    assert result.deterministic_grounding_passed is True
    assert result.trust_safety == TrustSafety.SAFE
    assert result.overall_quality == OverallQuality.STRONG
    assert result.gate_result == GateResult.PASSED


def test_same_canonical_replay_produces_identical_result() -> None:
    first = run_replay(SCENARIO_ID).model_dump(by_alias=True, mode="json")
    second = run_replay(SCENARIO_ID).model_dump(by_alias=True, mode="json")
    assert first == second


def test_historical_no_proposal_false_strong_is_blocked() -> None:
    scenario_data = _scenario_data()
    replay_data = _replay_data()
    _without_proposals(replay_data)
    scenario, replay = _models_from_data(scenario_data, replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.proposal_correctness == Correctness.INCORRECT
    assert result.delta_correctness == Correctness.INCORRECT
    assert result.target_correctness == TargetCorrectness.MISSING_REQUIRED_TARGET
    assert result.overall_quality == OverallQuality.WEAK
    assert result.gate_result == GateResult.FAILED
    assert result.counters.incorrect_delta_count == 1
    assert result.counters.incorrect_target_count == 1
    assert result.counters.total_runs == 1


def test_missing_enriches_target_is_structurally_invalid_and_weak(
    tmp_path: Path,
) -> None:
    def remove_target(replay_data: dict) -> None:
        replay_data["output"]["proposals"][0].pop("targetInsightId")

    result = _run_artifacts(tmp_path, mutate_replay=remove_target)

    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.INVALID
    assert result.target_correctness == TargetCorrectness.MISSING_REQUIRED_TARGET
    assert result.overall_quality == OverallQuality.WEAK
    assert result.gate_result == GateResult.FAILED
    assert result.counters.incorrect_target_count == 1


def test_wrong_target_is_weak() -> None:
    scenario_data = _scenario_data()
    replay_data = _replay_data()
    second_target = copy.deepcopy(
        scenario_data["promptRequest"]["selectedKnowledge"][
            "existingArchitectureKnowledge"
        ][0]
    )
    second_target["insightId"] = TARGET_B
    scenario_data["promptRequest"]["selectedKnowledge"][
        "existingArchitectureKnowledge"
    ].append(second_target)
    replay_data["output"]["proposals"][0]["targetInsightId"] = TARGET_B
    scenario, replay = _models_from_data(scenario_data, replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.target_correctness == TargetCorrectness.WRONG_TARGET
    assert result.trust_safety == TrustSafety.SAFE
    assert result.overall_quality == OverallQuality.WEAK
    assert result.counters.incorrect_target_count == 1


def test_expected_absence_without_proposal_is_strong() -> None:
    scenario_data = _scenario_data()
    replay_data = _replay_data()
    _with_expectation(scenario_data, delta=None, target=None)
    _without_proposals(replay_data)
    scenario, replay = _models_from_data(scenario_data, replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.proposal_correctness == Correctness.CORRECT
    assert result.delta_correctness == Correctness.CORRECT
    assert result.target_correctness == TargetCorrectness.TARGET_NOT_APPLICABLE
    assert result.overall_quality == OverallQuality.STRONG


def test_unexpected_proposal_under_expected_absence_is_weak() -> None:
    scenario_data = _scenario_data()
    _with_expectation(scenario_data, delta=None, target=None)
    scenario, replay = _models_from_data(scenario_data, _replay_data())

    result = evaluate_replay(scenario, replay)

    assert result.proposal_correctness == Correctness.INCORRECT
    assert result.delta_correctness == Correctness.INCORRECT
    assert result.overall_quality == OverallQuality.WEAK


def test_new_without_target_is_strong() -> None:
    scenario_data = _scenario_data()
    replay_data = _replay_data()
    _with_expectation(scenario_data, delta="NEW", target=None)
    _as_new(replay_data)
    scenario, replay = _models_from_data(scenario_data, replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.delta_correctness == Correctness.CORRECT
    assert result.target_correctness == TargetCorrectness.TARGET_NOT_APPLICABLE
    assert result.overall_quality == OverallQuality.STRONG


def test_mixed_proposals_are_incorrect() -> None:
    replay_data = _replay_data()
    second = copy.deepcopy(replay_data["output"]["proposals"][0])
    second["deltaType"] = "NEW"
    second.pop("targetInsightId")
    replay_data["output"]["proposals"].append(second)
    scenario, replay = _models_from_data(_scenario_data(), replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.proposal_count == 2
    assert result.delta_correctness == Correctness.INCORRECT
    assert result.overall_quality == OverallQuality.WEAK


@pytest.mark.parametrize(
    ("field", "expected_grounding"),
    [
        ("unsupportedMaterialClaims", GroundingQuality.UNSUPPORTED),
        ("contradictedMaterialClaims", GroundingQuality.CONTRADICTED),
        ("plausibleButUnprovenMaterialClaims", GroundingQuality.UNSUPPORTED),
    ],
)
def test_material_reviewed_claim_failure_is_weak(
    field: str,
    expected_grounding: GroundingQuality,
) -> None:
    replay_data = _replay_data()
    replay_data["qualitativeGrounding"][field] = 1
    scenario, replay = _models_from_data(_scenario_data(), replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.grounding_quality == expected_grounding
    assert result.overall_quality == OverallQuality.WEAK


def test_missing_required_qualitative_review_blocks_strong(tmp_path: Path) -> None:
    def remove_review(replay_data: dict) -> None:
        replay_data["qualitativeGrounding"] = None

    result = _run_artifacts(tmp_path, mutate_replay=remove_review)

    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.NOT_EVALUATED
    assert result.grounding_quality == GroundingQuality.REVIEW_REQUIRED
    assert result.overall_quality == OverallQuality.WEAK


@pytest.mark.parametrize("invalid_value", [True, 1.0])
def test_qualitative_claim_counts_require_strict_integers(
    tmp_path: Path,
    invalid_value,
) -> None:
    def invalidate_review(replay_data: dict) -> None:
        replay_data["qualitativeGrounding"][
            "unsupportedMaterialClaims"
        ] = invalid_value

    result = _run_artifacts(tmp_path, mutate_replay=invalidate_review)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.NOT_EVALUATED


def test_limited_imprecision_is_acceptable_only_for_correct_safe_output() -> None:
    replay_data = _replay_data()
    replay_data["qualitativeGrounding"]["limitedNonCriticalImprecision"] = True
    scenario, replay = _models_from_data(_scenario_data(), replay_data)
    acceptable = evaluate_replay(scenario, replay)
    assert acceptable.grounding_quality == GroundingQuality.LIMITED
    assert acceptable.overall_quality == OverallQuality.ACCEPTABLE

    replay_data["output"]["proposals"][0]["targetInsightId"] = TARGET_B
    _, wrong_replay = _models_from_data(_scenario_data(), replay_data)
    assert evaluate_replay(scenario, wrong_replay).overall_quality == OverallQuality.WEAK


def test_foreign_grounding_reference_is_trust_violation() -> None:
    replay_data = _replay_data()
    replay_data["output"]["proposals"][0]["supportingFactIds"] = [FOREIGN_FACT]
    scenario, replay = _models_from_data(_scenario_data(), replay_data)

    result = evaluate_replay(scenario, replay)

    assert result.deterministic_grounding_passed is False
    assert result.grounding_quality == GroundingQuality.UNSUPPORTED
    assert result.trust_safety == TrustSafety.VIOLATION
    assert result.overall_quality == OverallQuality.WEAK


def test_valid_minimal_scenario_and_replay_load() -> None:
    scenario = load_scenario(SCENARIO_ID)
    replay = load_replay(scenario)
    assert scenario.prompt_request.intent.id == "architecture-overview"
    assert replay.output.proposals[0].delta_type == KnowledgeDeltaType.ENRICHES


def test_phase4_invalid_fixture_is_rejected_before_evaluation(tmp_path: Path) -> None:
    def remove_required_fact(scenario_data: dict) -> None:
        scenario_data["promptRequest"]["selectedKnowledge"]["selectedFacts"].pop()

    result = _run_artifacts(tmp_path, mutate_scenario=remove_required_fact)

    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.NOT_EVALUATED
    assert result.overall_quality != OverallQuality.STRONG
    assert result.gate_result != GateResult.PASSED
    assert any(
        issue.code == "REQUIRED_SELECTED_INPUT_MISSING"
        for issue in result.validation_errors
    )


def test_contradicts_expected_delta_is_rejected(tmp_path: Path) -> None:
    result = _run_artifacts(
        tmp_path,
        mutate_scenario=lambda data: _with_expectation(
            data, delta="CONTRADICTS", target=TARGET_A
        ),
    )
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert any(issue.code == "SCENARIO_SCHEMA_INVALID" for issue in result.validation_errors)


def test_enriches_without_expected_target_is_rejected(tmp_path: Path) -> None:
    result = _run_artifacts(
        tmp_path,
        mutate_scenario=lambda data: _with_expectation(
            data, delta="ENRICHES", target=None
        ),
    )
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


def test_expected_target_must_exist_in_trusted_fixture(tmp_path: Path) -> None:
    def remove_target(scenario_data: dict) -> None:
        scenario_data["promptRequest"]["selectedKnowledge"][
            "existingArchitectureKnowledge"
        ] = []

    result = _run_artifacts(tmp_path, mutate_scenario=remove_target)
    assert any(
        issue.code == "EXPECTED_TARGET_UNRESOLVED" for issue in result.validation_errors
    )


@pytest.mark.parametrize("delta", ["NEW", None])
def test_target_is_rejected_when_not_applicable(tmp_path: Path, delta: str | None) -> None:
    result = _run_artifacts(
        tmp_path,
        mutate_scenario=lambda data: _with_expectation(
            data, delta=delta, target=TARGET_A
        ),
    )
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


def test_unresolved_allowlist_reference_is_rejected(tmp_path: Path) -> None:
    def add_foreign_fact(scenario_data: dict) -> None:
        scenario_data["expectation"]["allowedFactIds"].append(FOREIGN_FACT)

    result = _run_artifacts(tmp_path, mutate_scenario=add_foreign_fact)
    assert any(
        issue.code == "ALLOWED_FACT_UNRESOLVED" for issue in result.validation_errors
    )


def test_scenario_intent_identity_mismatch_is_rejected(tmp_path: Path) -> None:
    def mismatch(scenario_data: dict) -> None:
        scenario_data["intent"]["version"] = "v1"

    result = _run_artifacts(tmp_path, mutate_scenario=mismatch)
    assert any(
        issue.code == "INTENT_IDENTITY_MISMATCH" for issue in result.validation_errors
    )


def test_malformed_prompt_request_is_rejected(tmp_path: Path) -> None:
    def remove_request_id(scenario_data: dict) -> None:
        scenario_data["promptRequest"].pop("requestId")

    result = _run_artifacts(tmp_path, mutate_scenario=remove_request_id)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert any(issue.code == "SCENARIO_SCHEMA_INVALID" for issue in result.validation_errors)


def test_malformed_replay_output_is_structurally_invalid(tmp_path: Path) -> None:
    def remove_summary(replay_data: dict) -> None:
        replay_data["output"]["proposals"][0].pop("summary")

    result = _run_artifacts(tmp_path, mutate_replay=remove_summary)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.INVALID
    assert result.overall_quality == OverallQuality.WEAK


def test_replay_scenario_identity_mismatch_is_rejected(tmp_path: Path) -> None:
    def mismatch(replay_data: dict) -> None:
        replay_data["scenarioVersion"] = "2.0.0"

    result = _run_artifacts(tmp_path, mutate_replay=mismatch)
    assert any(
        issue.code == "REPLAY_SCENARIO_MISMATCH" for issue in result.validation_errors
    )


def test_reproducibility_mismatch_is_rejected(tmp_path: Path) -> None:
    def mismatch(replay_data: dict) -> None:
        replay_data["capture"]["repositoryRevision"] = "different-revision"

    result = _run_artifacts(tmp_path, mutate_replay=mismatch)
    assert any(
        issue.code == "REPRODUCIBILITY_MISMATCH" for issue in result.validation_errors
    )


def test_invalid_capture_metadata_does_not_measure_output(tmp_path: Path) -> None:
    def invalidate_capture(replay_data: dict) -> None:
        replay_data["capture"]["capturedAt"] = "not-a-timestamp"

    result = _run_artifacts(tmp_path, mutate_replay=invalidate_capture)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO
    assert result.structural_validity == StructuralValidity.NOT_EVALUATED
    assert result.proposal_correctness == Correctness.NOT_EVALUATED
    assert result.overall_quality == OverallQuality.NOT_EVALUATED
    assert result.counters.total_runs == 0


def test_expected_output_contract_must_match_intent(tmp_path: Path) -> None:
    def mismatch_contract(scenario_data: dict) -> None:
        scenario_data["promptRequest"]["expectedOutputContract"]["root"] = "other"

    result = _run_artifacts(tmp_path, mutate_scenario=mismatch_contract)
    assert any(
        issue.code == "OUTPUT_CONTRACT_MISMATCH" for issue in result.validation_errors
    )


def test_prompt_digest_must_match_production_prompt(tmp_path: Path) -> None:
    def mismatch_digest(scenario_data: dict) -> None:
        scenario_data["reproducibility"]["promptContentDigest"] = "0" * 64

    result = _run_artifacts(tmp_path, mutate_scenario=mismatch_digest)
    assert any(
        issue.code == "PROMPT_DIGEST_MISMATCH" for issue in result.validation_errors
    )


def test_scenario_version_must_be_semantic(tmp_path: Path) -> None:
    def invalidate_version(scenario_data: dict) -> None:
        scenario_data["scenarioVersion"] = "latest"

    result = _run_artifacts(tmp_path, mutate_scenario=invalidate_version)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


@pytest.mark.parametrize("invalid_value", [True, 1.0])
def test_gate_requires_strict_integers(tmp_path: Path, invalid_value) -> None:
    def invalidate_gate(scenario_data: dict) -> None:
        scenario_data["gate"]["minimumStrong"] = invalid_value

    result = _run_artifacts(tmp_path, mutate_scenario=invalidate_gate)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


def test_gate_minimum_strong_cannot_exceed_runs(tmp_path: Path) -> None:
    def invalidate_gate(scenario_data: dict) -> None:
        scenario_data["gate"]["minimumStrong"] = 2

    result = _run_artifacts(tmp_path, mutate_scenario=invalidate_gate)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


def test_unknown_scenario_field_is_rejected(tmp_path: Path) -> None:
    def add_unknown(scenario_data: dict) -> None:
        scenario_data["unexpected"] = True

    result = _run_artifacts(tmp_path, mutate_scenario=add_unknown)
    assert result.execution_status == ExecutionStatus.INVALID_SCENARIO


def test_path_traversal_is_rejected(tmp_path: Path) -> None:
    with pytest.raises(ArtifactValidationError):
        resolve_scenario_directory("../outside", tmp_path)
    assert run_replay("../outside", tmp_path).execution_status == ExecutionStatus.INVALID_SCENARIO


def test_runner_reports_text_and_exit_codes(capsys) -> None:
    result = run_replay(SCENARIO_ID)
    report = format_text_report(result)
    assert "Deterministic grounding: PASSED" in report
    assert "Reviewed grounding: REVIEWED" in report
    assert "Overall quality: STRONG" in report

    assert main([SCENARIO_ID, "--json"]) == 0
    output = json.loads(capsys.readouterr().out)
    assert output["gateResult"] == "PASSED"

    assert main(["../outside"]) == 1
    assert "INVALID_SCENARIO" in capsys.readouterr().out


def test_production_does_not_import_evaluations() -> None:
    app_root = Path(__file__).parents[1] / "app"
    offenders = []
    for path in app_root.rglob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                names = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom):
                names = [node.module or ""]
            else:
                continue
            if any(name == "evaluations" or name.startswith("evaluations.") for name in names):
                offenders.append(str(path.relative_to(app_root)))
    assert offenders == []


def test_evaluation_has_no_provider_or_network_execution_imports() -> None:
    evaluation_root = Path(__file__).parents[1] / "evaluations"
    forbidden_roots = {"httpx", "openai", "requests", "urllib"}
    offenders = []
    for path in evaluation_root.glob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                names = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom):
                names = [node.module or ""]
            else:
                continue
            if any(name.split(".")[0] in forbidden_roots for name in names):
                offenders.append(str(path.name))
    assert offenders == []


def test_generic_evaluator_contains_no_canonical_answers() -> None:
    evaluator_path = Path(__file__).parents[1] / "evaluations" / "evaluator.py"
    source = evaluator_path.read_text(encoding="utf-8").lower()
    assert "backend" not in source
    assert "ai-engine" not in source
    assert "docker" not in source
    assert "containerization" not in source
    assert TARGET_A not in source


def test_canonical_artifacts_have_no_sensitive_transport_data() -> None:
    combined = json.dumps(_scenario_data()).lower() + json.dumps(_replay_data()).lower()
    for forbidden in (
        "api_key",
        "authorization",
        "bearer ",
        "cookie",
        "hidden_reasoning",
        "raw_response",
    ):
        assert forbidden not in combined
