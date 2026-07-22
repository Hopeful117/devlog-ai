from typing import Annotated

from fastapi import APIRouter, BackgroundTasks, Depends, status
from fastapi.responses import JSONResponse

from app.core.dependencies import get_task_processing_service
from app.models.ai_task import AiTaskType
from app.schemas.ai_task import (
    AiTaskSubmissionRequest,
    AiTaskSubmissionResponse,
    UnsupportedTaskTypeResponse,
)
from app.services.ai_task_service import AiTaskAcceptanceService
from app.services.task_processing_service import AiTaskProcessingService

router = APIRouter(prefix="/ai/tasks", tags=["ai-tasks"])
SUPPORTED_TASK_TYPES = frozenset({AiTaskType.INSIGHT_GENERATION})


async def get_acceptance_service() -> AiTaskAcceptanceService:
    return AiTaskAcceptanceService()


async def get_processing_service() -> AiTaskProcessingService:
    return get_task_processing_service()


@router.post(
    "",
    response_model=AiTaskSubmissionResponse,
    status_code=status.HTTP_202_ACCEPTED,
    responses={422: {"model": UnsupportedTaskTypeResponse}},
)
async def submit_ai_task(
    request: AiTaskSubmissionRequest,
    background_tasks: BackgroundTasks,
    service: Annotated[AiTaskAcceptanceService, Depends(get_acceptance_service)],
    processing_service: Annotated[
        AiTaskProcessingService,
        Depends(get_processing_service),
    ],
) -> AiTaskSubmissionResponse | JSONResponse:
    if request.task_type not in SUPPORTED_TASK_TYPES:
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_CONTENT,
            content={
                "code": "UNSUPPORTED_TASK_TYPE",
                "taskType": request.task_type.value,
                "supportedTaskTypes": sorted(
                    task_type.value for task_type in SUPPORTED_TASK_TYPES
                ),
            },
        )
    response = service.accept(request)
    background_tasks.add_task(
        processing_service.process,
        request,
        response.external_job_id,
    )
    return response
