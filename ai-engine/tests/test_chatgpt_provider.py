import pytest
import httpx

from app.providers.chatgpt import ChatGptProviderError, OpenAiChatGptProvider


class StreamResponse:
    status_code = 200

    def raise_for_status(self):
        return None

    async def aiter_lines(self):
        for line in (
            "event: response.output_text.delta",
            'data: {"delta":"{\\"ok\\":true}"}',
            "",
            "event: response.completed",
            'data: {"type":"response.completed"}',
            "",
        ):
            yield line


class StreamContext:
    async def __aenter__(self):
        return StreamResponse()

    async def __aexit__(self, *_):
        return False


class FakeClient:
    last_headers = None

    def __init__(self, **kwargs):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_):
        return False

    def stream(self, method, url, *, headers, json):
        self.last_headers = headers
        FakeClient.last_headers = headers
        return StreamContext()


@pytest.mark.asyncio
async def test_stream_requires_completed_event(monkeypatch):
    monkeypatch.setattr("app.providers.chatgpt.httpx.AsyncClient", FakeClient)
    provider = OpenAiChatGptProvider(
        resolver=None, endpoint="https://api.openai.com/v1/responses",
        model="test", timeout_seconds=1, token_endpoint=None,
        client_id=None, resource=None,
    )

    assert await provider._stream("fixture-token", {"store": False}) == '{"ok":true}'
    assert FakeClient.last_headers["Authorization"] == "Bearer fixture-token"


def test_provider_error_message_is_bounded() -> None:
    response = httpx.Response(
        400,
        json={"error": {"code": "invalid_request_error", "type": "invalid_request"}},
    )

    message = OpenAiChatGptProvider._provider_error_message(400, response)

    assert message == (
        "ChatGPT provider request failed "
        "(HTTP 400; code=invalid_request_error, type=invalid_request)"
    )
