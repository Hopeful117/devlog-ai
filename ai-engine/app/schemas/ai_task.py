from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.ai_task import AiTaskType
from app.models.intent import InsightType


class ContractModel(BaseModel):
    model_config = ConfigDict(populate_by_name=True)


class IntentDefinition(ContractModel):
    id: str = Field(min_length=1, max_length=80)
    version: str = Field(min_length=1, max_length=20)
    objective: str = Field(min_length=1, max_length=1000)
    supported_insight_types: list[InsightType] = Field(
        alias="supportedInsightTypes", min_length=1
    )
    constraints: list[str] = Field(min_length=1)
    output_schema: dict[str, Any] = Field(alias="outputSchema")
    prompt_template: str = Field(alias="promptTemplate", min_length=1, max_length=100)


class AiTaskSubmissionRequest(ContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    task_type: AiTaskType = Field(alias="taskType")
    analysis_id: UUID = Field(alias="analysisId")
    intent: IntentDefinition
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
