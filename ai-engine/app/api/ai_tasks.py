from typing import Annotated

from fastapi import APIRouter, Depends, status

from app.schemas.ai_task import AiTaskSubmissionRequest, AiTaskSubmissionResponse
from app.services.ai_task_service import AiTaskAcceptanceService

router = APIRouter(prefix="/ai/tasks", tags=["ai-tasks"])


async def get_acceptance_service() -> AiTaskAcceptanceService:
    return AiTaskAcceptanceService()


@router.post(
    "",
    response_model=AiTaskSubmissionResponse,
    status_code=status.HTTP_202_ACCEPTED,
)
async def submit_ai_task(
    request: AiTaskSubmissionRequest,
    service: Annotated[AiTaskAcceptanceService, Depends(get_acceptance_service)],
) -> AiTaskSubmissionResponse:
    return service.accept(request)
