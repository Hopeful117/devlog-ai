import asyncio
from datetime import datetime, timezone
import json
import logging

from google import genai
from google.genai import types
from pydantic import BaseModel

from app.providers.base import Prompt, ProviderGenerationResult, StructuredOutput

logger = logging.getLogger(__name__)


class GeminiLlmProvider:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout_seconds: float,
        max_output_tokens: int,
        max_retries: int = 0,
        client: object | None = None,
    ) -> None:
        self._model = model
        self._max_output_tokens = max_output_tokens
        self._max_retries = max_retries
        self._client = client or genai.Client(
            api_key=api_key,
            http_options=types.HttpOptions(timeout=int(timeout_seconds * 1000)),
        ).aio

    async def generate_structured(
        self,
        request: Prompt,
        response_model: type[StructuredOutput],
    ) -> StructuredOutput:
        last_exception: Exception | None = None
        for attempt in range(1 + self._max_retries):
            try:
                return (await self._call_api(request, response_model)).output
            except Exception as exc:
                last_exception = exc
                if attempt >= self._max_retries or not self._is_retryable(exc):
                    raise
                delay = min(2**attempt, 10)
                logger.warning(
                    "Gemini call attempt %d/%d failed (%s), retrying in %ds",
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
        response = await self._client.models.generate_content(
            model=self._model,
            contents=request.user_message,
            config=types.GenerateContentConfig(
                system_instruction=request.system_message,
                response_mime_type="application/json",
                response_json_schema=response_model.model_json_schema(),
                max_output_tokens=self._max_output_tokens,
            ),
        )
        raw_output = getattr(response, "text", None)
        if not raw_output:
            raise ValueError("Gemini returned no structured output")
        parsed = response_model.model_validate_json(raw_output)
        usage = getattr(response, "usage_metadata", None)
        completed_at = datetime.now(timezone.utc)
        return ProviderGenerationResult(
            output=parsed,
            raw_output=raw_output or json.dumps(parsed.model_dump(mode="json")),
            started_at=started_at,
            completed_at=completed_at,
            input_tokens=getattr(usage, "prompt_token_count", None),
            output_tokens=getattr(usage, "candidates_token_count", None),
            total_tokens=getattr(usage, "total_token_count", None),
        )

    @staticmethod
    def _is_retryable(exc: Exception) -> bool:
        status_code = getattr(exc, "code", None)
        return status_code in {429, 500, 502, 503, 504} or isinstance(exc, TimeoutError)

    @property
    def provider_name(self) -> str:
        return "gemini"

    @property
    def model_identifier(self) -> str:
        return self._model
