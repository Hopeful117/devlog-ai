from typing import Any
from uuid import UUID

from app.schemas.insight import KnowledgeDeltaType

from evaluations.models import (
    Correctness,
    EvaluationCounters,
    EvaluationReplay,
    EvaluationResult,
    EvaluationScenario,
    ExecutionStatus,
    GateResult,
    GroundingQuality,
    OverallQuality,
    StructuralValidity,
    TargetCorrectness,
    TrustSafety,
    ValidationIssue,
)


EVALUATOR_VERSION = "v1"


def evaluate_replay(
    scenario: EvaluationScenario,
    replay: EvaluationReplay,
) -> EvaluationResult:
    proposals = replay.output.proposals
    expected_delta = scenario.expectation.expected_delta
    expected_target = scenario.expectation.expected_target_id
    actual_deltas = [proposal.delta_type for proposal in proposals]
    actual_targets = [proposal.target_insight_id for proposal in proposals]

    proposal_correct = len(proposals) == 0 if expected_delta is None else bool(proposals)
    delta_correct = proposal_correct and (
        expected_delta is None
        or all(delta == expected_delta for delta in actual_deltas)
    )
    target_correctness = _target_correctness(
        expected_delta,
        expected_target,
        actual_targets,
    )

    deterministic_grounding_passed = _deterministic_grounding_passes(
        scenario,
        replay,
    )
    trust_safety = (
        TrustSafety.SAFE
        if deterministic_grounding_passed
        else TrustSafety.VIOLATION
    )
    grounding_quality = _grounding_quality(
        scenario,
        replay,
        deterministic_grounding_passed,
    )
    overall_quality = _overall_quality(
        structural_validity=StructuralValidity.VALID,
        proposal_correct=proposal_correct,
        delta_correct=delta_correct,
        target_correctness=target_correctness,
        grounding_quality=grounding_quality,
        trust_safety=trust_safety,
    )
    counters = _counters(
        overall_quality,
        delta_correct,
        expected_delta,
        target_correctness,
        trust_safety,
    )
    gate_result = _gate_result(scenario, counters)

    return EvaluationResult(
        scenario_id=scenario.scenario_id,
        scenario_version=scenario.scenario_version,
        evaluator_version=EVALUATOR_VERSION,
        execution_status=ExecutionStatus.EVALUATED,
        validation_errors=[],
        proposal_count=len(proposals),
        expected_delta=expected_delta,
        actual_deltas=actual_deltas,
        expected_target_id=expected_target,
        actual_target_ids=actual_targets,
        structural_validity=StructuralValidity.VALID,
        proposal_correctness=(
            Correctness.CORRECT if proposal_correct else Correctness.INCORRECT
        ),
        delta_correctness=(
            Correctness.CORRECT if delta_correct else Correctness.INCORRECT
        ),
        target_correctness=target_correctness,
        grounding_quality=grounding_quality,
        deterministic_grounding_passed=deterministic_grounding_passed,
        qualitative_grounding_status=(
            replay.qualitative_grounding.status
            if replay.qualitative_grounding is not None
            else None
        ),
        trust_safety=trust_safety,
        overall_quality=overall_quality,
        gate_result=gate_result,
        counters=counters,
    )


def invalid_artifact_result(
    issues: list[ValidationIssue],
    *,
    scenario: EvaluationScenario | None = None,
    raw_replay: dict[str, Any] | None = None,
    scenario_id: str = "",
) -> EvaluationResult:
    if scenario is None or raw_replay is None:
        review_required = any(
            issue.code == "QUALITATIVE_REVIEW_REQUIRED" for issue in issues
        )
        return EvaluationResult(
            scenario_id=scenario.scenario_id if scenario else scenario_id,
            scenario_version=scenario.scenario_version if scenario else "",
            evaluator_version=EVALUATOR_VERSION,
            execution_status=ExecutionStatus.INVALID_SCENARIO,
            validation_errors=issues,
            proposal_count=0,
            expected_delta=(scenario.expectation.expected_delta if scenario else None),
            actual_deltas=[],
            expected_target_id=(
                scenario.expectation.expected_target_id if scenario else None
            ),
            actual_target_ids=[],
            structural_validity=StructuralValidity.NOT_EVALUATED,
            proposal_correctness=Correctness.NOT_EVALUATED,
            delta_correctness=Correctness.NOT_EVALUATED,
            target_correctness=TargetCorrectness.NOT_EVALUATED,
            grounding_quality=(
                GroundingQuality.REVIEW_REQUIRED
                if review_required
                else GroundingQuality.NOT_EVALUATED
            ),
            deterministic_grounding_passed=None,
            qualitative_grounding_status=None,
            trust_safety=TrustSafety.NOT_EVALUATED,
            overall_quality=(
                OverallQuality.WEAK
                if review_required
                else OverallQuality.NOT_EVALUATED
            ),
            gate_result=GateResult.FAILED,
            counters=_empty_counters(),
        )

    raw_output = raw_replay.get("output")
    raw_proposals = raw_output.get("proposals") if isinstance(raw_output, dict) else None
    if not isinstance(raw_proposals, list):
        return invalid_artifact_result(
            issues,
            scenario=scenario,
            scenario_id=scenario_id,
        )

    actual_deltas = _valid_raw_deltas(raw_proposals)
    actual_targets = _valid_raw_targets(raw_proposals)
    expected_delta = scenario.expectation.expected_delta
    proposal_correct = len(raw_proposals) == 0 if expected_delta is None else bool(raw_proposals)
    delta_correct = (
        proposal_correct
        and len(actual_deltas) == len(raw_proposals)
        and (
            expected_delta is None
            or all(delta == expected_delta for delta in actual_deltas)
        )
    )
    target_correctness = _target_correctness(
        expected_delta,
        scenario.expectation.expected_target_id,
        actual_targets,
    )
    counters = _counters(
        OverallQuality.WEAK,
        delta_correct,
        expected_delta,
        target_correctness,
        TrustSafety.NOT_EVALUATED,
    )

    return EvaluationResult(
        scenario_id=scenario.scenario_id,
        scenario_version=scenario.scenario_version,
        evaluator_version=EVALUATOR_VERSION,
        execution_status=ExecutionStatus.INVALID_SCENARIO,
        validation_errors=issues,
        proposal_count=len(raw_proposals),
        expected_delta=expected_delta,
        actual_deltas=actual_deltas,
        expected_target_id=scenario.expectation.expected_target_id,
        actual_target_ids=actual_targets,
        structural_validity=StructuralValidity.INVALID,
        proposal_correctness=(
            Correctness.CORRECT if proposal_correct else Correctness.INCORRECT
        ),
        delta_correctness=(
            Correctness.CORRECT if delta_correct else Correctness.INCORRECT
        ),
        target_correctness=target_correctness,
        grounding_quality=GroundingQuality.NOT_EVALUATED,
        deterministic_grounding_passed=None,
        qualitative_grounding_status=None,
        trust_safety=TrustSafety.NOT_EVALUATED,
        overall_quality=OverallQuality.WEAK,
        gate_result=GateResult.FAILED,
        counters=counters,
    )


def _target_correctness(
    expected_delta: KnowledgeDeltaType | None,
    expected_target: UUID | None,
    actual_targets: list[UUID | None],
) -> TargetCorrectness:
    if expected_delta != KnowledgeDeltaType.ENRICHES:
        return TargetCorrectness.TARGET_NOT_APPLICABLE
    if not actual_targets or any(target is None for target in actual_targets):
        return TargetCorrectness.MISSING_REQUIRED_TARGET
    if any(target != expected_target for target in actual_targets):
        return TargetCorrectness.WRONG_TARGET
    return TargetCorrectness.CORRECT_TARGET


def _deterministic_grounding_passes(
    scenario: EvaluationScenario,
    replay: EvaluationReplay,
) -> bool:
    expectation = scenario.expectation
    allowed_facts = set(expectation.allowed_fact_ids)
    allowed_observations = set(expectation.allowed_observation_ids)
    allowed_evidence = set(expectation.allowed_evidence_references)
    trusted_target_ids = _trusted_target_ids(scenario)

    for proposal in replay.output.proposals:
        if not set(proposal.supporting_fact_ids).issubset(allowed_facts):
            return False
        if not set(proposal.supporting_observation_ids).issubset(allowed_observations):
            return False
        if not set(proposal.evidence_references).issubset(allowed_evidence):
            return False
        if (
            proposal.target_insight_id is not None
            and proposal.target_insight_id not in trusted_target_ids
        ):
            return False

    synthesis = replay.output.synthesis
    return synthesis is None or set(synthesis.grounding_references).issubset(
        allowed_evidence
    )


def _trusted_target_ids(scenario: EvaluationScenario) -> set[UUID]:
    values: set[UUID] = set()
    items = scenario.prompt_request.selected_knowledge.get(
        "existingArchitectureKnowledge", []
    )
    if not isinstance(items, list):
        return values
    for item in items:
        if not isinstance(item, dict):
            continue
        try:
            values.add(UUID(str(item.get("insightId"))))
        except (TypeError, ValueError):
            continue
    return values


def _grounding_quality(
    scenario: EvaluationScenario,
    replay: EvaluationReplay,
    deterministic_grounding_passed: bool,
) -> GroundingQuality:
    if not deterministic_grounding_passed:
        return GroundingQuality.UNSUPPORTED

    review = replay.qualitative_grounding
    if review is None:
        return (
            GroundingQuality.REVIEW_REQUIRED
            if scenario.expectation.qualitative_grounding_required
            else GroundingQuality.GROUNDED
        )
    if review.contradicted_material_claims:
        return GroundingQuality.CONTRADICTED
    if (
        review.unsupported_material_claims
        or review.plausible_but_unproven_material_claims
    ):
        return GroundingQuality.UNSUPPORTED
    if review.limited_non_critical_imprecision:
        return GroundingQuality.LIMITED
    return GroundingQuality.GROUNDED


def _overall_quality(
    *,
    structural_validity: StructuralValidity,
    proposal_correct: bool,
    delta_correct: bool,
    target_correctness: TargetCorrectness,
    grounding_quality: GroundingQuality,
    trust_safety: TrustSafety,
) -> OverallQuality:
    semantics_correct = (
        structural_validity == StructuralValidity.VALID
        and proposal_correct
        and delta_correct
        and target_correctness
        in {
            TargetCorrectness.CORRECT_TARGET,
            TargetCorrectness.TARGET_NOT_APPLICABLE,
        }
        and trust_safety == TrustSafety.SAFE
    )
    if not semantics_correct:
        return OverallQuality.WEAK
    if grounding_quality == GroundingQuality.GROUNDED:
        return OverallQuality.STRONG
    if grounding_quality == GroundingQuality.LIMITED:
        return OverallQuality.ACCEPTABLE
    return OverallQuality.WEAK


def _counters(
    quality: OverallQuality,
    delta_correct: bool,
    expected_delta: KnowledgeDeltaType | None,
    target_correctness: TargetCorrectness,
    trust_safety: TrustSafety,
) -> EvaluationCounters:
    return EvaluationCounters(
        total_runs=1,
        strong_count=int(quality == OverallQuality.STRONG),
        acceptable_count=int(quality == OverallQuality.ACCEPTABLE),
        weak_count=int(quality == OverallQuality.WEAK),
        incorrect_delta_count=int(not delta_correct),
        incorrect_target_count=int(
            expected_delta == KnowledgeDeltaType.ENRICHES
            and target_correctness != TargetCorrectness.CORRECT_TARGET
        ),
        trust_violation_count=int(trust_safety == TrustSafety.VIOLATION),
        execution_failure_count=0,
    )


def _empty_counters() -> EvaluationCounters:
    return EvaluationCounters(
        total_runs=0,
        strong_count=0,
        acceptable_count=0,
        weak_count=0,
        incorrect_delta_count=0,
        incorrect_target_count=0,
        trust_violation_count=0,
        execution_failure_count=0,
    )


def _gate_result(
    scenario: EvaluationScenario,
    counters: EvaluationCounters,
) -> GateResult:
    gate = scenario.gate
    passed = (
        counters.total_runs == gate.required_runs
        and counters.strong_count >= gate.minimum_strong
        and counters.incorrect_delta_count <= gate.maximum_incorrect_delta
        and counters.incorrect_target_count <= gate.maximum_incorrect_target
        and counters.trust_violation_count <= gate.maximum_trust_violations
        and counters.execution_failure_count <= gate.maximum_execution_failures
    )
    return GateResult.PASSED if passed else GateResult.FAILED


def _valid_raw_deltas(raw_proposals: list[Any]) -> list[KnowledgeDeltaType]:
    deltas: list[KnowledgeDeltaType] = []
    for proposal in raw_proposals:
        if not isinstance(proposal, dict):
            continue
        try:
            deltas.append(KnowledgeDeltaType(proposal.get("deltaType")))
        except (TypeError, ValueError):
            continue
    return deltas


def _valid_raw_targets(raw_proposals: list[Any]) -> list[UUID | None]:
    targets: list[UUID | None] = []
    for proposal in raw_proposals:
        if not isinstance(proposal, dict):
            continue
        value = proposal.get("targetInsightId")
        if value is None:
            targets.append(None)
            continue
        try:
            targets.append(UUID(str(value)))
        except ValueError:
            targets.append(None)
    return targets
