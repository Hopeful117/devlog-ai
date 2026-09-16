from app.schemas.story_context_analysis import StoryContextAnalysisResult


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
