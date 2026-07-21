from datetime import datetime
from uuid import UUID, uuid4

import pytest
from httpx import ASGITransport, AsyncClient

from app.main import app


@pytest.mark.asyncio
async def test_submit_ai_task_returns_accepted_acknowledgement() -> None:
    correlation_id = uuid4()
    analysis_id = uuid4()
    payload = {
        "correlationId": str(correlation_id),
        "taskType": "DECISION_PROPOSAL_GENERATION",
        "analysisId": str(analysis_id),
        "context": {
            "project": {"id": str(uuid4()), "name": "DevLog AI"},
            "analysis": {"id": str(analysis_id)},
            "facts": [],
            "observations": [],
        },
    }

    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.post("/api/v1/ai/tasks", json=payload)

    assert response.status_code == 202
    body = response.json()
    assert body["correlationId"] == str(correlation_id)
    assert body["accepted"] is True
    assert UUID(body["externalJobId"])
    assert datetime.fromisoformat(body["acceptedAt"].replace("Z", "+00:00"))


@pytest.mark.asyncio
async def test_submit_ai_task_rejects_invalid_payload() -> None:
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as client:
        response = await client.post(
            "/api/v1/ai/tasks",
            json={
                "correlationId": "not-a-uuid",
                "taskType": "UNKNOWN_TASK",
                "analysisId": str(uuid4()),
            },
        )

    assert response.status_code == 422
