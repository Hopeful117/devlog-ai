import json
from typing import Any

from app.providers.base import LlmRequest


class InsightPromptBuilder:
    VERSION = "insight-generation-v1"

    SYSTEM_INSTRUCTIONS = """You are the interpretation component of DevLog AI.
Use only the supplied AnalysisContext. Never assume repository contents, query
external systems, or present a proposal as validated knowledge. If evidence is
insufficient, omit the proposal or express uncertainty in its rationale.
Return only structured output conforming to the requested schema."""

    TASK_DEFINITION = """Generate zero or more engineering insight proposals.
Every proposal must be grounded in the supplied facts and observations. Use
only fact IDs, observation IDs, and evidence references present in the context.
Confidence is a number from 0 to 1. Do not generate decisions, documentation,
knowledge events, or project modifications."""

    EXPECTED_OUTPUT = """Expected output: an object with a proposals array.
Each item contains title, summary, rationale, confidence, supportingFactIds,
supportingObservationIds, and evidenceReferences."""

    def build(self, context: dict[str, Any]) -> LlmRequest:
        serialized_context = json.dumps(
            context,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return LlmRequest(
            prompt_version=self.VERSION,
            system_instructions=self.SYSTEM_INSTRUCTIONS,
            user_prompt=(
                f"{self.TASK_DEFINITION}\n\n"
                "STRUCTURED ANALYSIS CONTEXT\n"
                f"{serialized_context}\n\n"
                f"{self.EXPECTED_OUTPUT}"
            ),
        )

    def corrective_retry(
        self,
        original: LlmRequest,
        validation_error: Exception,
    ) -> LlmRequest:
        return LlmRequest(
            prompt_version=original.prompt_version,
            system_instructions=original.system_instructions,
            user_prompt=original.user_prompt,
            corrective_feedback=str(validation_error),
        )
