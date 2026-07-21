from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class InsightOutputModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class InsightProposalOutput(InsightOutputModel):
    title: str = Field(min_length=1, max_length=255)
    summary: str = Field(min_length=1, max_length=5000)
    rationale: str = Field(min_length=1, max_length=5000)
    confidence: float = Field(ge=0.0, le=1.0)
    supporting_fact_ids: list[UUID] = Field(alias="supportingFactIds")
    supporting_observation_ids: list[UUID] = Field(
        alias="supportingObservationIds"
    )
    evidence_references: list[str] = Field(alias="evidenceReferences")

    @field_validator("evidence_references")
    @classmethod
    def validate_evidence_references(cls, values: list[str]) -> list[str]:
        if any(not value.strip() for value in values):
            raise ValueError("evidence references must not be blank")
        return values


class InsightGenerationOutput(InsightOutputModel):
    proposals: list[InsightProposalOutput] = Field(max_length=20)
