from uuid import uuid4
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.schemas.story_context_investigation import (
    InvestigationCapabilityResult,
    SearchEvidenceActionProposal,
    InvestigationPlanProposal,
)


def budget():
    return {
        "maximumSteps": 2,
        "maximumRetrievalCalls": 2,
        "maximumEvidenceItems": 10,
        "maximumBytes": 64000,
        "maximumTokens": 8000,
        "maximumElapsedMillis": 60000,
        "maximumProviderOutputTokens": 4000,
    }


def plan(action):
    execution_id = uuid4()
    return {
        "protocolVersion": "story-context-agent-protocol/v2",
        "planId": "plan-1",
        "executionId": execution_id,
        "parentTaskId": uuid4(),
        "scope": {"projectSlug": "devlog", "revision": "revision-1", "allowedPathPrefixes": ["backend", "docs"]},
        "budget": budget(),
        "actions": [action],
    }


def search(**overrides):
    value = {
        "kind": "SEARCH_EVIDENCE", "actionId": "action-1", "projectSlug": "devlog",
        "revision": "revision-1", "purpose": "find the validator", "query": "validator",
        "pathPrefix": "backend", "resultLimit": 10, "maximumBytes": 10000, "maximumTokens": 2000,
    }
    value.update(overrides)
    return value


def test_v2_plan_accepts_bounded_search_and_range_actions():
    parsed = InvestigationPlanProposal.model_validate(plan(search()))
    assert parsed.actions[0].action_id == "action-1"

    range_action = {
        "kind": "READ_EVIDENCE_RANGE", "actionId": "action-2", "projectSlug": "devlog",
        "revision": "revision-1", "purpose": "inspect the validator", "path": "backend/Main.java",
        "startLine": 10, "endLine": 20, "resultLimit": 1, "maximumBytes": 10000, "maximumTokens": 2000,
    }
    assert InvestigationPlanProposal.model_validate(plan(range_action)).actions[0].kind == "READ_EVIDENCE_RANGE"


@pytest.mark.parametrize("invalid", [
    {"kind": "UNKNOWN"},
    {"actionId": "bad id"},
    {"purpose": "   "},
    {"query": "   "},
    {"pathPrefix": "../secret"},
    {"resultLimit": 0},
    {"revision": "UNKNOWN"},
])
def test_v2_actions_reject_invalid_bounded_fields(invalid):
    with pytest.raises(ValidationError):
        InvestigationPlanProposal.model_validate(plan(search(**invalid)))


def test_v2_plan_rejects_duplicate_ids_mismatched_scope_and_invalid_range():
    duplicate = plan(search())
    duplicate["actions"].append(search())
    with pytest.raises(ValidationError):
        InvestigationPlanProposal.model_validate(duplicate)
    with pytest.raises(ValidationError):
        InvestigationPlanProposal.model_validate(plan(search(projectSlug="other")))
    range_action = search(kind="READ_EVIDENCE_RANGE", path="backend/Main.java", startLine=20, endLine=10)
    with pytest.raises(ValidationError):
        InvestigationPlanProposal.model_validate(plan(range_action))
    oversized_range = search(kind="READ_EVIDENCE_RANGE", path="backend/Main.java", startLine=1, endLine=502)
    with pytest.raises(ValidationError):
        InvestigationPlanProposal.model_validate(plan(oversized_range))


def test_capability_result_accepts_only_bounded_stop_reasons():
    value = {
        "protocolVersion": "story-context-agent-protocol/v2", "executionId": uuid4(), "parentTaskId": uuid4(),
        "actionId": "action-1", "kind": "SEARCH_EVIDENCE", "projectSlug": "devlog",
        "revision": "revision-1", "outcome": "EMPTY", "stopReason": "NO_RELEVANT_EVIDENCE",
    }
    assert InvestigationCapabilityResult.model_validate(value).outcome == "EMPTY"
    with pytest.raises(ValidationError):
        InvestigationCapabilityResult.model_validate({**value, "stopReason": "RETRY_FOREVER"})


def test_existing_v1_protocol_remains_unchanged():
    from app.schemas.story_context_agent_protocol import StoryContextAgentScope

    assert StoryContextAgentScope.model_validate(
        {"projectSlug": "devlog", "storyId": None, "intent": "summarize", "files": []}
    ).project_slug == "devlog"


def test_action_payload_accounts_only_the_current_provider_generation():
    from app.services.interaction_trace import InteractionTraceCollector

    collector = InteractionTraceCollector(object(), SimpleNamespace())
    collector.traces = [
        SimpleNamespace(output_tokens=17),
        SimpleNamespace(output_tokens=23),
    ]
    action = SearchEvidenceActionProposal.model_validate(search())
    submission = SimpleNamespace(
        metadata={"investigation": {"executionId": str(uuid4())}},
        ai_task_id=uuid4(), correlation_id=uuid4(), provider="openai",
        credential_binding_id=uuid4(),
    )

    from app.services.story_context_analysis_generation_service import StoryContextAnalysisGenerationService

    payload = StoryContextAnalysisGenerationService._action_payload(action, submission, collector)
    assert payload["providerOutputTokens"] == 40
    assert payload["providerGenerationCount"] == 2

    collector.account_provider_generations(2)
    collector.traces.append(SimpleNamespace(output_tokens=11, duration_ms=7))
    next_payload = StoryContextAnalysisGenerationService._action_payload(action, submission, collector)
    assert next_payload["providerOutputTokens"] == 11
    assert next_payload["providerGenerationCount"] == 1


def test_missing_provider_output_measurement_fails_closed():
    from app.services.interaction_trace import InteractionTraceCollector

    collector = InteractionTraceCollector(object(), SimpleNamespace())
    collector.traces = [SimpleNamespace(output_tokens=None, duration_ms=4)]
    with pytest.raises(ValueError, match="measurement"):
        collector.provider_accounting_delta()


def test_completed_investigation_requires_citable_evidence_from_the_scoped_revision():
    from app.services.story_context_analysis_generation_service import StoryContextAnalysisGenerationService

    submission = SimpleNamespace(
        metadata={"investigation": {
            "requiredEvidence": True,
            "scope": {"revision": "revision-1"},
        }}
    )
    assert not StoryContextAnalysisGenerationService._has_usable_investigation_evidence(
        submission, [{"outcome": "EMPTY", "evidence": []}]
    )
    assert not StoryContextAnalysisGenerationService._has_usable_investigation_evidence(
        submission, [{"outcome": "RESULTS", "evidence": [
            {"reference": "evidence-1", "revision": "other-revision", "citable": True}
        ]}]
    )
    assert not StoryContextAnalysisGenerationService._has_usable_investigation_evidence(
        submission, [{"outcome": "RESULTS", "evidence": [
            {"reference": "evidence-1", "revision": "revision-1"}
        ]}]
    )
    assert not StoryContextAnalysisGenerationService._has_usable_investigation_evidence(
        submission, [{"outcome": "RESULTS", "evidence": [
            {"reference": "evidence-1", "citable": True}
        ]}]
    )
    assert StoryContextAnalysisGenerationService._has_usable_investigation_evidence(
        submission, [{"outcome": "RESULTS", "evidence": [
            {"reference": "evidence-1", "revision": "revision-1", "citable": True}
        ]}]
    )


def test_failed_trace_keeps_bounded_redacted_validation_diagnostic_in_normal_mode():
    from datetime import datetime, timezone

    from app.providers.base import PromptTraceability
    from app.services.interaction_trace import InteractionTraceCollector

    collector = InteractionTraceCollector(
        SimpleNamespace(provider_name="mock", model_identifier="mock-model"),
        SimpleNamespace(
            selected_knowledge={"selectedFacts": [], "selectedObservations": []},
            intent=SimpleNamespace(id="intent", version="v1"),
        ),
    )
    prompt = SimpleNamespace(
        prompt_version="prompt-v1",
        system_message="system",
        user_message="user",
        content_digest="a" * 64,
        traceability=PromptTraceability(
            request_id="request",
            correlation_id="correlation",
            ai_task_id="task",
            analysis_id="analysis",
            intent_id="intent",
            intent_version="v1",
            context_digest="b" * 64,
            analysis_context_id=None,
            profile_id=None,
            profile_version=None,
        ),
    )

    trace = collector._trace(
        prompt,
        1,
        "INITIAL_GENERATION",
        datetime.now(timezone.utc),
        datetime.now(timezone.utc),
        "VALIDATION_FAILED",
        "STRUCTURAL_CONTRACT_ERROR",
        "STRUCTURAL_CONTRACT_ERROR",
        "causal answer contains token=secret-value",
        None,
        None,
        "VALIDATION",
        None,
        None,
    )

    assert trace.validation_diagnostics == "causal answer contains token=[REDACTED]"
    assert trace.system_prompt is None
    assert trace.raw_model_response is None


def test_investigation_no_evidence_failure_exposes_bounded_counts():
    from app.services.story_context_analysis_generation_service import (
        StoryContextAnalysisGenerationService,
        _InvestigationTerminal,
    )

    error = _InvestigationTerminal(
        "INVESTIGATION_INCOMPLETE_NO_EVIDENCE",
        "Required citable evidence was not returned (resultActions=1, evidenceItems=2, citableItems=0)",
    )

    assert "resultActions=1" in StoryContextAnalysisGenerationService._callback_error_message(error)
    assert "citableItems=0" in StoryContextAnalysisGenerationService._callback_error_message(error)


def test_investigation_no_evidence_failure_includes_outcome_breakdown():
    from app.services.story_context_analysis_generation_service import (
        StoryContextAnalysisGenerationService,
        _InvestigationTerminal,
    )

    error = _InvestigationTerminal(
        "INVESTIGATION_INCOMPLETE_NO_EVIDENCE",
        "Required citable evidence was not returned "
        "(resultActions=2, evidenceItems=1, citableItems=0, outcomes={'EMPTY': 1, 'REJECTED': 1})",
    )

    message = StoryContextAnalysisGenerationService._callback_error_message(error)
    assert "outcomes=" in message
    assert "REJECTED" in message


def test_pydantic_validation_failure_keeps_field_diagnostics():
    from app.services.interaction_trace import _failure_message

    with pytest.raises(ValidationError) as captured:
        InvestigationCapabilityResult.model_validate({"outcome": "INVALID"})

    message = _failure_message(captured.value)

    assert "protocolVersion" in message
    assert len(message) <= 5000
