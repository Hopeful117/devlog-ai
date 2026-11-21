"""Provider-free Python half of the Q1 C Python/Core integration harness."""

from __future__ import annotations

import argparse
import asyncio
import json
from pathlib import Path
from typing import Any
from uuid import UUID

from app.clients.core_callback_client import CoreCallbackClient
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.base import LlmProvider, Prompt, ProviderGenerationResult
from app.schemas.ai_task import PromptRequest
from app.schemas.story_context_analysis import ProviderStoryContextAnalysisResult
from app.schemas.story_context_investigation import (
    InvestigationDecisionResponse,
)
from app.services.story_context_analysis_generation_service import StoryContextAnalysisGenerationService


Q1_C_STATUS = "Q1_C_FULL_INVESTIGATION_INTEGRATION_PASS"


class Q1CBindingError(RuntimeError):
    """Raised when the local Python/Core integration harness fails."""


class ScriptedInvestigationProvider(LlmProvider):
    """Deterministic PLAN -> ACTION -> FINAL provider for one Core task."""

    provider_name = "mock"
    model_identifier = "q1-c-scripted-v1"

    def __init__(self, submission: PromptRequest) -> None:
        self._submission = submission
        self._turn = 0
        self.decisions: list[dict[str, Any]] = []

    async def generate_structured(self, request: Prompt, response_model: type[Any]) -> Any:
        self._turn += 1
        if self._turn == 1:
            output = self._plan()
        elif self._turn == 2:
            output = self._read_action()
        elif self._turn == 3:
            output = self._final(request)
        else:
            raise Q1CBindingError("scripted provider received more than PLAN/ACTION/FINAL")
        self.decisions.append(output)
        return response_model.model_validate(output)

    async def generate_structured_with_trace(
        self, request: Prompt, response_model: type[Any]
    ) -> ProviderGenerationResult:
        from datetime import datetime, timezone

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

    def _investigation(self) -> dict[str, Any]:
        value = self._submission.metadata.get("investigation")
        if not isinstance(value, dict):
            raise Q1CBindingError("Core submission did not contain investigation state")
        return value

    def _scope(self) -> dict[str, Any]:
        value = self._investigation().get("scope")
        if not isinstance(value, dict):
            raise Q1CBindingError("Core submission did not contain investigation scope")
        return {
            key: value[key]
            for key in ("projectSlug", "revision", "allowedPathPrefixes")
            if key in value
        }

    def _budget(self) -> dict[str, Any]:
        value = self._investigation().get("budget")
        if not isinstance(value, dict):
            raise Q1CBindingError("Core submission did not contain investigation budget")
        return value

    def _base_action(self, action_id: str, purpose: str) -> dict[str, Any]:
        scope = self._scope()
        budget = self._budget()
        return {
            "actionId": action_id,
            "projectSlug": scope["projectSlug"],
            "revision": scope["revision"],
            "purpose": purpose,
            "resultLimit": min(2, budget["maximumEvidenceItems"]),
            "maximumBytes": min(10_000, budget["maximumBytes"]),
            "maximumTokens": min(2_000, budget["maximumTokens"]),
        }

    def _plan(self) -> dict[str, Any]:
        investigation = self._investigation()
        scope = self._scope()
        budget = self._budget()
        prefix = scope["allowedPathPrefixes"][0]
        search = {
            **self._base_action("q1-search", "Locate the Core Story Context evidence."),
            "kind": "SEARCH_EVIDENCE",
            "query": "Core",
            "pathPrefix": prefix,
        }
        read = {
            **self._base_action("q1-read", "Read the authorized Story Context evidence range."),
            "kind": "READ_EVIDENCE_RANGE",
            "path": "docs/q1.md",
            "startLine": 1,
            "endLine": 2,
        }
        return {
            "phase": "PLAN",
            "plan": {
                "protocolVersion": "story-context-agent-protocol/v2",
                "planId": "q1-c-plan",
                "executionId": investigation["executionId"],
                "parentTaskId": str(self._submission.ai_task_id),
                "scope": scope,
                "budget": budget,
                "actions": [search, read],
            },
        }

    def _read_action(self) -> dict[str, Any]:
        scope = self._scope()
        return {
            "phase": "ACTION",
            "action": {
                **self._base_action("q1-read", "Read the authorized Story Context evidence range."),
                "kind": "READ_EVIDENCE_RANGE",
                "resultLimit": 1,
                "path": "docs/q1.md",
                "startLine": 1,
                "endLine": 2,
            },
        }

    def _prior_results(self, request: Prompt) -> list[dict[str, Any]]:
        marker = "PRIOR CAPABILITY RESULTS\n"
        end_marker = "\nDECISION RULES"
        if marker not in request.user_message or end_marker not in request.user_message:
            raise Q1CBindingError("investigation prompt did not expose prior Core results")
        raw = request.user_message.split(marker, 1)[1].split(end_marker, 1)[0]
        value = json.loads(raw)
        if not isinstance(value, list):
            raise Q1CBindingError("prior Core results are not a list")
        return value

    def _final(self, request: Prompt) -> dict[str, Any]:
        results = self._prior_results(request)
        if len(results) != 2 or any(item.get("outcome") not in {"RESULTS", "EMPTY"} for item in results):
            raise Q1CBindingError("FINAL was requested before both Core actions completed")
        traceability = request.traceability
        grounding_reference = self._submission.grounding_contract["allowedGroundingReferences"][0]["ref"]
        return {
            "phase": "FINAL",
            "stopReason": "SUFFICIENT_EVIDENCE",
            "result": {
                "objectiveUnderstanding": {
                    "summary": "Core authorization, bounded investigation, and callback persistence were traversed.",
                    "keyDomains": ["authorization", "investigation", "persistence"],
                    "primaryTechnologies": ["Java", "Python"],
                    "architecturalPatterns": ["Core authority", "callback boundary"],
                    "grounding": {
                        "evidenceReferences": [{"reference": grounding_reference}],
                        "classification": "FACTUAL_EXTRACTION",
                        "grounded": True,
                    },
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
                "confidence": "MEDIUM",
                "provenance": {
                    "contextDigest": traceability.context_digest,
                    "promptVersion": request.intent_version,
                    "provider": self.provider_name,
                    "modelIdentifier": self.model_identifier,
                    "promptContentDigest": "0" * 64,
                    "intentId": request.intent_id,
                    "intentVersion": request.intent_version,
                    "guidanceKeys": [],
                    "executionMetadata": {},
                },
                 "outputClassification": {"entries": [{
                     "findingReference": "objectiveUnderstanding",
                     "classification": "FACTUAL_EXTRACTION",
                     "grounded": True,
                     "rationale": "Grounded in the authorized Core evidence.",
                 }]},
                "causalClaims": [],
                "causalAssessment": None,
            },
        }


class RecordingCoreCallbackClient:
    def __init__(self, delegate: CoreCallbackClient) -> None:
        self._delegate = delegate
        self.events: list[dict[str, Any]] = []

    async def execute_investigation_action(self, ai_task_id: UUID, correlation_id: UUID, action: dict[str, Any]) -> dict[str, Any]:
        self.events.append({"event": "PYTHON_ACTION_REQUESTED", "action": action})
        try:
            result = await self._delegate.execute_investigation_action(ai_task_id, correlation_id, action)
        except Exception as error:
            self.events.append({"event": "PYTHON_ACTION_FAILED", "error": str(error)})
            raise
        self.events.append({"event": "PYTHON_EVIDENCE_RECEIVED", "result": result})
        return result

    async def send_result(self, ai_task_id: UUID, correlation_id: UUID, result: Any) -> Any:
        self.events.append({"event": "PYTHON_FINAL", "result": result.model_dump(mode="json", by_alias=True, exclude_none=True)})
        acknowledgement = await self._delegate.send_result(ai_task_id, correlation_id, result)
        self.events.append({"event": "CORE_CALLBACK_ACKNOWLEDGED", "acknowledgement": acknowledgement.model_dump(mode="json", by_alias=True)})
        return acknowledgement


async def run_binding(
    prompt_path: Path,
    core_url: str,
    secret: str,
    external_job_id: UUID,
    artifact_path: Path,
) -> dict[str, Any]:
    submission = PromptRequest.model_validate(json.loads(prompt_path.read_text(encoding="utf-8")))
    provider = ScriptedInvestigationProvider(submission)
    callback = RecordingCoreCallbackClient(CoreCallbackClient(core_url, callback_secret=secret, max_attempts=1))
    service = StoryContextAnalysisGenerationService(provider, StoryContextAnalysisPromptBuilder(), callback)
    await service.process(submission, external_job_id)
    if len(provider.decisions) != 3 or [item["event"] for item in callback.events] != [
        "PYTHON_ACTION_REQUESTED", "PYTHON_EVIDENCE_RECEIVED",
        "PYTHON_ACTION_REQUESTED", "PYTHON_EVIDENCE_RECEIVED",
        "PYTHON_FINAL", "CORE_CALLBACK_ACKNOWLEDGED",
    ]:
        raise Q1CBindingError(
            "Python/Core investigation did not complete PLAN/ACTION/FINAL: "
            f"providerCalls={len(provider.decisions)}, events="
            f"{json.dumps(callback.events, sort_keys=True)}"
        )
    artifact = {
        "status": Q1_C_STATUS,
        "observationId": "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1",
        "taskId": str(submission.ai_task_id),
        "correlationId": str(submission.correlation_id),
        "runtimePython": "StoryContextAnalysisGenerationService._process_investigation",
        "providerIdentity": f"{provider.provider_name}/{provider.model_identifier}",
        "decisions": provider.decisions,
        "events": callback.events,
        "providerCalls": len(provider.decisions),
        "externalProviderCalls": 0,
        "externalNetworkCalls": 0,
        "localCoreTransport": "HTTP_LOOPBACK_HMAC",
    }
    artifact_path.parent.mkdir(parents=True, exist_ok=True)
    artifact_path.write_text(json.dumps(artifact, sort_keys=True, indent=2), encoding="utf-8")
    return artifact


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the provider-free Q1 C Python/Core binding")
    parser.add_argument("--prompt-file", type=Path, required=True)
    parser.add_argument("--core-url", required=True)
    parser.add_argument("--secret", required=True)
    parser.add_argument("--external-job-id", type=UUID, required=True)
    parser.add_argument("--artifact", type=Path, required=True)
    args = parser.parse_args()
    asyncio.run(run_binding(args.prompt_file, args.core_url, args.secret, args.external_job_id, args.artifact))


if __name__ == "__main__":
    main()
