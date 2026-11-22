from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class EvaluationProviderRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="forbid")

    task_id: UUID = Field(alias="taskId")
    model: str = Field(min_length=1, max_length=255)
    instructions: str = Field(min_length=1, max_length=100_000)
    input: Any
    max_output_tokens: int = Field(alias="maxOutputTokens", ge=256, le=32_768)
    text: dict[str, Any]
    tools: list[dict[str, Any]] | None = None
