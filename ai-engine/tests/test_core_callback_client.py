from datetime import datetime, timezone
from uuid import uuid4

import httpx
import pytest

from app.clients.core_callback_client import CoreCallbackClient
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.schemas.ai_task_result import AiProposalResult, AiTaskResultRequest


@pytest.mark.asyncio
async def test_callback_client_posts_result_to_core() -> None:
    correlation_id = uuid4()
    external_job_id = str(uuid4())

    async def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert request.url.path == (
            f"/api/v1/ai/tasks/{correlation_id}/result"
        )
        payload = await request.aread()
        assert str(correlation_id).encode() in payload
        return httpx.Response(
            200,
            json={
                "correlationId": str(correlation_id),
                "acknowledged": True,
                "duplicate": False,
                "taskStatus": "COMPLETED",
                "proposalCount": 1,
            },
        )

    result = AiTaskResultRequest(
        correlation_id=correlation_id,
        external_job_id=external_job_id,
        status=AiTaskResultStatus.COMPLETED,
        completed_at=datetime.now(timezone.utc),
        proposals=[
            AiProposalResult(
                type=ProposalType.INSIGHT,
                payload={"summary": "Architecture became modular"},
                confidence=0.85,
                supporting_fact_ids=[],
                supporting_observation_ids=[],
                evidence_references=["src/app.py:10"],
            )
        ],
        error=None,
    )
    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
    )

    acknowledgement = await client.send_result(correlation_id, result)

    assert acknowledgement.acknowledged is True
    assert acknowledgement.task_status == "COMPLETED"
    assert acknowledgement.proposal_count == 1
