from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException

from app.core.dependencies import get_embedding_service
from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse
from app.services.embedding_service import EmbeddingProviderUnavailable, EmbeddingService

router = APIRouter(prefix="/embeddings", tags=["embeddings"])


@router.post("", response_model=EmbeddingResponse)
async def generate_embeddings(
    request: EmbeddingRequest,
    service: Annotated[EmbeddingService, Depends(get_embedding_service)],
) -> EmbeddingResponse:
    try:
        return await service.embed(request)
    except EmbeddingProviderUnavailable as error:
        raise HTTPException(status_code=503, detail=str(error)) from error
