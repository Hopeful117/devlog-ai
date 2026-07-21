from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.proposal import AiTaskResultStatus, ProposalType


class ResultContractModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class AiProposalResult(ResultContractModel):
    type: ProposalType
    payload: dict[str, Any]
    confidence: float = Field(ge=0.0, le=1.0)
    supporting_fact_ids: list[UUID] = Field(alias="supportingFactIds")
    supporting_observation_ids: list[UUID] = Field(
        alias="supportingObservationIds"
    )
    evidence_references: list[str] = Field(alias="evidenceReferences")


class AiTaskResultError(ResultContractModel):
    code: str = Field(min_length=1, max_length=100)
    message: str = Field(min_length=1, max_length=5000)


class AiTaskResultRequest(ResultContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    external_job_id: str | None = Field(alias="externalJobId")
    status: AiTaskResultStatus
    completed_at: datetime = Field(alias="completedAt")
    proposals: list[AiProposalResult]
    error: AiTaskResultError | None = None


class AiTaskResultAcknowledgement(ResultContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    acknowledged: bool
    duplicate: bool
    task_status: str = Field(alias="taskStatus")
    proposal_count: int = Field(alias="proposalCount", ge=0)
