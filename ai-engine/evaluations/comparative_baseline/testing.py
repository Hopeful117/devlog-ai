"""Offline provider transports used only by Story0134 tests."""

from __future__ import annotations

from collections.abc import Callable
from typing import Any

from .collection_runtime import ProviderRequest, ProviderResponse, TransportFailure


class ScriptedProvider:
    def __init__(self, script: list[ProviderResponse | Exception | Callable[[ProviderRequest], ProviderResponse]]):
        self.script = list(script)
        self.requests: list[ProviderRequest] = []

    def complete(self, request: ProviderRequest) -> ProviderResponse:
        self.requests.append(request)
        if request.on_transport_attempt is not None:
            request.on_transport_attempt()
        if not self.script:
            raise TransportFailure("script exhausted")
        item = self.script.pop(0)
        if isinstance(item, Exception):
            raise item
        if callable(item):
            response = item(request)
        else:
            response = item
        if request.on_provider_response is not None:
            request.on_provider_response(response.usage, {"kind": response.kind, "finishReason": response.finish_reason})
        return response


def final_answer(question_id: str, question_version: str, reference: str = "docs/example.md") -> ProviderResponse:
    answer = {
        "questionId": question_id,
        "questionVersion": question_version,
        "answerText": "The captured evidence supports this bounded answer.",
        "relationshipResult": "NOT_APPLICABLE",
        "abstention": False,
        "claims": [{"text": "The inspected evidence supports the answer.", "claimType": "FACT", "references": [reference]}],
        "evidence": [{"reference": reference, "locator": {"kind": "SECTION", "heading": "Evidence"}, "excerpt": "captured direct evidence", "role": "DIRECT"}],
        "confidence": "MEDIUM",
    }
    return ProviderResponse(kind="FINAL", payload=answer, raw_response={"output": answer}, usage={"inputTokens": 10, "outputTokens": 20})
