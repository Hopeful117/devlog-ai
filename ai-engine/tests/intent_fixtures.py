from app.models.intent import InsightType
from app.schemas.ai_task import IntentDefinition


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
    )


def describe_project_intent_json() -> dict[str, object]:
    return describe_project_intent().model_dump(by_alias=True, mode="json")
