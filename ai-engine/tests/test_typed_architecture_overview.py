from pydantic import ValidationError
import pytest

from app.prompts.insight import InsightPromptBuilder
from app.schemas.insight import TypedInsightGenerationOutput
from app.schemas.typed_reference import ProviderAiReference
from tests.intent_fixtures import architecture_overview_v3_intent, prompt_request


def typed_knowledge() -> dict[str, object]:
    return {
        "context": {"selectedInsights": [{"reference": {"type": "INSIGHT", "ref": "insight:1", "scope": "PROJECT"}}]},
        "groundingCandidates": {
            "facts": [{"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"}],
            "observations": [{"type": "OBSERVATION", "ref": "O001", "scope": "ANALYSIS_CONTEXT"}],
            "evidence": [{"type": "REPOSITORY_EVIDENCE", "ref": "git:source:sha", "scope": "REPOSITORY"}],
        },
        "selectionDigest": "a" * 64,
    }


def test_v3_prompt_uses_typed_context_and_candidates_only() -> None:
    request = prompt_request(knowledge=typed_knowledge(), intent=architecture_overview_v3_intent())

    prompt = InsightPromptBuilder().build(request)

    assert "F001" in prompt.user_message
    assert "supportingFactRefs" in prompt.user_message
    assert "canonicalSourceIdentity" not in prompt.user_message


def test_v3_output_rejects_legacy_grounding_fields() -> None:
    with pytest.raises(ValidationError):
        TypedInsightGenerationOutput.model_validate({
            "proposals": [], "synthesis": None, "supportingFactIds": []
        })


def test_typed_reference_serializes_with_core_enum_values() -> None:
    reference = ProviderAiReference(type="FACT", ref="F001", scope="ANALYSIS_CONTEXT")

    assert reference.model_dump(mode="json") == {
        "type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"
    }
