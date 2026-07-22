from uuid import uuid4

import pytest

from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.insight import InsightPromptBuilder
from app.providers.mock import MockLlmProvider
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.services.insight_generation_service import InsightGenerationService
from tests.intent_fixtures import describe_project_intent, selected_knowledge


class RecordingCallbackClient:
    def __init__(self) -> None:
        self.results: list[object] = []

    async def send_result(self, correlation_id: object, result: object) -> object:
        self.results.append(result)
        return object()


def submission() -> tuple[AiTaskSubmissionRequest, str, str, str]:
    fact_id = str(uuid4())
    observation_id = str(uuid4())
    evidence = "src/app.py:10"
    request = AiTaskSubmissionRequest(
        request_id=uuid4(),
        correlation_id=uuid4(),
        task_type=AiTaskType.INSIGHT_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=describe_project_intent(),
        selected_knowledge=selected_knowledge(
            facts=[
                {
                    "id": fact_id,
                    "content": "Modules were separated",
                    "evidenceReferences": [evidence],
                }
            ],
            observations=[
                {"id": observation_id, "content": "Architecture is modular"}
            ],
        ),
        expected_output_contract={"type": "object", "root": "proposals"},
        metadata={"source": "test"},
    )
    return request, fact_id, observation_id, evidence


def valid_output(fact_id: str, observation_id: str, evidence: str) -> dict:
    return {
        "proposals": [
            {
                "insightType": "ARCHITECTURE_DESCRIPTION",
                "title": "Modular architecture",
                "summary": "The application was split into modules.",
                "rationale": "The supplied fact and observation support it.",
                "confidence": 0.9,
                "supportingFactIds": [fact_id],
                "supportingObservationIds": [observation_id],
                "evidenceReferences": [evidence],
            }
        ]
    }


@pytest.mark.asyncio
async def test_successful_generation_sends_only_insight_proposals() -> None:
    request, fact_id, observation_id, evidence = submission()
    provider = MockLlmProvider([valid_output(fact_id, observation_id, evidence)])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 1
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.proposals[0].type == ProposalType.INSIGHT  # type: ignore[attr-defined]
    assert result.proposals[0].payload["title"] == "Modular architecture"  # type: ignore[attr-defined]
    assert result.proposals[0].payload["insightType"] == "ARCHITECTURE_DESCRIPTION"  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_unsupported_insight_type_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = submission()
    invalid = valid_output(fact_id, observation_id, evidence)
    invalid["proposals"][0]["insightType"] = "INSTALLATION"
    provider = MockLlmProvider(
        [invalid, valid_output(fact_id, observation_id, evidence)]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "not supported by Intent" in provider.requests[1].user_message


@pytest.mark.asyncio
async def test_invalid_output_gets_one_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = submission()
    provider = MockLlmProvider(
        [
            {"proposals": [{"title": "incomplete"}]},
            valid_output(fact_id, observation_id, evidence),
        ]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "CORRECTIVE RETRY" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_reference_outside_context_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = submission()
    invalid = valid_output(str(uuid4()), observation_id, evidence)
    provider = MockLlmProvider(
        [invalid, valid_output(fact_id, observation_id, evidence)]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "supportingFactIds" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_failed_corrective_retry_sends_failed_callback() -> None:
    request, _, _, _ = submission()
    provider = MockLlmProvider(
        [
            {"proposals": [{"title": "incomplete"}]},
            {"proposals": [{"confidence": 2}]},
        ]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.proposals == []  # type: ignore[attr-defined]
    assert result.error.code == "INVALID_LLM_OUTPUT"  # type: ignore[attr-defined]


class FailingProvider:
    provider_name = "failing-test-provider"
    model_identifier = "failing-test-model"

    async def generate_structured(self, request: object, model: object) -> object:
        raise RuntimeError("provider unavailable")


@pytest.mark.asyncio
async def test_provider_failure_sends_failed_callback_without_corrective_retry() -> None:
    request, _, _, _ = submission()
    callback = RecordingCallbackClient()
    service = InsightGenerationService(  # type: ignore[arg-type]
        FailingProvider(),
        InsightPromptBuilder(),
        callback,
    )

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "LLM_PROVIDER_ERROR"  # type: ignore[attr-defined]
