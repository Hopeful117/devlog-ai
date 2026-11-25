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
        'GROUNDING CONTRACT (AUTHORITATIVE)\n{}\n\n'
        'REQUESTED FILE SCOPE (AUTHORITATIVE)\n'
        '{"files":[],"groundingReferences":"ALL_AUTHORIZED_REFERENCES"}\n\n'
        'SELECTED KNOWLEDGE'
    ) in empty_contract_prompt.user_message


def test_investigation_prompt_bounds_range_reads_and_empty_evidence_final() -> None:
    base_request = load_scenario(SCENARIO_ID).prompt_request
    request = base_request.model_copy(update={
        "metadata": {
            **base_request.metadata,
            "investigation": {
                "initialized": True,
                "protocolVersion": "story-context-agent-protocol/v2",
                "executionId": str(base_request.ai_task_id),
                "parentTaskId": str(base_request.ai_task_id),
                "scope": {"projectSlug": "devlog-ai", "revision": "revision-1", "allowedPathPrefixes": ["docs"]},
                "budget": {
                    "maximumSteps": 2, "maximumRetrievalCalls": 2, "maximumEvidenceItems": 10,
                    "maximumBytes": 10000, "maximumTokens": 2000, "maximumElapsedMillis": 60000,
                    "maximumProviderOutputTokens": 4000,
                },
                "usage": {
                    "steps": 0, "retrievalCalls": 0, "evidenceItems": 0,
                    "bytes": 0, "tokens": 0, "elapsedMillis": 0, "providerOutputTokens": 0,
                },
            },
        },
    })

    prompt = StoryContextAnalysisPromptBuilder().build_investigation(request)

    assert "at most 500 lines" in prompt.user_message
    assert "After any capability result, never return PLAN" in prompt.user_message
    assert "Never use READ_EVIDENCE_RANGE to request a whole document" in prompt.user_message
    assert "If all capability results contain no evidence" in prompt.user_message
    assert "leave its summary empty and its grounding null" in prompt.user_message


def test_requested_file_scope_lists_only_file_grounding_references() -> None:
    builder = StoryContextAnalysisPromptBuilder()

    scope = builder._requested_file_scope(
        {"request": {"files": ["docker-compose.yml"]}},
        {
            "allowedGroundingReferences": [
                {
                    "ref": "docker-compose.yml",
                    "provenance": {"originatingFile": "docker-compose.yml"},
                },
                {
                    "ref": "docs/architecture.md",
                    "provenance": {"originatingFile": "docs/architecture.md"},
                },
            ]
        },
    )

    assert '"files":["docker-compose.yml"]' in scope
    assert '"docker-compose.yml":["docker-compose.yml"]' in scope
    assert "docs/architecture.md" not in scope
    assert "Every finding must cite at least one reference" in scope


def test_story_context_prompt_preserves_core_authorized_generation_parameters() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request.model_copy(
        update={
            "allowed_parameters": {"maxOutputTokens": 4096},
            "model_identifier": "authorized-model",
        }
    )

    prompt = StoryContextAnalysisPromptBuilder().build(request)

    assert prompt.generation_parameters == {"maxOutputTokens": 4096}
    assert prompt.model_identifier == "authorized-model"
    retry = StoryContextAnalysisPromptBuilder().corrective_retry(
        prompt, ValueError("invalid output")
    )
    assert retry.generation_parameters == {"maxOutputTokens": 4096}
    assert retry.model_identifier == "authorized-model"


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
    assert "INTENT\nID: engineering-story-context-analysis" in prompt.user_message
    assert "QUESTION\nengineering-story-context-analysis" not in prompt.user_message
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


def test_corrective_retry_preserves_bounded_grounding_failure_code() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    prompt = StoryContextAnalysisPromptBuilder().build(request)

    class RequestedFileScopeError(ValueError):
        failure_category = "GROUNDING_ERROR"
        failure_code = "GROUNDING_REQUESTED_FILE_SCOPE"

    retry = StoryContextAnalysisPromptBuilder().corrective_retry(
        prompt, RequestedFileScopeError("finding is outside requested scope")
    )

    assert "Failure category: GROUNDING_ERROR" in retry.user_message
    assert "Failure code: GROUNDING_REQUESTED_FILE_SCOPE" in retry.user_message


def test_finding_reference_schema_requires_positional_path() -> None:
    from pydantic import ValidationError
    from app.schemas.story_context_analysis import ClassificationEntry, ProviderClassificationEntry

    for reference in ("objectiveUnderstanding", "architectureFindings[0]", "impactedComponentFindings[12]"):
        ProviderClassificationEntry.model_validate(
            {"findingReference": reference, "classification": "FACTUAL_EXTRACTION", "grounded": True}
        )
    for reference in ("architecture:three-treatments", "architectureFindings", "missingInformation[0]"):
        with pytest.raises(ValidationError):
            ProviderClassificationEntry.model_validate(
                {"findingReference": reference, "classification": "FACTUAL_EXTRACTION", "grounded": True}
            )
    # Stored and adapted results (e.g. causalAssessment) keep the permissive internal entry.
    ClassificationEntry.model_validate(
        {"findingReference": "causalAssessment", "classification": "AI_INTERPRETATION", "grounded": True}
    )
    schema = ProviderStoryContextAnalysisResult.model_json_schema()
    assert "pattern" in schema["$defs"]["ProviderClassificationEntry"]["properties"]["findingReference"]


def test_one_shot_and_investigation_prompts_state_output_classification_rule() -> None:
    base_request = load_scenario(SCENARIO_ID).prompt_request
    one_shot = StoryContextAnalysisPromptBuilder().build(base_request)
    investigation_request = base_request.model_copy(update={
        "metadata": {
            **base_request.metadata,
            "investigation": {
                "initialized": True,
                "protocolVersion": "story-context-agent-protocol/v2",
                "executionId": str(base_request.ai_task_id),
                "parentTaskId": str(base_request.ai_task_id),
                "scope": {"projectSlug": "devlog-ai", "revision": "revision-1", "allowedPathPrefixes": ["docs"]},
                "budget": {
                    "maximumSteps": 2, "maximumRetrievalCalls": 2, "maximumEvidenceItems": 10,
                    "maximumBytes": 10000, "maximumTokens": 2000, "maximumElapsedMillis": 60000,
                    "maximumProviderOutputTokens": 4000,
                },
                "usage": {
                    "steps": 0, "retrievalCalls": 0, "evidenceItems": 0,
                    "bytes": 0, "tokens": 0, "elapsedMillis": 0, "providerOutputTokens": 0,
                },
            },
        },
    })
    investigation = StoryContextAnalysisPromptBuilder().build_investigation(investigation_request)

    for prompt in (one_shot, investigation):
        assert "OUTPUT CLASSIFICATION RULE" in prompt.user_message
        assert "architectureFindings[0]" in prompt.user_message
        assert "Copy classification and grounded verbatim" in prompt.user_message


def test_corrective_retry_restates_classification_rule_only_for_classification_failures() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    builder = StoryContextAnalysisPromptBuilder()
    prompt = builder.build(request)
    rule_marker = "The failure concerns outputClassification"

    classification_retry = builder.corrective_retry(
        prompt, ValueError("outputClassification references unknown item: architecture:three-treatments")
    )
    other_retry = builder.corrective_retry(prompt, ValueError("Invalid confidence level: VERY_HIGH"))
    investigation_retry = builder.investigation_corrective_retry(
        prompt, ValueError("outputClassification disagrees with grounding: objectiveUnderstanding")
    )

    assert rule_marker in classification_retry.user_message
    assert rule_marker not in other_retry.user_message
    assert rule_marker in investigation_retry.user_message


def test_prompts_require_verbatim_references_and_retry_restates_rule() -> None:
    request = load_scenario(SCENARIO_ID).prompt_request
    builder = StoryContextAnalysisPromptBuilder()
    prompt = builder.build(request)
    marker = "The failure concerns an evidence reference that is not authorized"

    assert "REFERENCE COPY RULE" in prompt.user_message
    assert "including any @revision suffix" in prompt.user_message

    unknown = "Uncertainty references unknown evidence: document:source:docs/story.md"
    assert marker in builder.investigation_corrective_retry(prompt, ValueError(unknown)).user_message
    assert marker in builder.corrective_retry(prompt, ValueError(unknown)).user_message
    assert marker not in builder.corrective_retry(prompt, ValueError("Invalid confidence level")).user_message


def test_citable_references_list_exactly_the_typed_allow_list_in_core_order() -> None:
    """Story 0243: the prompt carries the exact, exhaustive citable reference list."""
    from app.prompts.story_context_analysis import citable_references

    contract = {"allowedGroundingReferences": [
        {"ref": "file:s:backend/A.java@r"}, {"ref": "document:s:docs/ADR-006.md@r"},
        {"ref": "file:s:backend/A.java@r"}, {"other": "x"}, "bad"]}

    assert citable_references(contract) == ["file:s:backend/A.java@r", "document:s:docs/ADR-006.md@r"]
    assert citable_references(None) == []
    block = StoryContextAnalysisPromptBuilder()._citable_references_block(contract)
    assert block.startswith("CITABLE REFERENCES (EXACT, EXHAUSTIVE, AUTHORITATIVE)")
    assert "- file:s:backend/A.java@r" in block
    assert "merely mentioned" in block


def test_requested_directory_scope_maps_the_files_under_it() -> None:
    """Story 0243: a requested directory is not an empty mapping."""
    import json

    projection = {"request": {"files": ["backend/pkg"]}}
    contract = {"allowedGroundingReferences": [
        {"ref": "file:s:backend/pkg/usecase/A.java@r",
         "provenance": {"originatingFile": "backend/pkg/usecase/A.java"}},
        {"ref": "file:s:frontend/B.ts@r", "provenance": {"originatingFile": "frontend/B.ts"}}]}

    scope = json.loads(StoryContextAnalysisPromptBuilder()._requested_file_scope(projection, contract))

    assert scope["groundingReferences"]["backend/pkg"] == ["file:s:backend/pkg/usecase/A.java@r"]


def test_corrective_retry_quotes_rejected_references() -> None:
    """Story 0243: an unknown-reference retry names what must not be emitted again."""
    from app.prompts.story_context_analysis import _unknown_reference_retry_rule

    rule = _unknown_reference_retry_rule(
        "Objective understanding references unknown evidence: "
        "['file:s/ai-engine/app/schemas/x.py@r', 'file:s:ai-engine/app/schemas/y.py@r']")

    assert "UNKNOWN REFERENCE CORRECTION" in rule
    assert "- file:s/ai-engine/app/schemas/x.py@r" in rule
    assert "- file:s:ai-engine/app/schemas/y.py@r" in rule
    assert _unknown_reference_retry_rule("some other failure") == ""
