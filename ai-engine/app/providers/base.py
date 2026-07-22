from dataclasses import dataclass
from typing import Any, Protocol, TypeVar

from pydantic import BaseModel

StructuredOutput = TypeVar("StructuredOutput", bound=BaseModel)


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
