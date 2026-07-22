import pytest

from app.prompts.insight import InsightPromptBuilder, UnsupportedPromptTemplateError
from tests.intent_fixtures import describe_project_intent


def test_prompt_is_deterministic_intent_driven_and_versioned() -> None:
    builder = InsightPromptBuilder()
    intent = describe_project_intent()
    first = builder.build(intent, {"facts": [], "project": {"name": "Core"}})
    second = builder.build(intent, {"project": {"name": "Core"}, "facts": []})

    assert first == second
    assert first.prompt_version == "intent-driven-insight-v1:describe-project-prompt-v1"
    assert "only the supplied AnalysisContext" in first.system_instructions
    assert '"id":"describe-project"' in first.user_prompt
    assert "PROJECT_PRESENTATION" in first.user_prompt
    assert '"facts":[]' in first.user_prompt


def test_corrective_prompt_preserves_original_and_adds_feedback() -> None:
    builder = InsightPromptBuilder()
    original = builder.build(describe_project_intent(), {"facts": [], "observations": []})
    retry = builder.corrective_retry(original, ValueError("invalid insight type"))
    assert retry.user_prompt == original.user_prompt
    assert retry.corrective_feedback == "invalid insight type"


def test_unknown_prompt_template_is_rejected() -> None:
    intent = describe_project_intent().model_copy(
        update={"prompt_template": "free-form-template"}
    )
    with pytest.raises(UnsupportedPromptTemplateError):
        InsightPromptBuilder().build(intent, {})
