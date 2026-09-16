from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from evaluations.loader import load_scenario


SCENARIO_ID = "engineering-story-context-analysis-v1"


def test_story_context_prompt_uses_only_java_authored_grounding_contract() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    builder = StoryContextAnalysisPromptBuilder()

    prompt = builder.build(request)
    assert (
        'GROUNDING CONTRACT (AUTHORITATIVE)\n'
        '{"allowedEvidenceReferences":["docker-compose.yml"]}\n\n'
        'SELECTED KNOWLEDGE'
    ) in prompt.user_message

    empty_contract_prompt = builder.build(
        request.model_copy(update={"grounding_contract": {}})
    )
    assert (
        'GROUNDING CONTRACT (AUTHORITATIVE)\n{}\n\nSELECTED KNOWLEDGE'
    ) in empty_contract_prompt.user_message


def test_story_context_prompt_requires_evidence_ledger_and_safe_retry() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request.model_copy(
        update={
            "grounding_contract": {
                "allowedEvidenceReferences": ["docker-compose.yml"],
                "causalAnswerRequired": True,
                "causalRelationship": {"source": "ADR-A", "target": "Component-B"},
            }
        }
    )
    builder = StoryContextAnalysisPromptBuilder()
    prompt = builder.build(request)
    assert "Build a causal evidence ledger in this order" in prompt.system_message
    assert "causalAnswerRequired=true" in prompt.user_message
    retry = builder.corrective_retry(prompt, ValueError("classification exceeds defensible evidence level"))
    assert "SEMANTIC_SUPPORT_ERROR" in retry.user_message
    assert "downgrade the claim to NOT_ESTABLISHED" in retry.user_message
    assert "do not invent evidence" in retry.user_message
