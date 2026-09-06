from typing import Annotated, Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class StoryContextAnalysisContractModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True, extra="forbid", str_strip_whitespace=True
    )


class EvidenceRef(StoryContextAnalysisContractModel):
    reference: str = Field(min_length=1, max_length=500)
    resource: str | None = Field(default=None, min_length=1, max_length=500)


class GroundingMetadata(StoryContextAnalysisContractModel):
    evidence_references: list[EvidenceRef] = Field(default_factory=list, alias="evidenceReferences")
    classification: str = Field(default="AI_INTERPRETATION", min_length=1, max_length=50)
    grounded: bool = False


class ObjectiveUnderstanding(StoryContextAnalysisContractModel):
    summary: str = ""
    key_domains: list[str] = Field(default_factory=list, alias="keyDomains")
    primary_technologies: list[str] = Field(default_factory=list, alias="primaryTechnologies")
    architectural_patterns: list[str] = Field(default_factory=list, alias="architecturalPatterns")


class ArchitectureFinding(StoryContextAnalysisContractModel):
    title: str = Field(min_length=1, max_length=255)
    description: str = Field(min_length=1, max_length=10000)
    grounding: GroundingMetadata


class DecisionFinding(StoryContextAnalysisContractModel):
    title: str = Field(min_length=1, max_length=255)
    context: str = Field(min_length=1, max_length=10000)
    choice: str = Field(min_length=1, max_length=10000)
    rationale: str = Field(min_length=1, max_length=10000)
    grounding: GroundingMetadata


class EvidenceFinding(StoryContextAnalysisContractModel):
    title: str = Field(min_length=1, max_length=255)
    description: str = Field(min_length=1, max_length=10000)
    evidence_kind: str = Field(alias="evidenceKind", min_length=1, max_length=100)
    grounding: GroundingMetadata


class HistoricalContextItem(StoryContextAnalysisContractModel):
    title: str = Field(min_length=1, max_length=255)
    description: str = Field(min_length=1, max_length=10000)
    period: str = Field(min_length=1, max_length=100)
    related_commit_hashes: list[str] = Field(default_factory=list, alias="relatedCommitHashes")
    grounding: GroundingMetadata


class ConstraintFinding(StoryContextAnalysisContractModel):
    title: str = Field(min_length=1, max_length=255)
    description: str = Field(min_length=1, max_length=10000)
    constraint_type: str = Field(alias="constraintType", min_length=1, max_length=100)
    grounding: GroundingMetadata


class ImpactedComponentFinding(StoryContextAnalysisContractModel):
    component_name: str = Field(alias="componentName", min_length=1, max_length=255)
    impact_description: str = Field(alias="impactDescription", min_length=1, max_length=10000)
    impact_type: str = Field(alias="impactType", min_length=1, max_length=100)
    grounding: GroundingMetadata


class Uncertainty(StoryContextAnalysisContractModel):
    description: str = Field(min_length=1, max_length=10000)
    reason: str = Field(min_length=1, max_length=1000)
    related_evidence: list[EvidenceRef] = Field(default_factory=list, alias="relatedEvidence")


class MissingInformation(StoryContextAnalysisContractModel):
    description: str = Field(min_length=1, max_length=10000)
    reason: str = Field(min_length=1, max_length=1000)
    suggested_sources: list[str] = Field(default_factory=list, alias="suggestedSources")


class ImplementationQuestion(StoryContextAnalysisContractModel):
    question: str = Field(min_length=1, max_length=1000)
    context: str = Field(min_length=1, max_length=10000)
    related_components: list[str] = Field(default_factory=list, alias="relatedComponents")


class Confidence(StoryContextAnalysisContractModel):
    level: str = Field(min_length=1, max_length=10, pattern="^(HIGH|MEDIUM|LOW)$")
    rationale: str = ""


class Provenance(StoryContextAnalysisContractModel):
    context_digest: str = Field(alias="contextDigest", min_length=1, max_length=64)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=100)
    provider: str = Field(min_length=1, max_length=100)
    model_identifier: str = Field(alias="modelIdentifier", min_length=1, max_length=255)
    prompt_content_digest: str = Field(alias="promptContentDigest", min_length=1, max_length=64)
    intent_id: str = Field(alias="intentId", min_length=1, max_length=80)
    intent_version: str = Field(alias="intentVersion", min_length=1, max_length=20)
    guidance_keys: list[str] = Field(default_factory=list, alias="guidanceKeys")
    execution_metadata: dict[str, Any] = Field(default_factory=dict, alias="executionMetadata")


class OutputClassification(StoryContextAnalysisContractModel):
    finding_reference: str = Field(alias="findingReference", min_length=1, max_length=500)
    classification: str = Field(min_length=1, max_length=50, pattern="^(FACTUAL_EXTRACTION|AI_INTERPRETATION|RECOMMENDATION)$")
    grounded: bool = False
    rationale: str = ""


class StoryContextAnalysisResult(StoryContextAnalysisContractModel):
    objective_understanding: ObjectiveUnderstanding = Field(alias="objectiveUnderstanding")
    architecture_findings: list[ArchitectureFinding] = Field(default_factory=list, alias="architectureFindings")
    decision_findings: list[DecisionFinding] = Field(default_factory=list, alias="decisionFindings")
    evidence_findings: list[EvidenceFinding] = Field(default_factory=list, alias="evidenceFindings")
    historical_context: list[HistoricalContextItem] = Field(default_factory=list, alias="historicalContext")
    constraint_findings: list[ConstraintFinding] = Field(default_factory=list, alias="constraintFindings")
    impacted_component_findings: list[ImpactedComponentFinding] = Field(default_factory=list, alias="impactedComponentFindings")
    uncertainties: list[Uncertainty] = Field(default_factory=list)
    missing_information: list[MissingInformation] = Field(default_factory=list, alias="missingInformation")
    implementation_questions: list[ImplementationQuestion] = Field(default_factory=list, alias="implementationQuestions")
    confidence: Confidence
    provenance: Provenance
    output_classification: list[OutputClassification] = Field(default_factory=list, alias="outputClassification")