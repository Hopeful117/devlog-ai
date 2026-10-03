"""Evaluation protocol for Story 0158 response specificity.

The protocol keeps runtime output and human assessment separate. Core/runtime
captures provide the evidence ledger; reviewers provide the bounded qualitative
dimensions and deterministic safety flags.
"""

from collections import defaultdict
from pathlib import Path
from typing import Literal
import argparse
import hashlib
import json

from pydantic import Field

from evaluations.models import EvaluationModel


Condition = Literal["STORY_AGENT", "MCP_BASELINE"]


class SpecificityDimensions(EvaluationModel):
    relevance: int = Field(ge=0, le=2)
    specificity: int = Field(ge=0, le=2)
    evidence_coverage: int = Field(alias="evidenceCoverage", ge=0, le=2)
    generic_usefulness: int = Field(alias="genericUsefulness", ge=0, le=2)
    abstention: int = Field(ge=0, le=2)
    next_step_utility: int = Field(alias="nextStepUtility", ge=0, le=2)

    @property
    def total(self) -> int:
        return sum(self.model_dump().values())


class SpecificityAssessment(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId", min_length=1)
    repetition: int = Field(ge=1, le=3)
    condition: Condition
    capture_digest: str = Field(alias="captureDigest", pattern=r"^[0-9a-f]{64}$")
    question_repeated: bool = Field(alias="questionRepeated")
    dimensions: SpecificityDimensions
    cited_evidence: list[str] = Field(alias="citedEvidence", default_factory=list)
    unknown_evidence_references: list[str] = Field(
        alias="unknownEvidenceReferences", default_factory=list
    )
    fabricated_relations: int = Field(alias="fabricatedRelations", ge=0)
    scope_violation: bool = Field(alias="scopeViolation")
    trust_violation: bool = Field(alias="trustViolation")
    abstention_correct: bool = Field(alias="abstentionCorrect")
    next_step_grounded: bool = Field(alias="nextStepGrounded")

    @property
    def deterministic_gate_passed(self) -> bool:
        return not (
            self.unknown_evidence_references
            or self.fabricated_relations
            or self.scope_violation
            or self.trust_violation
            or not self.question_repeated
        )


class SpecificityScenario(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId", min_length=1)
    question_kind: Literal["BROAD", "TARGETED", "INSUFFICIENT_EVIDENCE", "TRUNCATED_CONTEXT"] = Field(alias="questionKind")
    question: str = Field(min_length=1)
    files: list[str] = Field(default_factory=list)
    context_fixture: str = Field(alias="contextFixture", min_length=1)
    expected_evidence: list[str] = Field(alias="expectedEvidence", default_factory=list)
    expected_abstention: bool = Field(alias="expectedAbstention")
    next_step_required: bool = Field(alias="nextStepRequired")


class SpecificityManifest(EvaluationModel):
    protocol_version: str = Field(alias="protocolVersion", pattern=r"^story-0158-specificity/v1$")
    repetitions: int = Field(ge=3, le=3)
    minimum_total_score: int = Field(alias="minimumTotalScore", ge=0, le=12)
    minimum_specificity_delta: int = Field(alias="minimumSpecificityDelta", ge=0, le=2)
    scenarios: list[SpecificityScenario] = Field(min_length=4)


class ScenarioSpecificityResult(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId")
    agent_average_score: float = Field(alias="agentAverageScore", ge=0, le=12)
    baseline_average_score: float = Field(alias="baselineAverageScore", ge=0, le=12)
    specificity_delta: float = Field(alias="specificityDelta")
    agent_average_specificity: float = Field(alias="agentAverageSpecificity", ge=0, le=2)
    baseline_average_specificity: float = Field(alias="baselineAverageSpecificity", ge=0, le=2)
    deterministic_gates_passed: bool = Field(alias="deterministicGatesPassed")
    abstention_gate_passed: bool = Field(alias="abstentionGatePassed")
    next_step_gate_passed: bool = Field(alias="nextStepGatePassed")
    passed: bool


class SpecificityEvaluationResult(EvaluationModel):
    protocol_version: str = Field(alias="protocolVersion")
    scenarios: list[ScenarioSpecificityResult]
    passed: bool


class RuntimeCapture(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId")
    condition: Condition
    repetition: int = Field(ge=1, le=3)
    question: str = Field(min_length=1)
    status: Literal["COMPLETED"]
    execution_id: str = Field(alias="executionId", min_length=1)
    context_digest: str = Field(alias="contextDigest", pattern=r"^[0-9a-f]{64}$")
    projection_digest: str | None = Field(default=None, alias="projectionDigest")
    repository_revision: str = Field(alias="repositoryRevision", min_length=1)
    capture_digest: str = Field(alias="captureDigest", pattern=r"^[0-9a-f]{64}$")
    payload: dict


def load_manifest(path: Path) -> SpecificityManifest:
    return SpecificityManifest.model_validate(json.loads(path.read_text(encoding="utf-8")))


def load_assessments(path: Path) -> list[SpecificityAssessment]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    values = raw["assessments"] if isinstance(raw, dict) else raw
    return [SpecificityAssessment.model_validate(value) for value in values]


def load_runtime_captures(
    manifest: SpecificityManifest,
    story_agent_directory: Path,
    baseline_directory: Path,
) -> dict[tuple[str, Condition, int], RuntimeCapture]:
    captures: dict[tuple[str, Condition, int], RuntimeCapture] = {}
    for scenario in manifest.scenarios:
        for repetition in range(1, manifest.repetitions + 1):
            for condition, directory in (
                ("STORY_AGENT", story_agent_directory),
                ("MCP_BASELINE", baseline_directory),
            ):
                path = directory / f"{scenario.scenario_id}-{condition}-r{repetition}.json"
                if not path.is_file():
                    raise ValueError(f"missing runtime capture: {path}")
                raw = json.loads(path.read_text(encoding="utf-8"))
                capture = RuntimeCapture.model_validate({
                    "scenarioId": raw.get("scenarioId"),
                    "condition": raw.get("condition"),
                    "repetition": raw.get("repetition"),
                    "question": raw.get("question"),
                    "status": raw.get("status"),
                    "executionId": raw.get("executionId"),
                    "contextDigest": raw.get("contextDigest"),
                    "projectionDigest": raw.get("projectionDigest"),
                    "repositoryRevision": raw.get("repositoryRevision"),
                    "captureDigest": raw.get("captureDigest"),
                    "payload": raw,
                })
                if (
                    capture.scenario_id != scenario.scenario_id
                    or capture.question != scenario.question
                    or capture.condition != condition
                    or capture.repetition != repetition
                ):
                    raise ValueError(f"runtime capture metadata mismatch: {path}")
                if condition == "STORY_AGENT" and not raw.get("result"):
                    raise ValueError(f"Story Agent capture has no result: {path}")
                if condition == "MCP_BASELINE" and not raw.get("answer"):
                    raise ValueError(f"MCP baseline capture has no answer: {path}")
                if condition == "STORY_AGENT" and not capture.projection_digest:
                    raise ValueError(f"Story Agent capture has no projection digest: {path}")
                if capture.status != "COMPLETED" or raw.get("status") != "COMPLETED":
                    raise ValueError(f"runtime capture is not completed: {path}")
                if capture.repository_revision in {"", "unknown", "UNKNOWN"}:
                    raise ValueError(f"runtime capture has no repository revision: {path}")
                if capture.capture_digest != _capture_digest(raw):
                    raise ValueError(f"runtime capture digest mismatch: {path}")
                _validate_payload_identity(capture, raw, path)
                captures[(scenario.scenario_id, condition, repetition)] = capture
    return captures


def evaluate_specificity(
    manifest: SpecificityManifest,
    assessments: list[SpecificityAssessment],
    captures: dict[tuple[str, Condition, int], RuntimeCapture],
) -> SpecificityEvaluationResult:
    scenarios = {scenario.scenario_id: scenario for scenario in manifest.scenarios}
    grouped: dict[tuple[str, Condition], list[SpecificityAssessment]] = defaultdict(list)
    for assessment in assessments:
        if assessment.scenario_id not in scenarios:
            raise ValueError(f"unknown Story 0158 scenario: {assessment.scenario_id}")
        grouped[(assessment.scenario_id, assessment.condition)].append(assessment)
        if (assessment.scenario_id, assessment.condition, assessment.repetition) not in captures:
            raise ValueError(
                f"assessment has no matching runtime capture: "
                f"{assessment.scenario_id}/{assessment.condition}/r{assessment.repetition}"
            )
        if assessment.capture_digest != captures[
            (assessment.scenario_id, assessment.condition, assessment.repetition)
        ].capture_digest:
            raise ValueError(
                f"assessment capture digest mismatch: "
                f"{assessment.scenario_id}/{assessment.condition}/r{assessment.repetition}"
            )

    results = []
    for scenario in manifest.scenarios:
        agent = _complete(grouped, scenario.scenario_id, "STORY_AGENT", manifest.repetitions)
        baseline = _complete(grouped, scenario.scenario_id, "MCP_BASELINE", manifest.repetitions)
        agent_scores = [value.dimensions for value in agent]
        baseline_scores = [value.dimensions for value in baseline]
        agent_average = _average(value.total for value in agent_scores)
        baseline_average = _average(value.total for value in baseline_scores)
        agent_specificity = _average(value.specificity for value in agent_scores)
        baseline_specificity = _average(value.specificity for value in baseline_scores)
        deterministic = all(
            value.deterministic_gate_passed
            and set(value.cited_evidence).issubset(
                set(scenario.expected_evidence)
                | _captured_evidence(captures[(scenario.scenario_id, value.condition, value.repetition)].payload)
            )
            for value in agent + baseline
        )
        abstention = all(value.abstention_correct == scenario.expected_abstention for value in agent + baseline)
        next_step = all(value.next_step_grounded == scenario.next_step_required for value in agent + baseline)
        passed = (
            deterministic
            and abstention
            and next_step
            and agent_average >= manifest.minimum_total_score
            and agent_specificity - baseline_specificity >= manifest.minimum_specificity_delta
        )
        results.append(ScenarioSpecificityResult(
            scenarioId=scenario.scenario_id,
            agentAverageScore=agent_average,
            baselineAverageScore=baseline_average,
            specificityDelta=agent_specificity - baseline_specificity,
            agentAverageSpecificity=agent_specificity,
            baselineAverageSpecificity=baseline_specificity,
            deterministicGatesPassed=deterministic,
            abstentionGatePassed=abstention,
            nextStepGatePassed=next_step,
            passed=passed,
        ))

    return SpecificityEvaluationResult(
        protocolVersion=manifest.protocol_version,
        scenarios=results,
        passed=all(value.passed for value in results),
    )


def _complete(grouped, scenario_id: str, condition: Condition, repetitions: int):
    values = grouped.get((scenario_id, condition), [])
    if len(values) != repetitions or {value.repetition for value in values} != set(range(1, repetitions + 1)):
        raise ValueError(f"{scenario_id}/{condition} requires repetitions 1..{repetitions}")
    return sorted(values, key=lambda value: value.repetition)


def _average(values) -> float:
    values = list(values)
    return round(sum(values) / len(values), 3)


def _captured_evidence(payload: object) -> set[str]:
    references: set[str] = set()

    def visit(value: object, key: str | None = None) -> None:
        if isinstance(value, dict):
            for child_key, child_value in value.items():
                visit(child_value, child_key)
        elif isinstance(value, list):
            for child_value in value:
                visit(child_value, key)
        elif isinstance(value, str) and key in {"reference", "evidenceReferences", "ref"}:
            references.add(value)

    visit(payload)
    return references


def _capture_digest(payload: dict) -> str:
    unsigned = {key: value for key, value in payload.items() if key != "captureDigest"}
    encoded = json.dumps(unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    return hashlib.sha256(encoded).hexdigest()


def _validate_payload_identity(capture: RuntimeCapture, payload: dict, path: Path) -> None:
    if capture.condition == "STORY_AGENT":
        result = payload["result"]
        provenance = result.get("analysis", {}).get("provenance", {})
        if provenance.get("contextDigest") != capture.context_digest:
            raise ValueError(f"Story Agent context digest mismatch: {path}")
        status = payload.get("taskStatus", {})
        if status.get("status") != "COMPLETED":
            raise ValueError(f"Story Agent task is not completed: {path}")
        if status.get("contextDigest") != capture.context_digest:
            raise ValueError(f"Story Agent task context digest mismatch: {path}")
        if status.get("projectionDigest") != capture.projection_digest:
            raise ValueError(f"Story Agent projection digest mismatch: {path}")
        revision = result.get("contextFreshness", {}).get("sourceRevision", {}).get("revision")
    else:
        context = payload["context"]
        metadata = context.get("metadata", {})
        if metadata.get("contextDigest") != capture.context_digest:
            raise ValueError(f"MCP baseline context digest mismatch: {path}")
        revision = metadata.get("freshness", {}).get("repositoryRevision")
    if revision != capture.repository_revision:
        raise ValueError(f"runtime capture repository revision mismatch: {path}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Evaluate Story 0158 specificity assessments")
    parser.add_argument("manifest", type=Path)
    parser.add_argument("assessments", type=Path)
    parser.add_argument("--story-agent-captures", type=Path, required=True)
    parser.add_argument("--baseline-captures", type=Path, required=True)
    args = parser.parse_args()
    manifest = load_manifest(args.manifest)
    captures = load_runtime_captures(manifest, args.story_agent_captures, args.baseline_captures)
    result = evaluate_specificity(manifest, load_assessments(args.assessments), captures)
    print(json.dumps(result.model_dump(by_alias=True, mode="json"), indent=2, sort_keys=True))
    return 0 if result.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
