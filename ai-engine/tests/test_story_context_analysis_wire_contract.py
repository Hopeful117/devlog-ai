import json
import hashlib
from types import SimpleNamespace
from uuid import UUID

import pytest
from openai.lib._parsing import type_to_response_format_param
from pydantic import ValidationError

from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
import app.services.story_context_analysis_generation_service as generation_module
from app.providers.chatgpt import ChatGptProviderError
from app.schemas.story_context_analysis import (
    ProviderStoryContextAnalysisResult,
    StoryContextAnalysisResult,
    provider_result_to_internal,
)
from app.schemas.interaction_trace import AiInteractionTrace
from evaluations.loader import load_scenario


def _result() -> StoryContextAnalysisResult:
    return StoryContextAnalysisResult.model_validate({
        "objectiveUnderstanding": {},
        "confidence": {"level": "HIGH", "rationale": "bounded"},
        "provenance": {
            "contextDigest": "a" * 64,
            "promptVersion": "prompt-v1",
            "provider": "openai",
            "modelIdentifier": "gpt-5.6-luna",
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


def _provider_payload(confidence: str) -> dict:
    payload = _result().model_dump(by_alias=True, mode="json")
    payload["confidence"] = confidence
    return payload


def test_production_provider_schema_uses_strict_scalar_confidence():
    response_format = type_to_response_format_param(ProviderStoryContextAnalysisResult)
    schema = response_format["json_schema"]
    confidence = schema["schema"]["properties"]["confidence"]

    assert schema["strict"] is True
    assert confidence == {"enum": ["HIGH", "MEDIUM", "LOW"], "title": "Confidence", "type": "string"}


@pytest.mark.parametrize("level", ["HIGH", "MEDIUM", "LOW"])
def test_provider_result_adapts_raw_scalar_to_internal_confidence(level: str):
    provider_result = ProviderStoryContextAnalysisResult.model_validate(_provider_payload(level))

    internal = provider_result_to_internal(provider_result)

    assert internal.confidence.level == level
    assert internal.confidence.rationale == ""
    assert internal.model_dump(by_alias=True, mode="json")["confidence"] == level


def test_provider_result_rejects_unsupported_confidence():
    with pytest.raises(ValidationError):
        ProviderStoryContextAnalysisResult.model_validate(_provider_payload("VERY_HIGH"))


def test_story_context_prompt_and_retry_use_provider_confidence_shape():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    prompt = StoryContextAnalysisPromptBuilder().build(request)

    assert '"confidence":{"enum":["HIGH","MEDIUM","LOW"]' in prompt.user_message
    assert "confidence must be exactly one scalar value" in prompt.user_message

    retry = StoryContextAnalysisPromptBuilder().corrective_retry(
        prompt, ValueError("confidence must be a scalar enum")
    )
    assert "confidence must remain a scalar HIGH, MEDIUM, or LOW value" in retry.user_message


def test_interaction_trace_accepts_bounded_failure_code():
    trace = AiInteractionTrace.model_validate({
        "id": "00000000-0000-0000-0000-000000000001",
        "attempt": 1,
        "interactionType": "INITIAL_GENERATION",
        "traceLevel": "NORMAL",
        "provider": "mock",
        "modelIdentifier": "test-model",
        "intent": "engineering-story-context-analysis",
        "intentVersion": "v1",
        "promptVersion": "prompt-v1",
        "startedAt": "2026-01-01T00:00:00Z",
        "completedAt": "2026-01-01T00:00:01Z",
        "durationMs": 1000,
        "validationStatus": "VALIDATION_FAILED",
        "failureCategory": "GROUNDING_ERROR",
        "failureCode": "GROUNDING_REQUESTED_FILE_SCOPE",
        "selectedKnowledgeFingerprint": "a" * 64,
        "promptFingerprint": "b" * 64,
        "selectedFactCount": 0,
        "selectedObservationCount": 0,
        "selectedInsightCount": 0,
        "selectedEngineeringEventCount": 0,
        "groundingFingerprint": "c" * 64,
    })

    assert trace.failure_code == "GROUNDING_REQUESTED_FILE_SCOPE"


@pytest.mark.asyncio
async def test_production_generation_uses_provider_model_before_adapting(monkeypatch):
    captured = {}

    class FakeTraces:
        def __init__(self, provider, submission):
            self.traces = []

        async def generate_and_validate(self, prompt, response_model, validator):
            captured["response_model"] = response_model
            provider_result = response_model.model_validate(_provider_payload("HIGH"))
            validator(provider_result)
            return provider_result

    monkeypatch.setattr(generation_module, "InteractionTraceCollector", FakeTraces)
    service = generation_module.StoryContextAnalysisGenerationService.__new__(
        generation_module.StoryContextAnalysisGenerationService
    )
    service._provider = object()
    output = await service._generate_and_validate(object(), {}, {"groundingContractVersion": "story-context-grounding/v1", "allowedGroundingReferences": []}, FakeTraces(None, None))

    assert captured["response_model"] is ProviderStoryContextAnalysisResult
    assert output.confidence.level == "HIGH"
    assert output.confidence.rationale == ""


class _ServiceTraces:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = 0
        self.retry_reasons = []
        self.traces = []
        self.prompts = []

    def retry(self, reason):
        self.retry_reasons.append(reason)

    async def generate_and_validate(self, prompt, response_model, validator):
        self.calls += 1
        self.prompts.append(prompt.user_message)
        response = next(self.responses)
        if isinstance(response, Exception):
            raise response
        provider_result = response_model.model_validate(response)
        validator(provider_result)
        return provider_result


class _Callback:
    def __init__(self):
        self.results = []

    async def send_result(self, ai_task_id, correlation_id, result):
        self.results.append(result)
        return None


def _service(monkeypatch, traces, callback):
    monkeypatch.setattr(generation_module, "InteractionTraceCollector", lambda provider, submission: traces)
    service = generation_module.StoryContextAnalysisGenerationService(
        provider=SimpleNamespace(provider_name="mock", model_identifier="test-model"),
        prompt_builder=StoryContextAnalysisPromptBuilder(),
        callback_client=callback,
    )
    return service


def _investigation_request():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    return request.model_copy(update={
        "metadata": {
            **request.metadata,
            "investigation": {
                "initialized": True,
                "protocolVersion": "story-context-agent-protocol/v2",
                "executionId": str(request.ai_task_id),
                "parentTaskId": str(request.ai_task_id),
                "scope": {"projectSlug": "devlog", "revision": "revision-1"},
                "budget": {"maximumSteps": 2, "maximumRetrievalCalls": 2,
                           "maximumEvidenceItems": 10, "maximumBytes": 64000,
                           "maximumTokens": 8000, "maximumElapsedMillis": 60000,
                           "maximumProviderOutputTokens": 4000},
            },
        },
        "grounding_contract": {
            "groundingContractVersion": "story-context-grounding/v1",
            "allowedGroundingReferences": [],
            "causalAnswerRequired": False,
        },
    })


def _investigation_plan(task_id):
    return {
        "phase": "PLAN",
        "plan": {
            "protocolVersion": "story-context-agent-protocol/v2",
            "planId": "plan-1",
            "executionId": str(task_id),
            "parentTaskId": str(task_id),
            "scope": {"projectSlug": "devlog", "revision": "revision-1", "allowedPathPrefixes": ["backend"]},
            "budget": {
                "maximumSteps": 2, "maximumRetrievalCalls": 2, "maximumEvidenceItems": 10,
                "maximumBytes": 64000, "maximumTokens": 8000, "maximumElapsedMillis": 60000,
                "maximumProviderOutputTokens": 4000,
            },
            "actions": [{
                "kind": "SEARCH_EVIDENCE", "actionId": "action-1", "projectSlug": "devlog",
                "revision": "revision-1", "purpose": "find evidence", "query": "validator",
                "pathPrefix": "backend", "resultLimit": 1, "maximumBytes": 1000, "maximumTokens": 250,
            }],
        },
    }


def _final_decision():
    return {"phase": "FINAL", "stopReason": "SUFFICIENT_EVIDENCE", "result": _provider_payload("HIGH")}


class _InvestigationCallback(_Callback):
    def __init__(self, action_result):
        super().__init__()
        self.action_result = action_result
        self.actions = []

    async def execute_investigation_action(self, ai_task_id, correlation_id, action):
        self.actions.append(action)
        result = dict(self.action_result)
        result.setdefault("protocolVersion", "story-context-agent-protocol/v2")
        result.setdefault("executionId", action.get("executionId"))
        result.setdefault("parentTaskId", action.get("parentTaskId"))
        result.setdefault("kind", action.get("kind"))
        result.setdefault("projectSlug", action.get("projectSlug"))
        result.setdefault("revision", action.get("revision"))
        contract = result.setdefault("groundingContract", {
            "groundingContractVersion": "story-context-grounding/v1",
            "allowedGroundingReferences": [],
            "causalAnswerRequired": False,
        })
        result.setdefault("groundingDigest", hashlib.sha256(
            json.dumps(contract, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
        ).hexdigest())
        return {"result": result}


@pytest.mark.asyncio
async def test_generation_retries_grounding_failure_once_and_completes(monkeypatch):
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request.model_copy(update={
        "grounding_contract": {
            "groundingContractVersion": "story-context-grounding/v1",
            "allowedGroundingReferences": [], "causalAnswerRequired": False,
        }
    })
    traces = _ServiceTraces([
        generation_module.StoryContextAnalysisUnknownReferenceError("unknown reference"),
        _provider_payload("HIGH"),
    ])
    callback = _Callback()
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000002"))

    assert traces.calls == 2
    assert len(traces.retry_reasons) == 1
    assert callback.results[0].status.value == "COMPLETED"


@pytest.mark.asyncio
async def test_initialized_investigation_executes_one_action_and_returns_final(monkeypatch):
    request = _investigation_request()
    traces = _ServiceTraces([
        _investigation_plan(request.ai_task_id),
        _final_decision(),
    ])
    callback = _InvestigationCallback({"actionId": "action-1", "outcome": "RESULTS", "evidence": []})
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000005"))

    assert len(callback.actions) == 1
    assert callback.actions[0]["kind"] == "SEARCH_EVIDENCE"
    assert callback.actions[0]["limits"] == {"resultLimit": 1, "maximumBytes": 1000, "maximumTokens": 250}
    assert '"actionId":"action-1"' in traces.prompts[1]
    assert callback.results[0].status.value == "COMPLETED"
    assert callback.results[0].investigation_stop_reason.value == "SUFFICIENT_EVIDENCE"


@pytest.mark.asyncio
async def test_investigation_synchronizes_updated_grounding_for_final_callback(monkeypatch):
    request = _investigation_request()
    contract = {
        "groundingContractVersion": "story-context-grounding/v1",
        "allowedGroundingReferences": [{
            "type": "REPOSITORY_EVIDENCE", "ref": "retrieved:backend/App.java@revision-1",
            "coreReference": "retrieved:backend/App.java@revision-1",
            "taskReference": "retrieved:backend/App.java@revision-1",
            "scope": "PROJECT_REVISION", "project": "devlog", "revision": "revision-1",
            "provenance": {"sourceType": "REPOSITORY", "repositoryLocation": "backend/App.java"},
            "resource": "backend/App.java", "trust": "TECHNICAL_EVIDENCE", "citable": True,
        }],
        "causalAnswerRequired": False,
    }
    digest = hashlib.sha256(
        json.dumps(contract, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    ).hexdigest()
    traces = _ServiceTraces([_investigation_plan(request.ai_task_id), _final_decision()])
    callback = _InvestigationCallback({
        "actionId": "action-1", "outcome": "RESULTS", "evidence": [],
        "groundingContract": contract, "groundingDigest": digest,
    })
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000009"))

    assert request.grounding_contract == contract
    assert request.metadata["groundingDigest"] == digest
    assert callback.results[0].status.value == "COMPLETED"
    assert callback.results[0].prompt_execution.grounding_digest == digest


@pytest.mark.asyncio
async def test_investigation_fails_closed_when_updated_grounding_digest_is_invalid(monkeypatch):
    request = _investigation_request()
    traces = _ServiceTraces([_investigation_plan(request.ai_task_id)])
    callback = _InvestigationCallback({
        "actionId": "action-1", "outcome": "RESULTS", "evidence": [],
        "groundingContract": {"allowedGroundingReferences": []}, "groundingDigest": "0" * 64,
    })
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-00000000000a"))

    assert callback.results[0].status.value == "FAILED"
    assert callback.results[0].error.code == "GROUNDING_CONTRACT_INVALID"


@pytest.mark.asyncio
async def test_investigation_action_rejection_is_terminal(monkeypatch):
    request = _investigation_request()
    traces = _ServiceTraces([_investigation_plan(request.ai_task_id)])
    callback = _InvestigationCallback({"actionId": "action-1", "outcome": "REJECTED"})
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000006"))

    assert callback.results[0].status.value == "FAILED"
    assert callback.results[0].error.code == "INVESTIGATION_ACTION_REJECTED"


@pytest.mark.asyncio
async def test_investigation_budget_stop_is_bounded(monkeypatch):
    base_request = _investigation_request()
    request = base_request.model_copy(update={
        "metadata": {**base_request.metadata, "investigation": {
            "initialized": True, "protocolVersion": "story-context-agent-protocol/v2",
            "executionId": str(base_request.ai_task_id), "parentTaskId": str(base_request.ai_task_id),
            "scope": {"projectSlug": "devlog", "revision": "revision-1"},
            "budget": {"maximumSteps": 1, "maximumRetrievalCalls": 2,
                       "maximumEvidenceItems": 10, "maximumBytes": 64000,
                       "maximumTokens": 8000, "maximumElapsedMillis": 60000,
                       "maximumProviderOutputTokens": 4000},
        }}
    })
    action = {"phase": "ACTION", "action": _investigation_plan(request.ai_task_id)["plan"]["actions"][0]}
    traces = _ServiceTraces([_investigation_plan(request.ai_task_id), action])
    callback = _InvestigationCallback({"actionId": "action-1", "outcome": "RESULTS", "evidence": []})
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000007"))

    assert callback.results[0].error.code == "BUDGET_EXHAUSTED"


@pytest.mark.asyncio
async def test_investigation_provider_incomplete_is_terminal(monkeypatch):
    request = _investigation_request()
    traces = _ServiceTraces([ChatGptProviderError("incomplete", status="RESPONSE.INCOMPLETE", reason="length")])
    callback = _InvestigationCallback({"actionId": "unused", "outcome": "RESULTS"})
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000008"))

    assert callback.results[0].error.code == "PROVIDER_INCOMPLETE_RESPONSE"
    assert not callback.actions


def test_execution_provenance_overrides_model_provided_values():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    prompt = StoryContextAnalysisPromptBuilder().build(request)
    service = generation_module.StoryContextAnalysisGenerationService(
        provider=SimpleNamespace(provider_name="mock", model_identifier="test-model"),
        prompt_builder=StoryContextAnalysisPromptBuilder(),
        callback_client=SimpleNamespace(),
    )

    result = service._apply_execution_provenance(_result(), prompt)

    assert result.provenance.provider == "mock"
    assert result.provenance.model_identifier == "test-model"
    assert result.provenance.prompt_content_digest == prompt.content_digest


@pytest.mark.asyncio
async def test_generation_does_not_retry_deterministic_grounding_contract_failure(monkeypatch):
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    traces = _ServiceTraces([
        generation_module.StoryContextAnalysisContractGroundingError("invalid Core contract"),
    ])
    callback = _Callback()
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000003"))

    assert traces.calls == 1
    assert callback.results[0].status.value == "FAILED"
    assert callback.results[0].error.code == "GROUNDING_CONTRACT_INVALID"
    assert callback.results[0].error.message == "Grounding validation failed; inspect the bounded interaction trace diagnostics."


@pytest.mark.asyncio
async def test_provider_incomplete_is_terminal_and_not_grounding_failure(monkeypatch):
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    traces = _ServiceTraces([
        ChatGptProviderError(
            "ChatGPT response did not complete",
            status="RESPONSE.INCOMPLETE",
            reason="max_output_tokens",
        ),
    ])
    callback = _Callback()
    service = _service(monkeypatch, traces, callback)

    await service.process(request, UUID("00000000-0000-0000-0000-000000000004"))

    assert traces.calls == 1
    assert callback.results[0].status.value == "FAILED"
    assert callback.results[0].error.code == "PROVIDER_INCOMPLETE_RESPONSE"
    assert "grounding" not in callback.results[0].error.message.casefold()


def test_story_context_prompt_preserves_null_story_id_but_omits_optional_nulls():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    prompt = StoryContextAnalysisPromptBuilder().build(request)
    rendered = prompt.user_message.split("SELECTED KNOWLEDGE\n", 1)[1].split("\n\nUSER GUIDANCE", 1)[0]
    payload = json.loads(rendered)

    assert payload["request"]["storyId"] is None
    assert payload["requestEcho"]["storyId"] is None
    assert payload["scope"]["storyId"] is None
    assert "compatibility" not in payload


def test_prompt_construction_failure_keeps_core_identity_metadata():
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    service = generation_module.StoryContextAnalysisGenerationService.__new__(
        generation_module.StoryContextAnalysisGenerationService
    )
    metadata = service._failure_execution_metadata(request)

    assert metadata.context_digest == request.context_digest
    assert metadata.projection_digest == request.projection_digest
    assert metadata.projection_version == request.metadata.get("projectionVersion")
    assert metadata.scope == request.metadata.get("scope")
    assert metadata.freshness == request.metadata.get("freshness")
    assert metadata.grounding_digest == request.metadata.get("groundingDigest")
    assert metadata.provider == "unavailable"
