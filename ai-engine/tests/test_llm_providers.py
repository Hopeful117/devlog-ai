from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.core.dependencies import build_llm_provider
from app.prompts.insight import InsightPromptBuilder
from tests.intent_fixtures import prompt_request
from app.providers.mock import MockLlmProvider
from app.providers.openai import OpenAiLlmProvider
from app.providers.gemini import GeminiLlmProvider
from app.providers.anthropic import AnthropicLlmProvider
from app.providers.openrouter import OpenRouterLlmProvider
from app.schemas.insight import InsightGenerationOutput


@pytest.mark.asyncio
async def test_mock_provider_is_deterministic_without_configuration() -> None:
    provider = MockLlmProvider()
    request = InsightPromptBuilder().build(prompt_request())

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
            InsightPromptBuilder().build(prompt_request()),
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


def test_provider_factory_can_substitute_gemini() -> None:
    provider = build_llm_provider(
        Settings(llm_provider="gemini", llm_api_key="test-key", llm_model="gemini-test")
    )

    assert isinstance(provider, GeminiLlmProvider)


def test_provider_factory_can_substitute_anthropic() -> None:
    provider = build_llm_provider(
        Settings(llm_provider="anthropic", llm_api_key="test-key", llm_model="claude-test")
    )

    assert isinstance(provider, AnthropicLlmProvider)


def test_provider_factory_can_substitute_openrouter() -> None:
    provider = build_llm_provider(
        Settings(llm_provider="openrouter", llm_api_key="test-key", llm_model="deepseek/deepseek-chat")
    )

    assert isinstance(provider, OpenRouterLlmProvider)


class FakeResponses:
    def __init__(self) -> None:
        self.arguments: dict[str, object] = {}

    async def parse(self, **kwargs: object) -> object:
        self.arguments = kwargs
        return SimpleNamespace(output_parsed={"proposals": []})


class FakeOpenAiClient:
    def __init__(self) -> None:
        self.responses = FakeResponses()


class FakeGeminiModels:
    async def generate_content(self, **kwargs: object) -> object:
        return SimpleNamespace(
            text='{"proposals": []}',
            usage_metadata=SimpleNamespace(
                prompt_token_count=10,
                candidates_token_count=5,
                total_token_count=15,
            ),
        )


class FakeGeminiClient:
    def __init__(self) -> None:
        self.models = FakeGeminiModels()


class FakeAnthropicMessages:
    def __init__(self) -> None:
        self.arguments: dict[str, object] = {}

    async def create(self, **kwargs: object) -> object:
        self.arguments = kwargs
        return SimpleNamespace(
            content=[SimpleNamespace(type="tool_use", input={"proposals": []})],
            usage=SimpleNamespace(input_tokens=10, output_tokens=5),
        )


class FakeAnthropicClient:
    def __init__(self) -> None:
        self.messages = FakeAnthropicMessages()


class FakeOpenRouterCompletions:
    def __init__(self) -> None:
        self.arguments: dict[str, object] = {}

    async def create(self, **kwargs: object) -> object:
        self.arguments = kwargs
        return SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content='{"proposals": []}'))],
            usage=SimpleNamespace(prompt_tokens=10, completion_tokens=5, total_tokens=15),
        )


class FakeOpenRouterClient:
    def __init__(self) -> None:
        self.chat = SimpleNamespace(completions=FakeOpenRouterCompletions())


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
    request = InsightPromptBuilder().build(prompt_request())

    output = await provider.generate_structured(request, InsightGenerationOutput)

    assert output.proposals == []
    assert client.responses.arguments["model"] == "test-model"
    assert client.responses.arguments["text_format"] is InsightGenerationOutput
    assert client.responses.arguments["max_output_tokens"] == 500


@pytest.mark.asyncio
async def test_gemini_provider_uses_structured_json_schema() -> None:
    client = FakeGeminiClient()
    provider = GeminiLlmProvider(
        api_key="test-key",
        model="gemini-test",
        timeout_seconds=1,
        max_output_tokens=500,
        client=client,
    )
    request = InsightPromptBuilder().build(prompt_request())

    output = await provider.generate_structured(request, InsightGenerationOutput)

    assert output.proposals == []


@pytest.mark.asyncio
async def test_anthropic_provider_uses_forced_structured_tool() -> None:
    client = FakeAnthropicClient()
    provider = AnthropicLlmProvider(
        api_key="test-key",
        model="claude-test",
        timeout_seconds=1,
        max_output_tokens=500,
        client=client,
        workspace_id="wrkspc_test",
    )
    request = InsightPromptBuilder().build(prompt_request())

    output = await provider.generate_structured(request, InsightGenerationOutput)

    assert output.proposals == []
    assert client.messages.arguments["model"] == "claude-test"
    assert client.messages.arguments["tool_choice"] == {"type": "tool", "name": "structured_output"}
    assert client.messages.arguments["workspace_id"] == "wrkspc_test"


@pytest.mark.asyncio
async def test_openrouter_provider_uses_chat_structured_output() -> None:
    client = FakeOpenRouterClient()
    provider = OpenRouterLlmProvider(
        api_key="test-key",
        model="deepseek/deepseek-chat",
        timeout_seconds=1,
        max_output_tokens=500,
        client=client,
    )
    request = InsightPromptBuilder().build(prompt_request())

    output = await provider.generate_structured(request, InsightGenerationOutput)

    assert output.proposals == []
    assert client.chat.completions.arguments["model"] == "deepseek/deepseek-chat"
    assert client.chat.completions.arguments["response_format"]["type"] == "json_schema"  # type: ignore[index]
