from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.core.dependencies import build_llm_provider
from app.prompts.insight import InsightPromptBuilder
from tests.intent_fixtures import describe_project_intent
from app.providers.mock import MockLlmProvider
from app.providers.openai import OpenAiLlmProvider
from app.schemas.insight import InsightGenerationOutput


@pytest.mark.asyncio
async def test_mock_provider_is_deterministic_without_configuration() -> None:
    provider = MockLlmProvider()
    request = InsightPromptBuilder().build(describe_project_intent(), {"facts": [], "observations": []})

    output = await provider.generate_structured(
        request,
        InsightGenerationOutput,
    )

    assert output.proposals == []
    assert provider.requests == [request]


@pytest.mark.asyncio
async def test_mock_provider_validates_configured_output() -> None:
    provider = MockLlmProvider([{"proposals": [{"title": "incomplete"}]}])

    with pytest.raises(ValidationError):
        await provider.generate_structured(
            InsightPromptBuilder().build(describe_project_intent(), {}),
            InsightGenerationOutput,
        )


def test_provider_factory_defaults_to_mock() -> None:
    provider = build_llm_provider(Settings())

    assert isinstance(provider, MockLlmProvider)


def test_provider_factory_can_substitute_openai() -> None:
    provider = build_llm_provider(
        Settings(llm_provider="openai", llm_api_key="test-key")
    )

    assert isinstance(provider, OpenAiLlmProvider)


class FakeResponses:
    def __init__(self) -> None:
        self.arguments: dict[str, object] = {}

    async def parse(self, **kwargs: object) -> object:
        self.arguments = kwargs
        return SimpleNamespace(output_parsed={"proposals": []})


class FakeOpenAiClient:
    def __init__(self) -> None:
        self.responses = FakeResponses()


@pytest.mark.asyncio
async def test_openai_provider_uses_structured_response_model() -> None:
    client = FakeOpenAiClient()
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="test-model",
        timeout_seconds=1,
        max_output_tokens=500,
        client=client,  # type: ignore[arg-type]
    )
    request = InsightPromptBuilder().build(describe_project_intent(), {"facts": [], "observations": []})

    output = await provider.generate_structured(request, InsightGenerationOutput)

    assert output.proposals == []
    assert client.responses.arguments["model"] == "test-model"
    assert client.responses.arguments["text_format"] is InsightGenerationOutput
    assert client.responses.arguments["max_output_tokens"] == 500
