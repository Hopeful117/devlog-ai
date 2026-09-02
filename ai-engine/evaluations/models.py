from datetime import datetime
from enum import Enum
from typing import Annotated
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, StrictInt, model_validator

from app.schemas.ai_task import PromptRequest
from app.schemas.insight import InsightGenerationOutput, KnowledgeDeltaType


NonBlank = Annotated[str, Field(min_length=1)]
Sha256 = Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]


class EvaluationModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class RequiredInputKind(str, Enum):
    FACT = "FACT"
    OBSERVATION = "OBSERVATION"


class ReviewStatus(str, Enum):
    REVIEWED = "REVIEWED"


class ExecutionStatus(str, Enum):
    INVALID_SCENARIO = "INVALID_SCENARIO"
    EXECUTION_FAILED = "EXECUTION_FAILED"
    EVALUATED = "EVALUATED"


class StructuralValidity(str, Enum):
    VALID = "VALID"
    INVALID = "INVALID"
    NOT_EVALUATED = "NOT_EVALUATED"


class Correctness(str, Enum):
    CORRECT = "CORRECT"
    INCORRECT = "INCORRECT"
    NOT_EVALUATED = "NOT_EVALUATED"


class TargetCorrectness(str, Enum):
    CORRECT_TARGET = "CORRECT_TARGET"
    WRONG_TARGET = "WRONG_TARGET"
    MISSING_REQUIRED_TARGET = "MISSING_REQUIRED_TARGET"
    TARGET_NOT_APPLICABLE = "TARGET_NOT_APPLICABLE"
    NOT_EVALUATED = "NOT_EVALUATED"


class GroundingQuality(str, Enum):
    GROUNDED = "GROUNDED"
    LIMITED = "LIMITED"
    UNSUPPORTED = "UNSUPPORTED"
    CONTRADICTED = "CONTRADICTED"
    REVIEW_REQUIRED = "REVIEW_REQUIRED"
    NOT_EVALUATED = "NOT_EVALUATED"


class TrustSafety(str, Enum):
    SAFE = "SAFE"
    VIOLATION = "VIOLATION"
    NOT_EVALUATED = "NOT_EVALUATED"


class OverallQuality(str, Enum):
    STRONG = "STRONG"
    ACCEPTABLE = "ACCEPTABLE"
    WEAK = "WEAK"
    NOT_EVALUATED = "NOT_EVALUATED"


class GateResult(str, Enum):
    PASSED = "PASSED"
    FAILED = "FAILED"
    NOT_EVALUATED = "NOT_EVALUATED"


class IntentIdentity(EvaluationModel):
    id: NonBlank
    version: NonBlank


class RequiredSelectedInput(EvaluationModel):
    kind: RequiredInputKind
    id: UUID


class ScenarioExpectation(EvaluationModel):
    expected_delta: KnowledgeDeltaType | None = Field(alias="expectedDelta")
    expected_target_id: UUID | None = Field(alias="expectedTargetId")
    allowed_fact_ids: list[UUID] = Field(alias="allowedFactIds")
    allowed_observation_ids: list[UUID] = Field(alias="allowedObservationIds")
    allowed_evidence_references: list[NonBlank] = Field(
        alias="allowedEvidenceReferences"
    )
    qualitative_grounding_required: bool = Field(
        alias="qualitativeGroundingRequired"
    )

    @model_validator(mode="after")
    def validate_target_semantics(self) -> "ScenarioExpectation":
        if (
            self.expected_delta == KnowledgeDeltaType.ENRICHES
            and self.expected_target_id is None
        ):
            raise ValueError("expectedTargetId is required for ENRICHES")
        if (
            self.expected_delta != KnowledgeDeltaType.ENRICHES
            and self.expected_target_id is not None
        ):
            raise ValueError("expectedTargetId is only applicable to ENRICHES")
        return self


class ScenarioGate(EvaluationModel):
    required_runs: StrictInt = Field(alias="requiredRuns", ge=0)
    minimum_strong: StrictInt = Field(alias="minimumStrong", ge=0)
    maximum_incorrect_delta: StrictInt = Field(alias="maximumIncorrectDelta", ge=0)
    maximum_incorrect_target: StrictInt = Field(alias="maximumIncorrectTarget", ge=0)
    maximum_trust_violations: StrictInt = Field(alias="maximumTrustViolations", ge=0)
    maximum_execution_failures: StrictInt = Field(
        alias="maximumExecutionFailures", ge=0
    )

    @model_validator(mode="after")
    def validate_thresholds(self) -> "ScenarioGate":
        if self.minimum_strong > self.required_runs:
            raise ValueError("minimumStrong cannot exceed requiredRuns")
        return self


class ScenarioReproducibility(EvaluationModel):
    repository_revision: NonBlank = Field(alias="repositoryRevision")
    prompt_version: NonBlank = Field(alias="promptVersion")
    prompt_content_digest: Sha256 = Field(alias="promptContentDigest")
    schema_digest: Sha256 = Field(alias="schemaDigest")


class EvaluationScenario(EvaluationModel):
    scenario_id: NonBlank = Field(alias="scenarioId")
    scenario_version: str = Field(
        alias="scenarioVersion",
        pattern=r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$",
    )
    intent: IntentIdentity
    prompt_request: PromptRequest = Field(alias="promptRequest")
    required_selected_inputs: list[RequiredSelectedInput] = Field(
        alias="requiredSelectedInputs", min_length=1
    )
    expectation: ScenarioExpectation
    gate: ScenarioGate
    reproducibility: ScenarioReproducibility


class CaptureMetadata(EvaluationModel):
    repository_revision: NonBlank = Field(alias="repositoryRevision")
    captured_at: datetime = Field(alias="capturedAt")
    provider: NonBlank
    model: NonBlank
    prompt_version: NonBlank = Field(alias="promptVersion")
    prompt_content_digest: Sha256 = Field(alias="promptContentDigest")
    schema_digest: Sha256 = Field(alias="schemaDigest")


class QualitativeGroundingAssessment(EvaluationModel):
    status: ReviewStatus
    unsupported_material_claims: StrictInt = Field(
        alias="unsupportedMaterialClaims", ge=0
    )
    contradicted_material_claims: StrictInt = Field(
        alias="contradictedMaterialClaims", ge=0
    )
    plausible_but_unproven_material_claims: StrictInt = Field(
        alias="plausibleButUnprovenMaterialClaims", ge=0
    )
    limited_non_critical_imprecision: bool = Field(
        alias="limitedNonCriticalImprecision"
    )
    notes: Annotated[str, Field(min_length=1, max_length=2000)] | None = None


class EvaluationReplay(EvaluationModel):
    scenario_id: NonBlank = Field(alias="scenarioId")
    scenario_version: NonBlank = Field(alias="scenarioVersion")
    output: InsightGenerationOutput
    capture: CaptureMetadata
    qualitative_grounding: QualitativeGroundingAssessment | None = Field(
        alias="qualitativeGrounding"
    )


class ValidationIssue(EvaluationModel):
    code: NonBlank
    message: NonBlank


class EvaluationCounters(EvaluationModel):
    total_runs: int = Field(alias="totalRuns", ge=0)
    strong_count: int = Field(alias="strongCount", ge=0)
    acceptable_count: int = Field(alias="acceptableCount", ge=0)
    weak_count: int = Field(alias="weakCount", ge=0)
    incorrect_delta_count: int = Field(alias="incorrectDeltaCount", ge=0)
    incorrect_target_count: int = Field(alias="incorrectTargetCount", ge=0)
    trust_violation_count: int = Field(alias="trustViolationCount", ge=0)
    execution_failure_count: int = Field(alias="executionFailureCount", ge=0)


class EvaluationResult(EvaluationModel):
    scenario_id: str = Field(alias="scenarioId")
    scenario_version: str = Field(alias="scenarioVersion")
    evaluator_version: NonBlank = Field(alias="evaluatorVersion")
    execution_status: ExecutionStatus = Field(alias="executionStatus")
    validation_errors: list[ValidationIssue] = Field(alias="validationErrors")
    proposal_count: int = Field(alias="proposalCount", ge=0)
    expected_delta: KnowledgeDeltaType | None = Field(alias="expectedDelta")
    actual_deltas: list[KnowledgeDeltaType] = Field(alias="actualDeltas")
    expected_target_id: UUID | None = Field(alias="expectedTargetId")
    actual_target_ids: list[UUID | None] = Field(alias="actualTargetIds")
    structural_validity: StructuralValidity = Field(alias="structuralValidity")
    proposal_correctness: Correctness = Field(alias="proposalCorrectness")
    delta_correctness: Correctness = Field(alias="deltaCorrectness")
    target_correctness: TargetCorrectness = Field(alias="targetCorrectness")
    grounding_quality: GroundingQuality = Field(alias="groundingQuality")
    deterministic_grounding_passed: bool | None = Field(
        alias="deterministicGroundingPassed"
    )
    qualitative_grounding_status: ReviewStatus | None = Field(
        alias="qualitativeGroundingStatus"
    )
    trust_safety: TrustSafety = Field(alias="trustSafety")
    overall_quality: OverallQuality = Field(alias="overallQuality")
    gate_result: GateResult = Field(alias="gateResult")
    counters: EvaluationCounters
