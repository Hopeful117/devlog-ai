import pytest
from httpx import ASGITransport, AsyncClient

from app.api.embeddings import router as embeddings_router
from app.core.dependencies import get_embedding_service
from app.main import app
from app.services.embedding_service import EmbeddingService
from app.services.embedding_service import EmbeddingProviderUnavailable
from app.providers.mock import MockLlmProvider
from app.schemas.embedding import EmbeddingRequest


def _make_mock_embedding_service() -> EmbeddingService:
    return EmbeddingService(provider=MockLlmProvider())


def _make_unavailable_embedding_service() -> EmbeddingService:
    class UnavailableProvider:
        async def embed(self, texts: list[str], model: str) -> list[list[float]]:
            raise EmbeddingProviderUnavailable("ChatGPT OAuth is not an embeddings provider")

    return EmbeddingService(provider=UnavailableProvider())


@pytest.mark.asyncio
async def test_embeddings_endpoint_returns_zero_vectors_with_mock_provider() -> None:
    app.dependency_overrides[get_embedding_service] = _make_mock_embedding_service
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/v1/embeddings",
                json={"texts": ["Architecture decision record.", "Java Core boundary."], "model": "text-embedding-3-small"},
            )
        assert response.status_code == 200
        body = response.json()
        assert body["model"] == "text-embedding-3-small"
        assert body["dimensions"] == 1536
        assert len(body["embeddings"]) == 2
        assert body["embeddings"][0] == [0.0] * 1536
        assert body["embeddings"][1] == [0.0] * 1536
    finally:
        app.dependency_overrides.pop(get_embedding_service, None)


@pytest.mark.asyncio
async def test_embeddings_endpoint_rejects_empty_texts() -> None:
    app.dependency_overrides[get_embedding_service] = _make_mock_embedding_service
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/v1/embeddings",
                json={"texts": [], "model": "text-embedding-3-small"},
            )
        assert response.status_code == 422
    finally:
        app.dependency_overrides.pop(get_embedding_service, None)


@pytest.mark.asyncio
async def test_embeddings_endpoint_rejects_too_many_texts() -> None:
    app.dependency_overrides[get_embedding_service] = _make_mock_embedding_service
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/v1/embeddings",
                json={"texts": ["text"] * 101, "model": "text-embedding-3-small"},
            )
        assert response.status_code == 422
    finally:
        app.dependency_overrides.pop(get_embedding_service, None)


@pytest.mark.asyncio
async def test_embeddings_endpoint_rejects_extra_fields() -> None:
    app.dependency_overrides[get_embedding_service] = _make_mock_embedding_service
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/v1/embeddings",
                json={"texts": ["hello"], "model": "text-embedding-3-small", "extra": "field"},
            )
        assert response.status_code == 422
    finally:
        app.dependency_overrides.pop(get_embedding_service, None)


@pytest.mark.asyncio
async def test_embeddings_endpoint_reports_unavailable_provider() -> None:
    app.dependency_overrides[get_embedding_service] = _make_unavailable_embedding_service
    try:
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://test"
        ) as client:
            response = await client.post(
                "/api/v1/embeddings",
                json={"texts": ["hello"], "model": "text-embedding-3-small"},
            )
        assert response.status_code == 503
        assert response.json()["detail"] == "ChatGPT OAuth is not an embeddings provider"
    finally:
        app.dependency_overrides.pop(get_embedding_service, None)


@pytest.mark.asyncio
async def test_mock_provider_returns_deterministic_zero_vectors() -> None:
    provider = MockLlmProvider()
    vectors = await provider.embed(["text A", "text B", "text C"], "any-model")
    assert len(vectors) == 3
    for v in vectors:
        assert len(v) == 1536
        assert all(x == 0.0 for x in v)


@pytest.mark.asyncio
async def test_unavailable_embedding_provider_is_a_bounded_failure() -> None:
    with pytest.raises(EmbeddingProviderUnavailable, match="not an embeddings provider"):
        await _make_unavailable_embedding_service().embed(
            EmbeddingRequest(texts=["text"], model="embedding")
        )
