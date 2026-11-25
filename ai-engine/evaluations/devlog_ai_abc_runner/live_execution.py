"""Explicitly authorized live execution for the bounded Q1 A/B/C pilot.

This module is intentionally separate from the provider-free runner.  It uses
the real V4 provider transport for B and the real Core HTTP boundary for A/C.
Credentials are read only from the process environment and never enter an
authorization document or an evaluation artifact.
"""

from __future__ import annotations

import asyncio
import ast
import hashlib
import hmac
import json
import logging
import os
import re
import time
from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Mapping
from uuid import UUID

import httpx

from evaluations.comparative_baseline.collection_runtime import ProviderResponse
from evaluations.comparative_baseline.collection_runtime import TransportFailure, provider_failure_attribution
from evaluations.comparative_baseline.live_adapters import common_answer_schema, serialize_v4_conversation
from evaluations.comparative_baseline_v4.protocol import SafetyCeilings, V4Assignment
from evaluations.comparative_baseline_v4.runtime import V4CollectionRuntime

from .benchmark import (
    BenchmarkCase,
    BenchmarkCaseError,
    assert_question_fidelity,
    citable_scope_instruction,
    load_case,
)
from .q1_b_binding import Q1PinnedRepositoryTools, _case_runtime_input, load_q1_b_config
from .runner import TreatmentAdapter, run_dry_run
from .provenance import Q1_SOURCE_REVISION, assert_reproducible_worktree, current_head, execution_identity, verify_commit
from .prepared_plan import PreparedPlanError, validate_prepared_plan
from evaluations.story_context_specificity_live import McpClient
from .live import canonical_manifest_digest


class LiveConfigurationError(ValueError):
    """Raised when the live runtime cannot be configured without guessing."""


def _mcp_response_diagnostic(response: Mapping[str, Any]) -> dict[str, Any]:
    """Keep response shape evidence without persisting project content."""
    encoded = json.dumps(response, sort_keys=True, separators=(",", ":"), default=str)
    diagnostics = response.get("diagnostics")
    nested_diagnostics = diagnostics if isinstance(diagnostics, Mapping) else {}
    return {
        "responseDigest": hashlib.sha256(encoded.encode("utf-8")).hexdigest(),
        "responseKeys": sorted(str(key) for key in response),
        "status": response.get("status"),
        "aiTaskIdPresent": bool(response.get("aiTaskId")),
        "snapshotIdPresent": bool(response.get("snapshotId")),
        "resultPresent": bool(response.get("result")),
        "diagnosticKeys": sorted(str(key) for key in diagnostics) if isinstance(diagnostics, Mapping) else [],
        "failureCodePresent": bool(response.get("failureCode") or nested_diagnostics.get("failureCode")),
        "failureMessagePresent": bool(response.get("failureMessage") or nested_diagnostics.get("failureMessage")),
    }


logger = logging.getLogger(__name__)


_ENV_KEY = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
_LIVE_PROVIDER_REQUIRED_ENV = (
    "CHATGPT_REDIRECT_URI",
    "CHATGPT_EXT_AGENT_HOST_ID",
    "DEVLOG_CREDENTIAL_ENCRYPTION_KEY",
    "DEVLOG_SERVICE_AUTH_SECRET",
)


def load_environment_file(path: str | Path, *, environ: dict[str, str] | None = None) -> tuple[str, ...]:
    """Load a strict dotenv file without overriding explicit environment values."""

    target = os.environ if environ is None else environ
    loaded: list[str] = []
    try:
        lines = Path(path).read_text(encoding="utf-8").splitlines()
    except OSError as error:
        raise LiveConfigurationError(f"environment file cannot be read: {path}") from error
    for line_number, raw_line in enumerate(lines, start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("export "):
            line = line[7:].lstrip()
        if "=" not in line:
            raise LiveConfigurationError(f"environment file line {line_number} is invalid")
        key, raw_value = line.split("=", 1)
        key = key.strip()
        if not _ENV_KEY.fullmatch(key):
            raise LiveConfigurationError(f"environment file line {line_number} has an invalid key")
        raw_value = raw_value.strip()
        if raw_value.startswith(("'", '"')):
            try:
                value = ast.literal_eval(raw_value)
            except (SyntaxError, ValueError) as error:
                raise LiveConfigurationError(f"environment file line {line_number} has an invalid quoted value") from error
            if not isinstance(value, str):
                raise LiveConfigurationError(f"environment file line {line_number} has an invalid value")
        else:
            value = raw_value
        if key not in target:
            target[key] = value
            loaded.append(key)
    return tuple(loaded)


def missing_live_provider_environment(environment: Mapping[str, str] | None = None) -> tuple[str, ...]:
    values = os.environ if environment is None else environment
    if values.get("LLM_PROVIDER", "openai_chatgpt").strip().lower() != "openai_chatgpt":
        return ()
    if values.get("CHATGPT_OAUTH_ENABLED", "false").strip().lower() != "true":
        return ()
    return tuple(name for name in _LIVE_PROVIDER_REQUIRED_ENV if not values.get(name, "").strip())


def require_live_provider_environment(environment: Mapping[str, str] | None = None) -> None:
    missing = missing_live_provider_environment(environment)
    if missing:
        raise LiveConfigurationError(
            "live provider environment is incomplete: " + ", ".join(missing)
        )


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
    diagnostic_metadata: dict[str, Any] | None = None,
) -> TransportFailure:
    status = getattr(error, "status_code", None)
    metadata = {
        "transportAttempted": transport_attempted,
        "providerResponseReceived": provider_response_received,
    }
    if isinstance(status, int):
        metadata["httpStatus"] = status
    if diagnostic_metadata:
        metadata.update(diagnostic_metadata)
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


def _bounded_mcp_text(value: object) -> str:
    """Keep plain MCP tool text diagnostic-only and credential-safe."""

    text = str(value)
    text = re.sub(r"(?i)bearer\s+\S+", "Bearer [REDACTED]", text)
    text = re.sub(r"(?i)(token|secret|password|api[_-]?key)=\S+", r"\1=[REDACTED]", text)
    return text[:500]


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

    def __init__(self, condition: str, config: LiveConfiguration, manifest: Mapping[str, Any], mcp: McpClient,
                 *, case: BenchmarkCase, case_story_id: str | None = None,
                 execution_scope: str | None = None, snapshot_grace_seconds: float = 30.0):
        self.condition = condition
        self.live_status = "LIVE_CORE_MCP_BOUNDARY"
        self._config = config
        # Story 0237: question, scope and Story binding come from the frozen
        # benchmark case, identical for every condition, never from runbook
        # placeholders or from the credential-binding Story.
        self._case = case
        self._case_story_id = case_story_id
        self._manifest = manifest
        self._mcp = mcp
        self._execution_scope = execution_scope
        self._snapshot_grace_seconds = snapshot_grace_seconds

    def _resolve_poll_timeout(self, request: dict[str, Any], execution: dict[str, Any]) -> dict[str, Any]:
        """Wait past the MCP poll ceiling for the Core-owned terminal status, reading only snapshots."""
        diagnostics = execution.get("diagnostics")
        if not isinstance(diagnostics, dict) or diagnostics.get("code") != "POLL_TIMEOUT":
            return execution
        task_id = diagnostics.get("aiTaskId") or execution.get("aiTaskId")
        try:
            deadline = datetime.fromisoformat(str(diagnostics.get("deadlineAt")).replace("Z", "+00:00"))
        except ValueError:
            return execution
        if not isinstance(task_id, str) or not task_id:
            return execution
        stop_at = deadline + timedelta(seconds=self._snapshot_grace_seconds)
        while datetime.now(timezone.utc) < stop_at:
            time.sleep(self._config.poll_interval_seconds)
            try:
                snapshot = self._mcp.tool("story_context_agent_get_snapshot_authenticated", {"aiTaskId": task_id})
            except (OSError, RuntimeError, TimeoutError):
                continue
            status = snapshot.get("status") if isinstance(snapshot, dict) else None
            if status == "COMPLETED":
                # A COMPLETED task is reused for the identical request, so this re-read
                # fetches its result without starting a new provider execution.
                completed = self._mcp.tool("devlog_story_agent", request)
                if isinstance(completed, dict) and (
                    completed.get("aiTaskId") or completed.get("snapshotId")
                ) == task_id:
                    return completed
                resolved = dict(execution)
                resolved["diagnostics"] = {**diagnostics, "code": "COMPLETED_REREAD_IDENTITY_MISMATCH"}
                return resolved
            if status in {"FAILED", "TIMED_OUT", "NOT_ESTABLISHED"}:
                resolved = dict(execution)
                resolved["status"] = status
                resolved["diagnostics"] = {
                    key: value for key, value in {
                        "status": status,
                        "aiTaskId": task_id,
                        "failureCode": snapshot.get("failureCode"),
                        "failureMessage": snapshot.get("failureMessage"),
                        "terminalReason": snapshot.get("terminalReason"),
                        "deadlineAt": snapshot.get("deadlineAt"),
                        "resolvedAfter": "POLL_TIMEOUT",
                    }.items() if value is not None
                }
                return resolved
        return execution

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        guidance: dict[str, Any] = {}
        if self.condition == "C_DEVLOG_ENGINEERING_ANALYST":
            guidance = {
                "investigation": {
                    "enabled": True,
                    # Story 0237: C investigates the same human-reviewed case
                    # scope as A and B. Core still rejects any invented path
                    # outside it (PATH_OUT_OF_SCOPE) or absent from the source.
                    "allowedPathPrefixes": list(self._case.scope_files),
                    "budget": {
                        "maximumSteps": 5,
                        "maximumRetrievalCalls": 5,
                        "maximumEvidenceItems": 15,
                        "maximumBytes": 30_000,
                        "maximumTokens": 7_500,
                        "maximumElapsedMillis": 120_000,
                         "maximumProviderOutputTokens": 12_000,
                    },
                }
            }
        idempotency_key = (
            f"q1-live-{observation['observationId']}-{current_head(Path.cwd())[:12]}"
        )
        if self._execution_scope:
            idempotency_key = f"{idempotency_key}-{self._execution_scope}"
        # Core keeps evaluation out of the prompt but includes it in the submission digest,
        # so each authorization yields its own execution instead of reusing an earlier one.
        guidance = {
            **guidance,
            "evaluation": {"executionIdentity": idempotency_key},
        }
        started = time.perf_counter()
        request = {
            "projectSlug": self._config.project_slug,
            "intent": "engineering-story-context-analysis",
            "question": self._case.question,
            "files": list(self._case.scope_files),
            "guidance": guidance,
            "idempotencyKey": idempotency_key,
        }
        if self._case_story_id is not None:
            request["storyId"] = self._case_story_id
        try:
            assert_question_fidelity(request["question"], self._case)
        except BenchmarkCaseError as error:
            raise LiveConfigurationError(str(error)) from error
        try:
            execution = self._mcp.tool("devlog_story_agent", request)
        except (OSError, RuntimeError, TimeoutError) as error:
            detail = _bounded_mcp_error(error)
            raise LiveConfigurationError(
                f"MCP authenticated Story Agent execution failed: {detail}"
            ) from error
        if not isinstance(execution, dict):
            raise LiveConfigurationError("MCP Story Agent response must be an object")
        execution = self._resolve_poll_timeout(request, execution)
        response_diagnostic = _mcp_response_diagnostic(execution)
        task_id = execution.get("aiTaskId") or execution.get("snapshotId")
        if not isinstance(task_id, str) or not task_id:
            response = {
                "executionMode": "LIVE",
                "executionStatus": "RUNTIME_FAILURE",
                "integrationStatus": "LIVE_CORE_MCP_BOUNDARY_FAILURE",
                "providerCalls": 0,
                "externalProviderCalls": 0,
                "externalNetworkCalls": 0,
                "networkCalls": 0,
                "errorType": "MCP_TASK_ID_MISSING",
                "errorMessage": "MCP Story Agent response did not return aiTaskId or snapshotId",
                "mcpResponse": response_diagnostic,
            }
            if isinstance(execution.get("mcpText"), str):
                response["errorType"] = "MCP_TEXT_RESPONSE"
                response["errorMessage"] = "MCP Story Agent returned non-JSON tool text"
                response["mcpResponse"]["text"] = _bounded_mcp_text(execution["mcpText"])
            return response
        result = execution.get("result") if isinstance(execution.get("result"), dict) else {}
        execution_status = execution.get("status", "RUNTIME_FAILURE")
        if execution_status == "COMPLETED" and not result:
            return {
                "executionMode": "LIVE",
                "executionStatus": "RUNTIME_FAILURE",
                "integrationStatus": "LIVE_CORE_MCP_BOUNDARY_FAILURE",
                "providerCalls": 0,
                "externalProviderCalls": 0,
                "externalNetworkCalls": 0,
                "networkCalls": 0,
                "errorType": "MCP_COMPLETED_RESULT_UNAVAILABLE",
                "errorMessage": "MCP Story Agent completed without a readable result",
                "taskId": task_id,
                "mcpResponse": response_diagnostic,
            }
        if self.condition == "C_DEVLOG_ENGINEERING_ANALYST":
            # MCP leaves scope at the top level when the task was already COMPLETED on submission.
            scope = result.get("scope") if isinstance(result.get("scope"), dict) else execution.get("scope")
            resolved_revision = scope.get("resolvedRevision") if isinstance(scope, dict) else None
            expected_revision = manifest.get("repositoryRevision")
            if not isinstance(expected_revision, str) or not expected_revision:
                return {
                    "executionMode": "LIVE",
                    "executionStatus": "RUNTIME_FAILURE",
                    "integrationStatus": "LIVE_CORE_MCP_BOUNDARY_FAILURE",
                    "providerCalls": 0,
                    "externalProviderCalls": 0,
                    "externalNetworkCalls": 0,
                    "networkCalls": 0,
                    "errorType": "Q1_SOURCE_REVISION_UNAVAILABLE",
                    "errorMessage": "Q1 manifest does not contain an expected source revision",
                    "taskId": task_id,
                    "mcpResponse": response_diagnostic,
                }
            if not isinstance(resolved_revision, str) or not resolved_revision:
                return {
                    "executionMode": "LIVE",
                    "executionStatus": "RUNTIME_FAILURE",
                    "integrationStatus": "LIVE_CORE_MCP_BOUNDARY_FAILURE",
                    "providerCalls": 0,
                    "externalProviderCalls": 0,
                    "externalNetworkCalls": 0,
                    "networkCalls": 0,
                    "errorType": "Q1_SOURCE_REVISION_UNAVAILABLE",
                    "errorMessage": "Core did not return a resolved source revision for C",
                    "taskId": task_id,
                    "mcpResponse": response_diagnostic,
                }
            if resolved_revision != expected_revision:
                return {
                    "executionMode": "LIVE",
                    "executionStatus": "RUNTIME_FAILURE",
                    "integrationStatus": "LIVE_CORE_MCP_BOUNDARY_FAILURE",
                    "providerCalls": 0,
                    "externalProviderCalls": 0,
                    "externalNetworkCalls": 0,
                    "networkCalls": 0,
                    "errorType": "Q1_SOURCE_REVISION_MISMATCH",
                    "errorMessage": "Core source revision does not match the authorized Q1 revision",
                    "taskId": task_id,
                    "sourceRevision": expected_revision,
                    "resolvedRevision": resolved_revision,
                    "mcpResponse": response_diagnostic,
                }
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
                "storyNumber": self._case.story_number,
                "storyId": self._case_story_id,
            },
            "benchmarkCase": self._case.identity(),
            "submission": {"status": "MCP_ACCEPTED", "aiTaskId": task_id},
            "finalResponse": result,
            "mcpExecution": execution,
            "mcpResponse": response_diagnostic,
        }


class ChatGptOAuthTransport:
    """Use the existing encrypted OAuth credential/binding boundary for V4."""

    def __init__(self, *, task_id: UUID, model: str, scope_instruction: str):
        from app.core.config import get_settings
        from app.core.dependencies import get_credential_resolver, get_credential_store
        from app.services.chatgpt_oauth import ChatGptOAuthService, OAuthSessionStore, required_scopes_granted

        self._task_id = task_id
        self._model = model
        self._scope_instruction = scope_instruction
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
        remote_provider = os.getenv("DEVLOG_LIVE_REMOTE_PROVIDER", "false").lower() == "true"
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
            if not remote_provider:
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
                instructions = (
                    "Use only the typed repository tools. Return the answer JSON after bounded inspection. "
                    "Keep answerText concise and under 3000 characters. "
                    + self._scope_instruction
                    + " Every evidence excerpt must be copied verbatim from the returned tool content, must be at most 400 characters, and must not be paraphrased."
                )
                user_input: Any = serialize_v4_conversation(request.input_payload)
            else:
                instructions = request.input_payload["prompt"]["system"]
                user_input = request.input_payload["prompt"]["user"]
            body: dict[str, Any] = {
                "model": self._model,
                "store": False,
                "stream": True,
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
            output_text_parts: list[str] = []
            function_call: dict[str, Any] | None = None
            function_arguments: dict[str, str] = {}
            completed_payload: dict[str, Any] | None = None
            with httpx.Client(timeout=self._settings.llm_timeout_seconds) as client:
                if remote_provider:
                    return self._complete_remote(request, body, client)
                with client.stream(
                    "POST",
                    self._settings.chatgpt_responses_endpoint,
                    headers={"Authorization": f"Bearer {credential.access_token}"},
                    json=body,
                ) as response:
                    phase = "PROVIDER_RESPONSE"
                    provider_response_received = True
                    if response.status_code >= 400:
                        response.read()
                        raise _ProviderHttpError(response.status_code)
                    event_type = None
                    data_lines: list[str] = []
                    for line in response.iter_lines():
                        if line.startswith("event:"):
                            event_type = line[6:].strip()
                        elif line.startswith("data:"):
                            data_lines.append(line[5:].strip())
                        elif not line and data_lines:
                            payload = json.loads("\n".join(data_lines))
                            received_type = event_type or payload.get("type")
                            if received_type == "response.output_text.delta":
                                output_text_parts.append(payload.get("delta", ""))
                            elif received_type == "response.function_call_arguments.delta":
                                item_id = payload.get("item_id")
                                if isinstance(item_id, str):
                                    function_arguments[item_id] = function_arguments.get(item_id, "") + payload.get("delta", "")
                            elif received_type == "response.output_item.done":
                                item = payload.get("item", {})
                                if item.get("type") == "function_call" and function_call is None:
                                    item_id = item.get("id")
                                    arguments = item.get("arguments") or function_arguments.get(item_id, "{}")
                                    function_call = {
                                        "operation": item.get("name"),
                                        "arguments": json.loads(arguments),
                                    }
                            elif received_type == "response.completed":
                                completed_payload = payload.get("response", payload)
                            elif received_type in {"response.failed", "response.incomplete", "error"}:
                                raise RuntimeError("ChatGPT OAuth response was incomplete")
                            event_type, data_lines = None, []
            if completed_payload is None:
                raise RuntimeError("ChatGPT OAuth response did not complete")
            payload = completed_payload
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
            if function_call is not None:
                outcome = "TOOL_CALL"
                return ProviderResponse("TOOL_CALL", function_call, payload)
            output_text = "".join(output_text_parts)
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
                diagnostic_metadata={
                    "bindingTaskId": str(self._task_id),
                    "bindingProvider": "chatgpt",
                    "bindingStorePathDigest": hashlib.sha256(
                        str(getattr(self._settings, "binding_store_path", "")).encode("utf-8")
                    ).hexdigest(),
                },
            ) from error
        except Exception as error:
            raise _oauth_transport_failure(
                error,
                phase=phase,
                category="AUTHENTICATION_ERROR" if phase.startswith("CREDENTIAL") else None,
                transport_attempted=transport_attempted,
                provider_response_received=provider_response_received,
                diagnostic_metadata={
                    "bindingTaskId": str(self._task_id),
                    "bindingProvider": "chatgpt",
                    "bindingStorePathDigest": hashlib.sha256(
                        str(getattr(self._settings, "binding_store_path", "")).encode("utf-8")
                    ).hexdigest(),
                },
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

    def _complete_remote(self, request: Any, body: dict[str, Any], client: httpx.Client) -> ProviderResponse:
        endpoint = os.getenv(
            "DEVLOG_LIVE_PROVIDER_ENDPOINT",
            "http://127.0.0.1:18081/api/v1/evaluations/chatgpt/responses",
        )
        secret = os.getenv("DEVLOG_SERVICE_AUTH_SECRET", "")
        if not secret:
            raise LiveConfigurationError("remote provider execution requires DEVLOG_SERVICE_AUTH_SECRET")
        payload = {"taskId": str(self._task_id), **body}
        raw_body = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()
        timestamp = str(int(time.time()))
        nonce = str(UUID(int=int.from_bytes(os.urandom(16), "big")))
        request_id = str(UUID(int=int.from_bytes(os.urandom(16), "big")))
        canonical = "\n".join(("POST", "/api/v1/evaluations/chatgpt/responses", timestamp, nonce, request_id)).encode()
        signature = hmac.new(secret.encode(), canonical + b"\n" + raw_body, hashlib.sha256).hexdigest()
        response = client.post(
            endpoint,
            content=raw_body,
            headers={
                "Content-Type": "application/json",
                "X-DevLog-Service-Signature": f"sha256={signature}",
                "X-DevLog-Service-Timestamp": timestamp,
                "X-DevLog-Service-Nonce": nonce,
                "X-DevLog-Service-Request-Id": request_id,
            },
        )
        if response.status_code >= 400:
            error_payload = response.json() if response.headers.get("content-type", "").startswith("application/json") else {}
            error = _ProviderHttpError(response.status_code)
            raise _oauth_transport_failure(
                error,
                phase="CREDENTIAL_RESOLUTION" if response.status_code == 401 else "PROVIDER_REQUEST",
                transport_attempted=True,
                provider_response_received=True,
                diagnostic_metadata={
                    "bindingTaskId": str(self._task_id),
                    "bindingProvider": "chatgpt",
                    "remoteProviderEndpoint": endpoint,
                    "remoteProviderErrorCode": error_payload.get("code"),
                    "remoteProviderErrorFields": error_payload.get("fields", []),
                    "remoteProviderErrorProviderFields": sorted(
                        str(key) for key in error_payload.get("providerError", {})
                    ) if isinstance(error_payload.get("providerError"), dict) else [],
                },
            )
        result = response.json()
        if result.get("status") != "COMPLETED":
            raise _oauth_transport_failure(
                RuntimeError(str(result.get("code", "REMOTE_PROVIDER_FAILED"))),
                phase="PROVIDER_RESPONSE",
                transport_attempted=True,
                provider_response_received=True,
                diagnostic_metadata={
                    "bindingTaskId": str(self._task_id),
                    "bindingProvider": "chatgpt",
                    "remoteProviderEndpoint": endpoint,
                    "remoteProviderError": result.get("providerError"),
                },
            )
        if result.get("kind") == "TOOL_CALL":
            function_call = result.get("functionCall", {})
            return ProviderResponse(
                "TOOL_CALL",
                {
                    "operation": function_call.get("operation"),
                    "arguments": json.loads(function_call.get("arguments", "{}")),
                },
                result,
            )
        output_text = result.get("outputText", "")
        if not isinstance(output_text, str) or not output_text:
            raise _oauth_transport_failure(
                RuntimeError("remote provider returned no final answer"),
                phase="RESPONSE_CONTRACT",
                transport_attempted=True,
                provider_response_received=True,
            )
        return ProviderResponse("FINAL", json.loads(output_text), result)


class DirectLiveTreatmentAdapter:
    """Run the Q1 direct treatment with the existing OAuth-backed V4 transport."""

    condition = "B_DIRECT_INVESTIGATOR"
    live_status = "LIVE_CHATGPT_OAUTH_V4_BOUNDARY"

    def __init__(self, repository_root: Path, task_id: UUID, model: str, *, case: BenchmarkCase):
        self._repository_root = repository_root
        self._task_id = task_id
        self._model = model
        self._case = case

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        config = load_q1_b_config(self._repository_root, allow_live=True)
        case = self._case
        if config.get("questionId") == case.case_id:
            # The B configuration freezes the Q1 question; it must stay identical.
            try:
                assert_question_fidelity(config.get("question"), case)
            except BenchmarkCaseError as error:
                raise LiveConfigurationError(f"B {error}") from error
        try:
            assert_question_fidelity(case.question, case)
        except BenchmarkCaseError as error:
            raise LiveConfigurationError(f"B {error}") from error
        verify_commit(self._repository_root, Q1_SOURCE_REVISION)
        repetition = int(observation.get("repetition", 1))
        provider = ChatGptOAuthTransport(
            task_id=self._task_id, model=self._model,
            scope_instruction=(
                f"Set questionId to {case.case_id} and questionVersion to {case.version} in the final answer. "
                + citable_scope_instruction(case)
            ),
        )
        assignment = V4Assignment(
            f"{case.case_id.lower()}-live-b-r{repetition}", case.case_id, case.version, case.case_id,
            "AGENT_DIRECT_OPEN", repetition,
        )
        runtime = V4CollectionRuntime(
            provider,
            ceilings=SafetyCeilings(),
            tool_factory=lambda _assignment: Q1PinnedRepositoryTools(self._repository_root),
            run_id=str(observation["observationId"]),
            test_only=False,
        )
        with _case_runtime_input(case.case_id, case.version, case.question, config["repository"]["revision"]):
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


def _reserve_task_binding(mcp: McpClient, config: LiveConfiguration, idempotency_key: str) -> dict[str, Any]:
    try:
        result = mcp.tool("story_context_agent_prepare_task_binding", {
            "projectSlug": config.project_slug,
            "storyId": config.story_id,
            "files": list(config.files),
            "intent": "engineering-story-context-analysis",
            "idempotencyKey": idempotency_key,
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP task binding reservation failed") from error
    if not isinstance(result, dict):
        raise LiveConfigurationError("MCP task binding response must be an object")
    task_id = result.get("aiTaskId")
    if not isinstance(task_id, str) or not task_id:
        raise LiveConfigurationError("Core binding reservation did not return aiTaskId")
    try:
        UUID(task_id)
    except ValueError as error:
        raise LiveConfigurationError("Core binding reservation returned an invalid aiTaskId") from error
    return result


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
        result = _reserve_task_binding(mcp, config, os.getenv(
            "DEVLOG_LIVE_BINDING_IDEMPOTENCY_KEY", "q1-live-binding-prepare"
        ))
    finally:
        mcp.close()
    return {
        **result,
        "storyReference": {
            "projectSlug": config.project_slug,
            "storyNumber": config.story_number,
            "storyId": config.story_id,
            "storyPath": config.story_path,
        },
    }


def prepare_live_evaluation(
    *,
    manifest: Mapping[str, Any],
    oracle: Mapping[str, Any],
    repository_root: str | Path,
    output_root: str | Path,
    pricing_contract: Mapping[str, Any],
    environment: Mapping[str, str] | None = None,
    case_id: str | None = None,
) -> dict[str, Any]:
    """Request a Core preparation plan for one case batch without creating a task or provider client."""

    from .campaign import CampaignError, batch_observation_ids

    batch_case = case_id or manifest.get("caseId")
    try:
        batch_ids = batch_observation_ids(manifest, str(batch_case))
    except CampaignError as error:
        raise LiveConfigurationError(str(error)) from error

    root = Path(repository_root).resolve()
    values = os.environ if environment is None else environment
    idempotency_key = values.get("DEVLOG_LIVE_PREPARATION_IDEMPOTENCY_KEY", "").strip()
    if not idempotency_key:
        raise LiveConfigurationError(
            "LIVE_PREPARE requires DEVLOG_LIVE_PREPARATION_IDEMPOTENCY_KEY"
        )
    config = LiveConfiguration.from_environment(environment, require_task_id=False)
    identity = execution_identity(root, runtime_revision=current_head(root))
    namespace = values.get("DEVLOG_LIVE_ARTIFACT_NAMESPACE", f"{str(batch_case).lower()}/" + current_head(root)[:12]).strip()
    request = {
        "benchmarkId": "devlog-ai-abc",
        "benchmarkVersion": "1.0.0",
        "caseId": batch_case,
        "manifestId": manifest["manifestId"],
        "manifestVersion": manifest["version"],
        "manifestDigest": canonical_manifest_digest(manifest),
        "oracleStatus": oracle.get("status"),
        "oracleDigest": oracle.get("canonicalDigest"),
        "sourceRevision": manifest["repositoryRevision"],
        "runtimeRevision": identity["runtimeRevision"],
        "runtimeAttestation": {
            "runtimeRevision": identity["runtimeRevision"],
            "reproducible": identity["worktree"].get("reproducible") is True,
            # Core cannot verify the caller's worktree claim over MCP; this must
            # remain false until a trusted Core-side attestation exists.
            "coreVerified": False,
            "harnessSha256": identity["harness"].get("sha256"),
        },
        "runtimeIdentities": identity["runtimeIdentities"],
        "operationalLimits": manifest["technicalBudgets"],
        "provider": manifest["provider"],
        "model": manifest["model"],
        "pricingContract": dict(pricing_contract),
        "artifactNamespace": namespace,
        "observationIds": batch_ids,
    }
    try:
        mcp = McpClient(config.mcp_launcher, launcher=config.mcp_launcher,
                        read_timeout_seconds=config.mcp_read_timeout_seconds)
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP authenticated transport could not be started") from error
    try:
        result = mcp.tool("prepare_bounded_live_evaluation", {
            "projectSlug": config.project_slug,
            "idempotencyKey": idempotency_key,
            **request,
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP live evaluation preparation failed") from error
    finally:
        mcp.close()
    if not isinstance(result, dict):
        raise LiveConfigurationError("MCP live evaluation preparation response must be an object")
    return result


def require_b_live_environment(environment: Mapping[str, str] | None = None) -> None:
    values = os.environ if environment is None else environment
    if values.get("LLM_PROVIDER", "openai_chatgpt").strip().lower() != "openai_chatgpt":
        raise LiveConfigurationError("B live execution requires LLM_PROVIDER=openai_chatgpt")
    if values.get("CHATGPT_OAUTH_ENABLED", "false").strip().lower() != "true":
        raise LiveConfigurationError("B live execution requires CHATGPT_OAUTH_ENABLED=true")
    if values.get("DEVLOG_LIVE_REMOTE_PROVIDER", "false").strip().lower() != "true":
        raise LiveConfigurationError(
            "B live execution requires DEVLOG_LIVE_REMOTE_PROVIDER=true; "
            "the local binding store is not a live provider boundary"
        )


def live_adapters(*, repository_root: Path, manifest: Mapping[str, Any], config: LiveConfiguration,
                  mcp: McpClient, case: BenchmarkCase, case_story_id: str | None = None,
                  environment: Mapping[str, str] | None = None,
                  execution_scope: str | None = None) -> dict[str, TreatmentAdapter]:
    require_b_live_environment(environment)
    if config.b_task_id is None:
        raise LiveConfigurationError("B live execution requires a reserved credential-binding task")
    values = os.environ if environment is None else environment
    model = values.get("LLM_MODEL", manifest["model"]).strip()
    return {
        "A_DEVLOG_CURRENT": CoreLiveTreatmentAdapter("A_DEVLOG_CURRENT", config, manifest, mcp,
                                                     case=case, case_story_id=case_story_id,
                                                     execution_scope=execution_scope),
        "B_DIRECT_INVESTIGATOR": DirectLiveTreatmentAdapter(repository_root, config.b_task_id, model, case=case),
        "C_DEVLOG_ENGINEERING_ANALYST": CoreLiveTreatmentAdapter("C_DEVLOG_ENGINEERING_ANALYST", config, manifest, mcp,
                                                                 case=case, case_story_id=case_story_id,
                                                                 execution_scope=execution_scope),
    }


def load_live_case(repository_root: Path, manifest: Mapping[str, Any], case_id: str | None = None) -> BenchmarkCase:
    """Load the frozen benchmark case that every live condition of a batch must receive."""

    from .campaign import CampaignError, case_version

    case_id = case_id or manifest.get("caseId")
    if not isinstance(case_id, str) or not case_id:
        raise LiveConfigurationError("live execution requires a case identity")
    try:
        case = load_case(repository_root, case_id)
        expected_version = case_version(manifest, case_id)
    except (BenchmarkCaseError, CampaignError, KeyError) as error:
        raise LiveConfigurationError(f"benchmark case is not executable: {error}") from error
    if case.version != expected_version:
        raise LiveConfigurationError("benchmark case version does not match the manifest")
    return case


def resolve_case_story(case: BenchmarkCase, config: LiveConfiguration, mcp: McpClient) -> str | None:
    """Resolve the case Story through Core; a case without a Story binds none."""

    if case.story_number is None:
        return None
    try:
        result = mcp.tool("resolve_engineering_story", {
            "projectSlug": config.project_slug,
            "storyNumber": case.story_number,
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP case Story resolution failed") from error
    story_id = result.get("id") if isinstance(result, dict) else None
    if not isinstance(story_id, str) or result.get("storyNumber") != case.story_number:
        raise LiveConfigurationError("MCP case Story resolution returned an unexpected Story")
    try:
        UUID(story_id)
    except ValueError as error:
        raise LiveConfigurationError("MCP case Story resolution returned an invalid id") from error
    return story_id


FRESHNESS_BLOCKING_STATES = frozenset({"UNKNOWN", "NOT_ESTABLISHED", "NO_BASELINE"})


def require_known_freshness(case: BenchmarkCase, config: LiveConfiguration, mcp: McpClient,
                            story_id: str | None) -> dict[str, Any]:
    """Block a live run before authorization consumption when Core freshness is unknown.

    Freshness is read from the Core-owned projection, which creates no task and
    performs no provider call. The harness never derives freshness itself.
    """

    if story_id is None:
        raise LiveConfigurationError("Q1_FRESHNESS_GATE_UNAVAILABLE: no Story is available for the Core projection")
    try:
        projection = mcp.tool("story_context_agent_get_projection", {
            "projectSlug": config.project_slug,
            "storyId": story_id,
            "files": list(case.scope_files),
            "intent": "engineering-story-context-analysis",
        })
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("Q1_FRESHNESS_GATE_UNAVAILABLE: Core projection could not be read") from error
    freshness = projection.get("freshness") if isinstance(projection, dict) else None
    state = freshness.get("state") if isinstance(freshness, dict) else None
    if not isinstance(state, str) or state.upper() in FRESHNESS_BLOCKING_STATES:
        raise LiveConfigurationError(
            f"Q1_FRESHNESS_UNKNOWN: Core reports freshness state {state!r}; refresh the project baseline "
            "before live evaluation"
        )
    return {"state": state, "revisionConsistency": freshness.get("revisionConsistency")}


def run_live_execution(*, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, manifest: Mapping[str, Any], authorization: Mapping[str, Any], prepared_plan: Mapping[str, Any] | None = None, oracle: Mapping[str, Any] | None = None, pricing_contract: Mapping[str, Any] | None = None, environment: Mapping[str, str] | None = None, observation_ids: tuple[str, ...] | None = None):
    root = Path(repository_root).resolve()
    if prepared_plan is None or oracle is None:
        raise LiveConfigurationError("live execution requires an authoritative Core preparation plan")
    plan_id = prepared_plan.get("planId")
    expected_plan_digest = prepared_plan.get("planDigest")
    if not isinstance(plan_id, str) or not plan_id or not isinstance(expected_plan_digest, str):
        raise LiveConfigurationError("live execution requires a Core preparation plan identity")
    if authorization.get("planId") != plan_id or authorization.get("planDigest") != expected_plan_digest:
        raise LiveConfigurationError("live authorization is not bound to the Core preparation plan")
    if current_head(root) != authorization["runtimeRevision"]:
        raise LiveConfigurationError("live execution requires HEAD to equal the authorized runtime revision")
    try:
        assert_reproducible_worktree(root)
    except ValueError as error:
        raise LiveConfigurationError(str(error)) from error
    require_live_provider_environment(environment)
    config = LiveConfiguration.from_environment(environment, require_task_id=False)
    try:
        mcp = McpClient(config.mcp_launcher, launcher=config.mcp_launcher,
                        read_timeout_seconds=config.mcp_read_timeout_seconds)
    except (OSError, RuntimeError, TimeoutError) as error:
        raise LiveConfigurationError("MCP authenticated transport could not be started") from error
    try:
        authoritative_plan = mcp.tool("get_bounded_live_evaluation_preparation", {"planId": plan_id})
        if (
            not isinstance(authoritative_plan, dict)
            or authoritative_plan.get("planId") != plan_id
            or authoritative_plan.get("projectSlug") != config.project_slug
            or authoritative_plan.get("planDigest") != expected_plan_digest
        ):
            raise LiveConfigurationError("Core preparation plan changed or could not be re-read authoritatively")
        try:
            validate_prepared_plan(
                authoritative_plan,
                manifest=manifest,
                oracle=oracle,
                repository_root=root,
                authorization=authorization,
                pricing_contract=pricing_contract,
            )
        except PreparedPlanError as error:
            raise LiveConfigurationError(str(error)) from error
        config = resolve_live_story(config, mcp)
        require_b_live_environment(environment)
        # Core is authoritative for the batch case; the plan names it.
        plan_payload = authoritative_plan.get("plan") if isinstance(authoritative_plan.get("plan"), Mapping) else {}
        batch_case_id = plan_payload.get("caseId") or manifest.get("caseId")
        if observation_ids is not None and isinstance(plan_payload.get("observationIds"), list) \
                and list(observation_ids) != plan_payload["observationIds"]:
            raise LiveConfigurationError("live execution must run exactly the Core-authorized case batch")
        case = load_live_case(root, manifest, case_id=batch_case_id)
        case_story_id = resolve_case_story(case, config, mcp)
        require_known_freshness(case, config, mcp, case_story_id or config.story_id)
        authorization_id = authorization.get("authorizationId")
        if not isinstance(authorization_id, str) or not authorization_id.strip():
            raise LiveConfigurationError("live authorization requires an authorization identity")
        if config.b_task_id is None:
            # Bindings expire after minutes, so B gets a fresh one per authorization,
            # reserved before consumption so a reservation failure leaves the authorization unused.
            reservation = _reserve_task_binding(mcp, config, f"q1-live-binding-{authorization_id}")
            config = replace(config, b_task_id=UUID(reservation["aiTaskId"]))
        adapters = live_adapters(repository_root=root, manifest=manifest, config=config, mcp=mcp,
                                 case=case, case_story_id=case_story_id,
                                 environment=environment, execution_scope=authorization_id)
        execution_key = (environment or os.environ).get("DEVLOG_LIVE_EXECUTION_IDEMPOTENCY_KEY", "").strip()
        if not execution_key:
            execution_key = hashlib.sha256(json.dumps({
                "authorizationId": authorization_id,
                "planId": plan_id,
                "planDigest": expected_plan_digest,
                "manifestDigest": manifest.get("manifestDigest"),
                "observationIds": list(observation_ids) if observation_ids else [
                    item["observationId"] for item in manifest["observations"]
                ],
            }, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()
        try:
            consumed = mcp.tool("consume_bounded_live_evaluation_authorization", {
                "authorizationId": authorization_id,
                "planDigest": expected_plan_digest,
                "executionIdempotencyKey": execution_key,
            })
        except (RuntimeError, TimeoutError) as error:
            raise LiveConfigurationError(
                "live authorization consumption could not be confirmed; execution was not started"
            ) from error
        if not isinstance(consumed, Mapping) or consumed.get("status") != "CONSUMED":
            raise LiveConfigurationError("Core did not consume the live authorization")
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
