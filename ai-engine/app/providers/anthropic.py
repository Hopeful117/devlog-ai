import asyncio
from datetime import datetime, timezone
import json
import logging

from anthropic import AsyncAnthropic

from app.providers.base import Prompt, ProviderGenerationResult, StructuredOutput

logger = logging.getLogger(__name__)


class AnthropicLlmProvider:
    def __init__(
        self,
        *,
        api_key: str,
        model: str,
        timeout_seconds: float,
        max_output_tokens: int,
        max_retries: int = 0,
        workspace_id: str | None = None,
        client: AsyncAnthropic | None = None,
    ) -> None:
        self._model = model
        self._max_output_tokens = max_output_tokens
        self._max_retries = max_retries
        self._workspace_id = workspace_id
        self._client = client or AsyncAnthropic(
            api_key=api_key,
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
            except Exception as exc:
                last_exception = exc
                if attempt >= self._max_retries or not self._is_retryable(exc):
                    raise
                delay = min(2**attempt, 10)
                logger.warning(
                    "Anthropic call attempt %d/%d failed (%s), retrying in %ds",
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
        tool_name = "structured_output"
        response = await self._client.messages.create(
            model=self._model,
            system=request.system_message,
            messages=[{"role": "user", "content": request.user_message}],
            max_tokens=self._max_output_tokens,
            tools=[
                {
                    "name": tool_name,
                    "description": "Return the requested structured response.",
                    "input_schema": response_model.model_json_schema(),
                }
            ],
            tool_choice={"type": "tool", "name": tool_name},
            **({"workspace_id": self._workspace_id} if self._workspace_id else {}),
        )
        tool_use = next(
            (block for block in response.content if getattr(block, "type", None) == "tool_use"),
            None,
        )
        if tool_use is None:
            raise ValueError("Anthropic returned no structured tool output")
        parsed = response_model.model_validate(tool_use.input)
        usage = getattr(response, "usage", None)
        completed_at = datetime.now(timezone.utc)
        return ProviderGenerationResult(
            output=parsed,
            raw_output=json.dumps(parsed.model_dump(mode="json"), sort_keys=True),
            started_at=started_at,
            completed_at=completed_at,
            input_tokens=getattr(usage, "input_tokens", None),
            output_tokens=getattr(usage, "output_tokens", None),
            total_tokens=(
                (getattr(usage, "input_tokens", 0) or 0)
                + (getattr(usage, "output_tokens", 0) or 0)
            )
            if usage is not None
            else None,
        )

    @staticmethod
    def _is_retryable(exc: Exception) -> bool:
        status_code = getattr(exc, "status_code", None)
        return status_code in {429, 500, 502, 503, 504} or isinstance(exc, TimeoutError)

    @property
    def provider_name(self) -> str:
        return "anthropic"

    @property
    def model_identifier(self) -> str:
        return self._model
