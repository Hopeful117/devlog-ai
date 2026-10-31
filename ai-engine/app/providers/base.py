from dataclasses import dataclass, field
from datetime import datetime
import logging
from typing import Any, Protocol, TypeVar

from pydantic import BaseModel

StructuredOutput = TypeVar("StructuredOutput", bound=BaseModel)

STORY_CONTEXT_INTENT = "engineering-story-context-analysis"
logger = logging.getLogger(__name__)


class OutputAllowanceError(ValueError):
    """Raised when Core did not authorize a usable output allowance."""


def effective_output_tokens(request: "Prompt", fallback: int) -> int:
    """Use Core's allowance for Story Context instead of a local fallback."""
    value = request.generation_parameters.get("maxOutputTokens")
    task_id = request.traceability.ai_task_id
    if request.intent_id == STORY_CONTEXT_INTENT:
        if isinstance(value, bool) or not isinstance(value, int) or not 256 <= value <= 32_768:
            logger.warning(
                "Story Context output allowance rejected",
                extra={
                    "event": "story_context_output_allowance_rejected",
                    "taskId": task_id,
                    "intent": request.intent_id,
                    "authorizedOutputTokens": value if isinstance(value, int) and not isinstance(value, bool) else None,
                    "outputParameterSource": "CORE_AUTHORIZED",
                },
            )
            raise OutputAllowanceError("Story Context output allowance was not authorized by Core")
        logger.info(
            "Story Context output allowance resolved",
            extra={
                "event": "story_context_output_allowance_resolved",
                "taskId": task_id,
                "intent": request.intent_id,
                "authorizedOutputTokens": value,
                "effectiveOutputTokens": value,
                "outputParameterSource": "CORE_AUTHORIZED",
            },
        )
        return value
    effective = value if isinstance(value, int) else fallback
    logger.info(
        "LLM output allowance resolved",
        extra={
            "event": "llm_output_allowance_resolved",
            "taskId": task_id,
            "intent": request.intent_id,
            "authorizedOutputTokens": value if isinstance(value, int) else None,
            "effectiveOutputTokens": effective,
            "configuredOutputTokens": fallback,
            "outputParameterSource": "REQUEST" if isinstance(value, int) else "ENGINE_CONFIG",
        },
    )
    return effective


@dataclass(frozen=True)
class ProviderGenerationResult:
    output: Any
    raw_output: str | None
    started_at: datetime
    completed_at: datetime
    input_tokens: int | None
    output_tokens: int | None
    total_tokens: int | None
    response_status: str | None = None
    incomplete_reason: str | None = None


@dataclass(frozen=True)
class GenerationPolicy:
    maximum_insight_count: int
    maximum_output_size: int
    structured_output_required: bool = True


@dataclass(frozen=True)
class PromptTraceability:
    request_id: str
    correlation_id: str
    ai_task_id: str
    analysis_id: str
    intent_id: str
    intent_version: str
    context_digest: str
    analysis_context_id: str | None
    profile_id: str | None
    profile_version: str | None
    selection_digest: str | None = None
    projection_digest: str | None = None
    projection_version: str | None = None
    scope: dict[str, Any] | None = None
    freshness: dict[str, Any] | None = None
    grounding_digest: str | None = None
    protocol_version: str | None = None


@dataclass(frozen=True)
class Prompt:
    prompt_id: str
    prompt_version: str
    intent_id: str
    intent_version: str
    system_message: str
    user_message: str
    expected_output_schema: dict[str, Any]
    generation_policy: GenerationPolicy
    traceability: PromptTraceability
    content_digest: str
    generation_parameters: dict[str, Any] = field(default_factory=dict)
    model_identifier: str | None = None


class LlmProvider(Protocol):
    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        ...

    @property
    def provider_name(self) -> str: ...

    @property
    def model_identifier(self) -> str: ...
