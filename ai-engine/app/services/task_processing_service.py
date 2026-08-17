from datetime import datetime, timezone
from uuid import UUID

from app.clients.core_callback_client import CoreCallbackClient
from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.schemas.ai_task_result import AiTaskResultError, AiTaskResultRequest
from app.services.insight_generation_service import InsightGenerationService
from app.services.engineering_event_generation_service import EngineeringEventGenerationService
from app.services.decision_generation_service import EngineeringDecisionGenerationService


class AiTaskProcessingService:
    def __init__(
        self,
        insight_generation_service: InsightGenerationService,
        callback_client: CoreCallbackClient,
        engineering_event_service: EngineeringEventGenerationService | None = None,
        decision_generation_service: EngineeringDecisionGenerationService | None = None,
    ) -> None:
        self._insight_generation_service = insight_generation_service
        self._callback_client = callback_client
        self._engineering_event_service = engineering_event_service
        self._decision_generation_service = decision_generation_service

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
        if (submission.task_type == AiTaskType.EVENT_PROPOSAL_GENERATION
                and self._engineering_event_service is not None):
            await self._engineering_event_service.process(submission, external_job_id)
            return
        if (submission.task_type == AiTaskType.DECISION_PROPOSAL_GENERATION
                and self._decision_generation_service is not None):
            await self._decision_generation_service.process(submission, external_job_id)
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
