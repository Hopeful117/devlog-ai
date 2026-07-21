from openai import AsyncOpenAI
from pydantic import BaseModel

from app.providers.base import LlmRequest, StructuredOutput


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
        request: LlmRequest,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        user_content = request.user_prompt
        if request.corrective_feedback:
            user_content += (
                "\n\nCORRECTIVE RETRY\n"
                "The previous response was invalid. Correct these errors and "
                "return the complete output again:\n"
                f"{request.corrective_feedback}"
            )

        response = await self._client.responses.parse(
            model=self._model,
            input=[
                {"role": "system", "content": request.system_instructions},
                {"role": "user", "content": user_content},
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
