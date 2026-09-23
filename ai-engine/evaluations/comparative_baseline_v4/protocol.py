"""Deterministic V4 contracts and post-provider evaluation.

This module contains no provider, network, Git, or repository side effects.
V4 deliberately has its own assignment, execution, and accounting contracts;
the frozen V3 contracts are read only as benchmark authorities.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from enum import StrEnum
from pathlib import Path
from typing import Any

from evaluations.comparative_baseline.collection_runtime import (
    Assignment,
    ProviderResponse,
    assert_secret_free,
    validate_answer,
)
from evaluations.comparative_baseline.infrastructure import (
    assignment_matrix,
    assert_no_oracle_leakage,
    canonical,
    load_manifest,
    raw_output_hash,
    write_immutable_json,
)

PACKAGE = Path(__file__).resolve().parent
ROOT = PACKAGE.parents[2]
V4_MANIFEST_PATH = PACKAGE / "v4-manifest.json"
ORACLE_PATH = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
BENCHMARK_PATH = ROOT / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"

V4_IDENTITIES = {
    "manifest": "comparative-baseline-v4-3.0.0",
    "answer": "comparative-v4-common-answer-1.0.0",
    "grounding": "comparative-v4-deterministic-grounding-1.0.0",
    "semantic": "comparative-v4-semantic-diagnostic-1.0.0",
    "resources": "comparative-v4-resource-accounting-4.0.0",
    "safety": "comparative-v4-safety-ceilings-2.0.0",
    "execution": "comparative-v4-execution-config-2.0.0",
    "instrumentation": "comparative-v4-instrumentation-4.0.0",
    "raw": "comparative-v4-raw-observation-4.0.0",
    "projection": "comparative-v4-deterministic-projection-4.0.0",
    "runtime": "comparative-v4-live-runtime-contract-5.1.0",
}
V4_RUNTIME_CONTRACT = {
    "identityVersion": V4_IDENTITIES["runtime"],
    "providerResponseNormalization": "comparative-v4-provider-response-normalization-5.0.0",
    "toolLifecycle": "comparative-v4-tool-lifecycle-4.0.0",
    "typedRepositoryToolSchema": "comparative-v4-typed-repository-tools-2.0.0",
    "ceilingEnforcement": "comparative-v4-tool-ceiling-order-3.0.0",
    "naturalCompletion": "comparative-v4-natural-completion-1.0.0",
    "runawayGuard": "NONE_EXPERIMENT_DEFINED",
    "recovery": "comparative-v4-natural-model-recovery-1.0.0",
    "conversationPropagation": "comparative-v4-conversation-propagation-1.0.0",
    "executionStatus": "comparative-v4-execution-status-1.0.0",
    "providerAccounting": "comparative-v4-provider-transport-accounting-1.0.0",
}
V4_CONDITIONS = ("DEVLOG", "AGENT_DIRECT_OPEN")
V4_TOOL_OPERATIONS = ("read_file", "search_repository", "git_log", "git_show", "git_diff", "inspect_commit")
# This matrix mirrors PinnedGitRepositoryTools.execute. Optional values are
# nullable in the provider schema because strict schemas require every key.
V4_TOOL_OPERATION_MATRIX = {
    "read_file": {"required": {"path": "string"}, "optional": {"startLine": "integer", "endLine": "integer"}},
    "search_repository": {"required": {"query": "string"}, "optional": {"paths": "string[]", "maxMatches": "integer"}},
    "git_log": {"required": {}, "optional": {"commit": "string"}},
    "git_show": {"required": {}, "optional": {"commit": "string", "path": "string"}},
    "git_diff": {"required": {}, "optional": {"commit": "string", "parent": "string"}},
    "inspect_commit": {"required": {}, "optional": {"commit": "string"}},
}
# These constraints are limited to deterministic argument checks already made
# by the pinned repository adapter. They are shared by schema construction and
# runtime validation so a provider-valid request cannot fail the local contract.
V4_TOOL_FIELD_CONSTRAINTS = {
    "read_file": {
        "path": {"minLength": 1, "pattern": r"^(?!/)(?!.*\.\.)[^\x00]+$"},
        "startLine": {"minimum": 1},
        "endLine": {"minimum": 1},
    },
    "search_repository": {
        "query": {"minLength": 1},
        "paths": {"items": {"minLength": 1, "pattern": r"^(?!/)(?!.*\.\.)[^\x00]+$"}},
        "maxMatches": {"minimum": 1},
    },
    "git_log": {"commit": {"pattern": r"^[0-9a-f]{40}$"}},
    "git_show": {
        "commit": {"pattern": r"^[0-9a-f]{40}$"},
        "path": {"minLength": 1, "pattern": r"^(?!/)(?!.*\.\.)[^\x00]+$"},
    },
    "git_diff": {
        "commit": {"pattern": r"^[0-9a-f]{40}$"},
        "parent": {"pattern": r"^[0-9a-f]{40}$"},
    },
    "inspect_commit": {"commit": {"pattern": r"^[0-9a-f]{40}$"}},
}
V4_ARTIFACT_VERSION = "comparative-v4-raw-observation-4.0.0"
V4_PILOT_REPETITION_COUNT = 1


class ExecutionStatus(StrEnum):
    COMPLETED = "COMPLETED"
    CENSORED = "CENSORED"
    CENSORED_RUNAWAY = "CENSORED_RUNAWAY"
    MODEL_FAILURE = "MODEL_FAILURE"
    PROVIDER_FAILURE = "PROVIDER_FAILURE"
    PROVIDER_TIMEOUT = "PROVIDER_TIMEOUT"
    TOOL_TIMEOUT = "TOOL_TIMEOUT"
    RUNTIME_FAILURE = "RUNTIME_FAILURE"


class SafetyCeilings:
    """Marker for the absence of experiment-defined DIRECT resource ceilings."""

    def __init__(
        self,
        max_tool_operations: int | None = None,
        max_model_turns: int | None = None,
        max_wall_clock_seconds: int | None = None,
        max_provider_calls_per_observation: int | None = None,
        max_delivered_result_bytes: int | None = None,
        *,
        max_read_bytes_per_operation: int | None = None,
    ) -> None:
        supplied = (max_tool_operations, max_model_turns, max_wall_clock_seconds,
                    max_provider_calls_per_observation, max_delivered_result_bytes,
                    max_read_bytes_per_operation)
        if any(value is not None for value in supplied):
            raise ValueError("DIRECT has no experiment-defined resource ceilings")
        self.max_tool_operations = None
        self.max_model_turns = None
        self.max_wall_clock_seconds = None
        self.max_provider_calls_per_observation = None
        self.max_delivered_result_bytes = None

    @property
    def max_read_bytes_per_operation(self) -> int | None:
        """Deprecated spelling retained only for fixture migration."""
        return self.max_delivered_result_bytes

    @property
    def approved(self) -> bool:
        return True

    def require_approved(self) -> None:
        return None

    def as_dict(self) -> dict[str, Any]:
        return {
            "maxToolOperations": self.max_tool_operations,
            "maxModelTurns": self.max_model_turns,
            "maxWallClockSeconds": self.max_wall_clock_seconds,
            "maxProviderCallsPerObservation": self.max_provider_calls_per_observation,
            "maxDeliveredResultBytes": self.max_delivered_result_bytes,
        }

    @classmethod
    def from_manifest(cls, manifest: dict[str, Any]) -> "SafetyCeilings":
        values = manifest["safetyCeilings"]
        return cls(values.get("maxToolOperations"), values.get("maxModelTurns"), values.get("maxWallClockSeconds"), values.get("maxProviderCallsPerObservation"), values.get("maxReadBytesPerOperation"))


@dataclass(frozen=True)
class V4Assignment:
    assignment_id: str
    question_id: str
    question_version: str
    case_id: str
    condition: str
    repetition: int

    def __post_init__(self) -> None:
        if self.condition not in V4_CONDITIONS:
            raise ValueError(f"invalid V4 condition: {self.condition}")

    @classmethod
    def from_row(cls, row: dict[str, Any]) -> "V4Assignment":
        condition = "AGENT_DIRECT_OPEN" if row["condition"] == "AGENT_DIRECT" else row["condition"]
        return cls(row["assignmentId"].replace(":AGENT_DIRECT:", ":AGENT_DIRECT_OPEN:"), row["questionId"], row["questionVersion"], row["caseId"], condition, row["repetition"])

    def identity(self) -> dict[str, Any]:
        return {
            "assignmentId": self.assignment_id,
            "questionId": self.question_id,
            "questionVersion": self.question_version,
            "caseId": self.case_id,
            "condition": self.condition,
            "repetition": self.repetition,
        }


def v4_assignment_matrix() -> list[dict[str, Any]]:
    return [V4Assignment.from_row(row).identity() for row in assignment_matrix(load_manifest())]


def v4_pilot_assignment_matrix() -> list[dict[str, Any]]:
    """Return the separate one-repetition pilot plan, never the official plan."""
    return [row for row in v4_assignment_matrix() if row["repetition"] == V4_PILOT_REPETITION_COUNT]


def validate_v4_plan(rows: list[dict[str, Any]]) -> None:
    expected = {row["assignmentId"]: row for row in v4_assignment_matrix()}
    actual = [row["assignmentId"] for row in rows]
    if len(actual) != len(set(actual)):
        raise ValueError("V4 assignment plan contains duplicate slots")
    for row in rows:
        if row["assignmentId"] not in expected or row != expected[row["assignmentId"]]:
            raise ValueError(f"assignment is not a frozen V4 experimental cell: {row.get('assignmentId')}")


def validate_v4_pilot_plan(rows: list[dict[str, Any]]) -> None:
    expected = v4_pilot_assignment_matrix()
    if rows != expected:
        raise ValueError("V4 pilot plan is not the frozen six-slot plan")
    if len(rows) != 6 or len({row["assignmentId"] for row in rows}) != 6:
        raise ValueError("V4 pilot plan must contain six unique cells")


def v4_pilot_identity(*, safety: SafetyCeilings | None = None) -> dict[str, Any]:
    payload = {
        "identityVersion": "comparative-v4-pilot-plan-identity-1.0.0",
        "experimentIdentity": v4_experiment_identity(safety=safety)["sha256"],
        "assignmentPlan": v4_pilot_assignment_matrix(),
        "repetitionCount": V4_PILOT_REPETITION_COUNT,
    }
    projection = {
        "identityVersion": payload["identityVersion"],
        "sha256": hashlib.sha256(canonical(payload).encode("utf-8")).hexdigest(),
        "inputs": payload,
    }
    return projection


def v4_official_plan_identity(*, safety: SafetyCeilings | None = None) -> dict[str, Any]:
    payload = {
        "identityVersion": "comparative-v4-official-plan-identity-1.0.0",
        "experimentIdentity": v4_experiment_identity(safety=safety)["sha256"],
        "assignmentPlan": v4_assignment_matrix(),
        "repetitionCount": 3,
    }
    return {
        "identityVersion": payload["identityVersion"],
        "sha256": hashlib.sha256(canonical(payload).encode("utf-8")).hexdigest(),
        "inputs": payload,
    }


def v4_runtime_contract_identity() -> dict[str, Any]:
    payload = {"identityVersion": V4_RUNTIME_CONTRACT["identityVersion"], "components": dict(V4_RUNTIME_CONTRACT)}
    return {
        "identityVersion": payload["identityVersion"],
        "sha256": hashlib.sha256(canonical(payload).encode("utf-8")).hexdigest(),
        "inputs": payload,
    }


def v4_experiment_identity(*, safety: SafetyCeilings | None = None) -> dict[str, Any]:
    manifest = _load_v4_manifest()
    base = load_manifest()
    configured_safety = manifest["safetyCeilings"]
    supplied_safety = ({key: getattr(safety, field) for key, field in (("maxToolOperations", "max_tool_operations"), ("maxModelTurns", "max_model_turns"), ("maxWallClockSeconds", "max_wall_clock_seconds"), ("maxProviderCallsPerObservation", "max_provider_calls_per_observation"), ("maxReadBytesPerOperation", "max_delivered_result_bytes"))} if safety else {key: configured_safety[key] for key in ("maxToolOperations", "maxModelTurns", "maxWallClockSeconds", "maxProviderCallsPerObservation", "maxReadBytesPerOperation")})
    manifest_safety = {key: configured_safety[key] for key in ("maxToolOperations", "maxModelTurns", "maxWallClockSeconds", "maxProviderCallsPerObservation", "maxReadBytesPerOperation")}
    if supplied_safety != manifest_safety:
        raise ValueError("V4 safety ceilings do not match the frozen manifest")
    payload = {
        "protocol": V4_IDENTITIES["manifest"],
        "manifest": manifest["manifestVersion"],
        "benchmarkManifestSha256": manifest["benchmarkManifestSha256"],
        "questionSetSha256": manifest["questionSetSha256"],
        "questions": [{"questionId": item["questionId"], "questionVersion": item["questionVersion"], "question": item["question"]} for item in base["questions"]],
        "oracleSha256": manifest["oracleSha256"],
        "repositoryRevision": manifest["repositoryRevision"],
        "conditions": manifest["conditions"],
        "provider": manifest["provider"],
        "model": manifest["model"],
        "executionBudgetPolicy": manifest["executionBudgetPolicy"],
        "executionConfiguration": manifest["executionConfiguration"],
        "recoveryPolicy": manifest["toolRecoveryPolicy"],
        "groundingContract": V4_IDENTITIES["grounding"],
        "semanticContract": V4_IDENTITIES["semantic"],
        "resourceContract": V4_IDENTITIES["resources"],
        "rawSchema": V4_IDENTITIES["raw"],
        "projectionContract": V4_IDENTITIES["projection"],
        "instrumentation": V4_IDENTITIES["instrumentation"],
        "runtimeContract": v4_runtime_contract_identity(),
        "assignmentPlan": [row["assignmentId"] for row in v4_assignment_matrix()],
        "repetitionCount": 3,
        "safety": supplied_safety,
    }
    return {"identityVersion": "comparative-v4-experiment-identity-2.0.0", "sha256": hashlib.sha256(canonical(payload).encode("utf-8")).hexdigest(), "inputs": payload}


@dataclass(frozen=True)
class EvidenceItem:
    reference: str
    content: str
    source_type: str = "TOOL_RESULT"
    locator_contract_version: str = "comparative-v4-deterministic-grounding-1.0.0"
    provider_visible_source_identity: str = "AGENT_DIRECT_TOOL_RESULT"
    authorized: bool = True
    resolved: bool = True
    content_sha256: str | None = None

    @property
    def byte_length(self) -> int:
        return len(self.content.encode("utf-8"))

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.content.encode("utf-8")).hexdigest()

    @property
    def digest_valid(self) -> bool:
        return self.content_sha256 in (None, self.sha256)


@dataclass
class ProviderAttempt:
    """Bounded, provider-agnostic facts about one provider boundary invocation."""

    turn_index: int
    transport_attempted: bool = False
    provider_response_received: bool = False
    usable_provider_response: bool = False
    response_bearing_model_turn: bool = False
    usage: dict[str, Any] = field(default_factory=dict)
    diagnostics: dict[str, Any] = field(default_factory=dict)
    transport_latency_ms: int | str = "NOT_MEASURED"

    def record_transport(self) -> None:
        self.transport_attempted = True

    def record_response(self, usage: dict[str, Any] | None, diagnostics: dict[str, Any] | None) -> None:
        self.provider_response_received = True
        supplied = usage or {}
        self.usage = {
            key: value if isinstance(value := supplied.get(key), int) and not isinstance(value, bool) else "NOT_MEASURED"
            for key in ("inputTokens", "outputTokens", "totalTokens")
        }
        self.diagnostics = dict(diagnostics or {})
        assert_secret_free(self.usage)
        assert_secret_free(self.diagnostics)

    def as_raw(self) -> dict[str, Any]:
        return {
            "turn": self.turn_index,
            "transportAttempted": self.transport_attempted,
            "providerResponseReceived": self.provider_response_received,
            "usableProviderResponse": self.usable_provider_response,
            "responseBearingModelTurn": self.response_bearing_model_turn,
            "usage": dict(self.usage),
            "providerResponseDiagnostics": dict(self.diagnostics),
            "transportLatencyMs": self.transport_latency_ms,
        }


@dataclass
class ResourceAccounting:
    model_turns: int = 0
    total_provider_calls: int = 0
    provider_transport_attempts: int = 0
    provider_responses_received: int = 0
    usable_provider_responses: int = 0
    response_bearing_model_turns: int = 0
    provider_attempts: list[ProviderAttempt] = field(default_factory=list)
    navigation_provider_calls: int = 0
    final_answer_provider_calls: int = 0
    technical_provider_retries: int = 0
    provider_timeouts: int = 0
    tool_timeouts: int = 0
    tool_execution_failures: int = 0
    tool_calls: int = 0
    tool_attempts: int = 0
    executed_tool_operations: int = 0
    invalid_tool_requests: int = 0
    skipped_tool_operations: int = 0
    ceiling_counted_operations: int = 0
    valid_tool_calls: int = 0
    invalid_tool_calls: int = 0
    repository_searches: int = 0
    repository_reads: int = 0
    serialized_result_bytes: int = 0
    serialized_request_bytes: int = 0
    bytes_delivered_to_model: int = 0
    bytes_rejected: int = 0
    cumulative_repository_bytes_delivered: int = 0
    largest_single_result_bytes_produced: int = 0
    largest_single_result_bytes_delivered: int = 0
    input_tokens: int | str = "NOT_MEASURED"
    output_tokens: int | str = "NOT_MEASURED"
    total_tokens: int | str = "NOT_MEASURED"
    assignment_latency_ms: int | str = "NOT_MEASURED"
    provider_latency_ms: int | str = "NOT_MEASURED"
    provider_transport_latency_ms: int | str = "NOT_MEASURED"
    cost: Any = "NOT_AVAILABLE"
    stopping_reason: str = "NOT_STOPPED"

    def begin_provider_attempt(self, turn_index: int) -> ProviderAttempt:
        attempt = ProviderAttempt(turn_index=turn_index)
        self.provider_attempts.append(attempt)
        return attempt

    def record_transport_attempt(self, attempt: ProviderAttempt) -> None:
        if not attempt.transport_attempted:
            attempt.record_transport()
            self.provider_transport_attempts += 1

    def record_provider_response(self, attempt: ProviderAttempt, usage: dict[str, Any] | None, diagnostics: dict[str, Any] | None) -> None:
        if not attempt.provider_response_received:
            attempt.record_response(usage, diagnostics)
            self.provider_responses_received += 1
            self._record_usage(attempt.usage)

    def record_attempt_latency(self, attempt: ProviderAttempt, latency_ms: int) -> None:
        attempt.transport_latency_ms = latency_ms
        self.provider_transport_latency_ms = latency_ms if self.provider_transport_latency_ms == "NOT_MEASURED" else self.provider_transport_latency_ms + latency_ms

    def _record_usage(self, usage: dict[str, Any]) -> None:
        for key, field_name in (("inputTokens", "input_tokens"), ("outputTokens", "output_tokens"), ("totalTokens", "total_tokens")):
            value = usage.get(key)
            if isinstance(value, int):
                current = getattr(self, field_name)
                setattr(self, field_name, value if not isinstance(current, int) else current + value)

    def record_usable_provider(self, attempt: ProviderAttempt, response: ProviderResponse, *, final: bool, latency_ms: int) -> None:
        self.record_provider_response(attempt, response.usage, {"kind": response.kind, "finishReason": response.finish_reason})
        if not attempt.usable_provider_response:
            attempt.usable_provider_response = True
            attempt.response_bearing_model_turn = True
            self.usable_provider_responses += 1
            self.response_bearing_model_turns += 1
            self.model_turns += 1
            self.total_provider_calls += 1
            if final:
                self.final_answer_provider_calls += 1
            else:
                self.navigation_provider_calls += 1
            self.provider_latency_ms = latency_ms if self.provider_latency_ms == "NOT_MEASURED" else self.provider_latency_ms + latency_ms

    def record_provider(self, response: ProviderResponse, *, final: bool, latency_ms: int) -> None:
        attempt = self.begin_provider_attempt(self.model_turns + 1)
        self.record_transport_attempt(attempt)
        self.record_usable_provider(attempt, response, final=final, latency_ms=latency_ms)
        self.record_attempt_latency(attempt, latency_ms)

    def record_tool(self, operation: str | None, *, valid: bool, executed: bool, skipped: bool = False, result_bytes: int = 0, delivered_bytes: int = 0, rejected_bytes: int = 0) -> None:
        self.tool_calls += 1
        self.tool_attempts += 1
        self.serialized_result_bytes += result_bytes
        self.bytes_delivered_to_model += delivered_bytes
        self.bytes_rejected += rejected_bytes
        self.cumulative_repository_bytes_delivered += delivered_bytes
        self.largest_single_result_bytes_produced = max(self.largest_single_result_bytes_produced, result_bytes)
        self.largest_single_result_bytes_delivered = max(self.largest_single_result_bytes_delivered, delivered_bytes)
        if skipped:
            self.skipped_tool_operations += 1
        elif valid and executed:
            self.executed_tool_operations += 1
            self.ceiling_counted_operations += 1
            self.valid_tool_calls += 1
            if operation == "search_repository":
                self.repository_searches += 1
            if operation == "read_file":
                self.repository_reads += 1
        elif not valid:
            self.invalid_tool_calls += 1
            self.invalid_tool_requests += 1
            if rejected_bytes == 0 and result_bytes == 0:
                self.ceiling_counted_operations += 1

    def record_request(self, request_bytes: int) -> None:
        self.serialized_request_bytes += request_bytes

    def stop(self, reason: str) -> None:
        if self.stopping_reason == "NOT_STOPPED":
            self.stopping_reason = reason

    def as_dict(self) -> dict[str, Any]:
        return {
            "resourceAccountingVersion": V4_IDENTITIES["resources"],
            "modelTurns": self.model_turns,
            "totalProviderCalls": self.total_provider_calls,
            "providerTransportAttempts": self.provider_transport_attempts,
            "providerResponsesReceived": self.provider_responses_received,
            "usableProviderResponses": self.usable_provider_responses,
            "responseBearingModelTurns": self.response_bearing_model_turns,
            "navigationProviderCalls": self.navigation_provider_calls,
            "finalAnswerProviderCalls": self.final_answer_provider_calls,
            "technicalProviderRetries": self.technical_provider_retries,
            "providerTimeouts": self.provider_timeouts,
            "toolTimeouts": self.tool_timeouts,
            "toolExecutionFailures": self.tool_execution_failures,
            "toolCalls": self.tool_calls,
            "toolAttempts": self.tool_attempts,
            "executedToolOperations": self.executed_tool_operations,
            "invalidToolRequests": self.invalid_tool_requests,
            "skippedToolOperations": self.skipped_tool_operations,
            "ceilingCountedOperations": self.ceiling_counted_operations,
            "validToolCalls": self.valid_tool_calls,
            "invalidToolCalls": self.invalid_tool_calls,
            "repositorySearches": self.repository_searches,
            "repositoryReads": self.repository_reads,
            "serializedResultBytes": self.serialized_result_bytes,
            "serializedRequestBytes": self.serialized_request_bytes,
            "bytesDeliveredToModel": self.bytes_delivered_to_model,
            "bytesRejected": self.bytes_rejected,
            "cumulativeRepositoryBytesDelivered": self.cumulative_repository_bytes_delivered,
            "resultBytesProduced": self.serialized_result_bytes,
            "resultBytesDelivered": self.bytes_delivered_to_model,
            "resultBytesRejected": self.bytes_rejected,
            "largestSingleResultBytesProduced": self.largest_single_result_bytes_produced,
            "largestSingleResultBytesDelivered": self.largest_single_result_bytes_delivered,
            "inputTokens": self.input_tokens,
            "outputTokens": self.output_tokens,
            "totalTokens": self.total_tokens,
            "assignmentLatencyMs": self.assignment_latency_ms,
            "providerLatencyMs": self.provider_latency_ms,
            "providerTransportLatencyMs": self.provider_transport_latency_ms,
            "cost": self.cost,
            "stoppingReason": self.stopping_reason,
        }

    def provider_attempts_as_raw(self) -> list[dict[str, Any]]:
        return [attempt.as_raw() for attempt in self.provider_attempts]


def accounting_from_tool_trace(tool_trace: list[dict[str, Any]]) -> dict[str, int]:
    """Reconcile corrected counters from immutable lifecycle trace entries."""
    executed = [item for item in tool_trace if str(item.get("executionStatus", "")).startswith("EXECUTED_")]
    invalid = [item for item in tool_trace if item.get("executionStatus") == "NOT_EXECUTED_INVALID_REQUEST"]
    skipped = [item for item in tool_trace if item.get("executionStatus") == "NOT_EXECUTED_SAFETY_CEILING"]
    return {
        "toolAttempts": len(tool_trace),
        "executedToolOperations": len(executed),
        "invalidToolRequests": len(invalid),
        "skippedToolOperations": len(skipped),
        "ceilingCountedOperations": len(executed) + len(invalid),
        "resultBytesProduced": sum(int(item.get("resultByteCount") or 0) for item in tool_trace),
        "resultBytesDelivered": sum(int(item.get("resultByteCount") or 0) for item in tool_trace if item.get("delivered") is True),
        "resultBytesRejected": sum(int(item.get("resultByteCount") or 0) for item in tool_trace if item.get("executionStatus") == "EXECUTED_REJECTED_RESULT"),
        "largestSingleResultBytesProduced": max((int(item.get("resultByteCount") or 0) for item in tool_trace), default=0),
        "largestSingleResultBytesDelivered": max((int(item.get("resultByteCount") or 0) for item in tool_trace if item.get("delivered") is True), default=0),
    }


def accounting_from_provider_attempts(attempts: list[dict[str, Any]]) -> dict[str, int]:
    """Reconcile provider lifecycle counters from immutable RAW attempt facts."""
    return {
        "providerTransportAttempts": sum(bool(item.get("transportAttempted")) for item in attempts),
        "providerResponsesReceived": sum(bool(item.get("providerResponseReceived")) for item in attempts),
        "usableProviderResponses": sum(bool(item.get("usableProviderResponse")) for item in attempts),
        "responseBearingModelTurns": sum(bool(item.get("responseBearingModelTurn")) for item in attempts),
    }


def reconstruct_historical_accounting(observation: dict[str, Any]) -> dict[str, Any]:
    """Project corrected accounting from a V4 1.x RAW without mutation."""
    trace = observation.get("rawOutput", {}).get("toolTrace", []) if isinstance(observation.get("rawOutput"), dict) else []
    result = accounting_from_tool_trace(trace)
    result["reconstructability"] = {
        **{key: "EXACT" for key in result if key != "reconstructability"},
        "bytesRequested": "NOT_RECONSTRUCTABLE",
        "providerFailureCause": "EXACT" if observation.get("providerFailure") else "NOT_RECONSTRUCTABLE",
    }
    return result


def _load_v4_manifest() -> dict[str, Any]:
    return json.loads(V4_MANIFEST_PATH.read_text(encoding="utf-8"))


def v4_execution_configuration() -> dict[str, Any]:
    return dict(_load_v4_manifest()["executionConfiguration"])


def _load_benchmark() -> dict[str, Any]:
    return json.loads(BENCHMARK_PATH.read_text(encoding="utf-8"))


def _load_oracle() -> dict[str, Any]:
    return json.loads(ORACLE_PATH.read_text(encoding="utf-8"))


def _file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _question(question_id: str) -> dict[str, Any]:
    try:
        return next(item for item in load_manifest()["questions"] if item["questionId"] == question_id)
    except StopIteration as error:
        raise ValueError(f"unknown V4 question: {question_id}") from error


def condition_input(condition: str, question_id: str, repetition: int) -> dict[str, Any]:
    if condition not in V4_CONDITIONS or repetition not in (1, 2, 3):
        raise ValueError("invalid V4 condition or repetition")
    question = _question(question_id)
    result: dict[str, Any] = {
        "benchmarkVersion": load_manifest()["benchmarkVersion"],
        "questionId": question_id,
        "questionVersion": question["questionVersion"],
        "question": question["question"],
        "condition": condition,
        "repetition": repetition,
        "repositoryId": load_manifest()["repositoryId"],
        "repositoryRevision": load_manifest()["repositoryRevision"],
    }
    if condition == "DEVLOG":
        result["contextStrategy"] = "FROZEN_DEVLOG_EVALUATION_CONTEXT_PROJECTION"
    else:
        result["accessMode"] = "READ_ONLY_PINNED_REPOSITORY_OPEN_SAFETY_CEILINGS"
        result["allowedOperations"] = list(V4_TOOL_OPERATIONS)
    assert_no_oracle_leakage(result)
    return result


def validate_provider_payload(condition: str, payload: dict[str, Any]) -> None:
    """Check serialized condition boundaries without scanning legitimate repository data."""
    if not isinstance(payload, dict) or payload.get("condition") != condition:
        raise ValueError("provider payload condition identity mismatch")
    if condition == "AGENT_DIRECT_OPEN":
        forbidden_keys = {"contextStrategy", "providerVisibleEvidence", "expectedEvidence", "expectedOutcome", "oracle", "semanticCorrect"}
        if forbidden_keys.intersection(payload):
            raise ValueError("direct provider payload contains contamination material")
        conversation = payload.get("conversation")
        if not isinstance(conversation, list) or any(not isinstance(item, dict) or item.get("role") not in {"user", "assistant", "tool"} for item in conversation):
            raise ValueError("direct provider payload conversation is invalid")
    elif condition == "DEVLOG":
        if any(key in payload for key in ("allowedOperations", "toolSchema", "conversation")):
            raise ValueError("DEVLOG provider payload contains direct-agent material")


def validate_v4_identity() -> dict[str, Any]:
    manifest = _load_v4_manifest()
    base = load_manifest()
    oracle = _load_oracle()
    if manifest["manifestVersion"] != V4_IDENTITIES["manifest"]:
        raise ValueError("V4 manifest identity mismatch")
    if manifest["benchmarkVersion"] != base["benchmarkVersion"] or manifest["repositoryId"] != base["repositoryId"] or manifest["repositoryRevision"] != base["repositoryRevision"]:
        raise ValueError("V4 benchmark/repository identity differs from frozen benchmark")
    if oracle["oracleStatus"] != "HUMAN_APPROVED" or oracle["oracleVersion"] != manifest["oracleVersion"]:
        raise ValueError("V4 oracle identity is not the approved frozen oracle")
    expected_question_hash = hashlib.sha256(canonical([{"questionId": item["questionId"], "questionVersion": item["questionVersion"], "question": item["question"]} for item in base["questions"]]).encode("utf-8")).hexdigest()
    if manifest.get("benchmarkManifestSha256") != _file_sha256(PACKAGE.parent / "comparative_baseline" / "baseline-manifest.json") or manifest.get("oracleSha256") != _file_sha256(ORACLE_PATH) or manifest.get("questionSetSha256") != expected_question_hash:
        raise ValueError("V4 frozen benchmark/question/oracle hash mismatch")
    if tuple(manifest["conditions"]) != V4_CONDITIONS or manifest["toolRecoveryPolicy"] != "NATURAL_MODEL_RECOVERY" or manifest.get("executionBudgetPolicy") != "NATURAL_COMPLETION_WITHOUT_EXPERIMENT_LIMITS":
        raise ValueError("V4 condition or recovery identity is not frozen")
    if any(manifest.get(key) != V4_IDENTITIES[value] for key, value in (("resourceAccountingVersion", "resources"), ("instrumentationVersion", "instrumentation"), ("rawSchemaVersion", "raw"), ("projectionVersion", "projection"))):
        raise ValueError("V4 instrumentation identity is not frozen")
    runtime_contract = v4_runtime_contract_identity()
    if manifest.get("runtimeContractVersion") != runtime_contract["identityVersion"] or manifest.get("runtimeContractDigest") != runtime_contract["sha256"]:
        raise ValueError("V4 runtime contract identity is not frozen")
    if len(v4_assignment_matrix()) != 18:
        raise ValueError("V4 assignment matrix is not the frozen 18-slot matrix")
    execution = manifest.get("executionConfiguration", {})
    execution_identity = {key: value for key, value in execution.items() if key != "sha256"}
    expected_execution_hash = hashlib.sha256(canonical(execution_identity).encode("utf-8")).hexdigest()
    if execution.get("version") != V4_IDENTITIES["execution"] or execution.get("finalOutputTokens") != 32768 or execution.get("intermediateOutputTokens") != 32768 or execution.get("sha256") != expected_execution_hash:
        raise ValueError("V4 execution configuration identity is not frozen")
    safety = manifest["safetyCeilings"]
    if safety.get("status") != "NO_EXPERIMENT_LIMITS" or any(value is not None for key, value in safety.items() if key != "status"):
        raise ValueError("V4 DIRECT resource ceilings are not unbounded")
    configured_safety = execution.get("safetyCeilings")
    expected_safety = {key: None for key in ("maxToolOperations", "maxModelTurns", "maxWallClockSeconds", "maxProviderCallsPerObservation", "maxReadBytesPerOperation")}
    if configured_safety != expected_safety:
        raise ValueError("V4 execution configuration safety ceilings do not match manifest")
    expected_native = {
        "nativeProviderTimeoutSeconds": 30,
        "nativeProviderConnectTimeoutSeconds": 5,
        "nativeProviderPoolTimeoutSeconds": 5,
        "nativeRepositoryTimeoutSeconds": 15,
        "nativeGroundingBridgeTimeoutSeconds": 60,
        "providerMaxRetries": 0,
    }
    if {key: execution.get(key) for key in expected_native} != expected_native:
        raise ValueError("V4 native timeout or retry configuration is not frozen")
    return manifest


def _line_count(content: str) -> int:
    return len(content.splitlines())


def _resolve_locator(citation: dict[str, Any], source: EvidenceItem) -> tuple[bool, str, str]:
    locator = citation.get("locator") or {}
    kind = locator.get("kind")
    if kind == "LINE_RANGE":
        start, end = locator.get("startLine"), locator.get("endLine")
        if not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start:
            return False, "LOCATOR_INVALID", ""
        if end > _line_count(source.content):
            return False, "LOCATOR_OUT_OF_RANGE", ""
        return True, "LINE_RANGE_RESOLVED", "\n".join(source.content.splitlines()[start - 1:end])
    if kind == "SECTION":
        heading = locator.get("heading")
        if not isinstance(heading, str) or not heading:
            return False, "LOCATOR_INVALID", ""
        return (heading in source.content, "SECTION_RESOLVED" if heading in source.content else "LOCATOR_UNRESOLVED", source.content)
    if kind == "COMMIT_HUNK":
        if not all(isinstance(locator.get(key), str) and locator[key] for key in ("commit", "path", "header")):
            return False, "LOCATOR_INVALID", ""
        expected = f"{locator['commit']}:{locator['path']}"
        if source.reference not in {expected, locator["path"], f"commit:{expected}"}:
            return False, "LOCATOR_UNRESOLVED", ""
        return (locator["header"] in source.content, "COMMIT_HUNK_RESOLVED" if locator["header"] in source.content else "LOCATOR_UNRESOLVED", source.content)
    return False, "LOCATOR_INVALID", ""


def _authorized_references(question_id: str) -> set[str]:
    return set(_question(question_id).get("authorizedEvidence", []))


def evaluate_grounding(answer: dict[str, Any], evidence: list[EvidenceItem | dict[str, Any]], question_id: str | None = None) -> dict[str, Any]:
    normalized = [item if isinstance(item, EvidenceItem) else EvidenceItem(item["reference"], item.get("content", ""), authorized=item.get("authorized", True), resolved=item.get("resolved", True), content_sha256=item.get("contentSha256")) for item in evidence]
    by_reference = {item.reference: item for item in normalized}
    authorized = _authorized_references(question_id) if question_id else None
    resolutions: list[dict[str, Any]] = []
    valid = True
    for citation in answer.get("evidence", []):
        reference = citation.get("reference")
        source = by_reference.get(reference)
        authorization_ok = source is not None and source.authorized and (authorized is None or reference in authorized)
        resolution_ok = source is not None and source.resolved
        if source is None:
            resolutions.append({"reference": reference, "authorization": "NOT_AUTHORIZED", "resolution": "UNRESOLVED", "status": "FAIL"})
            valid = False
            continue
        locator_ok, locator_status, resolved_content = _resolve_locator(citation, source)
        excerpt = citation.get("excerpt")
        exact = isinstance(excerpt, str) and bool(excerpt) and excerpt in resolved_content
        digest_ok = source.digest_valid
        item_valid = authorization_ok and resolution_ok and locator_ok and exact and digest_ok
        valid = valid and item_valid
        resolutions.append({
            "reference": reference,
            "authorization": "AUTHORIZED" if authorization_ok else "NOT_AUTHORIZED",
            "resolution": "RESOLVED" if resolution_ok else "UNRESOLVED",
            "locator": locator_status,
            "exactExcerpt": exact,
            "contentDigestValid": digest_ok,
            "resolvedContentDigest": source.sha256,
            "excerptMatchOffsets": [index for index in range(len(resolved_content)) if resolved_content.startswith(excerpt, index)] if exact else [],
            "status": "PASS" if item_valid else "FAIL",
        })
    if answer.get("relationshipResult") == "ESTABLISHED" and not answer.get("evidence"):
        valid = False
        resolutions.append({"status": "FAIL", "resolution": "REQUIRED_EVIDENCE_MISSING"})
    return {"status": "PASS" if valid else "FAIL", "resolutions": resolutions, "contract": V4_IDENTITIES["grounding"]}


def _normalized_terms(value: Any) -> str:
    return "".join(character for character in canonical(value).casefold() if character.isalnum())


def evaluate_semantics(answer: dict[str, Any], question_id: str) -> dict[str, Any]:
    benchmark = next(item for item in _load_benchmark()["cases"] if item["caseId"] == question_id.replace("-COMPARATIVE", "") or item["caseId"] == question_id)
    oracle = _load_oracle()
    if question_id == "CASE-04":
        expected = oracle["causalClassifications"]["CASE-04"]
        correct = answer.get("relationshipResult") == ("NOT_ESTABLISHED" if expected == "NOT_ESTABLISHED" else expected)
    elif question_id == "CASE-01-COMPARATIVE":
        links = [link["id"] for link in benchmark["expectedCausalLinks"]]
        correct = answer.get("relationshipResult") == "ESTABLISHED" and all(oracle["causalClassifications"].get(link) in {"STRONGLY_SUPPORTED", "EXPLICITLY_DOCUMENTED"} for link in links)
    elif question_id == "CASE-03":
        answer_text = _normalized_terms(answer)
        expected_terms = benchmark["expectedAffectedComponents"] + benchmark["expectedAffectedTests"]
        correct = all(_normalized_terms(term) in answer_text for term in expected_terms)
    else:
        raise ValueError(f"unknown V4 question: {question_id}")
    return {"semanticCorrect": correct, "semanticEvaluated": True, "contract": V4_IDENTITIES["semantic"], "authority": {"benchmark": str(BENCHMARK_PATH.relative_to(ROOT)), "oracle": str(ORACLE_PATH.relative_to(ROOT))}}


def not_evaluated() -> dict[str, Any]:
    return {"structural": {"valid": None, "status": "NOT_EVALUATED"}, "grounding": {"status": "NOT_EVALUATED"}, "semantic": {"semanticEvaluated": False, "semanticCorrect": "NOT_EVALUATED"}, "correctGroundedAnswer": "NOT_EVALUATED"}


def _is_censored(status: str | ExecutionStatus) -> bool:
    return status in {ExecutionStatus.CENSORED, ExecutionStatus.CENSORED_RUNAWAY}


def evaluate_answer(answer: dict[str, Any], assignment: Any, evidence: list[EvidenceItem | dict[str, Any]]) -> dict[str, Any]:
    structural: dict[str, Any] = {"valid": False, "status": "NOT_EVALUATED"}
    try:
        validate_answer(answer, Assignment(assignment.assignment_id, assignment.question_id, assignment.question_version, assignment.case_id, "AGENT_DIRECT", assignment.repetition))
        structural = {"valid": True, "status": "PASS"}
    except Exception as error:
        structural = {"valid": False, "status": "FAIL", "error": str(error)}
    if not structural["valid"]:
        return {"structural": structural, "grounding": {"status": "NOT_EVALUATED"}, "semantic": {"semanticEvaluated": False, "semanticCorrect": "NOT_EVALUATED"}, "correctGroundedAnswer": "NOT_EVALUATED"}
    grounding = evaluate_grounding(answer, evidence, assignment.question_id)
    semantic = evaluate_semantics(answer, assignment.question_id)
    return {"structural": structural, "grounding": grounding, "semantic": semantic, "correctGroundedAnswer": grounding["status"] == "PASS" and semantic["semanticCorrect"]}


def classify_failure(*, execution_status: str = ExecutionStatus.COMPLETED, structural_valid: bool | None, grounding_status: str, semantic_evaluated: bool, semantic_correct: bool | str, stopping_reason: str = "NOT_STOPPED") -> str:
    if execution_status == ExecutionStatus.CENSORED_RUNAWAY or stopping_reason.startswith("RUNAWAY_GUARD_"):
        return "RUNAWAY_GUARD_CENSORING"
    if execution_status == ExecutionStatus.CENSORED or stopping_reason.startswith("SAFETY_"):
        return "SAFETY_CEILING_CENSORING"
    if execution_status == ExecutionStatus.PROVIDER_TIMEOUT or stopping_reason == "PROVIDER_TIMEOUT":
        return "PROVIDER_TIMEOUT"
    if execution_status == ExecutionStatus.TOOL_TIMEOUT or stopping_reason == "TOOL_TIMEOUT":
        return "TOOL_TIMEOUT"
    if execution_status == ExecutionStatus.PROVIDER_FAILURE or stopping_reason == "PROVIDER_FAILURE":
        return "PROVIDER_FAILURE"
    if execution_status == ExecutionStatus.RUNTIME_FAILURE:
        return "RUNTIME_INFRASTRUCTURE_FAILURE"
    if execution_status == ExecutionStatus.MODEL_FAILURE or stopping_reason == "MODEL_TOOL_CONTRACT_FAILURE":
        return "MODEL_TOOL_CONTRACT_FAILURE"
    if structural_valid is False:
        return "MODEL_STRUCTURAL_FAILURE"
    if grounding_status == "FAIL":
        return "GROUNDING_FAILURE"
    if semantic_evaluated and semantic_correct is False:
        return "SEMANTIC_FAILURE"
    if semantic_evaluated and semantic_correct is True and grounding_status == "PASS":
        return "CORRECT_GROUNDED_ANSWER"
    return "NOT_EVALUATED"


def deterministic_projection(observation: dict[str, Any]) -> dict[str, Any]:
    """Stable derived view used by replay and future collection summaries."""
    projection = {
        "executionStatus": observation["executionStatus"],
        "primaryDiagnostic": observation["primaryDiagnostic"],
        "structuralValid": observation["structuralValid"],
        "groundingValid": observation["groundingValid"],
        "semanticEvaluated": observation["semanticEvaluated"],
        "semanticCorrect": observation["semanticCorrect"],
        "correctGroundedAnswer": observation["correctGroundedAnswer"],
        "stoppingReason": observation["stoppingReason"],
    }
    if observation.get("rawSchemaVersion") == V4_IDENTITIES["raw"]:
        projection["providerFailure"] = observation.get("providerFailure")
        projection["resourceAccounting"] = {
            key: observation["resources"].get(key)
            for key in (
                "providerTransportAttempts", "providerResponsesReceived",
                "usableProviderResponses", "responseBearingModelTurns",
                "toolAttempts", "executedToolOperations", "invalidToolRequests",
                "skippedToolOperations", "ceilingCountedOperations",
                "resultBytesProduced", "resultBytesDelivered", "resultBytesRejected",
                "largestSingleResultBytesProduced", "largestSingleResultBytesDelivered",
                "inputTokens", "outputTokens", "totalTokens",
                "providerLatencyMs", "providerTransportLatencyMs",
            )
        }
    return projection


def build_observation(*, assignment: dict[str, Any], run_id: str, raw_output: Any, evaluation: dict[str, Any], resources: ResourceAccounting, execution_status: str = ExecutionStatus.COMPLETED, experiment_identity: dict[str, Any] | None = None, provider_failure: dict[str, Any] | None = None) -> dict[str, Any]:
    if provider_failure is not None:
        assert_secret_free(provider_failure)
    structural = evaluation["structural"]
    structural_value = "YES" if structural["valid"] is True else "NO" if structural["valid"] is False and not _is_censored(execution_status) else "NOT_EVALUATED"
    grounding_status = evaluation["grounding"].get("status", "NOT_EVALUATED")
    semantic = evaluation["semantic"]
    observation = {
        "observationId": f"{run_id}:{assignment['assignmentId']}",
        "v4ManifestVersion": V4_IDENTITIES["manifest"],
        "rawSchemaVersion": V4_IDENTITIES["raw"],
        "answerContractVersion": V4_IDENTITIES["answer"],
        "groundingContractVersion": V4_IDENTITIES["grounding"],
        "semanticEvaluationVersion": V4_IDENTITIES["semantic"],
        "executionConfigurationVersion": V4_IDENTITIES["execution"],
        "assignment": assignment,
        "runId": run_id,
        "rawOutput": raw_output,
        "rawOutputSha256": raw_output_hash(raw_output),
        "executionStatus": execution_status.value if isinstance(execution_status, ExecutionStatus) else execution_status,
        "structuralValid": structural_value,
        "groundingValid": "YES" if grounding_status == "PASS" else grounding_status,
        "semanticEvaluated": semantic.get("semanticEvaluated", False),
        "semanticCorrect": semantic.get("semanticCorrect", "NOT_EVALUATED"),
        "correctGroundedAnswer": evaluation["correctGroundedAnswer"],
        "primaryDiagnostic": classify_failure(execution_status=execution_status, structural_valid=structural["valid"], grounding_status=grounding_status, semantic_evaluated=semantic.get("semanticEvaluated", False), semantic_correct=semantic.get("semanticCorrect", "NOT_EVALUATED"), stopping_reason=resources.stopping_reason),
        "stoppingReason": resources.stopping_reason,
        "resources": resources.as_dict(),
        "providerFailure": provider_failure,
        "evaluation": evaluation,
        "experimentIdentity": experiment_identity or v4_experiment_identity(),
    }
    observation["deterministicProjection"] = deterministic_projection(observation)
    return observation


def write_observation(path: str | Path, observation: dict[str, Any]) -> None:
    if observation.get("v4ManifestVersion") != V4_IDENTITIES["manifest"]:
        raise ValueError("not a V4 observation")
    artifact = {"artifactVersion": V4_IDENTITIES["raw"], "immutable": True, "observation": observation}
    artifact["artifactSha256"] = raw_output_hash({key: value for key, value in artifact.items() if key != "artifactSha256"})
    write_immutable_json(path, artifact)


def write_derived_projection(path: str | Path, observation: dict[str, Any]) -> None:
    """Write a versioned derived view without rewriting the immutable RAW artifact."""
    projection = {
        "projectionVersion": V4_IDENTITIES["projection"],
        "observationId": observation["observationId"],
        "rawOutputSha256": observation["rawOutputSha256"],
        "experimentIdentity": observation.get("experimentIdentity"),
        "projection": observation["deterministicProjection"],
    }
    projection["projectionSha256"] = raw_output_hash({key: value for key, value in projection.items() if key != "projectionSha256"})
    write_immutable_json(path, projection)


def _replay_evaluation(observation: dict[str, Any]) -> dict[str, Any]:
    raw = observation.get("rawOutput")
    if not isinstance(raw, dict):
        return not_evaluated()
    final = raw.get("finalResponse")
    if final is None:
        return not_evaluated()
    identity = observation["assignment"]
    assignment = V4Assignment(identity["assignmentId"], identity["questionId"], identity["questionVersion"], identity["caseId"], identity["condition"], identity["repetition"])
    evidence = [EvidenceItem(item["reference"], item.get("content", ""), authorized=item.get("authorized", True), resolved=item.get("resolved", True), content_sha256=item.get("contentSha256")) for item in raw.get("evidence", [])]
    return evaluate_answer(final, assignment, evidence)


def replay_observation(observation: dict[str, Any]) -> dict[str, Any]:
    if observation.get("rawOutputSha256") != raw_output_hash(observation.get("rawOutput")):
        raise ValueError("V4 raw output hash mismatch")
    replayed = _replay_evaluation(observation)
    for field in ("structuralValid", "groundingValid", "semanticEvaluated", "semanticCorrect", "correctGroundedAnswer"):
        expected = observation[field]
        actual = {
            "structuralValid": "YES" if replayed["structural"]["valid"] is True else "NO" if replayed["structural"]["valid"] is False and not _is_censored(observation["executionStatus"]) else "NOT_EVALUATED",
            "groundingValid": "YES" if replayed["grounding"].get("status") == "PASS" else replayed["grounding"].get("status", "NOT_EVALUATED"),
            "semanticEvaluated": replayed["semantic"].get("semanticEvaluated", False),
            "semanticCorrect": replayed["semantic"].get("semanticCorrect", "NOT_EVALUATED"),
            "correctGroundedAnswer": replayed["correctGroundedAnswer"],
        }[field]
        if expected != actual and observation["executionStatus"] == ExecutionStatus.COMPLETED:
            raise ValueError(f"V4 replay mismatch: {field}")
    if observation.get("rawSchemaVersion") == V4_IDENTITIES["raw"]:
        raw_output = observation.get("rawOutput")
        tool_trace = raw_output.get("toolTrace", []) if isinstance(raw_output, dict) else []
        expected_accounting = accounting_from_tool_trace(tool_trace)
        actual_accounting = {key: observation["resources"].get(key) for key in expected_accounting}
        if actual_accounting != expected_accounting:
            raise ValueError("V4 replay mismatch: corrected resource accounting")
        provider_attempts = raw_output.get("providerAttempts", []) if isinstance(raw_output, dict) else []
        expected_provider_accounting = accounting_from_provider_attempts(provider_attempts)
        actual_provider_accounting = {key: observation["resources"].get(key) for key in expected_provider_accounting}
        if actual_provider_accounting != expected_provider_accounting:
            raise ValueError("V4 replay mismatch: provider lifecycle accounting")
    replay_execution_status = observation["executionStatus"]
    replay_primary = classify_failure(
        execution_status=replay_execution_status,
        structural_valid=replayed["structural"]["valid"],
        grounding_status=replayed["grounding"].get("status", "NOT_EVALUATED"),
        semantic_evaluated=replayed["semantic"].get("semanticEvaluated", False),
        semantic_correct=replayed["semantic"].get("semanticCorrect", "NOT_EVALUATED"),
        stopping_reason=observation["stoppingReason"],
    )
    replay_observation_view = {
        **observation,
        "primaryDiagnostic": replay_primary,
        "structuralValid": "YES" if replayed["structural"]["valid"] is True else "NO" if replayed["structural"]["valid"] is False and not _is_censored(observation["executionStatus"]) else "NOT_EVALUATED",
        "groundingValid": "YES" if replayed["grounding"].get("status") == "PASS" else replayed["grounding"].get("status", "NOT_EVALUATED"),
        "semanticEvaluated": replayed["semantic"].get("semanticEvaluated", False),
        "semanticCorrect": replayed["semantic"].get("semanticCorrect", "NOT_EVALUATED"),
        "correctGroundedAnswer": replayed["correctGroundedAnswer"],
    }
    if observation.get("deterministicProjection") != deterministic_projection(replay_observation_view):
        raise ValueError("V4 replay mismatch: deterministic projection")
    return {"replayed": True, "providerCalls": 0, "networkCalls": 0, "deterministicEvaluationRecomputed": True, "deterministicProjectionRecomputed": True, "observationId": observation["observationId"]}


def run_preflight() -> dict[str, Any]:
    manifest = validate_v4_identity()
    ceilings = SafetyCeilings.from_manifest(manifest)
    pilot = v4_pilot_assignment_matrix()
    validate_v4_pilot_plan(pilot)
    experiment = v4_experiment_identity(safety=ceilings)
    pilot_identity = v4_pilot_identity(safety=ceilings)
    return {
        "status": "PASS",
        "configurationReady": True,
        "pilotAuthorized": False,
        "manifest": manifest["manifestVersion"],
        "experimentIdentity": experiment,
        "pilotIdentity": pilot_identity,
        "officialAssignmentCount": len(v4_assignment_matrix()),
        "pilotAssignmentCount": len(pilot),
        "toolRecoveryPolicy": manifest["toolRecoveryPolicy"],
        "nativeTechnicalConfiguration": {
            key: manifest["executionConfiguration"][key]
            for key in (
                "nativeProviderTimeoutSeconds",
                "nativeProviderConnectTimeoutSeconds",
                "nativeProviderPoolTimeoutSeconds",
                "nativeRepositoryTimeoutSeconds",
                "nativeGroundingBridgeTimeoutSeconds",
                "providerMaxRetries",
            )
        },
        "safetyCeilings": ceilings.as_dict(),
        "safetyCeilingStatus": manifest["safetyCeilings"]["status"],
        "officialCollectionAllowed": False,
        "providerCalls": 0,
        "networkCalls": 0,
        "deterministicReplay": "READY",
    }
