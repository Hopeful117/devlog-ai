"""Credential-free bounded behavioral baseline for Story 0184.

This is intentionally a small test-local evaluation, not a new evaluation
framework. It compares the existing one-shot v1 path with explicitly
initialized bounded investigation using deterministic provider/Core fakes.
"""

import hashlib
import json
from types import SimpleNamespace
from uuid import UUID

import pytest

import app.services.story_context_analysis_generation_service as generation_module
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from evaluations.loader import load_scenario


REVISION = "revision-0184"
VALID_REFERENCE = f"retrieved:docs/architecture.md@{REVISION}"


def _provider_payload() -> dict:
    return {
        "objectiveUnderstanding": {},
        "confidence": "LOW",
        "provenance": {
            "contextDigest": "a" * 64,
            "promptVersion": "prompt-v1",
            "provider": "mock",
            "modelIdentifier": "fixture-model",
            "promptContentDigest": "b" * 64,
            "intentId": "engineering-story-context-analysis",
            "intentVersion": "v1",
        },
        "outputClassification": {"entries": []},
    }


def _request(*, initialized: bool, maximum_steps: int = 2):
    request = load_scenario("engineering-story-context-analysis-v1").prompt_request
    metadata = {**request.metadata}
    if initialized:
        metadata["investigation"] = {
            "initialized": True,
            "protocolVersion": "story-context-agent-protocol/v2",
            "executionId": str(request.ai_task_id),
            "parentTaskId": str(request.ai_task_id),
            "scope": {"projectSlug": "devlog", "revision": REVISION},
            "budget": {
                "maximumSteps": maximum_steps, "maximumRetrievalCalls": 2,
                "maximumEvidenceItems": 4, "maximumBytes": 4000, "maximumTokens": 1000,
                "maximumElapsedMillis": 10000, "maximumProviderOutputTokens": 1000,
            },
        }
        metadata["provider"] = "mock"
        metadata["credentialBindingId"] = str(request.ai_task_id)
    return request.model_copy(update={
        "metadata": metadata,
        "grounding_contract": {
            "groundingContractVersion": "story-context-grounding/v1",
            "allowedGroundingReferences": [],
            "causalAnswerRequired": False,
        },
    })


def _plan(task_id) -> dict:
    return {
        "phase": "PLAN",
        "plan": {
            "protocolVersion": "story-context-agent-protocol/v2",
            "planId": "plan-0184",
            "executionId": str(task_id),
            "parentTaskId": str(task_id),
            "scope": {"projectSlug": "devlog", "revision": REVISION, "allowedPathPrefixes": ["docs"]},
            "budget": {
                "maximumSteps": 2,
                "maximumRetrievalCalls": 2,
                "maximumEvidenceItems": 4,
                "maximumBytes": 4000,
                "maximumTokens": 1000,
                "maximumElapsedMillis": 10000,
                "maximumProviderOutputTokens": 1000,
            },
            "actions": [{
                "kind": "SEARCH_EVIDENCE",
                "actionId": "action-0184",
                "projectSlug": "devlog",
                "revision": REVISION,
                "purpose": "locate the bounded answer evidence",
                "query": "bounded",
                "pathPrefix": "docs",
                "resultLimit": 1,
                "maximumBytes": 1000,
                "maximumTokens": 250,
            }],
        },
    }


def _final(stop_reason: str = "SUFFICIENT_EVIDENCE") -> dict:
    return {"phase": "FINAL", "stopReason": stop_reason, "result": _provider_payload()}


class _Traces:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = 0
        self.traces = []
        self.retry_reasons = []
        self.decisions = []

    def retry(self, reason):
        self.retry_reasons.append(reason)

    async def generate_and_validate(self, _prompt, response_model, validator):
        self.calls += 1
        value = next(self.responses)
        parsed = response_model.model_validate(value)
        validator(parsed)
        self.decisions.append(parsed)
        return parsed


class _Callback:
    def __init__(self, outcome: str):
        self.outcome = outcome
        self.actions = []
        self.action_results = []
        self.results = []

    async def execute_investigation_action(self, _task_id, _correlation_id, action):
        self.actions.append(action)
        contract = {
            "groundingContractVersion": "story-context-grounding/v1",
            "allowedGroundingReferences": ([{
                "type": "REPOSITORY_EVIDENCE",
                "ref": VALID_REFERENCE,
                "coreReference": VALID_REFERENCE,
                "taskReference": VALID_REFERENCE,
                "scope": "PROJECT_REVISION",
                "project": "devlog",
                "revision": REVISION,
                "provenance": {"sourceType": "DOCUMENT", "repositoryLocation": "docs/architecture.md"},
                "resource": "docs/architecture.md",
                "trust": "TECHNICAL_EVIDENCE",
                "citable": True,
            }] if self.outcome == "RESULTS" else []),
            "causalAnswerRequired": False,
        }
        result = {
            "protocolVersion": "story-context-agent-protocol/v2",
            "executionId": str(action.get("executionId")),
            "parentTaskId": str(action.get("parentTaskId")),
            "actionId": action["actionId"],
            "outcome": self.outcome,
            "kind": action["kind"], "projectSlug": action["projectSlug"], "revision": action["revision"],
            "evidence": ([{"reference": VALID_REFERENCE}] if self.outcome == "RESULTS" else []),
            "groundingContract": contract,
            "groundingDigest": hashlib.sha256(
                json.dumps(contract, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
            ).hexdigest(),
        }
        self.action_results.append(result)
        return {"result": result}

    async def send_result(self, _task_id, _correlation_id, result):
        self.results.append(result)


def _service(monkeypatch, traces, callback):
    monkeypatch.setattr(generation_module, "InteractionTraceCollector", lambda *_args: traces)
    return generation_module.StoryContextAnalysisGenerationService(
        provider=SimpleNamespace(provider_name="mock", model_identifier="fixture-model"),
        prompt_builder=StoryContextAnalysisPromptBuilder(),
        callback_client=callback,
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("scenario", "bounded_responses", "outcome", "expected_actions", "expected_status", "expected_error"),
    [
        ("broad-truncated", [_plan(UUID(int=1)), _final()], "RESULTS", 1, "COMPLETED", None),
        ("targeted-valid", [_plan(UUID(int=2)), _final()], "RESULTS", 1, "COMPLETED", None),
        ("empty-abstention", [_plan(UUID(int=3)), _final("NO_RELEVANT_EVIDENCE")], "EMPTY", 1, "COMPLETED", None),
        ("action-rejection", [_plan(UUID(int=4))], "REJECTED", 1, "FAILED", "INVESTIGATION_ACTION_REJECTED"),
        ("budget-exhaustion", [_plan(UUID(int=5)), {"phase": "ACTION", "action": _plan(UUID(int=5))["plan"]["actions"][0]}], "RESULTS", 1, "FAILED", "BUDGET_EXHAUSTED"),
    ],
)
async def test_story0184_compares_one_shot_v1_with_bounded_scenario(
    monkeypatch, scenario, bounded_responses, outcome, expected_actions, expected_status, expected_error
):
    one_shot_callback = _Callback("EMPTY")
    one_shot_traces = _Traces([_provider_payload()])
    await _service(monkeypatch, one_shot_traces, one_shot_callback).process(
        _request(initialized=False), UUID(int=100)
    )
    assert len(one_shot_callback.actions) == 0
    assert one_shot_callback.results[0].status.value == "COMPLETED"
    assert one_shot_callback.results[0].prompt_execution.protocol_version == "story-context-agent-protocol/v1"

    bounded_callback = _Callback(outcome)
    bounded_traces = _Traces(bounded_responses)
    maximum_steps = 1 if scenario == "budget-exhaustion" else 2
    await _service(monkeypatch, bounded_traces, bounded_callback).process(
        _request(initialized=True, maximum_steps=maximum_steps), UUID(int=200)
    )

    assert len(bounded_callback.actions) == expected_actions, scenario
    result = bounded_callback.results[0]
    assert result.status.value == expected_status
    if expected_error:
        assert result.error.code == expected_error
        assert bounded_traces.decisions
        if scenario == "action-rejection":
            assert len(bounded_traces.decisions) == 1
        if scenario == "budget-exhaustion":
            assert len(bounded_traces.decisions) == 2
        return

    assert result.prompt_execution.protocol_version == "story-context-agent-protocol/v1"
    assert result.prompt_execution.grounding_digest
    returned_references = {
        item["reference"]
        for action_result in bounded_callback.action_results
        for item in action_result["evidence"]
    }
    assert returned_references <= {VALID_REFERENCE}
    if scenario == "empty-abstention":
        assert bounded_traces.calls == 2
        assert bounded_traces.decisions[-1].root.stop_reason.value == "NO_RELEVANT_EVIDENCE"
    else:
        assert bounded_traces.calls == 2
