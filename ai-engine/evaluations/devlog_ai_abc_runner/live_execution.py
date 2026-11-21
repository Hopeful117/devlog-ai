"""Explicitly authorized live execution for the bounded Q1 A/B/C pilot.

This module is intentionally separate from the provider-free runner.  It uses
the real V4 provider transport for B and the real Core HTTP boundary for A/C.
Credentials are read only from the process environment and never enter an
authorization document or an evaluation artifact.
"""

from __future__ import annotations

import asyncio
import json
import os
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping
from uuid import UUID, uuid4

import httpx

from evaluations.comparative_baseline.collection_runtime import ProviderResponse
from evaluations.comparative_baseline.live_adapters import common_answer_schema, serialize_v4_conversation
from evaluations.comparative_baseline_v4.protocol import SafetyCeilings, V4Assignment
from evaluations.comparative_baseline_v4.runtime import V4CollectionRuntime

from .q1_b_binding import Q1PinnedRepositoryTools, load_q1_b_config
from .runner import TreatmentAdapter, run_dry_run
from .provenance import Q1_SOURCE_REVISION, assert_reproducible_worktree, current_head, verify_commit


class LiveConfigurationError(ValueError):
    """Raised when the live runtime cannot be configured without guessing."""


@dataclass(frozen=True)
class LiveConfiguration:
    core_base_url: str
    core_bearer_token: str
    project_slug: str
    story_id: str | None
    files: tuple[str, ...]
    b_task_id: UUID
    callback_timeout_seconds: float
    poll_interval_seconds: float

    @classmethod
    def from_environment(cls, environment: Mapping[str, str] | None = None) -> "LiveConfiguration":
        values = os.environ if environment is None else environment
        base_url = values.get("DEVLOG_CORE_BASE_URL", "").strip().rstrip("/")
        token = values.get("DEVLOG_CORE_BEARER_TOKEN", "").strip()
        project = values.get("DEVLOG_LIVE_PROJECT_SLUG", "").strip()
        files = tuple(item.strip() for item in values.get("DEVLOG_LIVE_FILES", "").split(",") if item.strip())
        b_task_value = values.get("DEVLOG_LIVE_B_TASK_ID", "").strip()
        if not base_url or not token or not project or not files or not b_task_value:
            raise LiveConfigurationError(
                "DEVLOG_CORE_BASE_URL, DEVLOG_CORE_BEARER_TOKEN, "
                "DEVLOG_LIVE_PROJECT_SLUG, DEVLOG_LIVE_FILES, and "
                "DEVLOG_LIVE_B_TASK_ID are required"
            )
        try:
            timeout = float(values.get("DEVLOG_LIVE_TIMEOUT_SECONDS", "120"))
            interval = float(values.get("DEVLOG_LIVE_POLL_INTERVAL_SECONDS", "1"))
        except ValueError as error:
            raise LiveConfigurationError("live timeout values must be numeric") from error
        if timeout <= 0 or interval <= 0 or interval > timeout:
            raise LiveConfigurationError("live timeout values must be positive and ordered")
        try:
            b_task_id = UUID(b_task_value)
        except ValueError as error:
            raise LiveConfigurationError("DEVLOG_LIVE_B_TASK_ID must be a UUID") from error
        story_id = values.get("DEVLOG_LIVE_STORY_ID", "").strip() or None
        return cls(base_url, token, project, story_id, files, b_task_id, timeout, interval)


def _bounded_json(response: httpx.Response, *, limit: int = 256_000) -> dict[str, Any]:
    if len(response.content) > limit:
        raise LiveConfigurationError("Core response exceeds the bounded live capture size")
    try:
        value = response.json()
    except ValueError as error:
        raise LiveConfigurationError("Core returned a non-JSON live response") from error
    if not isinstance(value, dict):
        raise LiveConfigurationError("Core live response must be an object")
    return value


class CoreLiveTreatmentAdapter:
    """Execute one Core-authorized Story Context treatment over HTTP."""

    def __init__(self, condition: str, config: LiveConfiguration, manifest: Mapping[str, Any]):
        self.condition = condition
        self.live_status = "LIVE_CORE_HTTP_BOUNDARY"
        self._config = config
        self._manifest = manifest

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        guidance: dict[str, Any] = {}
        if self.condition == "C_DEVLOG_ENGINEERING_ANALYST":
            guidance = {
                "investigation": {
                    "enabled": True,
                    "allowedPathPrefixes": ["docs", "backend", "ai-engine"],
                    "budget": {
                        "maximumSteps": 2,
                        "maximumRetrievalCalls": 2,
                        "maximumEvidenceItems": 10,
                        "maximumBytes": 20_000,
                        "maximumTokens": 5_000,
                        "maximumElapsedMillis": 30_000,
                        "maximumProviderOutputTokens": 1_000,
                    },
                }
            }
        headers = {
            "Authorization": f"Bearer {self._config.core_bearer_token}",
            "Idempotency-Key": f"q1-live-{observation['observationId']}-{uuid4().hex}",
        }
        payload = {
            "files": list(self._config.files),
            "intent": "engineering-story-context-analysis",
            "guidance": guidance,
        }
        url = f"{self._config.core_base_url}/api/v1/story-context-agent/projects/{self._config.project_slug}/tasks"
        params = {"storyId": self._config.story_id} if self._config.story_id else {}
        started = time.perf_counter()
        with httpx.Client(timeout=self._config.callback_timeout_seconds, headers=headers) as client:
            response = client.post(url, params=params, json=payload)
            if response.status_code != 202:
                raise LiveConfigurationError(f"Core submission failed with HTTP {response.status_code}")
            submitted = _bounded_json(response)
            task_id = submitted.get("aiTaskId")
            if not isinstance(task_id, str) or not task_id:
                raise LiveConfigurationError("Core submission did not return aiTaskId")
            result_url = f"{self._config.core_base_url}/api/v1/story-context-agent/tasks/{task_id}/result"
            deadline = time.monotonic() + self._config.callback_timeout_seconds
            result: dict[str, Any] | None = None
            while time.monotonic() < deadline:
                result_response = client.get(result_url)
                if result_response.status_code == 200:
                    result = _bounded_json(result_response)
                    break
                if result_response.status_code not in {404, 409, 425}:
                    raise LiveConfigurationError(
                        f"Core result polling failed with HTTP {result_response.status_code}"
                    )
                time.sleep(self._config.poll_interval_seconds)
            if result is None:
                raise TimeoutError("Core Story Context result polling timed out")
        return {
            "executionMode": "LIVE",
            "executionStatus": "COMPLETED",
            "integrationStatus": "LIVE_CORE_CALLBACK_COMPLETED",
            "providerType": "CORE_CONFIGURED_PROVIDER",
            "providerCalls": 1,
            "externalProviderCalls": 1,
            "externalNetworkCalls": 1,
            "networkCalls": 1,
            "latencyMs": round((time.perf_counter() - started) * 1000),
            "taskId": task_id,
            "submission": {"status": response.status_code, "aiTaskId": task_id},
            "finalResponse": result,
        }


class ChatGptOAuthTransport:
    """Use the existing encrypted OAuth credential/binding boundary for V4."""

    def __init__(self, *, task_id: UUID, model: str):
        from app.core.config import get_settings
        from app.core.dependencies import get_credential_resolver, get_credential_store
        from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthSessionStore, required_scopes_granted

        self._task_id = task_id
        self._model = model
        self._settings = get_settings()
        self._resolver = get_credential_resolver()
        self._refresh_service = ChatGptOAuthService(
            self._settings,
            OAuthSessionStore(self._settings.oauth_store_path),
            get_credential_store(),
        )
        self._required_scopes_granted = required_scopes_granted

    def complete(self, request: Any) -> ProviderResponse:
        credential = self._resolver.resolve_for_task(self._task_id, "chatgpt")
        if credential.access_token_expires_at is not None and credential.access_token_expires_at <= datetime.now(timezone.utc):
            credential = asyncio.run(self._refresh_service.refresh_if_expired(credential.handle))
        if not self._required_scopes_granted(credential.scopes):
            raise LiveConfigurationError("ChatGPT OAuth credential lacks required scopes")
        if request.mode == "AGENT_DIRECT_OPEN":
            instructions = "Use only the typed repository tools. Return the Q1 answer JSON after bounded inspection."
            user_input: Any = serialize_v4_conversation(request.input_payload)
        else:
            instructions = request.input_payload["prompt"]["system"]
            user_input = request.input_payload["prompt"]["user"]
        body: dict[str, Any] = {
            "model": self._model,
            "store": False,
            "stream": False,
            "instructions": instructions,
            "input": user_input,
            "max_output_tokens": request.max_output_tokens,
            "text": {"format": {"type": "json_schema", "name": "q1_answer", "strict": True, "schema": common_answer_schema()}},
        }
        if request.tool_schema:
            body["tools"] = request.tool_schema
        if request.on_transport_attempt is not None:
            request.on_transport_attempt()
        with httpx.Client(timeout=self._settings.llm_timeout_seconds) as client:
            response = client.post(
                self._settings.chatgpt_responses_endpoint,
                headers={"Authorization": f"Bearer {credential.access_token}"},
                json=body,
            )
        if response.status_code >= 400:
            raise LiveConfigurationError(f"ChatGPT OAuth request failed with HTTP {response.status_code}")
        payload = response.json()
        if request.on_provider_response is not None:
            usage = payload.get("usage", {}) if isinstance(payload.get("usage"), dict) else {}
            request.on_provider_response(
                {
                    "inputTokens": usage.get("input_tokens", "NOT_MEASURED"),
                    "outputTokens": usage.get("output_tokens", "NOT_MEASURED"),
                    "totalTokens": usage.get("total_tokens", "NOT_MEASURED"),
                },
                {
                    "transportAttempted": True,
                    "providerResponseReceived": True,
                    "requestedModel": self._model,
                    "resolvedProviderModel": payload.get("model"),
                },
            )
        if payload.get("status") == "incomplete":
            raise LiveConfigurationError("ChatGPT OAuth response was incomplete")
        for item in payload.get("output", []):
            if item.get("type") == "function_call":
                return ProviderResponse(
                    "TOOL_CALL",
                    {"operation": item.get("name"), "arguments": json.loads(item.get("arguments", "{}"))},
                    payload,
                )
        output_text = payload.get("output_text")
        if not isinstance(output_text, str) or not output_text:
            raise LiveConfigurationError("ChatGPT OAuth response did not contain a final answer")
        return ProviderResponse("FINAL", json.loads(output_text), payload)


class DirectLiveTreatmentAdapter:
    """Run the Q1 direct treatment with the existing OAuth-backed V4 transport."""

    condition = "B_DIRECT_INVESTIGATOR"
    live_status = "LIVE_CHATGPT_OAUTH_V4_BOUNDARY"

    def __init__(self, repository_root: Path, task_id: UUID, model: str):
        self._repository_root = repository_root
        self._task_id = task_id
        self._model = model

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        config = load_q1_b_config(self._repository_root, allow_live=True)
        verify_commit(self._repository_root, Q1_SOURCE_REVISION)
        provider = ChatGptOAuthTransport(task_id=self._task_id, model=self._model)
        assignment = V4Assignment(
            "q1-live-b", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1
        )
        runtime = V4CollectionRuntime(
            provider,
            ceilings=SafetyCeilings(),
            tool_factory=lambda _assignment: Q1PinnedRepositoryTools(self._repository_root),
            run_id=str(observation["observationId"]),
            test_only=False,
        )
        native = runtime.run_assignment(assignment)
        raw = native.get("rawOutput", {})
        return {
            "executionMode": "LIVE",
            "executionStatus": native.get("executionStatus", "RUNTIME_FAILURE"),
            "integrationStatus": "LIVE_V4_COMPLETED" if native.get("executionStatus") == "COMPLETED" else "LIVE_V4_FAILED",
            "providerType": "CHATGPT_OAUTH_RESPONSES_API",
            "providerIdentity": f"openai_chatgpt/{self._model}",
            "providerCalls": native.get("resources", {}).get("providerCalls", 0),
            "externalProviderCalls": native.get("resources", {}).get("providerTransportAttempts", 0),
            "externalNetworkCalls": native.get("resources", {}).get("providerTransportAttempts", 0),
            "networkCalls": native.get("resources", {}).get("providerTransportAttempts", 0),
            "sourceSnapshot": {"revision": config["repository"]["revision"]},
            "nativeOutput": native,
            "finalResponse": raw.get("finalResponse"),
            "toolTrace": raw.get("toolTrace", []),
        }


def live_adapters(*, repository_root: Path, manifest: Mapping[str, Any], environment: Mapping[str, str] | None = None) -> dict[str, TreatmentAdapter]:
    values = os.environ if environment is None else environment
    if values.get("LLM_PROVIDER", "openai_chatgpt").strip().lower() != "openai_chatgpt":
        raise LiveConfigurationError("B live execution requires LLM_PROVIDER=openai_chatgpt")
    model = values.get("LLM_MODEL", manifest["model"]).strip()
    config = LiveConfiguration.from_environment(values)
    return {
        "A_DEVLOG_CURRENT": CoreLiveTreatmentAdapter("A_DEVLOG_CURRENT", config, manifest),
        "B_DIRECT_INVESTIGATOR": DirectLiveTreatmentAdapter(repository_root, config.b_task_id, model),
        "C_DEVLOG_ENGINEERING_ANALYST": CoreLiveTreatmentAdapter("C_DEVLOG_ENGINEERING_ANALYST", config, manifest),
    }


def run_live_execution(*, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, manifest: Mapping[str, Any], authorization: Mapping[str, Any], environment: Mapping[str, str] | None = None):
    root = Path(repository_root).resolve()
    if current_head(root) != authorization["runtimeRevision"]:
        raise LiveConfigurationError("live execution requires HEAD to equal the authorized runtime revision")
    try:
        assert_reproducible_worktree(root)
    except ValueError as error:
        raise LiveConfigurationError(str(error)) from error
    adapters = live_adapters(repository_root=root, manifest=manifest, environment=environment)
    return run_dry_run(
        manifest_path=manifest_path,
        output_root=output_root,
        repository_root=root,
        adapters=adapters,
        mode="LIVE",
    )
