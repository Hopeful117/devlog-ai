"""Provider-free Python half of the Q1 A Core/Python callback harness."""

from __future__ import annotations

import argparse
import asyncio
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import UUID

from app.clients.core_callback_client import CoreCallbackClient
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.base import LlmProvider, Prompt, ProviderGenerationResult
from app.schemas.ai_task import PromptRequest
from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult
from app.services.story_context_analysis_generation_service import StoryContextAnalysisGenerationService

from .q1_c_binding import RecordingCoreCallbackClient


Q1_A_STATUS = "Q1_A_FULL_CALLBACK_INTEGRATION_PASS"


class Q1ABindingError(RuntimeError):
    """Raised when the controlled A callback path fails."""


class ScriptedStoryContextProvider(LlmProvider):
    """Return one valid native Story Context result without external transport."""

    provider_name = "mock"
    model_identifier = "q1-a-scripted-v1"

    def __init__(self) -> None:
        self.calls = 0

    async def generate_structured(self, request: Prompt, response_model: type[Any]) -> Any:
        self.calls += 1
        if self.calls > 1:
            raise Q1ABindingError("one-shot provider received more than one generation")
        result = {
            "objectiveUnderstanding": {
                "summary": "Core authorized and persisted the Story Context analysis.",
                "keyDomains": ["authorization", "persistence"],
                "primaryTechnologies": ["Java", "Python"],
                "architecturalPatterns": ["Core authority", "callback boundary"],
            },
            "architectureFindings": [],
            "decisionFindings": [],
            "evidenceFindings": [],
            "historicalContext": [],
            "constraintFindings": [],
            "impactedComponentFindings": [],
            "uncertainties": [],
            "missingInformation": [],
            "implementationQuestions": [],
            "implementationPreparation": {"affectedFiles": [], "constraints": [], "testPlan": []},
            "confidence": "LOW",
            "provenance": {
                "contextDigest": request.traceability.context_digest,
                "promptVersion": request.prompt_version,
                "provider": self.provider_name,
                "modelIdentifier": self.model_identifier,
                "promptContentDigest": request.content_digest,
                "intentId": request.intent_id,
                "intentVersion": request.intent_version,
                "guidanceKeys": [],
                "executionMetadata": {},
            },
            "outputClassification": {"entries": []},
            "causalClaims": [],
            "causalAssessment": None,
        }
        return response_model.model_validate(result)

    async def generate_structured_with_trace(
        self, request: Prompt, response_model: type[Any]
    ) -> ProviderGenerationResult:
        started = datetime.now(timezone.utc)
        output = await self.generate_structured(request, response_model)
        return ProviderGenerationResult(
            output=output,
            raw_output=json.dumps(output.model_dump(mode="json", by_alias=True), sort_keys=True),
            started_at=started,
            completed_at=datetime.now(timezone.utc),
            input_tokens=100,
            output_tokens=100,
            total_tokens=200,
            response_status="COMPLETED",
        )


async def run_binding(
    prompt_path: Path,
    core_url: str,
    secret: str,
    external_job_id: UUID,
    artifact_path: Path,
) -> dict[str, Any]:
    submission = PromptRequest.model_validate(json.loads(prompt_path.read_text(encoding="utf-8")))
    provider = ScriptedStoryContextProvider()
    callback = RecordingCoreCallbackClient(
        CoreCallbackClient(core_url, callback_secret=secret, max_attempts=1)
    )
    service = StoryContextAnalysisGenerationService(
        provider, StoryContextAnalysisPromptBuilder(), callback
    )
    await service.process(submission, external_job_id)
    expected_events = ["PYTHON_FINAL", "CORE_CALLBACK_ACKNOWLEDGED"]
    if provider.calls != 1 or [item["event"] for item in callback.events] != expected_events:
        raise Q1ABindingError(
            "Python/Core one-shot callback did not complete: "
            f"providerCalls={provider.calls}, events={json.dumps(callback.events, sort_keys=True)}"
        )
    artifact = {
        "status": Q1_A_STATUS,
        "observationId": "Q1-A-DEVLOG-CURRENT-r1",
        "taskId": str(submission.ai_task_id),
        "correlationId": str(submission.correlation_id),
        "runtimePython": "StoryContextAnalysisGenerationService.process",
        "providerIdentity": f"{provider.provider_name}/{provider.model_identifier}",
        "events": callback.events,
        "providerCalls": provider.calls,
        "externalProviderCalls": 0,
        "externalNetworkCalls": 0,
        "localCoreTransport": "HTTP_LOOPBACK_HMAC",
    }
    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    artifact_path.write_text(json.dumps(artifact, sort_keys=True, indent=2), encoding="utf-8")
    return artifact


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the provider-free Q1 A Python/Core binding")
    parser.add_argument("--prompt-file", type=Path, required=True)
    parser.add_argument("--core-url", required=True)
    parser.add_argument("--secret", required=True)
    parser.add_argument("--external-job-id", type=UUID, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    args = parser.parse_args()
    asyncio.run(run_binding(args.prompt_file, args.core_url, args.secret, args.external_job_id, args.artifact))


if __name__ == "__main__":
    main()
