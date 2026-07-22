import json
from typing import Any

from app.providers.base import LlmRequest
from app.schemas.ai_task import IntentDefinition


class UnsupportedPromptTemplateError(ValueError):
    pass


class InsightPromptBuilder:
    VERSION = "intent-driven-insight-v1"
    SYSTEM_INSTRUCTIONS = """You are the interpretation component of DevLog AI.
Use only the supplied AnalysisContext. Never assume repository contents, query
external systems, or present a proposal as validated knowledge. The supplied
Intent is the exclusive objective. Return only structured Insight proposals."""

    TEMPLATES = {
        "describe-project-prompt-v1": (
            "describe-project", "v1",
            {"PROJECT_PRESENTATION", "ARCHITECTURE_DESCRIPTION", "TECHNOLOGY_DESCRIPTION"},
            "Describe the project using only objectively supported characteristics.",
        ),
        "generate-readme-prompt-v1": (
            "generate-readme", "v1",
            {"INSTALLATION", "USAGE", "REQUIREMENTS", "PROJECT_PRESENTATION"},
            "Propose structured README information; do not generate or modify a README.",
        ),
        "architecture-overview-prompt-v1": (
            "architecture-overview", "v1",
            {"ARCHITECTURE_DESCRIPTION", "TECHNOLOGY_DESCRIPTION", "INFRASTRUCTURE_DESCRIPTION", "API_DESCRIPTION"},
            "Describe demonstrable architectural characteristics without quality judgements.",
        ),
    }

    def build(self, intent: IntentDefinition, context: dict[str, Any]) -> LlmRequest:
        template = self.TEMPLATES.get(intent.prompt_template)
        if template is None:
            raise UnsupportedPromptTemplateError(
                f"Unsupported prompt template: {intent.prompt_template}"
            )
        expected_id, expected_version, expected_types, task_definition = template
        submitted_types = {value.value for value in intent.supported_insight_types}
        if (intent.id, intent.version, submitted_types) != (
            expected_id, expected_version, expected_types
        ):
            raise UnsupportedPromptTemplateError(
                "Intent identity or supported Insight types do not match its versioned template"
            )
        serialized_intent = json.dumps(
            intent.model_dump(by_alias=True, mode="json"),
            ensure_ascii=False, sort_keys=True, separators=(",", ":"),
        )
        serialized_context = json.dumps(
            context, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
        )
        supported = ", ".join(value.value for value in intent.supported_insight_types)
        return LlmRequest(
            prompt_version=f"{self.VERSION}:{intent.prompt_template}",
            system_instructions=self.SYSTEM_INSTRUCTIONS,
            user_prompt=(
                f"{task_definition}\n\n"
                f"BUSINESS INTENT\n{serialized_intent}\n\n"
                f"SUPPORTED INSIGHT TYPES\n{supported}\n\n"
                "STRUCTURED ANALYSIS CONTEXT\n"
                f"{serialized_context}\n\n"
                "Return an object with a proposals array. Every item must contain "
                "insightType, title, summary, rationale, confidence, supportingFactIds, "
                "supportingObservationIds, and evidenceReferences."
            ),
        )

    def corrective_retry(self, original: LlmRequest, validation_error: Exception) -> LlmRequest:
        return LlmRequest(
            prompt_version=original.prompt_version,
            system_instructions=original.system_instructions,
            user_prompt=original.user_prompt,
            corrective_feedback=str(validation_error),
        )
