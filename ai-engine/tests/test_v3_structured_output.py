import pytest
from pydantic import ValidationError

from evaluations.product_value.v3_structured_output import (
    ProviderParseFailure,
    ProviderStoryContextAnalysisResult,
    capture_then_parse,
    capture_sdk_response,
    provider_result_to_internal,
    provider_schema,
)


def _payload() -> dict:
    return {
        "id": "resp-test",
        "model": "gpt-4.1-mini",
        "status": "completed",
        "output": [{"type": "message", "content": [{"type": "output_text", "text": "payload"}]}],
    }


class FakeResponse:
    def __init__(self, payload: dict, text: str | None = None) -> None:
        self.payload = payload
        self.output_text = text
        self.usage = None

    def model_dump(self, **_kwargs):
        return self.payload


def test_provider_schema_uses_canonical_confidence_string():
    schema = provider_schema()

    assert schema["properties"]["confidence"]["type"] == "string"
    assert schema["properties"]["confidence"]["enum"] == ["HIGH", "MEDIUM", "LOW"]


def test_provider_confidence_maps_to_internal_default_rationale():
    payload = {
        "objectiveUnderstanding": {},
        "confidence": "HIGH",
        "provenance": {
            "contextDigest": "a" * 64,
            "promptVersion": "prompt-v1",
            "provider": "openai",
            "modelIdentifier": "gpt-4.1-mini",
            "promptContentDigest": "b" * 64,
            "intentId": "engineering-story-context-analysis",
            "intentVersion": "v1",
        },
    }
    provider_result = ProviderStoryContextAnalysisResult.model_validate(payload)

    internal = provider_result_to_internal(provider_result)

    assert internal.confidence.level == "HIGH"
    assert internal.confidence.rationale == ""


def test_invalid_confidence_representation_is_rejected_by_provider_model():
    with pytest.raises(ValidationError):
        ProviderStoryContextAnalysisResult.model_validate({
            "objectiveUnderstanding": {},
            "confidence": {"level": "HIGH", "rationale": "not provider wire"},
        })


def test_complete_response_is_captured_before_invalid_confidence_parse():
    response = FakeResponse(_payload(), text='{"confidence":{"level":"HIGH"}}')

    with pytest.raises(ProviderParseFailure) as failure:
        capture_then_parse(response, lambda _: ProviderStoryContextAnalysisResult.model_validate({
            "objectiveUnderstanding": {}, "confidence": "HIGH",
        }))

    assert failure.value.capture.raw_provider_response == _payload()
    assert failure.value.capture.provider_text_output == '{"confidence":{"level":"HIGH"}}'
    assert failure.value.capture.raw_response_capture["rawProviderResponse"] == _payload()
    assert "confidence" in str(failure.value.cause)


@pytest.mark.parametrize("text", ["{\"confidence\":", "{\"confidence\":\"HIGH\""])
def test_invalid_or_truncated_output_is_preserved_before_parser_failure(text: str):
    response = FakeResponse(_payload(), text=text)

    with pytest.raises(ProviderParseFailure) as failure:
        capture_then_parse(response, lambda _: (_ for _ in ()).throw(ValueError("invalid JSON")))

    assert failure.value.capture.provider_text_output == text
    assert failure.value.capture.raw_provider_response == _payload()


def test_parser_exception_is_not_replaced_by_field_level_input():
    response = FakeResponse(_payload(), text="complete-provider-output")
    parser_error = ValueError("field input was HIGH")

    with pytest.raises(ProviderParseFailure) as failure:
        capture_then_parse(response, lambda _: (_ for _ in ()).throw(parser_error))

    assert failure.value.cause is parser_error
    assert failure.value.capture.provider_text_output == "complete-provider-output"
