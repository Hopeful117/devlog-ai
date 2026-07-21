from datetime import datetime, timezone
from uuid import uuid4

from app.schemas.ai_task import AiTaskSubmissionRequest, AiTaskSubmissionResponse


class AiTaskAcceptanceService:
    """Creates the acknowledgement before background processing starts."""

    def accept(
        self,
        request: AiTaskSubmissionRequest,
    ) -> AiTaskSubmissionResponse:
        return AiTaskSubmissionResponse(
            correlation_id=request.correlation_id,
            accepted=True,
            external_job_id=uuid4(),
            accepted_at=datetime.now(timezone.utc),
        )
