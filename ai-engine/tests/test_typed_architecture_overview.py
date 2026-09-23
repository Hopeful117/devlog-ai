from pydantic import ValidationError
import pytest
from uuid import uuid4

from app.models.intent import InsightType
from app.prompts.insight import InsightPromptBuilder
from app.schemas.insight import TypedInsightGenerationOutput
from app.schemas.typed_reference import ProviderAiReference
from app.services.insight_generation_service import InsightGenerationService
from app.providers.mock import MockLlmProvider
from tests.intent_fixtures import architecture_overview_v3_intent, prompt_request


def typed_knowledge() -> dict[str, object]:
    return {
        "context": {"selectedInsights": [{"reference": {"type": "INSIGHT", "ref": "insight:1", "scope": "PROJECT"}}]},
        "groundingCandidates": {
            "facts": [{"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"}],
            "observations": [{"type": "OBSERVATION", "ref": "O001", "scope": "ANALYSIS_CONTEXT"}],
            "evidence": [{"type": "REPOSITORY_EVIDENCE", "ref": "git:source:sha", "scope": "REPOSITORY"}],
        },
        "selectionDigest": "a" * 64,
    }


def test_v3_prompt_uses_typed_context_and_candidates_only() -> None:
    request = prompt_request(knowledge=typed_knowledge(), intent=architecture_overview_v3_intent())

    prompt = InsightPromptBuilder().build(request)

    assert "F001" in prompt.user_message
    assert "supportingFactRefs" in prompt.user_message
    assert "canonicalSourceIdentity" not in prompt.user_message
    assert "CONTEXT REFERENCES" in prompt.user_message
    assert "groundingRefs accepts only the exact union" in prompt.user_message
    assert "Contextual Insight, Analysis, Project, and Project Profile refs are never valid groundingRefs" in prompt.user_message


def test_v3_output_rejects_legacy_grounding_fields() -> None:
    with pytest.raises(ValidationError):
        TypedInsightGenerationOutput.model_validate({
            "proposals": [], "synthesis": None, "supportingFactIds": []
        })


def test_typed_reference_serializes_with_core_enum_values() -> None:
    reference = ProviderAiReference(type="FACT", ref="F001", scope="ANALYSIS_CONTEXT")

    assert reference.model_dump(mode="json") == {
        "type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"
    }


def test_typed_reference_validation_uses_semantic_keys_for_set_operations() -> None:
    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]
    candidates = service._typed_candidates(
        [
            {"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"},
            {"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"},
            {"type": "FACT", "ref": "F002", "scope": "ANALYSIS_CONTEXT"},
        ],
        "FACT",
        "ANALYSIS_CONTEXT",
    )

    assert len(candidates) == 2
    service._require_typed_subset(
        [ProviderAiReference(type="FACT", ref="F002", scope="ANALYSIS_CONTEXT")],
        candidates,
        "supportingFactRefs",
    )


def typed_validation_context() -> dict[str, object]:
    return {
        "context": {
            "selectedFacts": [{
                "type": "DOCKER_SERVICE_DEPENDS_ON",
                "content": "from=backend,to=ai-engine",
                "evidenceReferences": ["evidence:relationship"],
            }],
            "analysis": {"reference": {
                "type": "ANALYSIS", "ref": "A001", "scope": "ANALYSIS_CONTEXT",
            }},
            "project": {"reference": {
                "type": "PROJECT", "ref": "project:1", "scope": "PROJECT",
            }},
            "projectProfile": {"reference": {
                "type": "PROJECT_PROFILE", "ref": "PP001", "scope": "ANALYSIS_CONTEXT",
            }},
            "selectedInsights": [{"reference": {
                "type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT",
            }}],
            "existingArchitectureKnowledge": [{
                "reference": {
                    "type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT",
                },
                "title": "Current architecture baseline",
                "content": "The project contains the backend and ai-engine components.",
                "evidenceReferences": ["evidence:relationship"],
            }],
        },
        "groundingCandidates": {
            "facts": [{"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"}],
            "observations": [{"type": "OBSERVATION", "ref": "O001", "scope": "ANALYSIS_CONTEXT"}],
            "evidence": [{
                "type": "REPOSITORY_EVIDENCE", "ref": "shared-token", "scope": "REPOSITORY",
            }],
        },
        "selectionDigest": "a" * 64,
    }


def typed_synthesis(
    grounding_refs: list[dict[str, str]],
    proposals: list[dict[str, object]] | None = None,
) -> dict[str, object]:
    proposal_items = proposals or []
    return {
        "proposals": proposal_items,
        "synthesis": {
            "title": "Current architecture",
            "sections": [{"name": "Evidence", "content": "The selected evidence supports this bounded view."}],
            "deltaConclusion": "DELTAS_PROPOSED" if proposal_items else "NO_MATERIAL_DELTA",
            "groundingRefs": grounding_refs,
        },
    }


def typed_proposal(
    delta_type: str,
    target: dict[str, str] | None = None,
) -> dict[str, object]:
    proposal: dict[str, object] = {
        "insightType": "ARCHITECTURE_DESCRIPTION",
        "title": "Architecture relationship",
        "summary": "The selected evidence describes an architecture relationship.",
        "rationale": "The proposal is grounded in the selected evidence.",
        "deltaType": delta_type,
        "confidence": 0.8,
        "supportingFactRefs": [],
        "supportingObservationRefs": [],
        "evidenceRefs": [],
    }
    if target is not None:
        proposal["targetInsightRef"] = target
    return proposal


def validate_typed_output(output: dict[str, object]) -> None:
    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]
    parsed = TypedInsightGenerationOutput.model_validate(output)
    service._validate_output(
        parsed,
        typed_validation_context(),
        set(architecture_overview_v3_intent().supported_insight_types),
        require_synthesis=True,
    )


def test_v3_grounding_accepts_fact_observation_and_repository_evidence_union() -> None:
    validate_typed_output(typed_synthesis([
        {"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"},
        {"type": "OBSERVATION", "ref": "O001", "scope": "ANALYSIS_CONTEXT"},
        {"type": "REPOSITORY_EVIDENCE", "ref": "shared-token", "scope": "REPOSITORY"},
    ]))


@pytest.mark.parametrize("reference", [
    {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"},
    {"type": "ANALYSIS", "ref": "A001", "scope": "ANALYSIS_CONTEXT"},
    {"type": "PROJECT", "ref": "project:1", "scope": "PROJECT"},
    {"type": "PROJECT_PROFILE", "ref": "PP001", "scope": "ANALYSIS_CONTEXT"},
])
def test_v3_grounding_rejects_context_only_references(reference: dict[str, str]) -> None:
    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]
    parsed = TypedInsightGenerationOutput.model_validate(typed_synthesis([reference]))

    with pytest.raises(ValueError) as raised:
        service._validate_output(
            parsed,
            typed_validation_context(),
            set(architecture_overview_v3_intent().supported_insight_types),
            require_synthesis=True,
        )

    error = raised.value
    assert getattr(error, "diagnostics")["field"] == "groundingRefs"
    rejected = getattr(error, "diagnostics")["rejectedReferences"][0]
    assert rejected["reason"] == "NOT_AUTHORIZED_FOR_GROUNDING"
    assert rejected["type"] == reference["type"]
    assert rejected["ref"] == reference["ref"]
    assert rejected["scope"] == reference["scope"]


def test_v3_grounding_collision_requires_exact_namespace_and_scope() -> None:
    validate_typed_output(typed_synthesis([
        {"type": "REPOSITORY_EVIDENCE", "ref": "shared-token", "scope": "REPOSITORY"},
    ]))

    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]
    parsed = TypedInsightGenerationOutput.model_validate(typed_synthesis([
        {"type": "REPOSITORY_EVIDENCE", "ref": "shared-token", "scope": "PROJECT"},
    ]))
    with pytest.raises(ValueError) as raised:
        service._validate_output(
            parsed,
            typed_validation_context(),
            {InsightType.ARCHITECTURE_DESCRIPTION},
            require_synthesis=True,
        )

    rejected = getattr(raised.value, "diagnostics")["rejectedReferences"][0]
    assert rejected["reason"] == "WRONG_SCOPE"


def test_v3_prompt_exposes_enriches_target_contract() -> None:
    prompt = InsightPromptBuilder().build(
        prompt_request(
            knowledge=typed_validation_context(),
            intent=architecture_overview_v3_intent(),
        )
    )

    assert "ARCHITECTURE ENRICHMENT TARGETS" in prompt.user_message
    assert '"targetInsightRef":{"ref":"shared-token"' in prompt.user_message
    assert "ENRICHES requires targetInsightRef copied exactly" in prompt.user_message
    assert "use NEW only when the knowledge is genuinely new" in prompt.user_message


def test_v3_accepts_enriches_with_exact_authorized_target() -> None:
    validate_typed_output(typed_synthesis(
        [{"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"}],
        proposals=[typed_proposal(
            "ENRICHES",
            {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"},
        )],
    ))


def test_v3_rejects_enriches_without_target() -> None:
    with pytest.raises(ValidationError, match="targetInsightRef is required"):
        TypedInsightGenerationOutput.model_validate(
            typed_synthesis([], proposals=[typed_proposal("ENRICHES")])
        )


@pytest.mark.parametrize("target,reason", [
    ({"type": "INSIGHT", "ref": "unknown", "scope": "PROJECT"},
     "NOT_IN_EXISTING_ARCHITECTURE_KNOWLEDGE"),
    ({"type": "FACT", "ref": "shared-token", "scope": "PROJECT"},
     "WRONG_NAMESPACE_OR_SCOPE"),
    ({"type": "INSIGHT", "ref": "shared-token", "scope": "ANALYSIS_CONTEXT"},
     "WRONG_NAMESPACE_OR_SCOPE"),
])
def test_v3_rejects_invalid_enriches_target(
    target: dict[str, str], reason: str
) -> None:
    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]
    parsed = TypedInsightGenerationOutput.model_validate(typed_synthesis(
        [], proposals=[typed_proposal("ENRICHES", target)]
    ))

    with pytest.raises(ValueError) as raised:
        service._validate_output(
            parsed,
            typed_validation_context(),
            set(architecture_overview_v3_intent().supported_insight_types),
            require_synthesis=True,
        )

    error = raised.value
    assert getattr(error, "diagnostics")["reason"] == reason
    assert getattr(error, "diagnostics")["authorizedSource"] == "existingArchitectureKnowledge"


def test_v3_accepts_new_without_target_and_rejects_new_with_target() -> None:
    validate_typed_output(typed_synthesis(
        [{"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"}],
        proposals=[typed_proposal("NEW")],
    ))

    with pytest.raises(ValidationError, match="targetInsightRef must be omitted"):
        TypedInsightGenerationOutput.model_validate(typed_synthesis(
            [], proposals=[typed_proposal(
                "NEW", {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"}
            )]
        ))


def test_v3_relationship_retry_context_uses_nested_typed_context() -> None:
    service = InsightGenerationService(MockLlmProvider([]), InsightPromptBuilder(), object())  # type: ignore[arg-type]

    retry_context = service._relationship_retry_context(typed_validation_context())

    assert retry_context is not None
    assert retry_context.target_field == "targetInsightRef"
    assert retry_context.candidates[0].target_reference == {
        "type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"
    }


class RecordingCallback:
    def __init__(self) -> None:
        self.results: list[object] = []

    async def send_result(self, correlation_id: object, result: object) -> object:
        self.results.append(result)
        return object()


@pytest.mark.asyncio
async def test_v3_unauthorized_grounding_gets_structured_retry_and_can_recover() -> None:
    request = prompt_request(
        knowledge=typed_validation_context(),
        intent=architecture_overview_v3_intent(),
    )
    invalid = typed_synthesis([
        {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"},
    ])
    valid = typed_synthesis([
        {"type": "REPOSITORY_EVIDENCE", "ref": "shared-token", "scope": "REPOSITORY"},
    ])
    provider = MockLlmProvider([invalid, valid])
    callback = RecordingCallback()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    retry_prompt = provider.requests[1].user_message
    assert "CORRECTIVE RETRY (attempt 2)" in retry_prompt
    assert "NOT_AUTHORIZED_FOR_GROUNDING" in retry_prompt
    assert "shared-token" in retry_prompt
    assert callback.results[0].status.value == "COMPLETED"  # type: ignore[attr-defined]
    trace = callback.results[0].interaction_traces[0]  # type: ignore[attr-defined]
    assert trace.validation_diagnostics is not None
    assert "NOT_AUTHORIZED_FOR_GROUNDING" in trace.validation_diagnostics


@pytest.mark.asyncio
async def test_v3_unauthorized_grounding_remains_failed_after_retry() -> None:
    request = prompt_request(
        knowledge=typed_validation_context(),
        intent=architecture_overview_v3_intent(),
    )
    invalid = typed_synthesis([
        {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"},
    ])
    provider = MockLlmProvider([invalid, invalid])
    callback = RecordingCallback()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    assert callback.results[0].status.value == "FAILED"  # type: ignore[attr-defined]
    assert callback.results[0].error.code == "INVALID_LLM_OUTPUT"  # type: ignore[attr-defined]


@pytest.mark.asyncio
async def test_v3_enriches_target_failure_retries_with_exact_target_context() -> None:
    request = prompt_request(
        knowledge=typed_validation_context(),
        intent=architecture_overview_v3_intent(),
    )
    invalid = typed_synthesis([
        {"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"},
    ], proposals=[typed_proposal(
        "ENRICHES",
        {"type": "INSIGHT", "ref": "unknown", "scope": "PROJECT"},
    )])
    valid = typed_synthesis([
        {"type": "FACT", "ref": "F001", "scope": "ANALYSIS_CONTEXT"},
    ], proposals=[typed_proposal(
        "ENRICHES",
        {"type": "INSIGHT", "ref": "shared-token", "scope": "PROJECT"},
    )])
    provider = MockLlmProvider([invalid, valid])
    callback = RecordingCallback()
    service = InsightGenerationService(provider, InsightPromptBuilder(), callback)  # type: ignore[arg-type]

    await service.process(request, uuid4())

    assert len(provider.requests) == 2
    retry_prompt = provider.requests[1].user_message
    assert "NOT_IN_EXISTING_ARCHITECTURE_KNOWLEDGE" in retry_prompt
    assert '"targetInsightRef":{"ref":"shared-token"' in retry_prompt
    assert "targetInsightRef is required for ENRICHES" in retry_prompt
    assert callback.results[0].status.value == "COMPLETED"  # type: ignore[attr-defined]
