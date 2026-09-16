from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import os
import re
from typing import Any, Awaitable, Callable
from uuid import UUID, uuid4

from pydantic import BaseModel
from pydantic import ValidationError

from app.providers.base import LlmProvider, Prompt, ProviderGenerationResult
from app.schemas.interaction_trace import AiInteractionTrace


_SECRET_PATTERNS = (
    re.compile(r"(?i)(authorization\s*:\s*bearer\s+)[^\s]+"),
    re.compile(r"(?i)((?:api[_-]?key|password|secret|token)\s*[:=]\s*)[^\s,;]+"),
    re.compile(r"\bsk-[A-Za-z0-9_-]+\b"),
)


def _failure_category(error: Exception) -> str:
    explicit = getattr(error, "failure_category", None)
    if explicit:
        return explicit
    message = str(error).casefold()
    if isinstance(error, ValidationError):
        if any(term in message for term in ("defensible evidence", "relationship support", "causalclassification", "evidencebasis")):
            return "SEMANTIC_SUPPORT_ERROR"
        return "STRUCTURAL_CONTRACT_ERROR"
    if "json" in message or "schema" in message:
        return "FORMAT_ERROR"
    return "PROVIDER_ERROR"


def redact_sensitive_text(value: str | None) -> str | None:
    if value is None:
        return None
    redacted = value
    for pattern in _SECRET_PATTERNS:
        redacted = pattern.sub(r"\1[REDACTED]" if pattern.groups else "[REDACTED]", redacted)
    return redacted


def _redact(value: Any) -> Any:
    if isinstance(value, str):
        return redact_sensitive_text(value)
    if isinstance(value, dict):
        return {str(key): _redact(nested) for key, nested in value.items()}
    if isinstance(value, list):
        return [_redact(nested) for nested in value]
    return value


def _json_value(value: Any) -> Any:
    if isinstance(value, BaseModel):
        return _redact(value.model_dump(mode="json", by_alias=True))
    return _redact(value)


def _grounding_fingerprint(selected_knowledge: dict[str, object]) -> str:
    grounding = {
        "facts": sorted(
            str(item.get("id"))
            for item in selected_knowledge.get("selectedFacts", [])
            if isinstance(item, dict) and item.get("id") is not None
        ),
        "observations": sorted(
            str(item.get("id"))
            for item in selected_knowledge.get("selectedObservations", [])
            if isinstance(item, dict) and item.get("id") is not None
        ),
    }
    return hashlib.sha256(
        json.dumps(grounding, sort_keys=True, separators=(",", ":")).encode()
    ).hexdigest()


@dataclass
class InteractionTraceCollector:
    provider: LlmProvider
    submission: Any

    def __post_init__(self) -> None:
        self.traces: list[AiInteractionTrace] = []
        self._retry_reason: str | None = None

    @property
    def diagnostic(self) -> bool:
        return os.getenv("AI_TRACE_LEVEL", "NORMAL").upper() == "DIAGNOSTIC"

    def retry(self, reason: Exception) -> None:
        self._retry_reason = str(reason)[:5000]

    async def generate_and_validate(
        self,
        prompt: Prompt,
        response_model: type[BaseModel],
        validator: Callable[[BaseModel], None],
    ) -> BaseModel:
        started_at = datetime.now(timezone.utc)
        attempt = len(self.traces) + 1
        interaction_type = "INITIAL_GENERATION" if attempt == 1 else "CORRECTIVE_RETRY"
        generation: ProviderGenerationResult | None = None
        parsed: BaseModel | None = None
        status = "SUCCEEDED"
        failure_category: str | None = None
        failure_message: str | None = None
        try:
            generation = await self._generate(prompt, response_model)
            try:
                parsed = response_model.model_validate(generation.output)
            except Exception:
                status = "PARSING_FAILED"
                failure_category = "FORMAT_ERROR"
                raise
            try:
                validator(parsed)
            except Exception as validation_error:
                status = "VALIDATION_FAILED"
                failure_category = _failure_category(validation_error)
                raise
            return parsed
        except Exception as error:
            failure_message = str(error)[:5000]
            if failure_category is None:
                if isinstance(error, ValidationError):
                    status = "PARSING_FAILED"
                    failure_category = _failure_category(error)
                else:
                    status = "PROVIDER_FAILED"
                    failure_category = _failure_category(error)
            raise
        finally:
            completed_at = datetime.now(timezone.utc)
            self.traces.append(
                self._trace(
                    prompt,
                    attempt,
                    interaction_type,
                    started_at,
                    completed_at,
                    status,
                    failure_category,
                    failure_message,
                    generation,
                    parsed,
                )
            )
            self._retry_reason = None

    async def _generate(
        self, prompt: Prompt, response_model: type[BaseModel]
    ) -> ProviderGenerationResult:
        traced = getattr(self.provider, "generate_structured_with_trace", None)
        if traced is not None:
            return await traced(prompt, response_model)
        started_at = datetime.now(timezone.utc)
        output = await self.provider.generate_structured(prompt, response_model)
        return ProviderGenerationResult(
            output=output,
            raw_output=None,
            started_at=started_at,
            completed_at=datetime.now(timezone.utc),
            input_tokens=None,
            output_tokens=None,
            total_tokens=None,
        )

    def _trace(
        self,
        prompt: Prompt,
        attempt: int,
        interaction_type: str,
        started_at: datetime,
        completed_at: datetime,
        status: str,
        failure_category: str | None,
        failure_message: str | None,
        generation: ProviderGenerationResult | None,
        parsed: BaseModel | None,
    ) -> AiInteractionTrace:
        knowledge = self.submission.selected_knowledge
        diagnostic = self.diagnostic
        return AiInteractionTrace(
            id=uuid4(),
            attempt=attempt,
            interaction_type=interaction_type,
            trace_level="DIAGNOSTIC" if diagnostic else "NORMAL",
            provider=self.provider.provider_name,
            model_identifier=self.provider.model_identifier,
            intent=self.submission.intent.id,
            intent_version=self.submission.intent.version,
            prompt_version=prompt.prompt_version,
            started_at=started_at,
            completed_at=completed_at,
            duration_ms=max(0, int((completed_at - started_at).total_seconds() * 1000)),
            validation_status=status,
            failure_category=failure_category,
            retry_reason=self._retry_reason,
            selected_knowledge_fingerprint=prompt.traceability.context_digest,
            prompt_fingerprint=prompt.content_digest,
            selected_fact_count=len(knowledge.get("selectedFacts", [])),
            selected_observation_count=len(knowledge.get("selectedObservations", [])),
            selected_insight_count=len(knowledge.get("selectedInsights", [])),
            selected_engineering_event_count=len(knowledge.get("selectedEngineeringEvents", [])),
            grounding_fingerprint=_grounding_fingerprint(knowledge),
            input_tokens=generation.input_tokens if generation else None,
            output_tokens=generation.output_tokens if generation else None,
            total_tokens=generation.total_tokens if generation else None,
            trace_id=None,
            span_id=None,
            system_prompt=redact_sensitive_text(prompt.system_message) if diagnostic else None,
            user_prompt=redact_sensitive_text(prompt.user_message) if diagnostic else None,
            raw_model_response=redact_sensitive_text(generation.raw_output)
            if diagnostic and generation else None,
            parsed_model_response=_json_value(parsed) if diagnostic and parsed else None,
            validation_diagnostics=redact_sensitive_text(failure_message) if diagnostic else None,
        )
