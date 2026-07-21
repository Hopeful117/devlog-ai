from datetime import datetime, timezone
from uuid import UUID

from app.clients.core_callback_client import CoreCallbackClient
from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import AiTaskResultError, AiTaskResultRequest
from app.services.insight_generation_service import InsightGenerationService


class AiTaskProcessingService:
    def __init__(
        self,
        insight_generation_service: InsightGenerationService,
        callback_client: CoreCallbackClient,
    ) -> None:
        self._insight_generation_service = insight_generation_service
        self._callback_client = callback_client

    async def process(
        self,
        submission: AiTaskSubmissionRequest,
        external_job_id: UUID,
    ) -> None:
        if submission.task_type == AiTaskType.INSIGHT_GENERATION:
            await self._insight_generation_service.process(
                submission,
                external_job_id,
            )
            return

        await self._callback_client.send_result(
            submission.correlation_id,
            AiTaskResultRequest(
                correlation_id=submission.correlation_id,
                external_job_id=str(external_job_id),
                status=AiTaskResultStatus.FAILED,
                completed_at=datetime.now(timezone.utc),
                proposals=[],
                error=AiTaskResultError(
                    code="UNSUPPORTED_TASK_TYPE",
                    message=(
                        f"Task type {submission.task_type.value} is not "
                        "implemented by this AI Engine version"
                    ),
                ),
            ),
        )
