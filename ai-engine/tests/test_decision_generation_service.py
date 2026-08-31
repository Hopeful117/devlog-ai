from uuid import uuid4

import pytest

from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.decision import EngineeringDecisionPromptBuilder
from app.providers.mock import MockLlmProvider
from app.schemas.ai_task import AiTaskSubmissionRequest, IntentDefinition
from app.schemas.decision import EngineeringDecisionGenerationOutput
from app.services.decision_generation_service import EngineeringDecisionGenerationService
from app.services.task_processing_service import AiTaskProcessingService
from tests.intent_fixtures import selected_knowledge


DECISION_OUTPUT_CONTRACT = {
    "type": "object",
    "root": "proposals",
    "structured": True,
    "minimumProposalCount": 0,
    "maximumProposalCount": 10,
    "proposalType": "ENGINEERING_DECISION",
    "schemaVersion": "engineering-decision-proposal-v1",
    "requiredProposalFields": [
        "title", "context", "choice", "rationale", "consequences",
    ],
}


def decision_intent() -> IntentDefinition:
    return IntentDefinition(
        id="analyze-engineering-decision",
        version="v1",
        objective="Propose engineering decisions grounded in analysis context.",
        output_proposal_type=ProposalType.ENGINEERING_DECISION,
        execution_mode="GENERIC",
        supported_insight_types=[],
        constraints=[
            "Use only selected analysis context.",
            "Never infer an unsupported engineering decision.",
            "Return zero proposals when evidence is insufficient.",
        ],
        output_schema=DECISION_OUTPUT_CONTRACT,
        prompt_template="analyze-engineering-decision-prompt-v1",
        context_profiles=["history-v1", "project-state-v1"],
    )


class RecordingCallbackClient:
    def __init__(self) -> None:
        self.results: list[object] = []

    async def send_result(self, correlation_id: object, result: object) -> object:
        self.results.append(result)
        return object()


def submission() -> AiTaskSubmissionRequest:
    return AiTaskSubmissionRequest(
        request_id=uuid4(),
        correlation_id=uuid4(),
        task_type=AiTaskType.DECISION_PROPOSAL_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=decision_intent(),
        selected_knowledge=selected_knowledge(
            facts=[
                {
                    "id": str(uuid4()),
                    "content": "DevLog exposes engineering context to agents.",
                    "evidenceReferences": ["docs/decisions/ADR-057.md"],
                }
            ],
            observations=[
                {"id": str(uuid4()), "content": "An MCP integration boundary exists."}
            ],
        ),
        expected_output_contract=DECISION_OUTPUT_CONTRACT,
        metadata={"source": "test"},
    )


def base_proposal() -> dict[str, object]:
    return {
        "title": "Use MCP as the agent-facing integration boundary",
        "context": "Agents need a standardized context boundary.",
        "choice": "Expose capabilities through DevLog MCP.",
        "rationale": "MCP is a stable agent-facing boundary.",
    }


def full_proposal() -> dict[str, object]:
    return {
        **base_proposal(),
        "consequences": "Consumers stay decoupled from REST internals.",
    }


@pytest.mark.asyncio
async def test_provider_called_and_base_payload_maps_to_engineering_decision() -> None:
    request = submission()
    provider = MockLlmProvider([{"proposals": [base_proposal()]}])
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        provider, EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 1
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED
    assert len(result.proposals) == 1
    proposal = result.proposals[0]
    assert proposal.type == ProposalType.ENGINEERING_DECISION
    assert proposal.payload["title"] == base_proposal()["title"]
    assert proposal.payload["context"] == base_proposal()["context"]
    assert proposal.payload["choice"] == base_proposal()["choice"]
    assert proposal.payload["rationale"] == base_proposal()["rationale"]
    assert "consequences" not in proposal.payload
    assert proposal.supporting_fact_ids == []
    assert proposal.supporting_observation_ids == []
    assert proposal.evidence_references == []


@pytest.mark.asyncio
async def test_full_payload_preserves_consequences() -> None:
    request = submission()
    provider = MockLlmProvider([{"proposals": [full_proposal()]}])
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        provider, EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    proposal = callback.results[0].proposals[0]
    assert proposal.payload["consequences"] == full_proposal()["consequences"]


@pytest.mark.asyncio
async def test_empty_proposals_completes_with_zero_proposals() -> None:
    request = submission()
    provider = MockLlmProvider([{"proposals": []}])
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        provider, EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED
    assert result.proposals == []


@pytest.mark.asyncio
async def test_invalid_output_retries_once_then_succeeds() -> None:
    request = submission()
    provider = MockLlmProvider([
        {"proposals": [{"title": "incomplete"}]},
        {"proposals": [base_proposal()]},
    ])
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        provider, EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "CORRECTIVE RETRY" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED


@pytest.mark.asyncio
async def test_invalid_output_after_retry_fails_with_invalid_llm_output() -> None:
    request = submission()
    provider = MockLlmProvider([
        {"proposals": [{"title": "incomplete"}]},
        {"proposals": [{"title": "still", "context": "bad"}]},
    ])
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        provider, EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED
    assert result.proposals == []
    assert result.error.code == "INVALID_LLM_OUTPUT"


class FailingProvider:
    provider_name = "failing-test-provider"
    model_identifier = "failing-test-model"

    async def generate_structured(self, request: object, model: object) -> object:
        raise RuntimeError("provider unavailable")


@pytest.mark.asyncio
async def test_provider_failure_sends_failed_callback() -> None:
    request = submission()
    callback = RecordingCallbackClient()
    service = EngineeringDecisionGenerationService(
        FailingProvider(), EngineeringDecisionPromptBuilder(), callback
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED
    assert result.proposals == []
    assert result.error.code == "LLM_PROVIDER_ERROR"


@pytest.mark.asyncio
async def test_schema_rejects_extra_fields() -> None:
    with pytest.raises(Exception):
        EngineeringDecisionGenerationOutput.model_validate(
            {
                "proposals": [
                    {
                        **base_proposal(),
                        "supportingFactIds": [str(uuid4())],
                    }
                ]
            }
        )


class RecordingDecisionService:
    def __init__(self) -> None:
        self.calls: list[tuple[object, object]] = []

    async def process(self, submission: object, external_job_id: object) -> None:
        self.calls.append((submission, external_job_id))


@pytest.mark.asyncio
async def test_decision_task_dispatches_to_decision_service_not_stub() -> None:
    request = submission()
    callback = RecordingCallbackClient()
    decision_service = RecordingDecisionService()
    dispatcher = AiTaskProcessingService(
        insight_generation_service=object(),  # type: ignore[arg-type]
        callback_client=callback,  # type: ignore[arg-type]
        decision_generation_service=decision_service,  # type: ignore[arg-type]
    )

    await dispatcher.process(request, uuid4())

    assert len(decision_service.calls) == 1
    assert decision_service.calls[0][0] is request
    assert callback.results == []


def test_decision_prompt_includes_shared_context_utilization_contract() -> None:
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "Base project-specific conclusions on the supplied project context" in prompt.user_message
    assert "Generic model knowledge must not be used as evidence" in prompt.user_message
    assert "Semantic Sections are semantic indexes" in prompt.user_message
    assert "Do not double-count" in prompt.user_message
    assert "Do not produce one proposal per section" in prompt.user_message
    assert "Do not infer causality, historical motivation, or developer intent" in prompt.user_message


def test_decision_prompt_requires_project_specific_decision_reconstruction() -> None:
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "Look first for explicit decision evidence or genuinely independent convergent signals" in prompt.user_message
    assert "Primary perspectives: DECISIONS, HISTORY, REPOSITORY_CHANGES, VALIDATED_KNOWLEDGE" in prompt.user_message
    assert "Technology usage alone is not sufficient" in prompt.user_message


def test_decision_prompt_has_positive_emission_gate() -> None:
    """RED: positive emission gate - must emit only when explicit decision evidence OR strong independent convergence"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    # Positive gate: must determine concrete project-specific choice is supported
    assert "Emission gate: emit a decision only when" in prompt.user_message
    assert "concrete project-specific engineering choice" in prompt.user_message
    assert "engineering decision" in prompt.user_message.lower()

    # Explicit evidence path
    assert "explicit decision evidence" in prompt.user_message.lower()

    # Strong convergence path requires independent signals
    assert "independent" in prompt.user_message.lower()
    assert "convergent" in prompt.user_message.lower()


def test_decision_prompt_defines_strong_convergence_with_independence() -> None:
    """RED: strong convergence must mean independent decision signals, not repeated implementation-state"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    # Strong convergence = independent decision signals, not repeated implementation-state observations
    assert "independent" in prompt.user_message.lower()
    assert "convergent" in prompt.user_message.lower()
    assert "independent" in prompt.user_message.lower()
    assert "implementation state" in prompt.user_message.lower()
    assert "same underlying implementation state" in prompt.user_message.lower()


def test_decision_prompt_has_evidence_independence_rule() -> None:
    """RED: evidence independence rule - repeated implementation-state observations are not independent evidence"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    # Evidence independence rule
    assert "independent" in prompt.user_message.lower()
    assert "evidence" in prompt.user_message.lower()
    assert "implementation state" in prompt.user_message.lower()
    assert "not count as independent" in prompt.user_message.lower()


def test_decision_prompt_distinguishes_implementation_state_from_decision_evidence() -> None:
    """RED: must distinguish implementation state from decision evidence"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "implementation" in prompt.user_message.lower()
    assert "state" in prompt.user_message.lower()
    assert "decision" in prompt.user_message.lower()
    assert "evidence" in prompt.user_message.lower()
    # Must explicitly say implementation state is not decision evidence
    assert "not sufficient" in prompt.user_message.lower()


def test_decision_prompt_has_negative_emission_gate() -> None:
    """RED: negative gate - technology presence, implementation evidence, repeated observations are insufficient"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    # Negative gate: technology presence alone is not sufficient
    assert "technology" in prompt.user_message.lower()
    assert "presence" in prompt.user_message.lower()
    assert "not sufficient" in prompt.user_message.lower()

    # Must not combine with generic knowledge
    assert "generic" in prompt.user_message.lower()
    assert "knowledge" in prompt.user_message.lower()
    assert "rationale" in prompt.user_message.lower()


def test_decision_prompt_has_rationale_eligibility_rule() -> None:
    """RED: rationale must be supported, generic knowledge cannot be used as project rationale"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "rationale" in prompt.user_message.lower()
    assert "supported" in prompt.user_message.lower()
    assert "generic" in prompt.user_message.lower()
    assert "knowledge" in prompt.user_message.lower()
    assert "rationale" in prompt.user_message.lower()


def test_decision_prompt_has_selectivity_principle() -> None:
    """RED: selectivity - prefer fewer well-supported over broad speculative coverage"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "fewer" in prompt.user_message.lower()
    assert "well-supported" in prompt.user_message.lower()
    assert "speculative" in prompt.user_message.lower()


def test_decision_prompt_preserves_zero_decision_validity() -> None:
    """RED: zero proposals must remain legitimate when no decision is supported"""
    prompt = EngineeringDecisionPromptBuilder().build(submission())

    assert "zero" in prompt.user_message.lower()
    assert "valid" in prompt.user_message.lower()
    assert "legitimate" in prompt.user_message.lower() or "acceptable" in prompt.user_message.lower()
