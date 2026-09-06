"""Version-independent transport schemas."""

from app.schemas.ai_task import AiTaskSubmissionRequest, AiTaskSubmissionResponse
from app.schemas.ai_task_result import (
    AiProposalResult,
    AiTaskResultAcknowledgement,
    AiTaskResultError,
    AiTaskResultRequest,
)
from app.schemas.insight import InsightGenerationOutput, InsightProposalOutput
from app.schemas.story_context_analysis import (
    StoryContextAnalysisResult,
    EvidenceRef,
    ObjectiveUnderstanding,
    ArchitectureFinding,
    DecisionFinding,
    EvidenceFinding,
    HistoricalContextItem,
    ConstraintFinding,
    ImpactedComponentFinding,
    Uncertainty,
    MissingInformation,
    ImplementationQuestion,
    Confidence,
    Provenance,
    OutputClassification,
)

__all__ = [
    "AiProposalResult",
    "AiTaskResultAcknowledgement",
    "AiTaskResultError",
    "AiTaskResultRequest",
    "AiTaskSubmissionRequest",
    "AiTaskSubmissionResponse",
    "InsightGenerationOutput",
    "InsightProposalOutput",
    "StoryContextAnalysisResult",
    "EvidenceRef",
    "ObjectiveUnderstanding",
    "ArchitectureFinding",
    "DecisionFinding",
    "EvidenceFinding",
    "HistoricalContextItem",
    "ConstraintFinding",
    "ImpactedComponentFinding",
    "Uncertainty",
    "MissingInformation",
    "ImplementationQuestion",
    "Confidence",
    "Provenance",
    "OutputClassification",
]
