from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


DeliverableType = Literal[
    "PROJECT_DESCRIPTION", "README", "ARCHITECTURE_SUMMARY",
    "PORTFOLIO_DESCRIPTION", "TECHNICAL_SUMMARY", "BLOG_ARTICLE",
]


class DeliverableContract(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid", str_strip_whitespace=True)


class ValidatedInsightSnapshot(DeliverableContract):
    id: UUID
    analysis_id: UUID = Field(alias="analysisId")
    type: str = Field(min_length=1, max_length=100)
    severity: str = Field(min_length=1, max_length=50)
    title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=20_000)


class DeliverableGenerationRequest(DeliverableContract):
    request_id: UUID = Field(alias="requestId")
    project_id: UUID = Field(alias="projectId")
    analysis_id: UUID | None = Field(default=None, alias="analysisId")
    type: DeliverableType
    audience: str = Field(min_length=1, max_length=200)
    style: str = Field(min_length=1, max_length=100)
    language: str = Field(min_length=1, max_length=20)
    additional_guidance: str | None = Field(default=None, alias="additionalGuidance", max_length=1000)
    validated_insights: list[ValidatedInsightSnapshot] = Field(alias="validatedInsights", min_length=1, max_length=100)


class DeliverableGenerationOutput(DeliverableContract):
    title: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=50_000)


class DeliverableGenerationResponse(DeliverableGenerationOutput):
    prompt_version: str = Field(alias="promptVersion")
    prompt_digest: str = Field(alias="promptDigest", pattern=r"^[0-9a-f]{64}$")
    provider: str
    model_identifier: str = Field(alias="modelIdentifier")
