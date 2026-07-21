from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Depends, status

from app.core.dependencies import get_task_processing_service
from app.schemas.ai_task import AiTaskSubmissionRequest, AiTaskSubmissionResponse
from app.services.ai_task_service import AiTaskAcceptanceService
from app.services.task_processing_service import AiTaskProcessingService

router = APIRouter(prefix="/ai/tasks", tags=["ai-tasks"])


async def get_acceptance_service() -> AiTaskAcceptanceService:
    return AiTaskAcceptanceService()


async def get_processing_service() -> AiTaskProcessingService:
    return get_task_processing_service()


@router.post(
    "",
    response_model=AiTaskSubmissionResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def submit_ai_task(
    request: AiTaskSubmissionRequest,
    background_tasks: BackgroundTasks,
    service: Annotated[AiTaskAcceptanceService, Depends(get_acceptance_service)],
    processing_service: Annotated[
        AiTaskProcessingService,
        Depends(get_processing_service),
    ],
) -> AiTaskSubmissionResponse:
    response = service.accept(request)
    background_tasks.add_task(
        processing_service.process,
        request,
        response.external_job_id,
    )
    return response
