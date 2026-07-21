from dataclasses import dataclass
from typing import Protocol, TypeVar

from pydantic import BaseModel

StructuredOutput = TypeVar("StructuredOutput", bound=BaseModel)


@dataclass(frozen=True)
class LlmRequest:
    prompt_version: str
    system_instructions: str
    user_prompt: str
    corrective_feedback: str | None = None


class LlmProvider(Protocol):
    async def generate_structured(
        self,
        request: LlmRequest,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        ...
