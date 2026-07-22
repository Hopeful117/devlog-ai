from datetime import datetime, timezone
from uuid import UUID, uuid4

import httpx
import pytest

from app.clients.core_callback_client import CoreCallbackClient, CoreCallbackError
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


def callback_result(correlation_id: UUID) -> AiTaskResultRequest:
    return AiTaskResultRequest(
        correlation_id=correlation_id,
        external_job_id="job-42",
        status=AiTaskResultStatus.COMPLETED,
        completed_at=datetime.now(timezone.utc),
        proposals=[],
        error=None,
    )


def acknowledgement(correlation_id: UUID) -> dict[str, object]:
    return {
        "correlationId": str(correlation_id),
        "acknowledged": True,
        "duplicate": False,
        "taskStatus": "COMPLETED",
        "proposalCount": 0,
    }


def not_ready() -> httpx.Response:
    return httpx.Response(
        409,
        json={
            "code": "AI_TASK_NOT_READY",
            "currentStatus": "CREATED",
            "message": "AI task cannot receive a result yet.",
        },
    )


@pytest.mark.asyncio
async def test_callback_retries_not_ready_then_succeeds_with_same_payload() -> None:
    correlation_id = uuid4()
    requests: list[bytes] = []
    delays: list[float] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        requests.append(await request.aread())
        if len(requests) < 3:
            return not_ready()
        return httpx.Response(200, json=acknowledgement(correlation_id))

    async def record_sleep(delay: float) -> None:
        delays.append(delay)

    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
        max_attempts=5,
        initial_delay_ms=100,
        max_delay_ms=1_000,
        sleep=record_sleep,
    )

    result = await client.send_result(
        correlation_id,
        callback_result(correlation_id),
    )

    assert result.acknowledged is True
    assert len(requests) == 3
    assert requests[0] == requests[1] == requests[2]
    assert all(str(correlation_id).encode() in payload for payload in requests)
    assert delays == [0.1, 0.2]


@pytest.mark.asyncio
async def test_callback_reports_bounded_error_after_retry_exhaustion() -> None:
    correlation_id = uuid4()
    attempts = 0
    delays: list[float] = []

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(
            409,
            json={
                "code": "AI_TASK_NOT_READY",
                "currentStatus": "CREATED",
                "padding": "x" * 2_000,
            },
        )

    async def record_sleep(delay: float) -> None:
        delays.append(delay)

    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
        max_attempts=5,
        initial_delay_ms=100,
        max_delay_ms=1_000,
        sleep=record_sleep,
    )

    with pytest.raises(CoreCallbackError) as raised:
        await client.send_result(correlation_id, callback_result(correlation_id))

    message = str(raised.value)
    assert attempts == 5
    assert delays == [0.1, 0.2, 0.4, 0.8]
    assert "status=409" in message
    assert f"correlationId={correlation_id}" in message
    assert "attempts=5" in message
    assert len(message) < 1_200


@pytest.mark.asyncio
@pytest.mark.parametrize("status_code", [400, 422])
async def test_callback_does_not_retry_contract_errors(status_code: int) -> None:
    correlation_id = uuid4()
    attempts = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(status_code, json={"code": "INVALID_RESULT"})

    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
        max_attempts=5,
        sleep=lambda _: pytest.fail("sleep must not be called"),
    )

    with pytest.raises(CoreCallbackError):
        await client.send_result(correlation_id, callback_result(correlation_id))

    assert attempts == 1


@pytest.mark.asyncio
async def test_callback_does_not_retry_terminal_conflict() -> None:
    correlation_id = uuid4()
    attempts = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal attempts
        attempts += 1
        return httpx.Response(
            409,
            json={
                "code": "AI_TASK_TERMINAL_CONFLICT",
                "currentStatus": "FAILED",
            },
        )

    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
        max_attempts=5,
    )

    with pytest.raises(CoreCallbackError):
        await client.send_result(correlation_id, callback_result(correlation_id))

    assert attempts == 1


@pytest.mark.asyncio
async def test_callback_rejects_mismatched_identifier_without_http_call() -> None:
    calls = 0

    async def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        return httpx.Response(200)

    client = CoreCallbackClient(
        "http://core.test",
        transport=httpx.MockTransport(handler),
    )

    with pytest.raises(ValueError):
        await client.send_result(uuid4(), callback_result(uuid4()))

    assert calls == 0
