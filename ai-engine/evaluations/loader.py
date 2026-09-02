import hashlib
import json
import re
from pathlib import Path
from typing import Any
from uuid import UUID

from pydantic import ValidationError

from app.models.ai_task import AiTaskType
from app.models.proposal import ProposalType
from app.prompts.insight import InsightPromptBuilder, PromptConstructionError
from app.schemas.insight import InsightGenerationOutput, KnowledgeDeltaType

from evaluations.models import (
    EvaluationReplay,
    EvaluationScenario,
    RequiredInputKind,
    ValidationIssue,
)


SCENARIOS_DIR = Path(__file__).parent / "scenarios"
_SCENARIO_ID = re.compile(r"^[a-z0-9][a-z0-9-]*$")


class ArtifactValidationError(Exception):
    def __init__(
        self,
        issues: list[ValidationIssue],
        *,
        scenario: EvaluationScenario | None = None,
        raw_artifact: dict[str, Any] | None = None,
        output_contract_invalid: bool = False,
    ) -> None:
        super().__init__("; ".join(issue.message for issue in issues))
        self.issues = issues
        self.scenario = scenario
        self.raw_artifact = raw_artifact
        self.output_contract_invalid = output_contract_invalid


def insight_output_schema_digest() -> str:
    encoded = json.dumps(
        InsightGenerationOutput.model_json_schema(),
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def resolve_scenario_directory(
    scenario_id: str,
    scenarios_root: Path = SCENARIOS_DIR,
) -> Path:
    if not _SCENARIO_ID.fullmatch(scenario_id):
        raise ArtifactValidationError(
            [
                ValidationIssue(
                    code="INVALID_SCENARIO_ID",
                    message="scenario identifier must contain only lowercase letters, digits, and hyphens",
                )
            ]
        )
    root = scenarios_root.resolve()
    candidate = (root / scenario_id).resolve()
    if candidate.parent != root:
        raise ArtifactValidationError(
            [
                ValidationIssue(
                    code="INVALID_SCENARIO_PATH",
                    message="scenario path must remain beneath the scenarios directory",
                )
            ]
        )
    return candidate


def load_scenario(
    scenario_id: str,
    scenarios_root: Path = SCENARIOS_DIR,
) -> EvaluationScenario:
    directory = resolve_scenario_directory(scenario_id, scenarios_root)
    raw = _read_json(directory / "scenario.json", "SCENARIO")
    try:
        scenario = EvaluationScenario.model_validate(raw)
    except ValidationError as exc:
        raise ArtifactValidationError(_pydantic_issues("SCENARIO", exc)) from exc

    issues = validate_scenario(scenario)
    if scenario.scenario_id != scenario_id:
        issues.append(
            ValidationIssue(
                code="SCENARIO_ID_MISMATCH",
                message="scenarioId does not match the selected directory identifier",
            )
        )
    if issues:
        raise ArtifactValidationError(issues, scenario=scenario, raw_artifact=raw)
    return scenario


def load_replay(
    scenario: EvaluationScenario,
    scenarios_root: Path = SCENARIOS_DIR,
) -> EvaluationReplay:
    directory = resolve_scenario_directory(scenario.scenario_id, scenarios_root)
    raw = _read_json(directory / "replay.json", "REPLAY")
    try:
        replay = EvaluationReplay.model_validate(raw)
    except ValidationError as exc:
        errors = exc.errors()
        raise ArtifactValidationError(
            _pydantic_issues("REPLAY", exc),
            scenario=scenario,
            raw_artifact=raw,
            output_contract_invalid=bool(errors)
            and all(item["loc"] and item["loc"][0] == "output" for item in errors),
        ) from exc

    issues = validate_replay(scenario, replay)
    if issues:
        raise ArtifactValidationError(
            issues,
            scenario=scenario,
            raw_artifact=raw,
        )
    return replay


def validate_scenario(scenario: EvaluationScenario) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    request = scenario.prompt_request
    expectation = scenario.expectation
    selected = request.selected_knowledge

    if (
        scenario.intent.id != request.intent.id
        or scenario.intent.version != request.intent.version
    ):
        issues.append(
            ValidationIssue(
                code="INTENT_IDENTITY_MISMATCH",
                message="scenario intent identity must match promptRequest.intent",
            )
        )
    if request.task_type != AiTaskType.INSIGHT_GENERATION:
        issues.append(
            ValidationIssue(
                code="INCOMPATIBLE_TASK_TYPE",
                message="promptRequest must use INSIGHT_GENERATION",
            )
        )
    if request.intent.output_proposal_type != ProposalType.INSIGHT:
        issues.append(
            ValidationIssue(
                code="INCOMPATIBLE_PROPOSAL_TYPE",
                message="promptRequest intent must produce INSIGHT proposals",
            )
        )
    if request.expected_output_contract != request.intent.output_schema:
        issues.append(
            ValidationIssue(
                code="OUTPUT_CONTRACT_MISMATCH",
                message="promptRequest expectedOutputContract must match intent.outputSchema",
            )
        )
    try:
        prompt = InsightPromptBuilder().build(request)
    except PromptConstructionError as exc:
        issues.append(
            ValidationIssue(
                code="PROMPT_REQUEST_INCOMPATIBLE",
                message=f"production prompt construction rejected the fixture: {exc}",
            )
        )
    else:
        if prompt.content_digest != scenario.reproducibility.prompt_content_digest:
            issues.append(
                ValidationIssue(
                    code="PROMPT_DIGEST_MISMATCH",
                    message="promptContentDigest does not match the production prompt",
                )
            )
    if scenario.reproducibility.prompt_version != request.intent.prompt_template:
        issues.append(
            ValidationIssue(
                code="PROMPT_VERSION_MISMATCH",
                message="promptVersion must match promptRequest.intent.promptTemplate",
            )
        )
    if scenario.reproducibility.schema_digest != insight_output_schema_digest():
        issues.append(
            ValidationIssue(
                code="SCHEMA_DIGEST_MISMATCH",
                message="schemaDigest does not match InsightGenerationOutput",
            )
        )
    if scenario.gate.required_runs != 1:
        issues.append(
            ValidationIssue(
                code="UNSUPPORTED_RUN_COUNT",
                message="the replay-first V1 runner requires requiredRuns to equal 1",
            )
        )

    facts = _items(selected, "selectedFacts")
    observations = _items(selected, "selectedObservations")
    fact_ids = _uuid_values(facts, "id")
    observation_ids = _uuid_values(observations, "id")

    for required in scenario.required_selected_inputs:
        available = fact_ids if required.kind == RequiredInputKind.FACT else observation_ids
        if required.id not in available:
            issues.append(
                ValidationIssue(
                    code="REQUIRED_SELECTED_INPUT_MISSING",
                    message=f"required {required.kind.value} {required.id} is absent",
                )
            )

    for fact_id in expectation.allowed_fact_ids:
        if fact_id not in fact_ids:
            issues.append(
                ValidationIssue(
                    code="ALLOWED_FACT_UNRESOLVED",
                    message=f"allowed Fact {fact_id} is absent from the fixture",
                )
            )
    for observation_id in expectation.allowed_observation_ids:
        if observation_id not in observation_ids:
            issues.append(
                ValidationIssue(
                    code="ALLOWED_OBSERVATION_UNRESOLVED",
                    message=f"allowed Observation {observation_id} is absent from the fixture",
                )
            )

    fixture_references = _fixture_evidence_references(selected)
    for reference in expectation.allowed_evidence_references:
        if reference not in fixture_references:
            issues.append(
                ValidationIssue(
                    code="ALLOWED_EVIDENCE_UNRESOLVED",
                    message=f"allowed evidence reference {reference!r} is absent from the fixture",
                )
            )

    if expectation.expected_delta == KnowledgeDeltaType.ENRICHES:
        target_ids = _uuid_values(
            _items(selected, "existingArchitectureKnowledge"),
            "insightId",
        )
        if expectation.expected_target_id not in target_ids:
            issues.append(
                ValidationIssue(
                    code="EXPECTED_TARGET_UNRESOLVED",
                    message="expectedTargetId is absent from existingArchitectureKnowledge",
                )
            )

    return issues


def validate_replay(
    scenario: EvaluationScenario,
    replay: EvaluationReplay,
) -> list[ValidationIssue]:
    issues: list[ValidationIssue] = []
    if (
        replay.scenario_id != scenario.scenario_id
        or replay.scenario_version != scenario.scenario_version
    ):
        issues.append(
            ValidationIssue(
                code="REPLAY_SCENARIO_MISMATCH",
                message="replay scenario identity/version must match the scenario",
            )
        )

    expected = scenario.reproducibility
    captured = replay.capture
    matching_fields = (
        ("repositoryRevision", expected.repository_revision, captured.repository_revision),
        ("promptVersion", expected.prompt_version, captured.prompt_version),
        ("promptContentDigest", expected.prompt_content_digest, captured.prompt_content_digest),
        ("schemaDigest", expected.schema_digest, captured.schema_digest),
    )
    for name, expected_value, captured_value in matching_fields:
        if expected_value != captured_value:
            issues.append(
                ValidationIssue(
                    code="REPRODUCIBILITY_MISMATCH",
                    message=f"replay {name} does not match the scenario",
                )
            )
    if (
        scenario.expectation.qualitative_grounding_required
        and replay.qualitative_grounding is None
    ):
        issues.append(
            ValidationIssue(
                code="QUALITATIVE_REVIEW_REQUIRED",
                message="a completed reviewed qualitative grounding assessment is required",
            )
        )
    return issues


def _read_json(path: Path, artifact: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ArtifactValidationError(
            [
                ValidationIssue(
                    code=f"{artifact}_READ_FAILED",
                    message=f"unable to read {path.name}: {exc}",
                )
            ]
        ) from exc
    if not isinstance(value, dict):
        raise ArtifactValidationError(
            [
                ValidationIssue(
                    code=f"{artifact}_SCHEMA_INVALID",
                    message=f"{path.name} must contain a JSON object",
                )
            ]
        )
    return value


def _pydantic_issues(prefix: str, error: ValidationError) -> list[ValidationIssue]:
    return [
        ValidationIssue(
            code=f"{prefix}_SCHEMA_INVALID",
            message=f"{'.'.join(str(part) for part in item['loc'])}: {item['msg']}",
        )
        for item in error.errors()
    ]


def _items(selected: dict[str, Any], key: str) -> list[dict[str, Any]]:
    value = selected.get(key, [])
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict)]


def _uuid_values(items: list[dict[str, Any]], key: str) -> set[UUID]:
    values: set[UUID] = set()
    for item in items:
        try:
            values.add(UUID(str(item.get(key))))
        except (TypeError, ValueError):
            continue
    return values


def _fixture_evidence_references(selected: dict[str, Any]) -> set[str]:
    references: set[str] = set()
    for collection in ("selectedFacts", "selectedObservations"):
        for item in _items(selected, collection):
            value = item.get("evidenceReferences", [])
            if isinstance(value, list):
                references.update(item for item in value if isinstance(item, str))

    repository_context = selected.get("repositoryContext")
    if isinstance(repository_context, dict):
        evidence = repository_context.get("evidence", [])
        if isinstance(evidence, list):
            for item in evidence:
                if not isinstance(item, dict):
                    continue
                for key in ("reference", "path", "evidenceReference"):
                    value = item.get(key)
                    if isinstance(value, str):
                        references.add(value)
    return references
