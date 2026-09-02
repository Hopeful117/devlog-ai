from uuid import uuid4

import pytest

from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.insight import InsightPromptBuilder
from app.providers.mock import MockLlmProvider
from app.schemas.ai_task import AiTaskSubmissionRequest
from app.services.insight_generation_service import InsightGenerationService
from tests.intent_fixtures import (
    architecture_overview_intent,
    architecture_overview_v2_intent,
    describe_project_intent,
    selected_knowledge,
)


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
                "deltaType": "NEW",
                "confidence": 0.9,
                "supportingFactIds": [fact_id],
                "supportingObservationIds": [observation_id],
                "evidenceReferences": [evidence],
            }
        ]
    }


def architecture_submission() -> tuple[AiTaskSubmissionRequest, str, str, str, str]:
    fact_id = str(uuid4())
    observation_id = str(uuid4())
    evidence = "src/architecture.md:10"
    target_insight_id = str(uuid4())
    request = AiTaskSubmissionRequest(
        request_id=uuid4(),
        correlation_id=uuid4(),
        task_type=AiTaskType.INSIGHT_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=architecture_overview_intent(),
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
            existing_architecture_knowledge=[
                {
                    "insightId": target_insight_id,
                    "title": "Existing modular architecture",
                    "content": "The system is modular.",
                    "sourceType": "ARCHITECTURE_DESCRIPTION",
                }
            ],
        ),
        expected_output_contract={
            "type": "object",
            "root": "proposals",
            "allowedDeltaTypes": ["NEW", "ENRICHES"],
        },
        metadata={"source": "test"},
    )
    return request, fact_id, observation_id, evidence, target_insight_id


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
async def test_repository_context_evidence_reference_is_accepted() -> None:
    request, fact_id, observation_id, _ = submission()
    repository_reference = "git:repository-id:commit-hash"
    request = request.model_copy(update={
        "selected_knowledge": {
            **request.selected_knowledge,
            "repositoryContext": {
                "evidence": [{
                    "reference": repository_reference,
                    "relatedReferences": [],
                }]
            },
        }
    })
    provider = MockLlmProvider([
        valid_output(fact_id, observation_id, repository_reference)
    ])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(
        provider, InsightPromptBuilder(), callback  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_supporting_fact_visible_on_selected_observation_is_accepted_when_selected() -> None:
    request, fact_id, observation_id, evidence = submission()
    request = request.model_copy(update={
        "selected_knowledge": selected_knowledge(
            facts=[
                {
                    "id": fact_id,
                    "content": "Modules were separated",
                    "evidenceReferences": [evidence],
                }
            ],
            observations=[
                {
                    "id": observation_id,
                    "content": "Architecture is modular",
                    "supportingFactIds": [fact_id],
                }
            ],
        )
    })
    provider = MockLlmProvider([valid_output(fact_id, observation_id, evidence)])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


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
async def test_selected_insight_id_reused_as_supporting_fact_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = submission()
    stray_insight_id = str(uuid4())
    request = request.model_copy(update={
        "selected_knowledge": {
            **request.selected_knowledge,
            "selectedInsights": [
                {
                    "id": stray_insight_id,
                    "analysisId": str(uuid4()),
                    "type": "ARCHITECTURAL",
                    "severity": "INFO",
                    "title": "Controllers",
                    "content": "The project exposes REST controllers.",
                }
            ],
        }
    })
    invalid = valid_output(stray_insight_id, observation_id, evidence)
    provider = MockLlmProvider(
        [invalid, valid_output(fact_id, observation_id, evidence)]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert stray_insight_id in provider.requests[1].user_message
    assert "supportingFactIds contains references absent from AnalysisContext" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_selected_insight_id_reused_as_supporting_observation_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = submission()
    stray_insight_id = str(uuid4())
    request = request.model_copy(update={
        "selected_knowledge": {
            **request.selected_knowledge,
            "selectedInsights": [
                {
                    "id": stray_insight_id,
                    "analysisId": str(uuid4()),
                    "type": "ARCHITECTURAL",
                    "severity": "INFO",
                    "title": "Controllers",
                    "content": "The project exposes REST controllers.",
                }
            ],
        }
    })
    invalid = valid_output(fact_id, stray_insight_id, evidence)
    provider = MockLlmProvider(
        [invalid, valid_output(fact_id, observation_id, evidence)]
    )
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert stray_insight_id in provider.requests[1].user_message
    assert "supportingObservationIds contains references absent from AnalysisContext" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_target_insight_id_with_new_delta_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence, target_insight_id = architecture_submission()
    invalid = {
        "proposals": [
            {
                "insightType": "ARCHITECTURE_DESCRIPTION",
                "title": "Fresh architecture view",
                "summary": "This should be a new insight.",
                "rationale": "New evidence was found.",
                "deltaType": "NEW",
                "targetInsightId": target_insight_id,
                "confidence": 0.9,
                "supportingFactIds": [fact_id],
                "supportingObservationIds": [observation_id],
                "evidenceReferences": [evidence],
            }
        ]
    }
    valid = {
        "proposals": [
            {
                "insightType": "ARCHITECTURE_DESCRIPTION",
                "title": "Fresh architecture view",
                "summary": "This is a genuinely new insight.",
                "rationale": "New evidence was found.",
                "deltaType": "NEW",
                "confidence": 0.9,
                "supportingFactIds": [fact_id],
                "supportingObservationIds": [observation_id],
                "evidenceReferences": [evidence],
            }
        ]
    }
    provider = MockLlmProvider([invalid, valid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "targetInsightId must be omitted when deltaType is NEW" in provider.requests[1].user_message
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


@pytest.mark.asyncio
async def test_architecture_enrichment_payload_copies_delta_metadata() -> None:
    request, fact_id, observation_id, evidence, target_insight_id = architecture_submission()
    provider = MockLlmProvider([
        {
            "proposals": [
                {
                    "insightType": "ARCHITECTURE_DESCRIPTION",
                    "title": "Richer modular architecture",
                    "summary": "Modules also isolate deployment cadence.",
                    "rationale": "New evidence proves deployment independence.",
                    "deltaType": "ENRICHES",
                    "targetInsightId": target_insight_id,
                    "confidence": 0.9,
                    "supportingFactIds": [fact_id],
                    "supportingObservationIds": [observation_id],
                    "evidenceReferences": [evidence],
                }
            ]
        }
    ])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    proposal = callback.results[0].proposals[0]  # type: ignore[attr-defined]
    assert proposal.payload["deltaType"] == "ENRICHES"
    assert proposal.payload["targetInsightId"] == target_insight_id


@pytest.mark.asyncio
async def test_architecture_generation_accepts_empty_proposals_for_no_significant_delta() -> None:
    request, _, _, _, _ = architecture_submission()
    provider = MockLlmProvider([{"proposals": []}])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.proposals == []  # type: ignore[attr-defined]


# ---- architecture-overview-v2 synthesis tests ----

def architecture_v2_submission() -> tuple[AiTaskSubmissionRequest, str, str, str]:
    fact_id = str(uuid4())
    observation_id = str(uuid4())
    evidence = "src/architecture.md:10"
    request = AiTaskSubmissionRequest(
        request_id=uuid4(),
        correlation_id=uuid4(),
        task_type=AiTaskType.INSIGHT_GENERATION,
        analysis_id=uuid4(),
        ai_task_id=uuid4(),
        intent=architecture_overview_v2_intent(),
        selected_knowledge=selected_knowledge(
            facts=[
                {
                    "id": fact_id,
                    "content": "Spring Boot REST API modules",
                    "evidenceReferences": [evidence],
                }
            ],
            observations=[
                {"id": observation_id, "content": "Architecture is modular and layered"}
            ],
        ),
        expected_output_contract=architecture_overview_v2_intent().output_schema,
        metadata={"source": "test"},
    )
    return request, fact_id, observation_id, evidence


def v2_valid_synthesis(fact_id: str, observation_id: str, evidence: str) -> dict:
    return {
        "synthesis": {
            "title": "Current Architecture Overview",
            "sections": [
                {
                    "name": "Components",
                    "content": "The system exposes REST endpoints through Spring Boot controllers backed by repository modules.",
                },
                {
                    "name": "Relationships",
                    "content": "The controller layer delegates to the service layer, which uses the repository layer for persistence.",
                },
            ],
            "deltaConclusion": "NO_MATERIAL_DELTA",
            "groundingReferences": [evidence],
        },
        "proposals": [],
    }


def v2_valid_synthesis_with_delta(fact_id: str, observation_id: str, evidence: str) -> dict:
    return {
        "synthesis": {
            "title": "Current Architecture Overview",
            "sections": [
                {
                    "name": "Components",
                    "content": "The system exposes REST endpoints through Spring Boot controllers backed by repository modules.",
                },
                {
                    "name": "Relationships",
                    "content": "The controller layer delegates to the service layer, which uses the repository layer for persistence.",
                },
            ],
            "deltaConclusion": "DELTAS_PROPOSED",
            "groundingReferences": [evidence],
        },
        "proposals": [
            {
                "insightType": "ARCHITECTURE_DESCRIPTION",
                "title": "New caching layer",
                "summary": "A caching layer was added between service and repository.",
                "rationale": "Evidence shows a new cache module.",
                "deltaType": "NEW",
                "confidence": 0.85,
                "supportingFactIds": [fact_id],
                "supportingObservationIds": [observation_id],
                "evidenceReferences": [evidence],
            }
        ],
    }


@pytest.mark.asyncio
async def test_v2_no_delta_synthesis_only_sends_completed_with_synthesis() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    output = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([output])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 1
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.proposals == []  # type: ignore[attr-defined]
    assert result.synthesis is not None  # type: ignore[attr-defined]
    assert result.synthesis.title == "Current Architecture Overview"  # type: ignore[attr-defined]
    assert len(result.synthesis.sections) == 2  # type: ignore[attr-defined]
    assert result.synthesis.sections[0].name == "Components"  # type: ignore[attr-defined]
    assert result.synthesis.grounding_references == [evidence]  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_synthesis_with_delta_sends_completed_with_both() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    output = v2_valid_synthesis_with_delta(fact_id, observation_id, evidence)
    provider = MockLlmProvider([output])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.synthesis is not None  # type: ignore[attr-defined]
    assert len(result.proposals) == 1  # type: ignore[attr-defined]
    assert result.proposals[0].type == ProposalType.INSIGHT  # type: ignore[attr-defined]
    assert result.proposals[0].payload["title"] == "New caching layer"  # type: ignore[attr-defined]
    assert result.proposals[0].payload["deltaType"] == "NEW"  # type: ignore[attr-defined]
    assert "targetInsightId" not in result.proposals[0].payload  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_missing_delta_type_is_invalid_instead_of_defaulting_to_new() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid = v2_valid_synthesis_with_delta(fact_id, observation_id, evidence)
    invalid["proposals"][0].pop("deltaType")
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]
    assert result.error.code == "INVALID_LLM_OUTPUT"  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_explicit_enriches_preserves_classification_and_target() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    target_insight_id = str(uuid4())
    request.selected_knowledge["existingArchitectureKnowledge"] = [{
        "insightId": target_insight_id,
        "title": "Containerized project",
        "content": "The project uses Docker Compose.",
    }]
    output = v2_valid_synthesis_with_delta(fact_id, observation_id, evidence)
    output["proposals"][0]["deltaType"] = "ENRICHES"
    output["proposals"][0]["targetInsightId"] = target_insight_id
    provider = MockLlmProvider([output])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.proposals[0].payload["deltaType"] == "ENRICHES"  # type: ignore[attr-defined]
    assert result.proposals[0].payload["targetInsightId"] == target_insight_id  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_missing_synthesis_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid_output = {"proposals": []}
    valid_output_data = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid_output, valid_output_data])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "synthesis" in provider.requests[1].user_message.lower()
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_synthesis_blank_title_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid_output = {
        "synthesis": {"title": "", "sections": [{"name": "X", "content": "Y"}]},
        "proposals": [],
    }
    valid_output_data = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid_output, valid_output_data])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_synthesis_empty_sections_gets_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid_output = {
        "synthesis": {"title": "Architecture", "sections": []},
        "proposals": [],
    }
    valid_output_data = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid_output, valid_output_data])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v1_still_works_without_synthesis() -> None:
    request, fact_id, observation_id, evidence, _ = architecture_submission()
    output = valid_output(fact_id, observation_id, evidence)
    provider = MockLlmProvider([output])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    result = callback.results[0]
    assert result.status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
    assert result.synthesis is None  # type: ignore[attr-defined]
    assert len(result.proposals) == 1  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_rejects_synthesis_grounding_outside_selected_context() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid = v2_valid_synthesis(fact_id, observation_id, evidence)
    invalid["synthesis"]["groundingReferences"] = ["invented.java:1"]
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert callback.results[0].status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_requires_delta_conclusion_to_match_proposals() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    invalid = v2_valid_synthesis_with_delta(fact_id, observation_id, evidence)
    invalid["synthesis"]["deltaConclusion"] = "NO_MATERIAL_DELTA"
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert callback.results[0].status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v2_requires_delta_for_uncovered_selected_relationship() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    request.selected_knowledge["selectedFacts"][0]["content"] = "from=api,to=database"
    invalid = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert "relationship" in provider.requests[1].user_message
    assert callback.results[0].status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]


def test_relationship_novelty_ignores_endpoint_cooccurrence_in_evidence_references() -> None:
    service = object.__new__(InsightGenerationService)
    context = {
        "selectedFacts": [{"content": "from=backend,to=ai-engine"}],
        "existingArchitectureKnowledge": [{
            "title": "Containerized project",
            "content": "The project uses Docker Compose.",
            "evidenceReferences": ["backend/Dockerfile", "ai-engine/Dockerfile"],
        }],
    }

    assert service._has_uncovered_relationship(context) is True


def test_relationship_novelty_preserves_direction() -> None:
    service = object.__new__(InsightGenerationService)
    context = {
        "selectedFacts": [{"content": "from=backend,to=ai-engine"}],
        "existingArchitectureKnowledge": [{
            "title": "Reverse dependency",
            "content": "ai-engine -> backend",
        }],
    }

    assert service._has_uncovered_relationship(context) is True


def test_relationship_novelty_accepts_exact_directional_relationship() -> None:
    service = object.__new__(InsightGenerationService)
    context = {
        "selectedFacts": [{"content": "from=backend,to=ai-engine"}],
        "existingArchitectureKnowledge": [{
            "title": "Backend dependency",
            "content": "backend -> ai-engine",
        }],
    }

    assert service._has_uncovered_relationship(context) is False


@pytest.mark.asyncio
async def test_uncovered_relationship_retry_exposes_classification_and_target_context() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    request.selected_knowledge["selectedFacts"][0].update({
        "type": "DOCKER_SERVICE_DEPENDS_ON",
        "content": "from=backend,to=ai-engine",
    })
    target_insight_id = str(uuid4())
    unrelated_insight_id = str(uuid4())
    request.selected_knowledge["existingArchitectureKnowledge"] = [
        {
            "insightId": target_insight_id,
            "title": "Project Containerization with Docker and Docker Compose",
            "content": "The project uses Docker Compose.",
            "evidenceReferences": [evidence],
        },
        {
            "insightId": unrelated_insight_id,
            "title": "Automated testing",
            "content": "The project has automated tests.",
            "evidenceReferences": ["tests/test_app.py"],
        },
    ]
    invalid = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(
        provider, InsightPromptBuilder(), callback  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    retry = provider.requests[1].user_message.split("CORRECTIVE RETRY", 1)[1]
    assert "DOCKER_SERVICE_DEPENDS_ON" in retry
    assert '"source":"backend"' in retry
    assert '"target":"ai-engine"' in retry
    assert '"direction":"backend -> ai-engine"' in retry
    assert evidence in retry
    assert "genuinely new architecture knowledge" in retry
    assert "extends or refines supplied trusted architecture knowledge" in retry
    assert target_insight_id in retry
    assert "Project Containerization with Docker and Docker Compose" in retry
    assert unrelated_insight_id not in retry
    assert "ENRICHES requires targetInsightId" in retry
    assert "NEW must omit targetInsightId" in retry
    assert "use ENRICHES" not in retry
    assert "must be ENRICHES" not in retry
    assert callback.results[0].status == AiTaskResultStatus.FAILED  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_uncovered_relationship_retry_preserves_reverse_direction() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    request.selected_knowledge["selectedFacts"][0].update({
        "type": "DOCKER_SERVICE_DEPENDS_ON",
        "content": "from=ai-engine,to=backend",
    })
    request.selected_knowledge["existingArchitectureKnowledge"] = [{
        "insightId": str(uuid4()),
        "title": "Containerized project",
        "content": "The project uses Docker Compose.",
        "evidenceReferences": [evidence],
    }]
    invalid = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(
        provider, InsightPromptBuilder(), callback  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    retry = provider.requests[1].user_message.split("CORRECTIVE RETRY", 1)[1]
    assert '"direction":"ai-engine -> backend"' in retry
    assert '"direction":"backend -> ai-engine"' not in retry
    assert len(provider.requests) == 2


def test_relationship_retry_uses_all_valid_targets_when_evidence_cannot_narrow() -> None:
    service = object.__new__(InsightGenerationService)
    first_id = str(uuid4())
    second_id = str(uuid4())
    context = {
        "selectedFacts": [{
            "type": "SERVICE_DEPENDENCY",
            "content": "from=orders,to=payments",
            "evidenceReferences": ["compose.yaml"],
        }],
        "existingArchitectureKnowledge": [
            {
                "insightId": first_id,
                "title": "Service architecture",
                "content": "The system has services.",
                "evidenceReferences": ["architecture.md"],
            },
            {
                "insightId": second_id,
                "title": "Deployment architecture",
                "content": "The system has deployment automation.",
                "evidenceReferences": ["deployment.md"],
            },
        ],
    }

    retry_context = service._relationship_retry_context(context)

    assert retry_context is not None
    assert [candidate.insight_id for candidate in retry_context.candidates] == [
        first_id, second_id
    ]


@pytest.mark.asyncio
async def test_exact_directional_relationship_does_not_trigger_corrective_retry() -> None:
    request, fact_id, observation_id, evidence = architecture_v2_submission()
    request.selected_knowledge["selectedFacts"][0]["content"] = (
        "from=backend,to=ai-engine"
    )
    request.selected_knowledge["existingArchitectureKnowledge"] = [{
        "title": "Backend dependency",
        "content": "backend -> ai-engine",
    }]
    output = v2_valid_synthesis(fact_id, observation_id, evidence)
    provider = MockLlmProvider([output])
    callback = RecordingCallbackClient()
    service = InsightGenerationService(
        provider, InsightPromptBuilder(), callback  # type: ignore[arg-type]
    )

    await service.process(request, uuid4())

    assert len(provider.requests) == 1
    assert callback.results[0].status == AiTaskResultStatus.COMPLETED  # type: ignore[attr-defined]
