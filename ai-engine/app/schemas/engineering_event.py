from enum import Enum
from uuid import UUID
from pydantic import BaseModel, ConfigDict, Field, model_validator


class EngineeringEventCategory(str, Enum):
    FEATURE_INTRODUCTION = "FEATURE_INTRODUCTION"
    BUG_RESOLUTION = "BUG_RESOLUTION"
    ARCHITECTURE_CHANGE = "ARCHITECTURE_CHANGE"
    TECHNOLOGY_CHANGE = "TECHNOLOGY_CHANGE"
    ENGINEERING_IMPROVEMENT = "ENGINEERING_IMPROVEMENT"
    INFRASTRUCTURE_CHANGE = "INFRASTRUCTURE_CHANGE"


class EngineeringEventProposalOutput(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid", str_strip_whitespace=True)
    schema_version: str = Field(alias="schemaVersion", pattern=r"^engineering-event-proposal-v1$")
    category: EngineeringEventCategory
    title: str = Field(min_length=1, max_length=255)
    summary: str = Field(min_length=1, max_length=5000)
    significance: str = Field(min_length=1, max_length=5000)
    confidence: float = Field(ge=0.0, le=1.0)
    supporting_fact_ids: list[UUID] = Field(alias="supportingFactIds")
    supporting_observation_ids: list[UUID] = Field(alias="supportingObservationIds")
    evidence_references: list[str] = Field(alias="evidenceReferences")

    @model_validator(mode="after")
    def require_grounding(self):
        if not (self.supporting_fact_ids or self.supporting_observation_ids or self.evidence_references):
            raise ValueError("Engineering Event proposal requires grounding")
        if any(not value.strip() for value in self.evidence_references):
            raise ValueError("evidence references must not be blank")
        return self


class EngineeringEventGenerationOutput(BaseModel):
    model_config = ConfigDict(extra="forbid")
    proposals: list[EngineeringEventProposalOutput] = Field(max_length=10)
