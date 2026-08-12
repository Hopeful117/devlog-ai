from app.models.intent import InsightType
from app.schemas.ai_task import IntentDefinition
from app.schemas.ai_task import PromptRequest, UserGuidance
from app.models.ai_task import AiTaskType
from uuid import uuid4


def describe_project_intent() -> IntentDefinition:
    return IntentDefinition(
        id="describe-project",
        version="v1",
        objective="Describe the project.",
        supported_insight_types=[
            InsightType.PROJECT_PRESENTATION,
            InsightType.ARCHITECTURE_DESCRIPTION,
            InsightType.TECHNOLOGY_DESCRIPTION,
        ],
        constraints=["Use only AnalysisContext."],
        output_schema={"type": "object", "root": "proposals"},
        prompt_template="describe-project-prompt-v1",
        context_profiles=["project-state-v1", "history-v1"],
    )


def describe_project_intent_json() -> dict[str, object]:
    return describe_project_intent().model_dump(by_alias=True, mode="json")


def architecture_overview_intent() -> IntentDefinition:
    return IntentDefinition(
        id="architecture-overview",
        version="v1",
        objective="Describe the architecture incrementally.",
        supported_insight_types=[
            InsightType.ARCHITECTURE_DESCRIPTION,
            InsightType.TECHNOLOGY_DESCRIPTION,
            InsightType.INFRASTRUCTURE_DESCRIPTION,
            InsightType.API_DESCRIPTION,
        ],
        constraints=["Use only AnalysisContext."],
        output_schema={"type": "object", "root": "proposals", "allowedDeltaTypes": ["NEW", "ENRICHES"]},
        prompt_template="architecture-overview-prompt-v1",
        context_profiles=["architecture-v1", "history-v1"],
    )


def selected_knowledge(
    *, facts: list[object] | None = None, observations: list[object] | None = None,
    analysis_id: object | None = None,
    repository_evidence: list[object] | None = None,
    existing_architecture_knowledge: list[object] | None = None,
) -> dict[str, object]:
    return {
        "project": {"id": str(uuid4()), "name": "Core"},
        "analysis": {"id": str(analysis_id or uuid4())},
        "projectProfile": {"id": str(uuid4()), "profileVersion": "v1"},
        "selectedFacts": facts or [], "selectedObservations": observations or [],
        "diagnostics": {"collectionComplete": True, "truncated": False,
                        "warningCount": 0, "errorCount": 0},
        "selectedInsights": [],
        "existingArchitectureKnowledge": existing_architecture_knowledge or [],
        "repositoryContext": {
            "contextVersion": "repository-context-engine-v1",
            "profile": "PROJECT_STATE",
            "evidence": repository_evidence or [],
            "usedTokens": 0,
            "contextDigest": "b" * 64,
        },
        "selectionMetadata": {"selectionVersion": "knowledge-selection-v2"},
        "selectionDigest": "a" * 64,
    }


def prompt_request(
    *, knowledge: dict[str, object] | None = None,
    guidance: UserGuidance | None = None,
    intent: IntentDefinition | None = None,
) -> PromptRequest:
    analysis_id = uuid4()
    resolved_intent = intent or describe_project_intent()
    return PromptRequest(
        request_id=uuid4(), correlation_id=uuid4(), analysis_id=analysis_id,
        ai_task_id=uuid4(), task_type=AiTaskType.INSIGHT_GENERATION,
        intent=resolved_intent, user_guidance=guidance,
        selected_knowledge=knowledge or selected_knowledge(analysis_id=analysis_id),
        expected_output_contract=resolved_intent.output_schema,
        metadata={"source": "test"},
    )
