"""Version-independent transport schemas."""

from app.schemas.ai_task import AiTaskSubmissionRequest, AiTaskSubmissionResponse
from app.schemas.ai_task_result import (
    AiProposalResult,
    AiTaskResultAcknowledgement,
    AiTaskResultError,
    AiTaskResultRequest,
)
from app.schemas.insight import InsightGenerationOutput, InsightProposalOutput

__all__ = [
    "AiProposalResult",
    "AiTaskResultAcknowledgement",
    "AiTaskResultError",
    "AiTaskResultRequest",
    "AiTaskSubmissionRequest",
    "AiTaskSubmissionResponse",
    "InsightGenerationOutput",
    "InsightProposalOutput",
]
