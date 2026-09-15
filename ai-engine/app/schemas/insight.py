from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from app.models.intent import InsightType
from app.schemas.story_context_analysis import StoryContextAnalysisResult
from enum import Enum
from app.schemas.typed_reference import ProviderAiReference


class KnowledgeDeltaType(str, Enum):
    NEW = "NEW"
    ENRICHES = "ENRICHES"


class ArchitectureDeltaConclusion(str, Enum):
    NO_MATERIAL_DELTA = "NO_MATERIAL_DELTA"
    DELTAS_PROPOSED = "DELTAS_PROPOSED"
    INSUFFICIENT_EVIDENCE = "INSUFFICIENT_EVIDENCE"


class InsightOutputModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
        str_strip_whitespace=True,
    )


class SynthesisSectionOutput(InsightOutputModel):
    name: str = Field(min_length=1, max_length=255)
    content: str = Field(min_length=1, max_length=10000)


class AnalysisSynthesisOutput(InsightOutputModel):
    title: str = Field(min_length=1, max_length=500)
    sections: list[SynthesisSectionOutput] = Field(min_length=1, max_length=20)
    delta_conclusion: ArchitectureDeltaConclusion = Field(alias="deltaConclusion")
    grounding_references: list[str] = Field(
        default_factory=list, alias="groundingReferences"
    )


class InsightProposalOutput(InsightOutputModel):
    insight_type: InsightType = Field(alias="insightType")
    title: str = Field(min_length=1, max_length=255)
    summary: str = Field(min_length=1, max_length=5000)
    rationale: str = Field(min_length=1, max_length=5000)
    delta_type: KnowledgeDeltaType = Field(alias="deltaType")
    target_insight_id: UUID | None = Field(default=None, alias="targetInsightId")
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

    @model_validator(mode="after")
    def validate_delta_target(self) -> "InsightProposalOutput":
        if self.delta_type == KnowledgeDeltaType.ENRICHES and self.target_insight_id is None:
            raise ValueError("targetInsightId is required when deltaType is ENRICHES")
        if self.delta_type == KnowledgeDeltaType.NEW and self.target_insight_id is not None:
            raise ValueError("targetInsightId must be omitted when deltaType is NEW")
        return self


class InsightGenerationOutput(InsightOutputModel):
    proposals: list[InsightProposalOutput] = Field(max_length=20)
    synthesis: AnalysisSynthesisOutput | None = Field(default=None)
    analysis_result: StoryContextAnalysisResult | None = Field(default=None, alias="analysisResult")


class TypedInsightProposalOutput(InsightOutputModel):
    insight_type: InsightType = Field(alias="insightType")
    title: str = Field(min_length=1, max_length=255)
    summary: str = Field(min_length=1, max_length=5000)
    rationale: str = Field(min_length=1, max_length=5000)
    delta_type: KnowledgeDeltaType = Field(alias="deltaType")
    target_insight_ref: ProviderAiReference | None = Field(default=None, alias="targetInsightRef")
    confidence: float = Field(ge=0.0, le=1.0)
    supporting_fact_refs: list[ProviderAiReference] = Field(alias="supportingFactRefs")
    supporting_observation_refs: list[ProviderAiReference] = Field(alias="supportingObservationRefs")
    evidence_refs: list[ProviderAiReference] = Field(alias="evidenceRefs")

    @field_validator("evidence_refs")
    @classmethod
    def validate_typed_evidence_references(
        cls, values: list[ProviderAiReference]
    ) -> list[ProviderAiReference]:
        return values

    @model_validator(mode="after")
    def validate_typed_delta_target(self) -> "TypedInsightProposalOutput":
        if self.delta_type == KnowledgeDeltaType.ENRICHES and self.target_insight_ref is None:
            raise ValueError("targetInsightRef is required when deltaType is ENRICHES")
        if self.delta_type == KnowledgeDeltaType.NEW and self.target_insight_ref is not None:
            raise ValueError("targetInsightRef must be omitted when deltaType is NEW")
        return self


class TypedAnalysisSynthesisOutput(InsightOutputModel):
    title: str = Field(min_length=1, max_length=500)
    sections: list[SynthesisSectionOutput] = Field(min_length=1, max_length=20)
    delta_conclusion: ArchitectureDeltaConclusion = Field(alias="deltaConclusion")
    grounding_refs: list[ProviderAiReference] = Field(default_factory=list, alias="groundingRefs")


class TypedInsightGenerationOutput(InsightOutputModel):
    proposals: list[TypedInsightProposalOutput] = Field(max_length=20)
    synthesis: TypedAnalysisSynthesisOutput | None = Field(default=None)
