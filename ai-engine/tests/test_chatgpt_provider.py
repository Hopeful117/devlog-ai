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
async def test_stream_reports_bounded_incomplete_reason(monkeypatch):
    class IncompleteResponse(StreamResponse):
        async def aiter_lines(self):
            for line in (
                'data: {"type":"response.incomplete","response":{"incomplete_details":{"reason":"max_output_tokens"}}}',
                "",
            ):
                yield line

    class IncompleteContext(StreamContext):
        async def __aenter__(self):
            return IncompleteResponse()

    class IncompleteClient(FakeClient):
        def stream(self, method, url, *, headers, json):
            return IncompleteContext()

    monkeypatch.setattr("app.providers.chatgpt.httpx.AsyncClient", IncompleteClient)
    provider = OpenAiChatGptProvider(
        resolver=None, endpoint="https://api.openai.com/v1/responses",
        model="test", timeout_seconds=1, token_endpoint=None,
        client_id=None, resource=None, max_output_tokens=2000,
    )

    with pytest.raises(ChatGptProviderError, match="reason=max_output_tokens"):
        await provider._stream("fixture-token", {"store": False})


@pytest.mark.asyncio
async def test_provider_uses_core_authorized_model_and_output_limit(monkeypatch, caplog):
    caplog.set_level("INFO")
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
    allowance_events = [
        record for record in caplog.records
        if getattr(record, "event", None) == "provider_request_output_allowance"
    ]
    assert allowance_events
    assert allowance_events[-1].taskId == request.traceability.ai_task_id
    assert allowance_events[-1].effectiveOutputTokens == 4096


@pytest.mark.asyncio
async def test_story_context_rejects_missing_core_output_allowance(monkeypatch):
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
    monkeypatch.setattr("app.providers.chatgpt.required_scopes_granted", lambda scopes: True)
    request = replace(
        InsightPromptBuilder().build(prompt_request()),
        intent_id="engineering-story-context-analysis",
        generation_parameters={},
    )

    with pytest.raises(ValueError, match="not authorized by Core"):
        await provider.generate_structured(request, InsightGenerationOutput)


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
