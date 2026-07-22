from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.ai_task import AiTaskType


class ContractModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class AiTaskSubmissionRequest(ContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    task_type: AiTaskType = Field(alias="taskType")
    analysis_id: UUID = Field(alias="analysisId")
    context: dict[str, Any]


class AiTaskSubmissionResponse(ContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    accepted: bool
    external_job_id: UUID = Field(alias="externalJobId")
    accepted_at: datetime = Field(alias="acceptedAt")


class UnsupportedTaskTypeResponse(ContractModel):
    code: str
    task_type: str = Field(alias="taskType")
    supported_task_types: list[str] = Field(alias="supportedTaskTypes")
