from typing import Annotated, Any, Literal
from uuid import UUID
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, field_serializer, model_validator


class StoryContextAnalysisContractModel(BaseModel):
    model_config = ConfigDict(
        populate_by_name=True, extra="forbid", str_strip_whitespace=True
    )


class RelationType(str, Enum):
    EXPLICIT = "EXPLICIT"
    TEMPORAL_PROXIMITY = "TEMPORAL_PROXIMITY"
    POSSIBLE_RELEVANCE = "POSSIBLE_RELEVANCE"
    INFERRED_HYPOTHESIS = "INFERRED_HYPOTHESIS"


class CausalClassification(str, Enum):
    EXPLICITLY_DOCUMENTED = "EXPLICITLY_DOCUMENTED"
    STRONGLY_SUPPORTED = "STRONGLY_SUPPORTED"
    NOT_ESTABLISHED = "NOT_ESTABLISHED"


class CausalEvidenceBasis(str, Enum):
    DIRECT_DOCUMENTATION = "DIRECT_DOCUMENTATION"
    MATERIAL_CORROBORATION = "MATERIAL_CORROBORATION"
    CHRONOLOGY_ONLY = "CHRONOLOGY_ONLY"
    TEMPORAL_PROXIMITY_ONLY = "TEMPORAL_PROXIMITY_ONLY"
    SHARED_TOPIC_ONLY = "SHARED_TOPIC_ONLY"
    ARCHITECTURAL_COMPATIBILITY_ONLY = "ARCHITECTURAL_COMPATIBILITY_ONLY"
    POSSIBLE_RELEVANCE_ONLY = "POSSIBLE_RELEVANCE_ONLY"
    CONFLICTING = "CONFLICTING"
    INSUFFICIENT = "INSUFFICIENT"


class CausalEvidenceRole(str, Enum):
    DIRECT_RELATIONSHIP_STATEMENT = "DIRECT_RELATIONSHIP_STATEMENT"
    MATERIAL_RELATIONSHIP_SUPPORT = "MATERIAL_RELATIONSHIP_SUPPORT"
    NON_CAUSAL_CONTEXT = "NON_CAUSAL_CONTEXT"
    CONTRADICTORY_EVIDENCE = "CONTRADICTORY_EVIDENCE"


class EvidenceRef(StoryContextAnalysisContractModel):
    reference: str = Field(min_length=1, max_length=500)
    resource: str | None = Field(default=None, min_length=1, max_length=500)
    role: CausalEvidenceRole | None = None


class CausalEvidenceRef(EvidenceRef):
    role: CausalEvidenceRole


def maximum_defensible_classification(
    evidence_references: list[EvidenceRef],
) -> CausalClassification:
    """Return the structural ceiling; semantic evidence meaning stays model-owned."""

    roles = [reference.role for reference in evidence_references]
    if not roles or any(
        role in {CausalEvidenceRole.NON_CAUSAL_CONTEXT, CausalEvidenceRole.CONTRADICTORY_EVIDENCE}
        for role in roles
    ):
        return CausalClassification.NOT_ESTABLISHED
    if CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT in roles:
        return CausalClassification.EXPLICITLY_DOCUMENTED
    material_references = {
        reference.reference
        for reference in evidence_references
        if reference.role is CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT
    }
    if len(material_references) >= 2:
        return CausalClassification.STRONGLY_SUPPORTED
    return CausalClassification.NOT_ESTABLISHED


class GroundingMetadata(StoryContextAnalysisContractModel):
    evidence_references: list[EvidenceRef] = Field(default_factory=list, alias="evidenceReferences")
    classification: str = Field(default="AI_INTERPRETATION", min_length=1, max_length=50, pattern="^(FACTUAL_EXTRACTION|AI_INTERPRETATION|RECOMMENDATION)$")
    grounded: bool = False
    relation_type: RelationType | None = Field(default=None, alias="relationType")


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


class CausalClaim(StoryContextAnalysisContractModel):
    """A bounded causal interpretation, separate from relationship metadata."""

    source: str = Field(min_length=1, max_length=500)
    target: str = Field(min_length=1, max_length=500)
    causal_classification: CausalClassification = Field(alias="causalClassification")
    evidence_basis: CausalEvidenceBasis = Field(alias="evidenceBasis")
    evidence_references: list[CausalEvidenceRef] = Field(
        default_factory=list, alias="evidenceReferences"
    )
    explanation: str = Field(min_length=1, max_length=5000)

    @model_validator(mode="after")
    def validate_semantics(self) -> "CausalClaim":
        if self.source == self.target:
            raise ValueError("source and target must differ")
        if any(reference.role is None for reference in self.evidence_references):
            raise ValueError("causal evidenceReferences require a role")
        roles = {reference.role for reference in self.evidence_references}
        affirmative = {
            CausalClassification.EXPLICITLY_DOCUMENTED,
            CausalClassification.STRONGLY_SUPPORTED,
        }
        affirmative_basis = {
            CausalEvidenceBasis.DIRECT_DOCUMENTATION,
            CausalEvidenceBasis.MATERIAL_CORROBORATION,
        }
        if self.causal_classification in affirmative:
            if not self.evidence_references:
                raise ValueError("affirmative causal claims require evidenceReferences")
            if (
                self.causal_classification is CausalClassification.STRONGLY_SUPPORTED
                and len({reference.reference for reference in self.evidence_references if reference.role is CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT}) < 2
            ):
                raise ValueError("STRONGLY_SUPPORTED requires multiple distinct MATERIAL_RELATIONSHIP_SUPPORT references")
            expected_basis = (
                CausalEvidenceBasis.DIRECT_DOCUMENTATION
                if self.causal_classification is CausalClassification.EXPLICITLY_DOCUMENTED
                else CausalEvidenceBasis.MATERIAL_CORROBORATION
            )
            if self.evidence_basis is not expected_basis:
                raise ValueError("causalClassification and evidenceBasis are contradictory")
            maximum = maximum_defensible_classification(self.evidence_references)
            if maximum is CausalClassification.NOT_ESTABLISHED:
                raise ValueError("affirmative causal classification exceeds defensible evidence roles")
            if self.causal_classification is CausalClassification.EXPLICITLY_DOCUMENTED and CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT not in roles:
                raise ValueError("EXPLICITLY_DOCUMENTED requires DIRECT_RELATIONSHIP_STATEMENT")
            if CausalEvidenceRole.NON_CAUSAL_CONTEXT in roles or CausalEvidenceRole.CONTRADICTORY_EVIDENCE in roles:
                raise ValueError("affirmative causal classification cannot include non-causal or contradictory evidence")
        elif self.evidence_basis in affirmative_basis:
            raise ValueError("NOT_ESTABLISHED requires non-affirmative evidenceBasis")
        return self


class CausalQuestion(StoryContextAnalysisContractModel):
    source: str = Field(min_length=1, max_length=500)
    target: str = Field(min_length=1, max_length=500)
    relation_asked: str = Field(alias="relationAsked", min_length=1, max_length=100)
    answer_required: bool = Field(alias="answerRequired")

    @model_validator(mode="after")
    def validate_distinct_endpoints(self) -> "CausalQuestion":
        if self.source == self.target:
            raise ValueError("source and target must differ")
        return self


class EvidenceLocator(StoryContextAnalysisContractModel):
    kind: str = Field(min_length=1, max_length=30)
    start_line: int | None = Field(default=None, alias="startLine", ge=1)
    end_line: int | None = Field(default=None, alias="endLine", ge=1)
    heading: str | None = Field(default=None, min_length=1, max_length=500)

    @model_validator(mode="after")
    def validate_locator(self) -> "EvidenceLocator":
        if self.kind == "LINE_RANGE":
            if self.start_line is None or self.end_line is None or self.end_line < self.start_line:
                raise ValueError("LINE_RANGE requires an ordered line range")
        elif self.kind == "SECTION":
            if self.heading is None:
                raise ValueError("SECTION requires a heading")
        else:
            raise ValueError("unsupported evidence locator kind")
        return self


class EvidenceAssertion(StoryContextAnalysisContractModel):
    evidence_reference: CausalEvidenceRef = Field(alias="evidenceReference")
    locator: EvidenceLocator
    resolved_content_digest: str | None = Field(default=None, alias="resolvedContentDigest")
    resolved_content: str | None = Field(default=None, alias="resolvedContent")
    excerpt: str | None = None
    assertion_role: CausalEvidenceRole = Field(alias="assertionRole")

    @model_validator(mode="after")
    def validate_role_alignment(self) -> "EvidenceAssertion":
        if self.evidence_reference.role is not self.assertion_role:
            raise ValueError("evidenceReference role and assertionRole must match")
        return self


class CausalAssessment(StoryContextAnalysisContractModel):
    question: CausalQuestion
    classification: CausalClassification
    evidence_assertions: list[EvidenceAssertion] = Field(
        default_factory=list, alias="evidenceAssertions"
    )
    explanation: str = Field(min_length=1, max_length=5000)


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


class ExecutionMetadata(StoryContextAnalysisContractModel):
    pass


class Provenance(StoryContextAnalysisContractModel):
    context_digest: str = Field(alias="contextDigest", min_length=1, max_length=64)
    prompt_version: str = Field(alias="promptVersion", min_length=1, max_length=100)
    provider: str = Field(min_length=1, max_length=100)
    model_identifier: str = Field(alias="modelIdentifier", min_length=1, max_length=255)
    prompt_content_digest: str = Field(alias="promptContentDigest", min_length=1, max_length=64)
    intent_id: str = Field(alias="intentId", min_length=1, max_length=80)
    intent_version: str = Field(alias="intentVersion", min_length=1, max_length=20)
    guidance_keys: list[str] = Field(default_factory=list, alias="guidanceKeys")
    execution_metadata: ExecutionMetadata = Field(default_factory=ExecutionMetadata, alias="executionMetadata")


class ClassificationEntry(StoryContextAnalysisContractModel):
    finding_reference: str = Field(alias="findingReference", min_length=1, max_length=500)
    classification: str = Field(min_length=1, max_length=50, pattern="^(FACTUAL_EXTRACTION|AI_INTERPRETATION|RECOMMENDATION)$")
    grounded: bool = False
    rationale: str = ""


class OutputClassification(StoryContextAnalysisContractModel):
    entries: list[ClassificationEntry] = Field(default_factory=list)


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
    output_classification: OutputClassification = Field(
        default_factory=OutputClassification, alias="outputClassification"
    )
    causal_claims: list[CausalClaim] = Field(default_factory=list, alias="causalClaims")
    causal_assessment: CausalAssessment | None = Field(default=None, alias="causalAssessment")

    @field_serializer("confidence")
    def serialize_confidence_for_core(self, value: Confidence | str) -> str:
        """Keep the internal rationale out of the canonical Java callback wire shape."""
        return value if isinstance(value, str) else value.level


ProviderConfidence = Literal["HIGH", "MEDIUM", "LOW"]


class ProviderStoryContextAnalysisResult(StoryContextAnalysisResult):
    """Strict provider wire result, separate from the richer internal model."""

    confidence: ProviderConfidence


def provider_result_to_internal(
    result: ProviderStoryContextAnalysisResult,
) -> StoryContextAnalysisResult:
    """Adapt provider confidence without inventing rationale or semantics."""

    payload = result.model_dump(mode="json", by_alias=True, exclude={"confidence"})
    payload["confidence"] = {"level": result.confidence, "rationale": ""}
    return StoryContextAnalysisResult.model_validate(payload)
