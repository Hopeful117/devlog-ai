from datetime import datetime
from typing import Annotated, Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.models.ai_task import AiTaskType
from app.models.intent import InsightType


class ContractModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True, extra="forbid", str_strip_whitespace=True
    )


class UserGuidance(ContractModel):
    focus: str | None = Field(default=None, min_length=1, max_length=500)
    audience: str | None = Field(default=None, min_length=1, max_length=200)
    level_of_detail: str | None = Field(
        default=None, alias="levelOfDetail", min_length=1, max_length=100
    )
    writing_style: str | None = Field(
        default=None, alias="writingStyle", min_length=1, max_length=100
    )
    output_context: str | None = Field(
        default=None, alias="outputContext", min_length=1, max_length=500
    )
    priorities: list[Annotated[str, Field(min_length=1, max_length=300)]] = Field(
        default_factory=list, max_length=10
    )


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
    context_profiles: list[str] = Field(
        default_factory=list, alias="contextProfiles"
    )


class PromptRequest(ContractModel):
    request_id: UUID = Field(alias="requestId")
    correlation_id: UUID = Field(alias="correlationId")
    analysis_id: UUID = Field(alias="analysisId")
    ai_task_id: UUID = Field(alias="aiTaskId")
    task_type: AiTaskType = Field(alias="taskType")
    intent: IntentDefinition
    user_guidance: UserGuidance | None = Field(default=None, alias="userGuidance")
    selected_knowledge: dict[str, Any] = Field(alias="selectedKnowledge")
    expected_output_contract: dict[str, Any] = Field(alias="expectedOutputContract")
    metadata: dict[str, Any]


AiTaskSubmissionRequest = PromptRequest


class AiTaskSubmissionResponse(ContractModel):
    correlation_id: UUID = Field(alias="correlationId")
    accepted: bool
    external_job_id: UUID = Field(alias="externalJobId")
    accepted_at: datetime = Field(alias="acceptedAt")


class UnsupportedTaskTypeResponse(ContractModel):
    code: str
    task_type: str = Field(alias="taskType")
    supported_task_types: list[str] = Field(alias="supportedTaskTypes")
