import asyncio
from datetime import datetime, timezone
import logging

from openai import APIStatusError, APITimeoutError, AsyncOpenAI

from app.providers.base import Prompt, ProviderGenerationResult, StructuredOutput, effective_output_tokens

logger = logging.getLogger(__name__)

_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
_RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


class OpenRouterLlmProvider:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout_seconds: float,
        max_output_tokens: int,
        max_retries: int = 0,
        client: AsyncOpenAI | None = None,
    ) -> None:
        self._model = model
        self._max_output_tokens = max_output_tokens
        self._max_retries = max_retries
        self._client = client or AsyncOpenAI(
            api_key=api_key,
            base_url=_OPENROUTER_BASE_URL,
            timeout=timeout_seconds,
            max_retries=0,
        )

    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        return (await self._generate_with_retries(request, response_model)).output

    async def generate_structured_with_trace(
        self, request: Prompt, response_model: type[StructuredOutput]
    ) -> ProviderGenerationResult:
        return await self._generate_with_retries(request, response_model)

    async def _generate_with_retries(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> ProviderGenerationResult:
        last_exception: Exception | None = None
        for attempt in range(1 + self._max_retries):
            try:
                return await self._call_api(request, response_model)
            except (APITimeoutError, APIStatusError) as exc:
                last_exception = exc
                if attempt >= self._max_retries or not self._is_retryable(exc):
                    raise
                delay = min(2**attempt, 10)
                logger.warning(
                    "OpenRouter call attempt %d/%d failed (%s), retrying in %ds",
                    attempt + 1,
                    1 + self._max_retries,
                    exc,
                    delay,
                )
                await asyncio.sleep(delay)
        raise last_exception  # type: ignore[misc]

    async def _call_api(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> ProviderGenerationResult:
        started_at = datetime.now(timezone.utc)
        effective_tokens = effective_output_tokens(request, self._max_output_tokens)
        logger.info(
            "Sending OpenRouter provider request",
            extra={
                "event": "provider_request_output_allowance",
                "taskId": request.traceability.ai_task_id,
                "intent": request.intent_id,
                "provider": "openrouter",
                "model": request.model_identifier or self._model,
                "effectiveOutputTokens": effective_tokens,
            },
        )
        response = await self._client.chat.completions.create(
            model=self._model,
            messages=[
                {"role": "system", "content": request.system_message},
                {"role": "user", "content": request.user_message},
            ],
            response_format={
                "type": "json_schema",
                "json_schema": {
                    "name": "structured_output",
                    "strict": True,
                    "schema": response_model.model_json_schema(),
                },
            },
            max_tokens=effective_tokens,
        )
        message = response.choices[0].message
        raw_output = message.content
        if not raw_output:
            raise ValueError("OpenRouter returned no structured output")
        parsed = response_model.model_validate_json(raw_output)
        usage = getattr(response, "usage", None)
        completed_at = datetime.now(timezone.utc)
        return ProviderGenerationResult(
            output=parsed,
            raw_output=raw_output,
            started_at=started_at,
            completed_at=completed_at,
            input_tokens=getattr(usage, "prompt_tokens", None),
            output_tokens=getattr(usage, "completion_tokens", None),
            total_tokens=getattr(usage, "total_tokens", None),
        )

    @staticmethod
    def _is_retryable(exc: Exception) -> bool:
        if isinstance(exc, APITimeoutError):
            return True
        return isinstance(exc, APIStatusError) and exc.status_code in _RETRYABLE_STATUS_CODES

    @property
    def provider_name(self) -> str:
        return "openrouter"

    @property
    def model_identifier(self) -> str:
        return self._model
