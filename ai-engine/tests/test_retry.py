"""Tests for OpenAiLlmProvider retry logic."""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from openai import APITimeoutError, APIStatusError

from app.providers.openai import OpenAiLlmProvider
from app.providers.base import Prompt, GenerationPolicy, PromptTraceability


@pytest.fixture
def prompt() -> Prompt:
    return Prompt(
        prompt_id="test-prompt",
        prompt_version="v1",
        intent_id="test-intent",
        intent_version="v1",
        system_message="You are a test assistant.",
        user_message="Generate test output.",
        expected_output_schema={},
        generation_policy=GenerationPolicy(
            maximum_insight_count=5,
            maximum_output_size=1000,
        ),
        traceability=PromptTraceability(
            request_id="req-1",
            correlation_id="corr-1",
            ai_task_id="task-1",
            analysis_id="analysis-1",
            intent_id="intent-1",
            intent_version="v1",
            context_digest="digest-1",
            analysis_context_id=None,
            profile_id=None,
            profile_version=None,
        ),
        content_digest="digest-1",
    )


@pytest.mark.asyncio
async def test_no_retry_on_success():
    """Successful call should not retry."""
    client = AsyncMock()
    client.responses.parse = AsyncMock(return_value=MagicMock(
        output_parsed={"proposals": [{
            "schemaVersion": "engineering-event-proposal-v1",
            "category": "ENGINEERING_IMPROVEMENT",
            "title": "test",
            "summary": "s",
            "significance": "low",
            "confidence": 0.8,
            "supportingFactIds": ["11111111-1111-1111-1111-111111111111"],
            "supportingObservationIds": [],
            "evidenceReferences": [],
        }]}
    ))
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="gpt-4.1-mini",
        timeout_seconds=90,
        max_output_tokens=2000,
        max_retries=2,
        client=client,
    )
    from app.schemas.engineering_event import EngineeringEventGenerationOutput
    result = await provider.generate_structured(
        MagicMock(spec=Prompt, system_message="sys", user_message="usr"),
        EngineeringEventGenerationOutput,
    )
    assert client.responses.parse.call_count == 1


@pytest.mark.asyncio
async def test_retry_on_timeout():
    """Timeout error should retry up to max_retries."""
    client = AsyncMock()
    error = APITimeoutError(request=MagicMock())
    client.responses.parse = AsyncMock(side_effect=error)
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="gpt-4.1-mini",
        timeout_seconds=90,
        max_output_tokens=2000,
        max_retries=2,
        client=client,
    )
    from app.schemas.engineering_event import EngineeringEventGenerationOutput
    with pytest.raises(APITimeoutError):
        await provider.generate_structured(
            MagicMock(spec=Prompt, system_message="sys", user_message="usr"),
            EngineeringEventGenerationOutput,
        )
    # 1 initial + 2 retries = 3 total calls
    assert client.responses.parse.call_count == 3


@pytest.mark.asyncio
async def test_no_retry_on_validation_error():
    """Non-retryable errors should not retry."""
    client = AsyncMock()
    client.responses.parse = AsyncMock(side_effect=ValueError("bad output"))
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="gpt-4.1-mini",
        timeout_seconds=90,
        max_output_tokens=2000,
        max_retries=2,
        client=client,
    )
    from app.schemas.engineering_event import EngineeringEventGenerationOutput
    with pytest.raises(ValueError, match="bad output"):
        await provider.generate_structured(
            MagicMock(spec=Prompt, system_message="sys", user_message="usr"),
            EngineeringEventGenerationOutput,
        )
    assert client.responses.parse.call_count == 1


@pytest.mark.asyncio
async def test_retry_on_429():
    """429 rate limit error should retry."""
    client = AsyncMock()
    error = APIStatusError(
        message="rate limited",
        response=MagicMock(status_code=429, headers={}),
        body=None,
    )
    client.responses.parse = AsyncMock(side_effect=error)
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="gpt-4.1-mini",
        timeout_seconds=90,
        max_output_tokens=2000,
        max_retries=1,
        client=client,
    )
    from app.schemas.engineering_event import EngineeringEventGenerationOutput
    with pytest.raises(APIStatusError):
        await provider.generate_structured(
            MagicMock(spec=Prompt, system_message="sys", user_message="usr"),
            EngineeringEventGenerationOutput,
        )
    assert client.responses.parse.call_count == 2


@pytest.mark.asyncio
async def test_no_retry_on_400():
    """400 bad request should not retry."""
    client = AsyncMock()
    error = APIStatusError(
        message="bad request",
        response=MagicMock(status_code=400, headers={}),
        body=None,
    )
    client.responses.parse = AsyncMock(side_effect=error)
    provider = OpenAiLlmProvider(
        api_key="test-key",
        model="gpt-4.1-mini",
        timeout_seconds=90,
        max_output_tokens=2000,
        max_retries=2,
        client=client,
    )
    from app.schemas.engineering_event import EngineeringEventGenerationOutput
    with pytest.raises(APIStatusError):
        await provider.generate_structured(
            MagicMock(spec=Prompt, system_message="sys", user_message="usr"),
            EngineeringEventGenerationOutput,
        )
    assert client.responses.parse.call_count == 1


def test_is_retryable():
    assert OpenAiLlmProvider._is_retryable(APITimeoutError(request=MagicMock())) is True
    assert OpenAiLlmProvider._is_retryable(
        APIStatusError(message="rate limited", response=MagicMock(status_code=429, headers={}), body=None)
    ) is True
    assert OpenAiLlmProvider._is_retryable(
        APIStatusError(message="bad request", response=MagicMock(status_code=400, headers={}), body=None)
    ) is False
    assert OpenAiLlmProvider._is_retryable(ValueError("other")) is False
