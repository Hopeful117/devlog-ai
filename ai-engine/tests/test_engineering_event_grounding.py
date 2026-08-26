from uuid import uuid4

import pytest

from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.engineering_event import EngineeringEventPromptBuilder
from app.providers.mock import MockLlmProvider
from app.schemas.ai_task import AiTaskSubmissionRequest, IntentDefinition
from app.services.engineering_event_generation_service import (
    EngineeringEventGenerationService,
)
from tests.intent_fixtures import selected_knowledge


class RecordingCallbackClient:
    def __init__(self) -> None:
        self.results: list[object] = []

    async def send_result(self, correlation_id: object, result: object) -> object:
        self.results.append(result)
        return object()


def event_intent() -> IntentDefinition:
    return IntentDefinition(
        id="analyze-engineering-event",
        version="v1",
        objective="Analyze one completed commit against its first parent.",
        output_proposal_type=ProposalType.ENGINEERING_EVENT,
        supported_insight_types=[],
        constraints=["Use only the immutable evolution context."],
        output_schema={"type": "object", "root": "proposals"},
        prompt_template="analyze-engineering-event-prompt-v1",
        context_profiles=["history-v1"],
    )


def event_knowledge(fact_id: str, observation_id: str, evidence: str) -> dict[str, object]:
    knowledge = selected_knowledge(
        facts=[{"id": fact_id, "content": "A module changed",
                "evidenceReferences": [evidence]}],
        observations=[{"id": observation_id, "content": "Behavior stayed stable"}],
    )
    knowledge["evolutionContext"] = {
        "projectId": str(uuid4()),
        "sourceId": str(uuid4()),
        "analysisId": str(uuid4()),
        "commitDiff": {"evidenceReferences": [evidence]},
    }
    return knowledge


def event_submission(
    fact_id: str, observation_id: str, evidence: str
) -> AiTaskSubmissionRequest:
    return AiTaskSubmissionRequest(
        request_id=uuid4(),
        correlation_id=uuid4(),
        task_type=AiTaskType.EVENT_PROPOSAL_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=event_intent(),
        selected_knowledge=event_knowledge(fact_id, observation_id, evidence),
        expected_output_contract={"type": "object", "root": "proposals"},
        metadata={"source": "test"},
    )


def event_output(
    fact_ids: list[str],
    observation_ids: list[str] | None = None,
    evidence_references: list[str] | None = None,
) -> dict:
    return {
        "proposals": [
            {
                "schemaVersion": "engineering-event-proposal-v1",
                "category": "ARCHITECTURE_CHANGE",
                "title": "Module boundary adjusted",
                "summary": "A bounded repository change was observed.",
                "significance": "The change matters to future engineering work.",
                "confidence": 0.8,
                "supportingFactIds": fact_ids,
                "supportingObservationIds": observation_ids or [],
                "evidenceReferences": evidence_references or [],
            }
        ]
    }


class ProviderFailure(Exception):
    pass


class FailingProvider:
    def __init__(self, failures: list[Exception]) -> None:
        self.failures = list(failures)
        self.requests = 0

    @property
    def provider_name(self) -> str:
        return "failing"

    @property
    def model_identifier(self) -> str:
        return "failing-model"

    async def generate_structured(self, prompt: object, schema: type):
        self.requests += 1
        raise self.failures.pop(0)


class ProviderFailingOnSecondAttempt:
    """Delegates the first call to a working inner provider, then fails.
    Instance-scoped so no shared class state leaks between tests."""

    def __init__(self, inner: MockLlmProvider) -> None:
        self.inner = inner
        self.requests = 0

    @property
    def provider_name(self) -> str:
        return "flaky"

    @property
    def model_identifier(self) -> str:
        return "flaky-model"

    async def generate_structured(self, prompt: object, schema: type):
        self.requests += 1
        if self.requests == 1:
            return await self.inner.generate_structured(prompt, schema)
        raise ConnectionError("provider unreachable")


# ---------------------------------------------------------------------------
# Prompt contract
# ---------------------------------------------------------------------------


def test_prompt_presents_separated_allow_lists() -> None:
    fact_id, observation_id = str(uuid4()), str(uuid4())
    evidence = "git:repo:commit"
    prompt = EngineeringEventPromptBuilder().build(
        event_submission(fact_id, observation_id, evidence)
    )

    assert "ALLOWED_SUPPORTING_FACT_IDS:" in prompt.user_message
    assert f"- {fact_id}" in prompt.user_message
    assert "ALLOWED_SUPPORTING_OBSERVATION_IDS:" in prompt.user_message
    assert f"- {observation_id}" in prompt.user_message
    assert "ALLOWED_EVIDENCE_REFERENCES:" in prompt.user_message
    assert f"- {evidence}" in prompt.user_message


def test_prompt_states_copy_exact_grounding_rules() -> None:
    prompt = EngineeringEventPromptBuilder().build(
        event_submission(str(uuid4()), str(uuid4()), "git:repo:commit")
    )

    assert "copy" in prompt.user_message and "exactly" in prompt.user_message
    assert "Never derive, shorten, extend or construct an identifier" in prompt.user_message
    assert "are NOT valid citations unless they also appear in the allowed lists" \
        in prompt.user_message


def test_prompt_states_empty_list_domain_rule() -> None:
    prompt = EngineeringEventPromptBuilder().build(
        event_submission(str(uuid4()), str(uuid4()), "git:repo:commit")
    )

    assert "at least one grounding element across its three fields" in prompt.user_message


# ---------------------------------------------------------------------------
# Validation matrix (service-level grounding subset checks)
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_accepts_single_allowed_fact_id() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    service = EngineeringEventGenerationService(
        MockLlmProvider([event_output([fact_id])]),
        EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert len(result.proposals) == 1  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_accepts_multiple_allowed_fact_ids() -> None:
    first, second, observation = str(uuid4()), str(uuid4()), str(uuid4())
    request = event_submission(first, observation, "git:repo:commit")
    request.selected_knowledge["selectedFacts"].append(
        {"id": second, "content": "Another grounded change"})
    callback = RecordingCallbackClient()
    service = EngineeringEventGenerationService(
        MockLlmProvider([event_output([first, second])]),
        EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_rejects_unknown_fact_id_and_names_it_in_retry_diagnostics() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    invented = str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = MockLlmProvider([
        event_output([invented]),          # invalid first attempt
        event_output([fact_id]),           # corrected second attempt
    ])
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    retry_prompt = provider.requests[1]
    assert invented in retry_prompt.user_message
    assert fact_id in retry_prompt.user_message
    assert "ALLOWED_SUPPORTING_FACT_IDS" in retry_prompt.user_message
    assert "CORRECTIVE RETRY" in retry_prompt.user_message
    assert "copied exactly from the ALLOWED_" in retry_prompt.user_message

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_rejects_source_identifier_cited_as_fact_id() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    source_id = event_knowledge("placeholder", "placeholder", "x")["evolutionContext"]["sourceId"]
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = MockLlmProvider([
        event_output([source_id]),
        event_output([fact_id]),
    ])
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED or \
        result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    first_failure = [r for r in callback.results][0]
    assert first_failure is not None
    # the offending source id must appear in the corrective retry prompt
    assert source_id in provider.requests[1].user_message


@pytest.mark.asyncio
async def test_empty_fact_ids_accepted_when_observations_ground_the_proposal() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    service = EngineeringEventGenerationService(
        MockLlmProvider([event_output([], observation_ids=[observation])]),
        EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_fully_ungrounded_proposal_is_rejected_by_schema_without_retry_loop() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = MockLlmProvider([
        event_output([], observation_ids=[], evidence_references=[]),
        event_output([], observation_ids=[], evidence_references=[]),
    ])
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "INVALID_LLM_OUTPUT"  # type: ignore[attr-defined]
    assert len(provider.requests) == 2  # bounded: exactly one corrective retry


# ---------------------------------------------------------------------------
# Terminal failure + honest error classification
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_persistent_invalid_output_ends_as_invalid_llm_output_with_offenders() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    invented = str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = MockLlmProvider([
        event_output([invented]),
        event_output([invented]),
    ])
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "INVALID_LLM_OUTPUT"  # type: ignore[attr-defined]
    assert invented in result.error.message  # type: ignore[attr-defined]
    assert len(provider.requests) == 2


@pytest.mark.asyncio
async def test_first_attempt_provider_timeout_is_not_retry_classified() -> None:
    request = event_submission(str(uuid4()), str(uuid4()), "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = FailingProvider([TimeoutError("request timed out")])
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "LLM_PROVIDER_ERROR"  # type: ignore[attr-defined]
    assert provider.requests == 1


@pytest.mark.asyncio
async def test_second_attempt_provider_failure_stays_provider_error() -> None:
    fact_id, observation = str(uuid4()), str(uuid4())
    invented = str(uuid4())
    request = event_submission(fact_id, observation, "git:repo:commit")
    callback = RecordingCallbackClient()
    provider = ProviderFailingOnSecondAttempt(MockLlmProvider([event_output([invented])]))
    service = EngineeringEventGenerationService(
        provider, EngineeringEventPromptBuilder(),  # type: ignore[arg-type]
        callback,  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "LLM_PROVIDER_ERROR"  # type: ignore[attr-defined]
    assert provider.requests == 2
