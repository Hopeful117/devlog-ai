from typing import Annotated

from fastapi import APIRouter, Depends

from app.core.dependencies import get_deliverable_generation_service
from app.schemas.deliverable import DeliverableGenerationRequest, DeliverableGenerationResponse
from app.services.deliverable_generation_service import DeliverableGenerationService

router = APIRouter(prefix="/deliverables", tags=["deliverables"])


@router.post("/generate", response_model=DeliverableGenerationResponse)
async def generate_deliverable(
    request: DeliverableGenerationRequest,
    service: Annotated[DeliverableGenerationService, Depends(get_deliverable_generation_service)],
) -> DeliverableGenerationResponse:
    return await service.generate(request)
