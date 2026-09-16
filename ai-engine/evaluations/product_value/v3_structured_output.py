"""Evaluation-only structured-output boundary helpers for V3.

The provider wire contract is intentionally separate from the richer Python
internal result model. This module does not change production generation.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Literal

from pydantic import BaseModel

from app.schemas.story_context_analysis import StoryContextAnalysisResult

from .v3_protocol import capture_raw_response


ProviderConfidence = Literal["HIGH", "MEDIUM", "LOW"]


class ProviderStoryContextAnalysisResult(StoryContextAnalysisResult):
    """The canonical provider/Core wire shape for confidence."""

    confidence: ProviderConfidence


def provider_schema() -> dict[str, Any]:
    return ProviderStoryContextAnalysisResult.model_json_schema()


def provider_result_to_internal(
    result: ProviderStoryContextAnalysisResult,
) -> StoryContextAnalysisResult:
    """Map canonical confidence to the internal model without inventing rationale."""

    payload = result.model_dump(
        mode="json", by_alias=True, exclude={"confidence"},
    )
    payload["confidence"] = {"level": result.confidence, "rationale": ""}
    return StoryContextAnalysisResult.model_validate(payload)


@dataclass(frozen=True)
class CapturedProviderResponse:
    """Complete SDK response capture taken before structured parsing."""

    raw_provider_response: dict[str, Any]
    provider_text_output: str | None
    provider_metadata: dict[str, Any]
    raw_response_capture: dict[str, Any]


class ProviderParseFailure(ValueError):
    def __init__(self, capture: CapturedProviderResponse, cause: Exception) -> None:
        super().__init__(str(cause))
        self.capture = capture
        self.cause = cause


def capture_sdk_response(response: Any) -> CapturedProviderResponse:
    """Capture the SDK Response model before invoking any parser/validator."""

    if isinstance(response, BaseModel):
        payload = response.model_dump(mode="json", by_alias=True)
    else:
        model_dump = getattr(response, "model_dump", None)
        if not callable(model_dump):
            raise TypeError("provider response must expose model_dump")
        payload = model_dump(mode="json", by_alias=True)
    if not isinstance(payload, dict):
        raise TypeError("provider response payload must be an object")

    text_output = getattr(response, "output_text", None)
    if text_output is not None and not isinstance(text_output, str):
        raise TypeError("provider output_text must be a string or None")

    usage = getattr(response, "usage", None)
    metadata = {
        "id": payload.get("id"),
        "model": payload.get("model"),
        "status": payload.get("status"),
        "incompleteDetails": payload.get("incomplete_details"),
        "usage": usage.model_dump(mode="json", by_alias=True)
        if isinstance(usage, BaseModel)
        else usage,
    }
    return CapturedProviderResponse(
        raw_provider_response=payload,
        provider_text_output=text_output,
        provider_metadata=metadata,
        raw_response_capture=capture_raw_response(payload),
    )


def capture_then_parse(
    response: Any, parser: Callable[[Any], Any],
) -> tuple[CapturedProviderResponse, Any]:
    """Preserve the complete SDK response even when parsing raises."""

    capture = capture_sdk_response(response)
    try:
        return capture, parser(response)
    except Exception as error:
        raise ProviderParseFailure(capture, error) from error


def capture_then_parse_openai_response(
    response: Any, response_model: type[BaseModel],
) -> tuple[CapturedProviderResponse, Any]:
    """Capture an SDK ``Response`` before applying the SDK structured parser.

    The caller obtains the response with ``responses.create``; the SDK's
    ``responses.parse`` helper installs its parser internally and discards the
    raw response when that parser raises.
    """

    from openai.lib._parsing._responses import parse_response

    return capture_then_parse(
        response,
        lambda raw: parse_response(input_tools=[], text_format=response_model, response=raw),
    )
