from openai import AsyncOpenAI
from pydantic import BaseModel

from app.providers.base import Prompt, StructuredOutput


class OpenAiLlmProvider:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout_seconds: float,
        max_output_tokens: int,
        client: AsyncOpenAI | None = None,
    ) -> None:
        self._model = model
        self._max_output_tokens = max_output_tokens
        self._client = client or AsyncOpenAI(
            api_key=api_key,
            timeout=timeout_seconds,
            max_retries=0,
        )

    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        response = await self._client.responses.parse(
            model=self._model,
            input=[
                {"role": "system", "content": request.system_message},
                {"role": "user", "content": request.user_message},
            ],
            text_format=response_model,
            max_output_tokens=self._max_output_tokens,
        )
        parsed = response.output_parsed
        if parsed is None:
            raise ValueError("OpenAI returned no parsed structured output")
        if isinstance(parsed, BaseModel):
            parsed = parsed.model_dump()
        return response_model.model_validate(parsed)

    @property
    def provider_name(self) -> str:
        return "openai"

    @property
    def model_identifier(self) -> str:
        return self._model
