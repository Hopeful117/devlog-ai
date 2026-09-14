from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import Field

from app.schemas.ai_task import ContractModel


class AiInteractionTrace(ContractModel):
    id: UUID
    attempt: int = Field(ge=1)
    interaction_type: str = Field(alias="interactionType")
    trace_level: str = Field(alias="traceLevel")
    provider: str
    model_identifier: str = Field(alias="modelIdentifier")
    intent: str
    intent_version: str = Field(alias="intentVersion")
    prompt_version: str = Field(alias="promptVersion")
    started_at: datetime = Field(alias="startedAt")
    completed_at: datetime = Field(alias="completedAt")
    duration_ms: int = Field(alias="durationMs", ge=0)
    validation_status: str = Field(alias="validationStatus")
    failure_category: str | None = Field(default=None, alias="failureCategory")
    retry_reason: str | None = Field(default=None, alias="retryReason")
    selected_knowledge_fingerprint: str = Field(alias="selectedKnowledgeFingerprint")
    prompt_fingerprint: str = Field(alias="promptFingerprint")
    selected_fact_count: int = Field(alias="selectedFactCount", ge=0)
    selected_observation_count: int = Field(alias="selectedObservationCount", ge=0)
    selected_insight_count: int = Field(alias="selectedInsightCount", ge=0)
    selected_engineering_event_count: int = Field(alias="selectedEngineeringEventCount", ge=0)
    grounding_fingerprint: str = Field(alias="groundingFingerprint")
    input_tokens: int | None = Field(default=None, alias="inputTokens", ge=0)
    output_tokens: int | None = Field(default=None, alias="outputTokens", ge=0)
    total_tokens: int | None = Field(default=None, alias="totalTokens", ge=0)
    trace_id: str | None = Field(default=None, alias="traceId")
    span_id: str | None = Field(default=None, alias="spanId")
    system_prompt: str | None = Field(default=None, alias="systemPrompt")
    user_prompt: str | None = Field(default=None, alias="userPrompt")
    raw_model_response: str | None = Field(default=None, alias="rawModelResponse")
    parsed_model_response: dict[str, Any] | list[Any] | None = Field(
        default=None, alias="parsedModelResponse"
    )
    validation_diagnostics: str | None = Field(default=None, alias="validationDiagnostics")
