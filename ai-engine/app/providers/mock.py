from collections import deque
from collections.abc import Iterable
from typing import Any

from pydantic import BaseModel

from app.providers.base import Prompt, StructuredOutput


class MockLlmProvider:
    """Deterministic provider used by default without external credentials."""

    def __init__(self, outputs: Iterable[Any] | None = None) -> None:
        self._outputs = deque(outputs or [])
        self.requests: list[Prompt] = []

    @property
    def provider_name(self) -> str:
        return "mock"

    @property
    def model_identifier(self) -> str:
        return "deterministic-v1"

    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        self.requests.append(request)
        raw_output: Any
        if self._outputs:
            raw_output = self._outputs.popleft()
        else:
            raw_output = {"proposals": []}

        if isinstance(raw_output, BaseModel):
            raw_output = raw_output.model_dump()
        return response_model.model_validate(raw_output)
