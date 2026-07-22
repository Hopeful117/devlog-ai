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
