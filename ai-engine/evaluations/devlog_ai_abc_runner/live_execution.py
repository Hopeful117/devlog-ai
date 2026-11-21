"""Explicitly authorized live execution for the bounded Q1 A/B/C pilot.

This module is intentionally separate from the provider-free runner.  It uses
the real V4 provider transport for B and the real Core HTTP boundary for A/C.
Credentials are read only from the process environment and never enter an
authorization document or an evaluation artifact.
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import re
import time
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping
from uuid import UUID

import httpx

from evaluations.comparative_baseline.collection_runtime import ProviderResponse
from evaluations.comparative_baseline.collection_runtime import TransportFailure, provider_failure_attribution
from evaluations.comparative_baseline.live_adapters import common_answer_schema, serialize_v4_conversation
from evaluations.comparative_baseline_v4.protocol import SafetyCeilings, V4Assignment
from evaluations.comparative_baseline_v4.runtime import V4CollectionRuntime

from .q1_b_binding import Q1PinnedRepositoryTools, load_q1_b_config
from .runner import TreatmentAdapter, run_dry_run
from .provenance import Q1_SOURCE_REVISION, assert_reproducible_worktree, current_head, verify_commit
from evaluations.story_context_specificity_live import McpClient


class LiveConfigurationError(ValueError):
    """Raised when the live runtime cannot be configured without guessing."""


logger = logging.getLogger(__name__)


class _ProviderHttpError(RuntimeError):
    def __init__(self, status_code: int):
        super().__init__(f"provider returned HTTP {status_code}")
        self.status_code = status_code


def _oauth_transport_failure(
    error: BaseException,
    *,
    phase: str,
    category: str | None = None,
    transport_attempted: bool = False,
    provider_response_received: bool = False,
) -> TransportFailure:
    status = getattr(error, "status_code", None)
    metadata = {
        "transportAttempted": transport_attempted,
        "providerResponseReceived": provider_response_received,
    }
    if isinstance(status, int):
        metadata["httpStatus"] = status
    attribution = provider_failure_attribution(
        error,
        category=category,
        request_phase=phase,
        diagnostic_metadata=metadata,
    )
    return TransportFailure("ChatGPT OAuth transport failed", attribution=attribution)


def _bounded_mcp_error(error: Exception) -> str:
    """Expose bounded MCP diagnostics without forwarding credentials."""

    value: Any
    try:
        value = json.loads(str(error))
    except json.JSONDecodeError:
        value = {}
    if isinstance(value, dict):
        value = value.get("error", value)
    if isinstance(value, dict):
        code = value.get("code")
        message = value.get("message")
        if isinstance(message, str):
            message = re.sub(r"(?i)bearer\s+\S+", "Bearer [REDACTED]", message)
            message = re.sub(r"(?i)(token|secret|password)=\S+", r"\1=[REDACTED]", message)
            message = message[:240]
        details = [str(item) for item in (code, message) if item is not None]
        if details:
            return ": ".join(details)
    return type(error).__name__


@dataclass(frozen=True)
class LiveConfiguration:
    mcp_launcher: Path
    project_slug: str
    story_number: int
    story_id: str | None
    story_path: str | None
    files: tuple[str, ...]
    b_task_id: UUID | None
    callback_timeout_seconds: float
    poll_interval_seconds: float
    mcp_read_timeout_seconds: float

    @classmethod
    def from_environment(cls, environment: Mapping[str, str] | None = None,
                         *, require_task_id: bool = True) -> "LiveConfiguration":
        values = os.environ if environment is None else environment
        launcher = Path(values.get(
            "DEVLOG_MCP_LAUNCHER",
            str(Path.home() / ".config/opencode/devlog-mcp-start.sh"),
        ).strip())
        project = values.get("DEVLOG_LIVE_PROJECT_SLUG", "").strip()
        files = tuple(item.strip() for item in values.get("DEVLOG_LIVE_FILES", "").split(",") if item.strip())
        b_task_value = values.get("DEVLOG_LIVE_B_TASK_ID", "").strip()
        story_number_value = values.get("DEVLOG_LIVE_STORY_NUMBER", "").strip()
        if not project or not story_number_value or not files or (require_task_id and not b_task_value):
            raise LiveConfigurationError(
                "DEVLOG_LIVE_PROJECT_SLUG, DEVLOG_LIVE_STORY_NUMBER and DEVLOG_LIVE_FILES are required"
            )
        try:
            story_number = int(story_number_value)
        except ValueError as error:
            raise LiveConfigurationError("DEVLOG_LIVE_STORY_NUMBER must be an integer") from error
        if story_number < 1:
            raise LiveConfigurationError("DEVLOG_LIVE_STORY_NUMBER must be positive")
        try:
            timeout = float(values.get("DEVLOG_LIVE_TIMEOUT_SECONDS", "120"))
            interval = float(values.get("DEVLOG_LIVE_POLL_INTERVAL_SECONDS", "1"))
        except ValueError as error:
            raise LiveConfigurationError("live timeout values must be numeric") from error
        try:
            mcp_timeout = float(values.get("DEVLOG_MCP_READ_TIMEOUT_SECONDS", "180"))
        except ValueError as error:
            raise LiveConfigurationError("MCP read timeout must be numeric") from error
        if timeout <= 0 or interval <= 0 or interval > timeout or mcp_timeout <= 0:
            raise LiveConfigurationError("live timeout values must be positive and ordered")
        b_task_id = None
        if b_task_value:
            try:
                b_task_id = UUID(b_task_value)
            except ValueError as error:
                raise LiveConfigurationError("DEVLOG_LIVE_B_TASK_ID must be a UUID") from error
        return cls(launcher, project, story_number, None, None, files, b_task_id,
                   timeout, interval, mcp_timeout)


class CoreLiveTreatmentAdapter:
    """Execute one Core-authorized Story Context treatment through MCP."""

    def __init__(self, condition: str, config: LiveConfiguration, manifest: Mapping[str, Any], mcp: McpClient):
        self.condition = condition
        self.live_status = "LIVE_CORE_MCP_BOUNDARY"
        self._config = config
        self._manifest = manifest
        self._mcp = mcp

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
        idempotency_key = (
            f"q1-live-{observation['observationId']}-{current_head(Path.cwd())[:12]}"
        )
        started = time.perf_counter()
        try:
            execution = self._mcp.tool("devlog_story_agent", {
                "projectSlug": self._config.project_slug,
                "storyId": self._config.story_id,
                "intent": "engineering-story-context-analysis",
                "question": "Q1 bounded live evaluation",
                "files": list(self._config.files),
                "guidance": guidance,
                "idempotencyKey": idempotency_key,
            })
        except (OSError, RuntimeError, TimeoutError) as error:
            detail = _bounded_mcp_error(error)
            raise LiveConfigurationError(
                f"MCP authenticated Story Agent execution failed: {detail}"
            ) from error
        if not isinstance(execution, dict):
            raise LiveConfigurationError("MCP Story Agent response must be an object")
        task_id = execution.get("aiTaskId")
        if not isinstance(task_id, str) or not task_id:
            raise LiveConfigurationError("MCP Story Agent response did not return aiTaskId")
        result = execution.get("result") if isinstance(execution.get("result"), dict) else {}
        execution_status = execution.get("status", "RUNTIME_FAILURE")
        completed = execution_status == "COMPLETED"
        return {
            "executionMode": "LIVE",
            "executionStatus": execution_status,
            "integrationStatus": "LIVE_CORE_CALLBACK_COMPLETED" if completed else "LIVE_CORE_FAILED",
            "providerType": "CORE_CONFIGURED_PROVIDER",
            "providerCalls": 1,
            "externalProviderCalls": 1,
            "externalNetworkCalls": 1,
            "networkCalls": 1,
            "latencyMs": round((time.perf_counter() - started) * 1000),
            "taskId": task_id,
            "storyReference": {
                "projectSlug": self._config.project_slug,
                "storyNumber": self._config.story_number,
                "storyId": self._config.story_id,
                "storyPath": self._config.story_path,
            },
            "submission": {"status": "MCP_ACCEPTED", "aiTaskId": task_id},
            "finalResponse": result,
            "mcpExecution": execution,
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
        started = time.perf_counter()
        phase = "CREDENTIAL_RESOLUTION"
        transport_attempted = False
        provider_response_received = False
        outcome = "FAILED"
        logger.info(
            "Q1 B provider attempt started",
            extra={
                "event": "Q1_B_PROVIDER_ATTEMPT_STARTED",
                "taskId": str(self._task_id),
                "model": self._model,
                "turn": request.turn_index,
                "phase": phase,
            },
        )
        try:
            credential = self._resolver.resolve_for_task(self._task_id, "chatgpt")
            phase = "CREDENTIAL_REFRESH"
            if credential.access_token_expires_at is not None and credential.access_token_expires_at <= datetime.now(timezone.utc):
                credential = asyncio.run(self._refresh_service.refresh_if_expired(credential.handle))
            phase = "CREDENTIAL_SCOPE_VALIDATION"
            if not self._required_scopes_granted(credential.scopes):
                raise _oauth_transport_failure(
                    RuntimeError("ChatGPT OAuth credential lacks required scopes"),
                    phase=phase,
                    category="AUTHENTICATION_ERROR",
                )
            phase = "REQUEST_BUILD"
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
            phase = "PROVIDER_REQUEST"
            transport_attempted = True
            if request.on_transport_attempt is not None:
                request.on_transport_attempt()
            with httpx.Client(timeout=self._settings.llm_timeout_seconds) as client:
                response = client.post(
                    self._settings.chatgpt_responses_endpoint,
                    headers={"Authorization": f"Bearer {credential.access_token}"},
                    json=body,
                )
            phase = "PROVIDER_RESPONSE"
            provider_response_received = True
            if response.status_code >= 400:
                raise _ProviderHttpError(response.status_code)
            try:
                payload = response.json()
            except (TypeError, ValueError) as error:
                raise _oauth_transport_failure(
                    error,
                    phase="RESPONSE_PARSE",
                    category="RESPONSE_PARSE_ERROR",
                    transport_attempted=transport_attempted,
                    provider_response_received=provider_response_received,
                ) from error
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
                raise _oauth_transport_failure(
                    RuntimeError("ChatGPT OAuth response was incomplete"),
                    phase="RESPONSE_STATE",
                    category="PROVIDER_INCOMPLETE_RESPONSE",
                    transport_attempted=transport_attempted,
                    provider_response_received=provider_response_received,
                )
            for item in payload.get("output", []):
                if item.get("type") == "function_call":
                    outcome = "TOOL_CALL"
                    return ProviderResponse(
                        "TOOL_CALL",
                        {"operation": item.get("name"), "arguments": json.loads(item.get("arguments", "{}"))},
                        payload,
                    )
            output_text = payload.get("output_text")
            if not isinstance(output_text, str) or not output_text:
                raise _oauth_transport_failure(
                    RuntimeError("ChatGPT OAuth response did not contain a final answer"),
                    phase="RESPONSE_CONTRACT",
                    category="RESPONSE_CONTRACT_ERROR",
                    transport_attempted=transport_attempted,
                    provider_response_received=provider_response_received,
                )
            try:
                parsed = json.loads(output_text)
            except (TypeError, ValueError) as error:
                raise _oauth_transport_failure(
                    error,
                    phase="RESPONSE_PARSE",
                    category="RESPONSE_PARSE_ERROR",
                    transport_attempted=transport_attempted,
                    provider_response_received=provider_response_received,
                ) from error
            outcome = "FINAL"
            return ProviderResponse("FINAL", parsed, payload)
        except TransportFailure as error:
            attribution = error.attribution or provider_failure_attribution(
                error,
                request_phase=phase,
                diagnostic_metadata={
                    "transportAttempted": transport_attempted,
                    "providerResponseReceived": provider_response_received,
                },
            )
            logger.warning(
                "Q1 B provider attempt failed",
                extra={
                    "event": "Q1_B_PROVIDER_ATTEMPT_FAILED",
                    "taskId": str(self._task_id),
                    "model": self._model,
                    "turn": request.turn_index,
                    "phase": phase,
                    "outcome": attribution["category"],
                    "providerStatus": attribution.get("httpStatus"),
                    "providerResponseReceived": provider_response_received,
                    "durationMs": round((time.perf_counter() - started) * 1000),
                },
            )
            raise
        except httpx.TimeoutException as error:
            raise _oauth_transport_failure(
                error,
                phase=phase,
                category="TIMEOUT",
                transport_attempted=transport_attempted,
                provider_response_received=provider_response_received,
            ) from error
        except httpx.HTTPError as error:
            raise _oauth_transport_failure(
                error,
                phase=phase,
                category="CONNECTION_ERROR",
                transport_attempted=transport_attempted,
                provider_response_received=provider_response_received,
            ) from error
        except _ProviderHttpError as error:
            raise _oauth_transport_failure(
                error,
                phase=phase,
                transport_attempted=transport_attempted,
                provider_response_received=provider_response_received,
            ) from error
        except Exception as error:
            raise _oauth_transport_failure(
                error,
                phase=phase,
                category="AUTHENTICATION_ERROR" if phase.startswith("CREDENTIAL") else None,
                transport_attempted=transport_attempted,
                provider_response_received=provider_response_received,
            ) from error
        finally:
            if outcome != "FAILED":
                logger.info(
                    "Q1 B provider attempt finished",
                    extra={
                        "event": "Q1_B_PROVIDER_ATTEMPT_FINISHED",
                        "taskId": str(self._task_id),
                        "model": self._model,
                        "turn": request.turn_index,
                        "phase": phase,
                        "outcome": outcome,
                        "providerResponseReceived": provider_response_received,
                        "durationMs": round((time.perf_counter() - started) * 1000),
                    },
                )


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


def resolve_live_story(config: LiveConfiguration, mcp: McpClient) -> LiveConfiguration:
    """Resolve the repository Story number through Core before UUID-scoped calls."""

    try:
        result = mcp.tool("resolve_engineering_story", {
            "projectSlug": config.project_slug,
            "storyNumber": config.story_number,
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP Engineering Story resolution failed") from error
    if not isinstance(result, dict):
        raise LiveConfigurationError("MCP Story resolution response must be an object")
    story_id = result.get("id")
    if not isinstance(story_id, str) or not story_id:
        raise LiveConfigurationError("MCP Story resolution response did not return id")
    try:
        UUID(story_id)
    except ValueError as error:
        raise LiveConfigurationError("MCP Story resolution response returned an invalid id") from error
    if result.get("storyNumber") != config.story_number:
        raise LiveConfigurationError("MCP Story resolution returned a different story number")
    story_path = result.get("storyPath")
    if story_path and story_path not in config.files:
        raise LiveConfigurationError("MCP Story resolution returned a path outside the configured files")
    return replace(config, story_id=story_id, story_path=story_path)


def reserve_live_task_binding(environment: Mapping[str, str] | None = None) -> dict[str, Any]:
    """Reserve a Core task/binding through MCP without dispatching provider work."""

    config = LiveConfiguration.from_environment(environment, require_task_id=False)
    try:
        mcp = McpClient(config.mcp_launcher, launcher=config.mcp_launcher,
                        read_timeout_seconds=config.mcp_read_timeout_seconds)
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP authenticated transport could not be started") from error
    try:
        config = resolve_live_story(config, mcp)
        result = mcp.tool("story_context_agent_prepare_task_binding", {
            "projectSlug": config.project_slug,
            "storyId": config.story_id,
            "files": list(config.files),
            "intent": "engineering-story-context-analysis",
            "idempotencyKey": "q1-live-binding-prepare",
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP task binding reservation failed") from error
    finally:
        mcp.close()
    if not isinstance(result, dict):
        raise LiveConfigurationError("MCP task binding response must be an object")
    task_id = result.get("aiTaskId")
    if not isinstance(task_id, str) or not task_id:
        raise LiveConfigurationError("Core binding reservation did not return aiTaskId")
    return {
        **result,
        "storyReference": {
            "projectSlug": config.project_slug,
            "storyNumber": config.story_number,
            "storyId": config.story_id,
            "storyPath": config.story_path,
        },
    }


def live_adapters(*, repository_root: Path, manifest: Mapping[str, Any], config: LiveConfiguration,
                  mcp: McpClient, environment: Mapping[str, str] | None = None) -> dict[str, TreatmentAdapter]:
    values = os.environ if environment is None else environment
    if values.get("LLM_PROVIDER", "openai_chatgpt").strip().lower() != "openai_chatgpt":
        raise LiveConfigurationError("B live execution requires LLM_PROVIDER=openai_chatgpt")
    if values.get("CHATGPT_OAUTH_ENABLED", "false").strip().lower() != "true":
        raise LiveConfigurationError("B live execution requires CHATGPT_OAUTH_ENABLED=true")
    model = values.get("LLM_MODEL", manifest["model"]).strip()
    return {
        "A_DEVLOG_CURRENT": CoreLiveTreatmentAdapter("A_DEVLOG_CURRENT", config, manifest, mcp),
        "B_DIRECT_INVESTIGATOR": DirectLiveTreatmentAdapter(repository_root, config.b_task_id, model),
        "C_DEVLOG_ENGINEERING_ANALYST": CoreLiveTreatmentAdapter("C_DEVLOG_ENGINEERING_ANALYST", config, manifest, mcp),
    }


def run_live_execution(*, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, manifest: Mapping[str, Any], authorization: Mapping[str, Any], environment: Mapping[str, str] | None = None, observation_ids: tuple[str, ...] | None = None):
    root = Path(repository_root).resolve()
    if current_head(root) != authorization["runtimeRevision"]:
        raise LiveConfigurationError("live execution requires HEAD to equal the authorized runtime revision")
    try:
        assert_reproducible_worktree(root)
    except ValueError as error:
        raise LiveConfigurationError(str(error)) from error
    config = LiveConfiguration.from_environment(environment)
    try:
        mcp = McpClient(config.mcp_launcher, launcher=config.mcp_launcher,
                        read_timeout_seconds=config.mcp_read_timeout_seconds)
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP authenticated transport could not be started") from error
    try:
        config = resolve_live_story(config, mcp)
        adapters = live_adapters(repository_root=root, manifest=manifest, config=config, mcp=mcp,
                                 environment=environment)
        return run_dry_run(
            manifest_path=manifest_path,
            output_root=output_root,
            repository_root=root,
            adapters=adapters,
            mode="LIVE",
            observation_ids=observation_ids,
        )
    finally:
        mcp.close()
