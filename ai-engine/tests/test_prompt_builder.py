from app.prompts.insight import InsightPromptBuilder


def test_prompt_is_deterministic_and_versioned() -> None:
    builder = InsightPromptBuilder()
    first = builder.build({"facts": [], "project": {"name": "Core"}})
    second = builder.build({"project": {"name": "Core"}, "facts": []})

    assert first == second
    assert first.prompt_version == "insight-generation-v1"
    assert "only the supplied AnalysisContext" in first.system_instructions
    assert '"facts":[]' in first.user_prompt


def test_corrective_prompt_preserves_original_and_adds_feedback() -> None:
    builder = InsightPromptBuilder()
    original = builder.build({"facts": [], "observations": []})

    retry = builder.corrective_retry(original, ValueError("invalid confidence"))

    assert retry.user_prompt == original.user_prompt
    assert retry.corrective_feedback == "invalid confidence"
