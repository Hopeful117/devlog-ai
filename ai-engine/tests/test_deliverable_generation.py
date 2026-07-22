from uuid import uuid4

import pytest
from httpx import ASGITransport, AsyncClient

from app.api.deliverables import get_deliverable_generation_service
from app.main import app
from app.prompts.deliverable import DeliverablePromptBuilder
from app.schemas.deliverable import DeliverableGenerationRequest, DeliverableGenerationResponse


def request() -> DeliverableGenerationRequest:
    return DeliverableGenerationRequest.model_validate({
        "requestId": str(uuid4()), "projectId": str(uuid4()), "type": "PROJECT_DESCRIPTION",
        "audience": "Engineers", "style": "Concise", "language": "en",
        "additionalGuidance": "Lead with architecture",
        "validatedInsights": [{
            "id": str(uuid4()), "analysisId": str(uuid4()), "type": "ARCHITECTURAL",
            "severity": "INFO", "title": "Layered Core", "content": "The Core uses Spring Boot.",
        }],
    })


def test_prompt_is_deterministic_and_contains_only_validated_insight_contract() -> None:
    builder = DeliverablePromptBuilder()
    source = request()
    first = builder.build(source)
    same = builder.build(source)
    assert first.prompt_version == "deliverable-generation-prompt-v1"
    assert "BEGIN UNTRUSTED VALIDATED INSIGHTS" in first.user_message
    assert "Facts" not in first.user_message
    assert len(first.content_digest) == 64
    assert first.content_digest == same.content_digest


class RecordingService:
    def __init__(self) -> None:
        self.request: DeliverableGenerationRequest | None = None

    async def generate(self, value: DeliverableGenerationRequest) -> DeliverableGenerationResponse:
        self.request = value
        return DeliverableGenerationResponse(
            title="Generated", content="Validated content", promptVersion="deliverable-generation-prompt-v1",
            promptDigest="a" * 64, provider="mock", modelIdentifier="deterministic-v1",
        )


@pytest.mark.asyncio
async def test_endpoint_accepts_only_typed_validated_insights() -> None:
    service = RecordingService()
    async def override_service() -> RecordingService:
        return service

    app.dependency_overrides[get_deliverable_generation_service] = override_service
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post("/api/v1/deliverables/generate", json=request().model_dump(by_alias=True, mode="json"))
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 200
    assert response.json()["provider"] == "mock"
    assert service.request is not None and len(service.request.validated_insights) == 1


@pytest.mark.asyncio
async def test_endpoint_rejects_empty_insight_input_and_unknown_repository_data() -> None:
    payload = request().model_dump(by_alias=True, mode="json")
    payload["validatedInsights"] = []
    payload["facts"] = []
    service = RecordingService()
    async def override_service() -> RecordingService:
        return service
    app.dependency_overrides[get_deliverable_generation_service] = override_service
    try:
        async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
            response = await client.post("/api/v1/deliverables/generate", json=payload)
    finally:
        app.dependency_overrides.clear()
    assert response.status_code == 422
