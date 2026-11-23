import logging
from typing import Protocol

from app.schemas.embedding import EmbeddingRequest, EmbeddingResponse

logger = logging.getLogger(__name__)


class EmbeddingProviderUnavailable(RuntimeError):
    """Raised when the configured LLM cannot serve embedding requests."""


class EmbeddingProvider(Protocol):
    async def embed(self, texts: list[str], model: str) -> list[list[float]]: ...

    @property
    def embedding_model_identifier(self) -> str: ...


class EmbeddingService:
    def __init__(self, provider: EmbeddingProvider) -> None:
        self._provider = provider

    async def embed(self, request: EmbeddingRequest) -> EmbeddingResponse:
        vectors = await self._provider.embed(request.texts, request.model)
        dimensions = len(vectors[0]) if vectors else 0
        logger.info(
            "Embedding generated",
            extra={
                "event": "embedding_generated",
                "textCount": len(request.texts),
                "model": request.model,
                "dimensions": dimensions,
            },
        )
        return EmbeddingResponse(
            embeddings=vectors,
            model=request.model,
            dimensions=dimensions,
        )
