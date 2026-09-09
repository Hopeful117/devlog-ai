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
