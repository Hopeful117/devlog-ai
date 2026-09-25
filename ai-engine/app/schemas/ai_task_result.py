from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.proposal import AiTaskResultStatus, ProposalType
from app.schemas.insight import ArchitectureDeltaConclusion
from app.schemas.story_context_analysis import StoryContextAnalysisResult
from app.schemas.interaction_trace import AiInteractionTrace
from app.schemas.typed_reference import ProviderAiReference


class ResultContractModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")


class AiProposalResult(ResultContractModel):
    type: ProposalType
    payload: dict[str, Any]
    confidence: float = Field(ge=0.0, le=1.0)
    supporting_fact_ids: list[UUID] | None = Field(default=None, alias="supportingFactIds")
    supporting_observation_ids: list[UUID] | None = Field(
        default=None, alias="supportingObservationIds"
    )
    evidence_references: list[str] | None = Field(default=None, alias="evidenceReferences")
    supporting_fact_refs: list[ProviderAiReference] | None = Field(default=None, alias="supportingFactRefs")
    supporting_observation_refs: list[ProviderAiReference] | None = Field(default=None, alias="supportingObservationRefs")
    evidence_refs: list[ProviderAiReference] | None = Field(default=None, alias="evidenceRefs")


class SynthesisSectionResult(ResultContractModel):
    name: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=10000)


class AnalysisSynthesisResult(ResultContractModel):
    title: str = Field(min_length=1, max_length=500)
    sections: list[SynthesisSectionResult] = Field(min_length=1, max_length=20)
    delta_conclusion: ArchitectureDeltaConclusion = Field(alias="deltaConclusion")
    grounding_references: list[str] | None = Field(default=None, alias="groundingReferences")
    grounding_refs: list[ProviderAiReference] | None = Field(default=None, alias="groundingRefs")


class AiTaskResultError(ResultContractModel):
    code: str = Field(min_length=1, max_length=100)
    message: str = Field(min_length=1, max_length=5000)


class PromptExecutionMetadata(ResultContractModel):
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=100)
    provider: str = Field(min_length=1, max_length=100)
    model_identifier: str = Field(alias="modelIdentifier", min_length=1, max_length=255)
    prompt_content_digest: str = Field(alias="promptContentDigest", pattern=r"^[0-9a-f]{64}$")
    context_digest: str = Field(alias="contextDigest", pattern=r"^[0-9a-f]{64}$")
    selection_digest: str | None = Field(default=None, alias="selectionDigest", pattern=r"^[0-9a-f]{64}$")
    projection_digest: str | None = Field(default=None, alias="projectionDigest", pattern=r"^[0-9a-f]{64}$")


class AiTaskResultRequest(ResultContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    external_job_id: str | None = Field(alias="externalJobId")
    status: AiTaskResultStatus
    completed_at: datetime = Field(alias="completedAt")
    proposals: list[AiProposalResult]
    error: AiTaskResultError | None = None
    prompt_execution: PromptExecutionMetadata | None = Field(
        default=None, alias="promptExecution"
    )
    synthesis: AnalysisSynthesisResult | None = Field(default=None)
    analysis_result: StoryContextAnalysisResult | None = Field(default=None, alias="analysisResult")
    interaction_traces: list[AiInteractionTrace] = Field(
        default_factory=list, alias="interactionTraces"
    )


class AiTaskResultAcknowledgement(ResultContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    acknowledged: bool
    duplicate: bool
    task_status: str = Field(alias="taskStatus")
    proposal_count: int = Field(alias="proposalCount", ge=0)
