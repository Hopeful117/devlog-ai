"""Deterministic evaluation of revision-pinned Story Context runtime captures.

This module consumes bounded Core snapshots and interaction-trace metadata. It
does not infer provider-visible evidence from projected or grounded evidence.
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


CAPTURE_VERSION = "story-context-runtime-loss-v1"
LIVE_CAPTURE_VERSION = "story-context-live-provider-v1"
LEDGER_VERSION = "story-context-loss-ledger-v1"
SHA256_LENGTH = 64
MAX_CAPTURE_BYTES = 256_000
MAX_CAPTURE_FILE_BYTES = 4 * 1024 * 1024
MAX_ROWS = 256
MAX_NESTED_ITEMS = 256
MAX_REASON_LENGTH = 128

TERMINAL_STATUSES = {
    "AUTHORIZED",
    "COMPLETED",
    "PROJECTION_REDUCED",
    "REJECTED",
    "PROVIDER_INCOMPLETE",
    "PROVIDER_TIMEOUT",
    "PROVIDER_TRANSPORT_FAILURE",
    "VALIDATION_FAILURE",
    "NOT_EVALUATED",
}
ELIGIBLE_STATUSES = {"COMPLETED", "PROJECTION_REDUCED"}
SEMANTIC_STATUSES = {"ELIGIBLE", "NOT_EVALUATED"}
EXECUTION_SCOPE_KEYS = {
    "project",
    "projectId",
    "sourceId",
    "resolvedRevision",
    "revisionSource",
    "resolutionState",
}
FRESHNESS_KEYS = {
    "state", "status", "sourceRevision", "warnings", "revisionConsistency", "revisionMismatch"
}
SOURCE_REVISION_KEYS = {"kind", "project", "revision"}
LEDGER_KEYS = {
    "version", "maximumDetailEntries", "counts", "lossReasons", "entries",
    "detailOverflowCount", "budgets", "projection", "providerOutcome",
    "evaluationTelemetry", "digest",
}
COUNT_KEYS = {
    "candidateCount", "uniqueCandidateCount", "duplicateCandidateCount", "rankedCount",
    "selectedCount", "enrichedCount", "projectedCount", "groundedCount",
    "providerVisibleCount", "discardedCount",
}
PROVIDER_OUTCOME_KEYS = {"status", "visibility", "outcomeSource", "details"}
TERMINAL_OUTCOME_KEYS = {"status", "semanticEligibility"}
TOP_LEVEL_KEYS = {
    "captureVersion", "taskId", "scenario", "repetition", "executionScope",
    "freshness", "contextDigest", "projectionDigest", "lossAccounting",
    "terminalOutcome", "analysisAvailable", "captureDiagnostics",
}
FORBIDDEN_KEY_PARTS = {
    "credential", "password", "secret", "token", "authorization", "prompt",
    "rawoutput", "repositorypayload", "privatekey", "apikey",
}
REQUIRED_COUNT_KEYS = {
    "candidateCount",
    "selectedCount",
    "projectedCount",
    "groundedCount",
    "providerVisibleCount",
}
LIVE_PROVIDER_KEYS = {
    "provider", "model", "status", "visibility", "outcomeSource",
    "failureCategory", "incompleteReason", "effectiveOutputLimit", "usage", "latencyMs",
}
LIVE_USAGE_KEYS = {"inputTokens", "outputTokens", "totalTokens"}
LIVE_DIAGNOSTIC_KEYS = {"identityStatus", "redactionPolicy", "measurementStatus"}
TRANSPORT_FAILURE_CATEGORIES = {
    "PROVIDER_TRANSPORT_FAILURE", "TRANSPORT_FAILURE", "NETWORK_FAILURE", "PROVIDER_ERROR"
}
SENSITIVE_VALUE_PATTERNS = (
    re.compile(r"(?i)bearer\s+\S+"),
    re.compile(r"(?i)(?:api[_-]?key|password|secret|token)\s*[:=]\s*\S+"),
    re.compile(r"-----BEGIN [A-Z ]+-----"),
    re.compile(r"\b(?:sk|ghp|xoxb|AIza)[-_][A-Za-z0-9_-]{12,}\b"),
)


class CaptureValidationError(ValueError):
    """Raised when a runtime capture cannot be safely evaluated."""


def _is_sha256(value: Any) -> bool:
    return isinstance(value, str) and len(value) == SHA256_LENGTH and all(
        character in "0123456789abcdef" for character in value
    )


def _is_revision(value: Any) -> bool:
    return (
        isinstance(value, str)
        and 7 <= len(value) <= SHA256_LENGTH
        and all(character in "0123456789abcdef" for character in value)
    )


def _bounded_string(value: Any, maximum: int = 160) -> bool:
    return isinstance(value, str) and 1 <= len(value) <= maximum


def _canonical_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def stable_digest(value: Any) -> str:
    """Return the stable digest used by capture/report identity checks."""

    return hashlib.sha256(_canonical_json(value)).hexdigest()


def _require_mapping(value: Any, name: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CaptureValidationError(f"{name} must be an object")
    return value


def _allow_keys(value: dict[str, Any], allowed: set[str], name: str) -> None:
    unsupported = set(value) - allowed
    if unsupported:
        raise CaptureValidationError(f"{name} contains unsupported fields: {sorted(unsupported)}")


def _validate_bounded_tree(
    value: Any, name: str, depth: int = 0, loss_reason_keys: bool = False
) -> None:
    if depth > 4:
        raise CaptureValidationError(f"{name} is nested too deeply")
    if isinstance(value, str):
        if len(value) > MAX_REASON_LENGTH:
            raise CaptureValidationError(f"{name} contains an oversized string")
        return
    if isinstance(value, list):
        if len(value) > MAX_NESTED_ITEMS:
            raise CaptureValidationError(f"{name} contains too many items")
        for index, item in enumerate(value):
            _validate_bounded_tree(item, f"{name}[{index}]", depth + 1)
        return
    if isinstance(value, dict):
        if len(value) > MAX_NESTED_ITEMS:
            raise CaptureValidationError(f"{name} contains too many fields")
        for key, item in value.items():
            if not isinstance(key, str) or len(key) > MAX_REASON_LENGTH:
                raise CaptureValidationError(f"{name} contains an invalid field name")
            valid_loss_reason_key = loss_reason_keys and all(
                character in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for character in key
            )
            if not valid_loss_reason_key and any(
                part in key.lower().replace("_", "") for part in FORBIDDEN_KEY_PARTS
            ):
                raise CaptureValidationError(f"{name}.{key} is forbidden in captures")
            _validate_bounded_tree(
                item,
                f"{name}.{key}",
                depth + 1,
                loss_reason_keys=key == "lossReasons",
            )
        return
    if value is not None and not isinstance(value, (bool, int, float)):
        raise CaptureValidationError(f"{name} contains an unsupported value")


def _validate_identity(
    capture: dict[str, Any],
    expected_revision: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> None:
    scope = _require_mapping(capture.get("executionScope"), "executionScope")
    if set(scope) != EXECUTION_SCOPE_KEYS:
        raise CaptureValidationError("executionScope fields are incomplete or unsupported")
    if scope.get("project") != expected_project or not _bounded_string(scope.get("project"), 100):
        raise CaptureValidationError("executionScope project is missing or invalid")
    if scope.get("projectId") != expected_project_id or not _bounded_string(scope.get("projectId"), 128):
        raise CaptureValidationError("executionScope projectId is missing or invalid")
    if scope.get("sourceId") not in expected_source_ids or not _bounded_string(scope.get("sourceId"), 128):
        raise CaptureValidationError("executionScope sourceId is missing or invalid")
    if scope.get("revisionSource") != expected_revision_source or not _bounded_string(scope.get("revisionSource"), 128):
        raise CaptureValidationError("executionScope revisionSource is missing or invalid")
    revision = scope.get("resolvedRevision")
    if not _is_revision(revision):
        raise CaptureValidationError("executionScope revision is missing or invalid")
    if scope.get("resolutionState") != "RESOLVED":
        raise CaptureValidationError("executionScope resolution is not resolved")
    if expected_revision is not None and revision != expected_revision:
        raise CaptureValidationError("scope revision does not match expected revision")

    freshness = _require_mapping(capture.get("freshness"), "freshness")
    _allow_keys(freshness, FRESHNESS_KEYS, "freshness")
    source_revision = _require_mapping(freshness.get("sourceRevision"), "freshness.sourceRevision")
    _allow_keys(source_revision, SOURCE_REVISION_KEYS, "freshness.sourceRevision")
    if source_revision.get("revision") != revision or not _is_revision(source_revision.get("revision")):
        raise CaptureValidationError("freshness revision does not match executionScope revision")
    source_revision_kind = source_revision.get("kind")
    if source_revision_kind != "PROJECT_REVISION":
        raise CaptureValidationError("freshness source revision kind is invalid")
    if source_revision.get("project") != scope.get("project"):
        raise CaptureValidationError("freshness project does not match executionScope project")
    state = freshness.get("state")
    if state not in {"CURRENT", "FRESH", "STALE", "PARTIALLY_FRESH", "NO_BASELINE", "UNKNOWN", "NOT_ESTABLISHED"}:
        raise CaptureValidationError("freshness state is missing or invalid")
    if not _bounded_string(state, 32):
        raise CaptureValidationError("freshness state is missing or invalid")
    if "status" in freshness and freshness["status"] not in {
        "CURRENT", "FRESH", "STALE", "PARTIALLY_FRESH", "NO_BASELINE", "UNKNOWN", "NOT_ESTABLISHED"
    }:
        raise CaptureValidationError("freshness status is invalid")
    if "revisionConsistency" in freshness and freshness["revisionConsistency"] not in {
        "CONSISTENT", "MISMATCH"
    }:
        raise CaptureValidationError("freshness revision consistency is invalid")
    if "warnings" in freshness:
        warnings = freshness["warnings"]
        if not isinstance(warnings, list) or len(warnings) > MAX_NESTED_ITEMS:
            raise CaptureValidationError("freshness warnings are invalid or unbounded")
        if any(not _bounded_string(warning, MAX_REASON_LENGTH) for warning in warnings):
            raise CaptureValidationError("freshness warnings contain invalid values")
    if "revisionMismatch" in freshness:
        mismatch = _require_mapping(freshness["revisionMismatch"], "freshness.revisionMismatch")
        if set(mismatch) != {"evidenceRevision", "freshnessRevision"} or any(
            not _is_revision(value) for value in mismatch.values()
        ):
            raise CaptureValidationError("freshness revision mismatch is invalid")

    for key in ("contextDigest", "projectionDigest"):
        if not _is_sha256(capture.get(key)):
            raise CaptureValidationError(f"{key} is missing or invalid")


def _validate_ledger(capture: dict[str, Any]) -> dict[str, Any]:
    ledger = _require_mapping(capture.get("lossAccounting"), "lossAccounting")
    _allow_keys(ledger, LEDGER_KEYS, "lossAccounting")
    if ledger.get("version") != LEDGER_VERSION or not _is_sha256(ledger.get("digest")):
        raise CaptureValidationError("loss ledger identity is missing or invalid")
    counts = _require_mapping(ledger.get("counts"), "lossAccounting.counts")
    _allow_keys(counts, COUNT_KEYS, "lossAccounting.counts")
    for key in REQUIRED_COUNT_KEYS:
        if type(counts.get(key)) is not int or counts[key] < 0:
            raise CaptureValidationError(f"loss ledger count is missing or invalid: {key}")
    provider = _require_mapping(ledger.get("providerOutcome"), "lossAccounting.providerOutcome")
    _allow_keys(provider, PROVIDER_OUTCOME_KEYS, "lossAccounting.providerOutcome")
    if not _bounded_string(provider.get("status"), 64):
        raise CaptureValidationError("provider outcome status is missing or invalid")
    if provider.get("visibility") not in {"UNKNOWN", "MEASURED"}:
        raise CaptureValidationError("provider outcome visibility is invalid")
    if provider.get("visibility") == "UNKNOWN" and counts["providerVisibleCount"] != 0:
        raise CaptureValidationError("unknown provider visibility cannot have visible evidence")
    if provider.get("visibility") == "MEASURED" and not _bounded_string(
        provider.get("outcomeSource"), 128
    ):
        raise CaptureValidationError("measured provider visibility requires attribution")
    if provider.get("visibility") == "MEASURED" and provider.get("outcomeSource") not in {
        "AI_INTERACTION_TRACE", "PROVIDER_RESPONSE_METADATA"
    }:
        raise CaptureValidationError("measured provider attribution is unsupported")
    loss_reasons = ledger.get("lossReasons") or {}
    if not isinstance(loss_reasons, dict) or len(loss_reasons) > MAX_NESTED_ITEMS:
        raise CaptureValidationError("loss reasons are invalid or unbounded")
    if any(
        not isinstance(reason, str)
        or len(reason) > MAX_REASON_LENGTH
        or not reason
        or any(character not in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_" for character in reason)
        or any(part in {"TOKEN", "PASSWORD", "SECRET", "CREDENTIAL", "APIKEY", "PRIVATEKEY"}
               for part in reason.split("_"))
        for reason in loss_reasons
    ):
        raise CaptureValidationError("loss reason identity is invalid")
    return ledger


def _validate_expected_digests(
    capture: dict[str, Any],
    expected_context_digest: str | None,
    expected_projection_digest: str | None,
    expected_ledger_digest: str | None,
) -> None:
    expected = {
        "contextDigest": expected_context_digest,
        "projectionDigest": expected_projection_digest,
        "lossAccounting.digest": expected_ledger_digest,
    }
    actual = {
        "contextDigest": capture.get("contextDigest"),
        "projectionDigest": capture.get("projectionDigest"),
        "lossAccounting.digest": capture["lossAccounting"].get("digest"),
    }
    for name, value in expected.items():
        if value is not None and actual[name] != value:
            raise CaptureValidationError(f"{name} does not match expected digest")


def validate_capture(
    capture: dict[str, Any],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> None:
    """Validate the bounded identity and diagnostic contract for one capture."""

    if not isinstance(capture, dict):
        raise CaptureValidationError("capture must be an object")
    if (
        not _bounded_string(expected_project, 100)
        or not _bounded_string(expected_project_id, 128)
        or not expected_source_ids
        or any(not _bounded_string(source_id, 128) for source_id in expected_source_ids)
        or not _bounded_string(expected_revision_source, 128)
        or not _is_revision(expected_revision)
        or not _is_sha256(expected_context_digest)
        or not _is_sha256(expected_projection_digest)
        or not _is_sha256(expected_ledger_digest)
    ):
        raise CaptureValidationError("expected capture identity or digest inputs are invalid")
    if set(capture) - TOP_LEVEL_KEYS:
        raise CaptureValidationError("capture contains unsupported fields")
    if capture.get("captureVersion") != CAPTURE_VERSION:
        raise CaptureValidationError("unsupported capture version")
    if not _bounded_string(capture.get("taskId"), 128):
        raise CaptureValidationError("taskId is missing or invalid")
    if not _bounded_string(capture.get("scenario"), 128):
        raise CaptureValidationError("scenario is missing or invalid")
    if type(capture.get("repetition")) is not int or not 1 <= capture["repetition"] <= 256:
        raise CaptureValidationError("repetition is missing or invalid")
    _validate_identity(
        capture,
        expected_revision,
        expected_project,
        expected_project_id,
        expected_source_ids,
        expected_revision_source,
    )
    _validate_ledger(capture)
    _validate_expected_digests(
        capture, expected_context_digest, expected_projection_digest, expected_ledger_digest
    )
    outcome = _require_mapping(capture.get("terminalOutcome"), "terminalOutcome")
    _allow_keys(outcome, TERMINAL_OUTCOME_KEYS, "terminalOutcome")
    if outcome.get("status") not in TERMINAL_STATUSES:
        raise CaptureValidationError("terminal outcome status is invalid")
    if outcome.get("semanticEligibility") not in SEMANTIC_STATUSES:
        raise CaptureValidationError("semantic eligibility is invalid")
    if outcome["status"] not in ELIGIBLE_STATUSES and outcome["semanticEligibility"] != "NOT_EVALUATED":
        raise CaptureValidationError("technical failure cannot be semantically eligible")
    if type(capture.get("analysisAvailable")) is not bool:
        raise CaptureValidationError("analysisAvailable is missing or invalid")
    if capture["terminalOutcome"]["status"] == "COMPLETED" and not capture["analysisAvailable"]:
        raise CaptureValidationError("completed capture must contain an analysis")
    provider_status = capture["lossAccounting"]["providerOutcome"]["status"]
    terminal_status = outcome["status"]
    required_terminal = {
        "INCOMPLETE": "PROVIDER_INCOMPLETE",
        "TIMEOUT": "PROVIDER_TIMEOUT",
        "NOT_SUBMITTED": "REJECTED",
    }.get(provider_status)
    if required_terminal is not None and terminal_status != required_terminal:
        raise CaptureValidationError("terminal outcome contradicts provider outcome")
    if terminal_status in {"COMPLETED", "PROJECTION_REDUCED"} and provider_status != "COMPLETED":
        raise CaptureValidationError("successful terminal outcome requires completed provider outcome")
    _validate_bounded_tree(capture, "capture")

    encoded = _canonical_json(capture)
    if len(encoded) > MAX_CAPTURE_BYTES:
        raise CaptureValidationError("capture exceeds bounded size")


def _bounded_optional_string(value: Any, maximum: int = 160) -> str | None:
    if value is None:
        return None
    if not _bounded_string(value, maximum):
        raise CaptureValidationError("live provider metadata contains an invalid string")
    return value


def _safe_optional_string(value: Any, name: str, maximum: int = 160) -> str | None:
    value = _bounded_optional_string(value, maximum)
    if value is not None and any(pattern.search(value) for pattern in SENSITIVE_VALUE_PATTERNS):
        raise CaptureValidationError(f"{name} contains a sensitive value")
    return value


def _bounded_optional_nonnegative_int(value: Any, name: str) -> int | None:
    if value is None:
        return None
    if type(value) is not int or value < 0:
        raise CaptureValidationError(f"{name} must be a non-negative integer or null")
    return value


def _validate_live_provider_outcome(value: Any) -> None:
    provider = _require_mapping(value, "providerOutcome")
    _allow_keys(provider, LIVE_PROVIDER_KEYS, "providerOutcome")
    _safe_optional_string(provider.get("provider"), "provider", 80)
    _safe_optional_string(provider.get("model"), "model", 128)
    if provider.get("status") not in {"COMPLETED", "INCOMPLETE", "TIMEOUT", "FAILED", "NOT_SUBMITTED", "UNKNOWN"}:
        raise CaptureValidationError("providerOutcome status is invalid")
    if provider.get("visibility") not in {"UNKNOWN", "MEASURED"}:
        raise CaptureValidationError("providerOutcome visibility is invalid")
    if provider.get("outcomeSource") not in {"AI_INTERACTION_TRACE", None}:
        raise CaptureValidationError("providerOutcome attribution is unsupported")
    if provider.get("visibility") == "MEASURED":
        if provider.get("outcomeSource") != "AI_INTERACTION_TRACE":
            raise CaptureValidationError("measured live visibility requires trace attribution")
        if not provider.get("provider") or not provider.get("model"):
            raise CaptureValidationError("measured live visibility requires provider and model")
    _safe_optional_string(provider.get("failureCategory"), "failureCategory", 80)
    _safe_optional_string(provider.get("incompleteReason"), "incompleteReason", MAX_REASON_LENGTH)
    _bounded_optional_nonnegative_int(provider.get("effectiveOutputLimit"), "effectiveOutputLimit")
    usage = provider.get("usage")
    if usage is not None:
        usage = _require_mapping(usage, "providerOutcome.usage")
        _allow_keys(usage, LIVE_USAGE_KEYS, "providerOutcome.usage")
        for key in LIVE_USAGE_KEYS:
            _bounded_optional_nonnegative_int(usage.get(key), f"providerOutcome.usage.{key}")
    _bounded_optional_nonnegative_int(provider.get("latencyMs"), "latencyMs")


def validate_live_capture(
    capture: dict[str, Any],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> None:
    """Validate the additive live projection without changing the v1 baseline."""

    required_keys = TOP_LEVEL_KEYS | {
        "captureSource", "correlationId", "providerOutcome", "captureDiagnostics"
    }
    if set(capture) != required_keys:
        raise CaptureValidationError("live capture fields are incomplete or unsupported")
    if capture.get("captureVersion") != LIVE_CAPTURE_VERSION:
        raise CaptureValidationError("unsupported live capture version")
    if capture.get("captureSource") != "LIVE_PROVIDER":
        raise CaptureValidationError("live capture source is invalid")
    if not _bounded_string(capture.get("correlationId"), 128):
        raise CaptureValidationError("correlationId is missing or invalid")
    diagnostics = _require_mapping(capture.get("captureDiagnostics"), "captureDiagnostics")
    _allow_keys(diagnostics, LIVE_DIAGNOSTIC_KEYS, "captureDiagnostics")
    if diagnostics.get("identityStatus") not in {"MATCHED", "NOT_EVALUATED"}:
        raise CaptureValidationError("capture diagnostic identity status is invalid")
    if diagnostics.get("redactionPolicy") != "ALLOWLISTED_METADATA_ONLY":
        raise CaptureValidationError("capture redaction policy is invalid")
    if diagnostics.get("measurementStatus") not in {"MEASURED", "UNKNOWN"}:
        raise CaptureValidationError("capture measurement status is invalid")
    _validate_live_provider_outcome(capture.get("providerOutcome"))

    baseline = {key: capture[key] for key in TOP_LEVEL_KEYS}
    baseline["captureVersion"] = CAPTURE_VERSION
    live_ledger = _require_mapping(capture.get("lossAccounting"), "lossAccounting")
    ledger_provider = _require_mapping(live_ledger.get("providerOutcome"), "lossAccounting.providerOutcome")
    baseline_ledger_status = ledger_provider.get("status")
    baseline_terminal_status = {
        "INCOMPLETE": "PROVIDER_INCOMPLETE",
        "TIMEOUT": "PROVIDER_TIMEOUT",
        "NOT_SUBMITTED": "REJECTED",
    }.get(baseline_ledger_status, "NOT_EVALUATED")
    baseline["terminalOutcome"] = {
        "status": baseline_terminal_status,
        "semanticEligibility": "NOT_EVALUATED",
    }
    baseline["analysisAvailable"] = False
    validate_capture(
        baseline,
        expected_revision,
        expected_context_digest,
        expected_projection_digest,
        expected_ledger_digest,
        expected_project,
        expected_project_id,
        expected_source_ids,
        expected_revision_source,
    )
    outcome = _require_mapping(capture.get("terminalOutcome"), "terminalOutcome")
    if outcome.get("status") not in TERMINAL_STATUSES:
        raise CaptureValidationError("live terminal outcome status is invalid")
    if outcome.get("semanticEligibility") not in SEMANTIC_STATUSES:
        raise CaptureValidationError("live semantic eligibility is invalid")
    provider_status = capture["providerOutcome"]["status"]
    expected_status = {
        "INCOMPLETE": "PROVIDER_INCOMPLETE",
        "TIMEOUT": "PROVIDER_TIMEOUT",
        "NOT_SUBMITTED": "REJECTED",
        "FAILED": "PROVIDER_TRANSPORT_FAILURE",
        "UNKNOWN": "NOT_EVALUATED",
    }.get(provider_status)
    if expected_status is not None and outcome.get("status") != expected_status:
        raise CaptureValidationError("provider outcome contradicts live terminal outcome")
    if provider_status == "COMPLETED" and outcome.get("status") not in {"COMPLETED", "PROJECTION_REDUCED", "VALIDATION_FAILURE"}:
        raise CaptureValidationError("completed provider outcome has an invalid terminal outcome")
    if outcome.get("status") in {"COMPLETED", "PROJECTION_REDUCED"} and not capture.get("analysisAvailable"):
        raise CaptureValidationError("successful live capture must contain an analysis")
    if outcome.get("status") not in {"COMPLETED", "PROJECTION_REDUCED"} and outcome.get("semanticEligibility") != "NOT_EVALUATED":
        raise CaptureValidationError("live technical failure cannot be semantically eligible")
    encoded = _canonical_json(capture)
    if len(encoded) > MAX_CAPTURE_BYTES:
        raise CaptureValidationError("live capture exceeds bounded size")


def _trace_value(trace: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in trace:
            return trace[key]
    return None


def project_live_capture(
    core_snapshot: dict[str, Any],
    interaction_trace: dict[str, Any],
    *,
    trace_binding: dict[str, Any] | None = None,
    scenario: str,
    repetition: int = 1,
    callback_status: str | None = None,
    preflight_outcome: str | None = None,
    projection_reduced: bool = False,
    analysis_available: bool = False,
) -> dict[str, Any]:
    """Project one Core snapshot and trace into a bounded live evaluation row."""

    snapshot = _require_mapping(core_snapshot, "core_snapshot")
    trace = _require_mapping(interaction_trace, "interaction_trace")
    task_id = snapshot.get("taskId")
    correlation_id = snapshot.get("correlationId")
    if not _bounded_string(task_id, 128) or not _bounded_string(correlation_id, 128):
        raise CaptureValidationError("Core task and correlation identity are required")
    binding = trace_binding or {}
    trace_task_id = _trace_value(trace, "taskId", "aiTaskId") or binding.get("taskId")
    trace_correlation_id = _trace_value(trace, "correlationId") or binding.get("correlationId")
    trace_id = _trace_value(trace, "traceId", "trace_id")
    bound_trace_id = binding.get("traceId")
    if trace_task_id != task_id or trace_correlation_id != correlation_id:
        raise CaptureValidationError("provider trace identity does not match Core identity")
    if bound_trace_id is not None and trace_id != bound_trace_id:
        raise CaptureValidationError("provider trace correlation does not match binding")
    if trace_id is None and not binding.get("traceId") and not trace.get("taskId"):
        raise CaptureValidationError("real provider trace requires an explicit trace binding")

    trace_status = _trace_value(trace, "providerStatus", "provider_status", "status")
    validation_status = _trace_value(trace, "validationStatus", "validation_status")
    failure_category = _trace_value(trace, "failureCategory", "failure_category")
    incomplete_reason = _trace_value(trace, "incompleteReason", "incomplete_reason")
    provider_status = "INCOMPLETE" if trace_status == "INCOMPLETE" or failure_category == "PROVIDER_INCOMPLETE" else (
        "TIMEOUT" if trace_status == "TIMEOUT" or failure_category == "PROVIDER_TIMEOUT" else (
            "NOT_SUBMITTED" if trace_status == "NOT_SUBMITTED" else (
                "FAILED" if trace_status in {"FAILED", "TRANSPORT_FAILURE"}
                or failure_category in TRANSPORT_FAILURE_CATEGORIES else (
                    "COMPLETED" if trace_status in {"COMPLETED", "SUCCEEDED"}
                    or (trace_status is None and validation_status == "SUCCEEDED")
                    else "UNKNOWN"))))
    terminal = normalize_terminal_outcome(
        callback_status=callback_status,
        interaction_traces=[trace],
        preflight_outcome=preflight_outcome,
        projection_reduced=projection_reduced,
    )
    provider = _trace_value(trace, "provider")
    model = _trace_value(trace, "modelIdentifier", "model_identifier")
    _safe_optional_string(provider, "provider", 80)
    _safe_optional_string(model, "model", 128)
    _safe_optional_string(failure_category, "failureCategory", 80)
    _safe_optional_string(incomplete_reason, "incompleteReason", MAX_REASON_LENGTH)
    measured = (
        _bounded_string(provider, 80)
        and _bounded_string(model, 128)
        and trace_task_id == task_id
        and trace_correlation_id == correlation_id
    )
    visibility = "MEASURED" if measured else "UNKNOWN"
    usage = {
        "inputTokens": _trace_value(trace, "inputTokens", "input_tokens"),
        "outputTokens": _trace_value(trace, "outputTokens", "output_tokens"),
        "totalTokens": _trace_value(trace, "totalTokens", "total_tokens"),
    }
    if all(value is None for value in usage.values()):
        usage = None
    provider_outcome = {
        "provider": provider if _bounded_string(provider, 80) else None,
        "model": model if _bounded_string(model, 128) else None,
        "status": provider_status,
        "visibility": visibility,
        "outcomeSource": "AI_INTERACTION_TRACE" if measured else None,
        "failureCategory": failure_category if _bounded_string(failure_category, 80) else None,
        "incompleteReason": incomplete_reason if _bounded_string(incomplete_reason, MAX_REASON_LENGTH) else None,
        "effectiveOutputLimit": _trace_value(trace, "effectiveOutputLimit", "outputLimit"),
        "usage": usage,
        "latencyMs": _trace_value(trace, "durationMs", "duration_ms"),
    }
    loss_accounting = copy.deepcopy(snapshot.get("lossAccounting"))
    if not isinstance(loss_accounting, dict):
        raise CaptureValidationError("Core loss accounting is required")
    if not isinstance(loss_accounting.get("providerOutcome"), dict):
        raise CaptureValidationError("Core provider outcome is required")

    capture = {
        "captureVersion": LIVE_CAPTURE_VERSION,
        "captureSource": "LIVE_PROVIDER",
        "taskId": task_id,
        "correlationId": correlation_id,
        "scenario": scenario,
        "repetition": repetition,
        "executionScope": snapshot.get("executionScope"),
        "freshness": snapshot.get("freshness"),
        "contextDigest": snapshot.get("contextDigest"),
        "projectionDigest": snapshot.get("projectionDigest"),
        "lossAccounting": loss_accounting,
        "providerOutcome": provider_outcome,
        "terminalOutcome": terminal,
        "analysisAvailable": analysis_available,
        "captureDiagnostics": {
            "identityStatus": "MATCHED",
            "redactionPolicy": "ALLOWLISTED_METADATA_ONLY",
            "measurementStatus": "MEASURED" if measured else "UNKNOWN",
        },
    }
    return capture


def evaluate_live_capture(
    capture: dict[str, Any],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> dict[str, Any]:
    """Evaluate a live row, excluding invalid rows from semantic denominators."""

    try:
        validate_live_capture(
            capture,
            expected_revision,
            expected_context_digest,
            expected_projection_digest,
            expected_ledger_digest,
            expected_project,
            expected_project_id,
            expected_source_ids,
            expected_revision_source,
        )
    except CaptureValidationError as error:
        return {
            "taskId": str(capture.get("taskId", ""))[:128],
            "scenario": str(capture.get("scenario", ""))[:128],
            "captureSource": "LIVE_PROVIDER",
            "status": "NOT_EVALUATED",
            "semanticEligibility": "NOT_EVALUATED",
            "providerVisibility": "UNKNOWN",
            "failureCode": str(error),
        }
    outcome = capture["terminalOutcome"]
    ledger = capture["lossAccounting"]
    result = {
        "taskId": capture["taskId"],
        "scenario": capture["scenario"],
        "status": outcome["status"],
        "semanticEligibility": outcome["semanticEligibility"],
        "providerVisibility": capture["providerOutcome"]["visibility"],
        "lossReasons": dict(sorted((ledger.get("lossReasons") or {}).items())),
        "counts": {
            key: ledger["counts"][key]
            for key in sorted(REQUIRED_COUNT_KEYS)
        },
    }
    result["captureSource"] = capture["captureSource"]
    result["correlationId"] = capture["correlationId"]
    result["provider"] = capture["providerOutcome"].get("provider")
    result["model"] = capture["providerOutcome"].get("model")
    result["usage"] = capture["providerOutcome"].get("usage")
    result["latencyMs"] = capture["providerOutcome"].get("latencyMs")
    return result


def summarize_live_captures(
    captures: Iterable[dict[str, Any]],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> dict[str, Any]:
    """Aggregate live captures while keeping invalid rows out of denominators."""

    bounded_captures = list(captures)
    if len(bounded_captures) > MAX_ROWS:
        raise CaptureValidationError("live capture collection exceeds bounded row count")
    results = [
        evaluate_live_capture(
            capture,
            expected_revision,
            expected_context_digest,
            expected_projection_digest,
            expected_ledger_digest,
            expected_project,
            expected_project_id,
            expected_source_ids,
            expected_revision_source,
        )
        for capture in bounded_captures
    ]
    statuses = Counter(result["status"] for result in results)
    evaluable = [result for result in results if result["semanticEligibility"] == "ELIGIBLE"]
    measured = sum(result.get("providerVisibility") == "MEASURED" for result in results)
    return {
        "captureVersion": LIVE_CAPTURE_VERSION,
        "captureSource": "LIVE_PROVIDER",
        "captureCount": len(results),
        "technicalStatusCounts": dict(sorted(statuses.items())),
        "measuredProviderCount": measured,
        "unknownProviderCount": len(results) - measured,
        "semanticEligibleCount": len(evaluable),
        "semanticNotEvaluatedCount": len(results) - len(evaluable),
        "semanticDenominator": len(evaluable),
        "rows": results[:MAX_ROWS],
        "reportDigest": stable_digest(results),
    }


def normalize_terminal_outcome(
    *,
    callback_status: str | None,
    interaction_traces: Iterable[dict[str, Any]] = (),
    preflight_outcome: str | None = None,
    projection_reduced: bool = False,
) -> dict[str, str]:
    """Normalize explicit Core/provider terminal evidence without inference."""

    traces = list(interaction_traces)
    direct_statuses = {
        "AUTHORIZED": {"status": "AUTHORIZED", "semanticEligibility": "NOT_EVALUATED"},
        "REJECTED": {"status": "REJECTED", "semanticEligibility": "NOT_EVALUATED"},
        "VALIDATION_FAILURE": {"status": "VALIDATION_FAILURE", "semanticEligibility": "NOT_EVALUATED"},
        "PROVIDER_INCOMPLETE": {"status": "PROVIDER_INCOMPLETE", "semanticEligibility": "NOT_EVALUATED"},
        "PROVIDER_TIMEOUT": {"status": "PROVIDER_TIMEOUT", "semanticEligibility": "NOT_EVALUATED"},
    }
    if any(
        trace.get("failureCategory") == "PROVIDER_INCOMPLETE"
        or trace.get("providerStatus") == "INCOMPLETE"
        for trace in traces
    ):
        return {"status": "PROVIDER_INCOMPLETE", "semanticEligibility": "NOT_EVALUATED"}
    if any(
        trace.get("failureCategory") == "PROVIDER_TIMEOUT"
        or trace.get("providerStatus") == "TIMEOUT"
    for trace in traces
    ):
        return {"status": "PROVIDER_TIMEOUT", "semanticEligibility": "NOT_EVALUATED"}
    if any(
        trace.get("failureCategory") in TRANSPORT_FAILURE_CATEGORIES
        or trace.get("providerStatus") in {"FAILED", "TRANSPORT_FAILURE"}
        for trace in traces
    ):
        return {"status": "PROVIDER_TRANSPORT_FAILURE", "semanticEligibility": "NOT_EVALUATED"}
    if any(
        trace.get("validationStatus") in {"FAILED", "VALIDATION_FAILED", "PARSING_FAILED"}
        or trace.get("failureCategory") in {"VALIDATION_FAILURE", "VALIDATION_FAILED"}
        for trace in traces
    ):
        return {"status": "VALIDATION_FAILURE", "semanticEligibility": "NOT_EVALUATED"}
    if preflight_outcome == "REJECTED":
        return {"status": "REJECTED", "semanticEligibility": "NOT_EVALUATED"}
    if callback_status in direct_statuses:
        return direct_statuses[callback_status]
    if projection_reduced:
        return {"status": "PROJECTION_REDUCED", "semanticEligibility": "ELIGIBLE"}
    if callback_status == "COMPLETED":
        return {"status": "COMPLETED", "semanticEligibility": "ELIGIBLE"}
    return {"status": "NOT_EVALUATED", "semanticEligibility": "NOT_EVALUATED"}


def evaluate_capture(
    capture: dict[str, Any],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> dict[str, Any]:
    """Return one bounded evaluation result; invalid captures are not scored."""

    try:
        validate_capture(
            capture,
            expected_revision,
            expected_context_digest,
            expected_projection_digest,
            expected_ledger_digest,
            expected_project,
            expected_project_id,
            expected_source_ids,
            expected_revision_source,
        )
    except CaptureValidationError as error:
        return {
            "taskId": str(capture.get("taskId", ""))[:128],
            "scenario": str(capture.get("scenario", ""))[:128],
            "status": "NOT_EVALUATED",
            "semanticEligibility": "NOT_EVALUATED",
            "failureCode": str(error),
        }
    outcome = capture["terminalOutcome"]
    ledger = capture["lossAccounting"]
    return {
        "taskId": capture["taskId"],
        "scenario": capture["scenario"],
        "status": outcome["status"],
        "semanticEligibility": outcome["semanticEligibility"],
        "providerVisibility": ledger["providerOutcome"]["visibility"],
        "lossReasons": dict(sorted((ledger.get("lossReasons") or {}).items())),
        "counts": {
            key: ledger["counts"][key]
            for key in sorted(REQUIRED_COUNT_KEYS)
        },
    }


def summarize_captures(
    captures: Iterable[dict[str, Any]],
    expected_revision: str,
    expected_context_digest: str,
    expected_projection_digest: str,
    expected_ledger_digest: str,
    expected_project: str,
    expected_project_id: str,
    expected_source_ids: set[str],
    expected_revision_source: str,
) -> dict[str, Any]:
    """Aggregate captures while excluding invalid/technical rows from quality denominators."""

    bounded_captures = list(captures)
    if len(bounded_captures) > MAX_ROWS:
        raise CaptureValidationError("capture collection exceeds bounded row count")
    results = [
        evaluate_capture(
            capture,
            expected_revision,
            expected_context_digest,
            expected_projection_digest,
            expected_ledger_digest,
            expected_project,
            expected_project_id,
            expected_source_ids,
            expected_revision_source,
        )
        for capture in bounded_captures
    ]
    statuses = Counter(result["status"] for result in results)
    evaluable = [result for result in results if result["semanticEligibility"] == "ELIGIBLE"]
    loss_reasons: Counter[str] = Counter()
    for result in evaluable:
        loss_reasons.update(result.get("lossReasons", {}))
    return {
        "captureVersion": CAPTURE_VERSION,
        "captureCount": len(results),
        "technicalStatusCounts": dict(sorted(statuses.items())),
        "semanticEligibleCount": len(evaluable),
        "semanticNotEvaluatedCount": len(results) - len(evaluable),
        "semanticDenominator": len(evaluable),
        "lossReasonCounts": dict(sorted(loss_reasons.items())),
        "rows": results[:MAX_ROWS],
        "reportDigest": stable_digest(results),
    }


def load_captures(path: Path) -> list[dict[str, Any]]:
    """Load bounded JSONL captures without silently accepting malformed rows."""

    if path.stat().st_size > MAX_CAPTURE_FILE_BYTES:
        raise CaptureValidationError("capture file exceeds bounded size")
    captures: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        if len(line.encode("utf-8")) > MAX_CAPTURE_BYTES:
            raise CaptureValidationError(f"line {line_number}: capture exceeds bounded size")
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            raise CaptureValidationError(f"line {line_number}: invalid JSON") from error
        if not isinstance(value, dict):
            raise CaptureValidationError(f"line {line_number}: capture must be an object")
        captures.append(value)
        if len(captures) > MAX_ROWS:
            raise CaptureValidationError("capture file exceeds bounded row count")
    return captures


def format_text_report(summary: dict[str, Any]) -> str:
    """Render a bounded human-readable report from ``summarize_captures``."""

    lines = [
        f"Capture version: {summary['captureVersion']}",
        f"Capture count: {summary['captureCount']}",
        f"Technical statuses: {json.dumps(summary['technicalStatusCounts'], sort_keys=True)}",
        f"Semantic eligible: {summary['semanticEligibleCount']}",
        f"Semantic NOT_EVALUATED: {summary['semanticNotEvaluatedCount']}",
        f"Semantic denominator: {summary['semanticDenominator']}",
        f"Loss reasons: {json.dumps(summary['lossReasonCounts'], sort_keys=True)}",
        f"Report digest: {summary['reportDigest']}",
    ]
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate Story Context runtime loss captures")
    parser.add_argument("capture_file", type=Path)
    parser.add_argument("--expected-revision", required=True)
    parser.add_argument("--context-digest", required=True)
    parser.add_argument("--projection-digest", required=True)
    parser.add_argument("--ledger-digest", required=True)
    parser.add_argument("--expected-project", required=True)
    parser.add_argument("--expected-project-id", required=True)
    parser.add_argument("--expected-source-id", action="append", required=True)
    parser.add_argument("--expected-revision-source", required=True)
    parser.add_argument("--json", action="store_true", dest="as_json")
    args = parser.parse_args(argv)
    summary = summarize_captures(
        load_captures(args.capture_file),
        args.expected_revision,
        args.context_digest,
        args.projection_digest,
        args.ledger_digest,
        args.expected_project,
        args.expected_project_id,
        set(args.expected_source_id),
        args.expected_revision_source,
    )
    if args.as_json:
        print(json.dumps(summary, indent=2, sort_keys=True))
    else:
        print(format_text_report(summary))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
