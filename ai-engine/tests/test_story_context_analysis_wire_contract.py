import pytest
from openai.lib._parsing import type_to_response_format_param
from pydantic import ValidationError

from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
import app.services.story_context_analysis_generation_service as generation_module
from app.schemas.story_context_analysis import (
    ProviderStoryContextAnalysisResult,
    StoryContextAnalysisResult,
    provider_result_to_internal,
)
from evaluations.loader import load_scenario


def _result() -> StoryContextAnalysisResult:
    return StoryContextAnalysisResult.model_validate({
        "objectiveUnderstanding": {},
        "confidence": {"level": "HIGH", "rationale": "bounded"},
        "provenance": {
            "contextDigest": "a" * 64,
            "promptVersion": "prompt-v1",
            "provider": "openai",
            "modelIdentifier": "gpt-4.1-mini",
            "promptContentDigest": "b" * 64,
            "intentId": "engineering-story-context-analysis",
            "intentVersion": "v1",
        },
        "outputClassification": {
            "entries": [{
                "findingReference": "finding-1",
                "classification": "FACTUAL_EXTRACTION",
                "grounded": True,
                "rationale": "bounded",
            }]
        },
    })


def test_core_callback_serializes_confidence_as_canonical_enum_string():
    payload = _result().model_dump(by_alias=True, mode="json")

    assert payload["confidence"] == "HIGH"
    assert payload["outputClassification"] == {
        "entries": [{
            "findingReference": "finding-1",
            "classification": "FACTUAL_EXTRACTION",
            "grounded": True,
            "rationale": "bounded",
        }]
    }


def test_python_keeps_confidence_rationale_internally():
    assert _result().confidence.level == "HIGH"
    assert _result().confidence.rationale == "bounded"


def _provider_payload(confidence: str) -> dict:
    payload = _result().model_dump(by_alias=True, mode="json")
    payload["confidence"] = confidence
    return payload


def test_production_provider_schema_uses_strict_scalar_confidence():
    response_format = type_to_response_format_param(ProviderStoryContextAnalysisResult)
    schema = response_format["json_schema"]
    confidence = schema["schema"]["properties"]["confidence"]

    assert schema["strict"] is True
    assert confidence == {"enum": ["HIGH", "MEDIUM", "LOW"], "title": "Confidence", "type": "string"}


@pytest.mark.parametrize("level", ["HIGH", "MEDIUM", "LOW"])
def test_provider_result_adapts_raw_scalar_to_internal_confidence(level: str):
    provider_result = ProviderStoryContextAnalysisResult.model_validate(_provider_payload(level))

    internal = provider_result_to_internal(provider_result)

    assert internal.confidence.level == level
    assert internal.confidence.rationale == ""
    assert internal.model_dump(by_alias=True, mode="json")["confidence"] == level


def test_provider_result_rejects_unsupported_confidence():
    with pytest.raises(ValidationError):
        ProviderStoryContextAnalysisResult.model_validate(_provider_payload("VERY_HIGH"))


def test_story_context_prompt_and_retry_use_provider_confidence_shape():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    prompt = StoryContextAnalysisPromptBuilder().build(request)

    assert '"confidence":{"enum":["HIGH","MEDIUM","LOW"]' in prompt.user_message
    assert "confidence must be exactly one scalar value" in prompt.user_message

    retry = StoryContextAnalysisPromptBuilder().corrective_retry(
        prompt, ValueError("confidence must be a scalar enum")
    )
    assert "confidence must remain a scalar HIGH, MEDIUM, or LOW value" in retry.user_message


@pytest.mark.asyncio
async def test_production_generation_uses_provider_model_before_adapting(monkeypatch):
    captured = {}

    class FakeTraces:
        def __init__(self, provider, submission):
            self.traces = []

        async def generate_and_validate(self, prompt, response_model, validator):
            captured["response_model"] = response_model
            provider_result = response_model.model_validate(_provider_payload("HIGH"))
            validator(provider_result)
            return provider_result

    monkeypatch.setattr(generation_module, "InteractionTraceCollector", FakeTraces)
    service = generation_module.StoryContextAnalysisGenerationService.__new__(
        generation_module.StoryContextAnalysisGenerationService
    )
    service._provider = object()
    output = await service._generate_and_validate(object(), {}, {}, FakeTraces(None, None))

    assert captured["response_model"] is ProviderStoryContextAnalysisResult
    assert output.confidence.level == "HIGH"
    assert output.confidence.rationale == ""
