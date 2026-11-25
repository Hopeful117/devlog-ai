import json
import logging

from httpx import ASGITransport, AsyncClient
import pytest

from app.core.logging import JsonFormatter, correlation_id_context
from app.main import app


@pytest.mark.asyncio
async def test_correlation_id_is_preserved_in_response() -> None:
    async with AsyncClient(
        transport=ASGITransport(app=app), base_url="http://test"
    ) as client:
        response = await client.get(
            "/health", headers={"X-Correlation-ID": "request-42"}
        )

    assert response.headers["X-Correlation-ID"] == "request-42"


def test_json_formatter_includes_correlation_id() -> None:
    token = correlation_id_context.set("request-42")
    try:
        record = logging.LogRecord(
            "test", logging.INFO, __file__, 1, "processed %s", ("task",), None
        )
        event = json.loads(JsonFormatter().format(record))
    finally:
        correlation_id_context.reset(token)

    assert event["service"] == "devlog-ai-engine"
    assert event["correlationId"] == "request-42"
    assert event["message"] == "processed task"


def test_json_formatter_includes_bounded_output_allowance_metadata() -> None:
    record = logging.LogRecord(
        "test", logging.INFO, __file__, 1, "allowance resolved", (), None
    )
    record.event = "provider_request_output_allowance"
    record.taskId = "task-1"
    record.effectiveOutputTokens = 4096
    record.outputParameterSource = "CORE_AUTHORIZED"

    event = json.loads(JsonFormatter().format(record))

    assert event["event"] == "provider_request_output_allowance"
    assert event["taskId"] == "task-1"
    assert event["effectiveOutputTokens"] == 4096
    assert event["outputParameterSource"] == "CORE_AUTHORIZED"
    assert "prompt" not in event
    assert "credential" not in event


def test_json_formatter_includes_provider_usage_tokens() -> None:
    record = logging.LogRecord("test", logging.INFO, __file__, 1, "usage measured", (), None)
    record.inputTokens = 1200
    record.outputTokens = 4800
    record.reasoningTokens = 3100

    event = json.loads(JsonFormatter().format(record))

    assert event["inputTokens"] == 1200
    assert event["outputTokens"] == 4800
    assert event["reasoningTokens"] == 3100
