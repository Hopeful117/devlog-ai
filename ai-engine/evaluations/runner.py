import argparse
import json
from pathlib import Path
from typing import Sequence

from evaluations.evaluator import evaluate_replay, invalid_artifact_result
from evaluations.loader import (
    SCENARIOS_DIR,
    ArtifactValidationError,
    load_replay,
    load_scenario,
)
from evaluations.models import EvaluationResult, GateResult


def run_replay(
    scenario_id: str,
    scenarios_root: Path = SCENARIOS_DIR,
) -> EvaluationResult:
    try:
        scenario = load_scenario(scenario_id, scenarios_root)
    except ArtifactValidationError as exc:
        return invalid_artifact_result(
            exc.issues,
            scenario=exc.scenario,
            scenario_id=scenario_id,
        )

    try:
        replay = load_replay(scenario, scenarios_root)
    except ArtifactValidationError as exc:
        return invalid_artifact_result(
            exc.issues,
            scenario=scenario,
            raw_replay=(exc.raw_artifact if exc.output_contract_invalid else None),
        )
    return evaluate_replay(scenario, replay)


def format_text_report(result: EvaluationResult) -> str:
    expected_delta = result.expected_delta.value if result.expected_delta else "null"
    actual_deltas = ", ".join(value.value for value in result.actual_deltas) or "none"
    expected_target = str(result.expected_target_id) if result.expected_target_id else "n/a"
    actual_targets = ", ".join(
        str(value) if value is not None else "missing"
        for value in result.actual_target_ids
    ) or "none"
    deterministic = (
        "NOT_EVALUATED"
        if result.deterministic_grounding_passed is None
        else ("PASSED" if result.deterministic_grounding_passed else "FAILED")
    )
    reviewed = (
        result.qualitative_grounding_status.value
        if result.qualitative_grounding_status
        else "NOT_PRESENT"
    )
    lines = [
        f"Scenario: {result.scenario_id} / {result.scenario_version}",
        f"Execution status: {result.execution_status.value}",
        f"Proposal count: {result.proposal_count}",
        f"Expected delta: {expected_delta}",
        f"Actual deltas: {actual_deltas}",
        f"Expected target: {expected_target}",
        f"Actual targets: {actual_targets}",
        f"Structural validity: {result.structural_validity.value}",
        f"Proposal correctness: {result.proposal_correctness.value}",
        f"Delta correctness: {result.delta_correctness.value}",
        f"Target correctness: {result.target_correctness.value}",
        f"Deterministic grounding: {deterministic}",
        f"Reviewed grounding: {reviewed}",
        f"Grounding quality: {result.grounding_quality.value}",
        f"Trust safety: {result.trust_safety.value}",
        f"Overall quality: {result.overall_quality.value}",
        f"Gate: {result.gate_result.value}",
    ]
    lines.extend(
        f"Validation error [{issue.code}]: {issue.message}"
        for issue in result.validation_errors
    )
    return "\n".join(lines)


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Replay a reviewed AI Intent result")
    parser.add_argument("scenario", help="explicit scenario identifier")
    parser.add_argument(
        "--json",
        action="store_true",
        dest="as_json",
        help="emit the structured JSON-compatible result",
    )
    args = parser.parse_args(argv)

    result = run_replay(args.scenario)
    if args.as_json:
        print(
            json.dumps(
                result.model_dump(by_alias=True, mode="json"),
                indent=2,
                sort_keys=True,
            )
        )
    else:
        print(format_text_report(result))
    return 0 if result.gate_result == GateResult.PASSED else 1


if __name__ == "__main__":
    raise SystemExit(main())
