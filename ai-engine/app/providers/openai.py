import asyncio
from datetime import datetime, timezone
import json
import logging

from openai import AsyncOpenAI, APITimeoutError, APIStatusError
from pydantic import BaseModel

from app.providers.base import Prompt, ProviderGenerationResult, StructuredOutput

logger = logging.getLogger(__name__)

_TIMEOUT_RETRYABLE_STATUS_CODES = {429, 500, 502, 503, 504}


class OpenAiLlmProvider:
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
            timeout=timeout_seconds,
            max_retries=0,
        )

    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        last_exception: Exception | None = None
        for attempt in range(1 + self._max_retries):
            try:
                return (await self._call_api(request, response_model)).output
            except (APITimeoutError, APIStatusError) as exc:
                last_exception = exc
                if not self._is_retryable(exc) or attempt >= self._max_retries:
                    raise
                delay = min(2 ** attempt, 10)
                logger.warning(
                    "LLM call attempt %d/%d failed (%s), retrying in %ds",
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
        usage = getattr(response, "usage", None)
        completed_at = datetime.now(timezone.utc)
        return ProviderGenerationResult(
            output=response_model.model_validate(parsed),
            raw_output=getattr(response, "output_text", None)
            or json.dumps(parsed, sort_keys=True),
            started_at=started_at,
            completed_at=completed_at,
            input_tokens=getattr(usage, "input_tokens", None),
            output_tokens=getattr(usage, "output_tokens", None),
            total_tokens=getattr(usage, "total_tokens", None),
        )

    async def generate_structured_with_trace(
        self, request: Prompt, response_model: type[StructuredOutput]
    ) -> ProviderGenerationResult:
        last_exception: Exception | None = None
        for attempt in range(1 + self._max_retries):
            try:
                return await self._call_api(request, response_model)
            except (APITimeoutError, APIStatusError) as exc:
                last_exception = exc
                if not self._is_retryable(exc) or attempt >= self._max_retries:
                    raise
                delay = min(2 ** attempt, 10)
                logger.warning(
                    "LLM call attempt %d/%d failed (%s), retrying in %ds",
                    attempt + 1, 1 + self._max_retries, exc, delay
                )
                await asyncio.sleep(delay)
        raise last_exception  # type: ignore[misc]

    @staticmethod
    def _is_retryable(exc: Exception) -> bool:
        if isinstance(exc, APITimeoutError):
            return True
        if isinstance(exc, APIStatusError):
            return exc.status_code in _TIMEOUT_RETRYABLE_STATUS_CODES
        return False

    @property
    def provider_name(self) -> str:
        return "openai"

    @property
    def model_identifier(self) -> str:
        return self._model
