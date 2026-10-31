from dataclasses import replace
from types import SimpleNamespace

import pytest
import httpx

from app.prompts.insight import InsightPromptBuilder
from app.providers.chatgpt import ChatGptProviderError, OpenAiChatGptProvider
from app.schemas.insight import InsightGenerationOutput
from tests.intent_fixtures import prompt_request


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
        client_id=None, resource=None, max_output_tokens=2000,
    )

    assert await provider._stream("fixture-token", {"store": False}) == '{"ok":true}'
    assert FakeClient.last_headers["Authorization"] == "Bearer fixture-token"


@pytest.mark.asyncio
async def test_provider_uses_core_authorized_model_and_output_limit(monkeypatch):
    captured: dict[str, object] = {}

    class Resolver:
        def resolve_for_task(self, task_id, provider):
            return SimpleNamespace(
                access_token="fixture-token",
                access_token_expires_at=None,
                scopes=["openid", "profile", "email"],
            )

    provider = OpenAiChatGptProvider(
        resolver=Resolver(), endpoint="https://api.openai.com/v1/responses",
        model="configured-model", timeout_seconds=1, token_endpoint=None,
        client_id=None, resource=None, max_output_tokens=2000,
    )

    async def fake_stream(access_token, body):
        captured.update(body)
        return '{"proposals": []}'

    monkeypatch.setattr("app.providers.chatgpt.required_scopes_granted", lambda scopes: True)
    monkeypatch.setattr(provider, "_stream", fake_stream)
    request = replace(
        InsightPromptBuilder().build(prompt_request()),
        model_identifier="authorized-model",
        generation_parameters={"maxOutputTokens": 4096},
    )

    await provider.generate_structured(request, InsightGenerationOutput)

    assert captured["model"] == "authorized-model"
    assert captured["max_output_tokens"] == 4096


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
