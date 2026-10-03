import pytest

from app.prompts.story_context_analysis import PromptConstructionError, StoryContextAnalysisPromptBuilder
from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult
from evaluations.loader import load_scenario


SCENARIO_ID = "engineering-story-context-analysis-v1"


def test_story_context_prompt_uses_only_java_authored_grounding_contract() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    builder = StoryContextAnalysisPromptBuilder()

    prompt = builder.build(request)
    assert "groundingContractVersion\":\"story-context-grounding/v1" in prompt.user_message
    assert "allowedGroundingReferences" in prompt.user_message
    assert "SELECTED KNOWLEDGE" in prompt.user_message

    empty_contract_prompt = builder.build(
        request.model_copy(update={"grounding_contract": {}})
    )
    assert (
        'GROUNDING CONTRACT (AUTHORITATIVE)\n{}\n\nSELECTED KNOWLEDGE'
    ) in empty_contract_prompt.user_message


def test_story_context_prompt_builder_requires_projection_digest() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request.model_copy(
        update={"projection_digest": None}
    )

    with pytest.raises(PromptConstructionError, match="projectionDigest is invalid"):
        StoryContextAnalysisPromptBuilder().build(request)


def test_story_context_prompt_requires_evidence_ledger_and_safe_retry() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request.model_copy(
        update={
            "grounding_contract": {
                "groundingContractVersion": "story-context-grounding/v1",
                "allowedGroundingReferences": [{"type":"REPOSITORY_EVIDENCE","ref":"docker-compose.yml","scope":"PROJECT_REVISION","project":"test-project","revision":"abc123","provenance":{"sourceType":"REPOSITORY"},"trust":"TECHNICAL_EVIDENCE","coreReference":"docker-compose.yml","taskReference":"docker-compose.yml"}],
                "causalAnswerRequired": True,
                "causalContractVersion": "V2",
                "causalQuestion": {"source":"ADR-A","target":"Component-B","relationAsked":"CAUSAL","answerRequired": True},
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


def test_question_aware_projection_is_presented_as_primary_prompt_objective() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    projection = dict(request.selected_knowledge)
    for key in ("request", "requestEcho", "scope"):
        projection[key] = {**projection[key], "question": "Which evidence explains the polling behavior?"}
    projection["contractVersion"] = "story-context-agent-projection/v2"
    projection["projectionVersion"] = "sca/v2"
    projection["policy"] = {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v2"}

    from app.schemas.story_context_agent_projection import _projection_digest
    projection["projectionDigest"] = _projection_digest(projection)
    prompt = StoryContextAnalysisPromptBuilder().build(
        request.model_copy(update={"selected_knowledge": projection})
    )

    assert "QUESTION\nWhich evidence explains the polling behavior?" in prompt.user_message
    assert "Answer the QUESTION before providing broader context" in prompt.user_message
    assert "question-specific evidence" in prompt.user_message
    assert "first sentence of objectiveUnderstanding.summary" in prompt.user_message
    assert "implementationQuestions item" in prompt.user_message
    assert "first sentence of objectiveUnderstanding.summary must say NOT_ESTABLISHED" in prompt.user_message


def test_truncated_projection_requires_explicit_abstention() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    projection = dict(request.selected_knowledge)
    for key in ("request", "requestEcho", "scope"):
        projection[key] = {**projection[key], "question": "What exact implementation decision explains the runtime dependency?"}
    projection["accounting"] = {**projection["accounting"], "truncated": True}
    projection["contractVersion"] = "story-context-agent-projection/v2"
    projection["projectionVersion"] = "sca/v2"
    projection["policy"] = {"compositionVersion": "ctx/v1", "projectionVersion": "sca/v2"}

    from app.schemas.story_context_agent_projection import _projection_digest
    projection["projectionDigest"] = _projection_digest(projection)
    prompt = StoryContextAnalysisPromptBuilder().build(
        request.model_copy(update={"selected_knowledge": projection})
    )

    assert "TRUNCATED CONTEXT SAFETY RULE (AUTHORITATIVE)" in prompt.user_message
    assert "first sentence of objectiveUnderstanding.summary MUST begin with NOT_ESTABLISHED" in prompt.user_message
    assert "A mention of Docker Compose" in prompt.user_message


def test_story_context_contract_exposes_implementation_preparation() -> None:
    schema = ProviderStoryContextAnalysisResult.model_json_schema()

    assert "implementationPreparation" in schema["properties"]
    preparation = schema["$defs"]["ImplementationPreparation"]
    assert set(preparation["properties"]) == {"affectedFiles", "constraints", "testPlan"}
