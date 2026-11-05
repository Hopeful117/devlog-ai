"""Aggregate bounded Story Context runtime captures.

The input is JSONL produced by ``story_context_budget_live.py``. The report
rejects rows without a task, revision, or digests and emits only aggregate
counts and normalized bounded diagnostics.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def load_rows(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        value = json.loads(line)
        if not isinstance(value, dict):
            raise ValueError(f"line {line_number}: capture row must be an object")
        rows.append(value)
    return rows


TERMINAL_STATUSES = {"COMPLETED", "FAILED", "TIMED_OUT"}
MANIFEST = Path(__file__).parent / "scenarios/story-0158-specificity-v1/manifest.json"
MANIFEST_DATA = json.loads(MANIFEST.read_text())
EXPECTED_SCENARIOS = {scenario["scenarioId"] for scenario in MANIFEST_DATA["scenarios"]}
EXPECTED_REPETITIONS = MANIFEST_DATA["repetitions"]
PROJECTION_SCALAR_KEYS = {
    "status", "budget", "usedTokens", "selectedCount", "candidateCount",
    "discardedCount", "truncated", "measurementUnit", "measurementStatus",
    "projectionDigest", "warningCount",
}
PROJECTION_COUNTER_KEYS = {
    "usedTokens", "selectedCount", "candidateCount", "discardedCount", "warningCount",
}
PROVIDER_OUTCOME_KEYS = {
    "status", "visibility", "outcomeSource", "traceCount", "visibilityEvidence",
    "validationStatuses", "incompleteReasons",
}
PROVIDER_BUDGET_KEYS = {
    "outputLimit", "inputEstimate", "modelCapacity", "complexityClass",
    "measurementUnit", "measurementStatus", "preflightOutcome",
    "outputLimitSource", "targetOutputLimit", "requiredOutputLimit",
    "promptPolicyVersion", "completionPolicyVersion", "measurementCertainty",
}
PROVIDER_STATUSES = {"COMPLETED", "FAILED", "AUTHORIZED_NOT_YET_MEASURED"}
PROVIDER_VISIBILITIES = {"UNKNOWN"}
PROVIDER_OUTCOME_SOURCES = {"AI_INTERACTION_TRACE"}
MAX_ELAPSED_MS = 86_400_000
READINESS_TERMINAL_STATUSES = {"COMPLETED", "FAILED", "TIMED_OUT"}
REVISION_PATTERN = re.compile(r"[0-9a-f]{7,64}")


def bounded_scalar(value: Any) -> bool:
    return (
        isinstance(value, bool)
        or type(value) is int and value >= 0
        or isinstance(value, str) and len(value) <= 160
    )


def valid_revision(value: Any) -> bool:
    return isinstance(value, str) and REVISION_PATTERN.fullmatch(value) is not None


def capture_fields_valid(row: dict[str, Any], expected_revision: str | None = None) -> bool:
    try:
        return _capture_fields_valid(row, expected_revision)
    except (AttributeError, KeyError, TypeError, ValueError):
        return False


def readiness_failures(
    row: dict[str, Any],
    expected_revision: str,
    seen_task_ids: set[str] | None = None,
    seen_identities: set[tuple[Any, Any]] | None = None,
) -> list[str]:
    failures: list[str] = []
    task_id = row.get("taskId")
    scenario = row.get("scenario")
    repetition = row.get("repetition")
    identity = (scenario, repetition) if isinstance(scenario, str) and type(repetition) is int else None
    raw_freshness = row.get("freshness") or {}
    freshness = raw_freshness if isinstance(raw_freshness, dict) else {}
    raw_source_revision = freshness.get("sourceRevision") or {}
    source_revision = raw_source_revision if isinstance(raw_source_revision, dict) else {}
    raw_ledger = row.get("lossAccounting") or {}
    ledger = raw_ledger if isinstance(raw_ledger, dict) else {}
    provider = ledger.get("providerOutcome")

    if not isinstance(task_id, str) or not 1 <= len(task_id) <= 128:
        failures.append("TASK_ID_MISSING_OR_INVALID")
    elif seen_task_ids is not None and task_id in seen_task_ids:
        failures.append("TASK_ID_REUSED")
    if not isinstance(scenario, str) or scenario not in EXPECTED_SCENARIOS or type(repetition) is not int:
        failures.append("SCENARIO_REPETITION_INVALID")
    elif not 1 <= repetition <= EXPECTED_REPETITIONS:
        failures.append("REPETITION_OUT_OF_RANGE")
    elif seen_identities is not None and identity in seen_identities:
        failures.append("SCENARIO_REPETITION_DUPLICATED")
    if row.get("status") not in READINESS_TERMINAL_STATUSES:
        failures.append("TERMINAL_STATUS_MISSING")
    if not valid_revision(expected_revision):
        failures.append("TARGET_REVISION_INVALID")
    if not valid_revision(source_revision.get("revision")) or source_revision.get("revision") != expected_revision:
        failures.append("TARGET_REVISION_MISMATCH")
    if not isinstance(row.get("contextDigest"), str) or re.fullmatch(r"[0-9a-f]{64}", row["contextDigest"]) is None:
        failures.append("CONTEXT_DIGEST_MISSING_OR_INVALID")
    if not isinstance(row.get("projectionDigest"), str) or re.fullmatch(r"[0-9a-f]{64}", row["projectionDigest"]) is None:
        failures.append("PROJECTION_DIGEST_MISSING_OR_INVALID")
    if not isinstance(ledger, dict) or ledger.get("version") != "story-context-loss-ledger-v1":
        failures.append("LOSS_LEDGER_MISSING_OR_INVALID")
    if not isinstance(ledger.get("digest"), str) or re.fullmatch(r"[0-9a-f]{64}", ledger.get("digest", "")) is None:
        failures.append("LOSS_LEDGER_DIGEST_MISSING_OR_INVALID")
    if not isinstance(provider, dict) or not provider.get("status"):
        failures.append("PROVIDER_OUTCOME_MISSING")
    if not valid_evaluation_telemetry(
            ledger.get("evaluationTelemetry"), expected_revision, row.get("taskId"),
            row.get("contextDigest"), row.get("projectionDigest"),
            (ledger.get("counts") or {}).get("candidateCount")):
        failures.append("EVALUATION_TELEMETRY_MISSING_OR_INVALID")
    expected_budget = row.get("expectedContextBudget")
    observed_budget = row.get("observedContextBudget")
    if type(expected_budget) is not int or type(observed_budget) is not int or expected_budget != observed_budget:
        failures.append("CONTEXT_BUDGET_MISMATCH")
    if row.get("status") == "COMPLETED" and row.get("analysisAvailable") is not True:
        failures.append("COMPLETED_ANALYSIS_MISSING")
    return failures


def readiness_report(rows: list[dict[str, Any]], expected_revision: str) -> dict[str, Any]:
    seen_task_ids: set[str] = set()
    seen_identities: set[tuple[Any, Any]] = set()
    failures: Counter[str] = Counter()
    rows_report: list[dict[str, Any]] = []
    ready_count = 0
    for row in rows:
        row_failures = readiness_failures(row, expected_revision, seen_task_ids, seen_identities)
        if not row_failures and (
            row.get("validCapture") is not True
            or not capture_fields_valid(row, expected_revision)
        ):
            row_failures.append("CAPTURE_CONTRACT_INVALID")
        task_id = row.get("taskId")
        scenario = row.get("scenario")
        repetition = row.get("repetition")
        identity = (scenario, repetition) if isinstance(scenario, str) and type(repetition) is int else None
        if isinstance(task_id, str):
            seen_task_ids.add(task_id)
        if identity is not None:
            seen_identities.add(identity)
        if not row_failures:
            ready_count += 1
        for failure in row_failures:
            failures[failure] += 1
        if len(rows_report) < 256:
            rows_report.append({
                "scenario": str(row.get("scenario", ""))[:80],
                "repetition": row.get("repetition") if type(row.get("repetition")) is int else None,
                "taskId": task_id[:128] if isinstance(task_id, str) else None,
                "status": str(row.get("status", ""))[:40],
                "failureCodes": row_failures,
            })
    return {
        "expectedRevision": expected_revision[:64] if isinstance(expected_revision, str) else "",
        "captureCount": len(rows),
        "readyCaptureCount": ready_count,
        "failureCount": len(rows) - ready_count,
        "campaignEvaluable": len(rows) > 0 and ready_count == len(rows),
        "failureReasons": dict(sorted(failures.items())),
        "rows": rows_report,
    }


def _capture_fields_valid(row: dict[str, Any], expected_revision: str | None = None) -> bool:
    freshness = row.get("freshness") or {}
    source_revision = freshness.get("sourceRevision") or {}
    ledger = row.get("lossAccounting") or {}
    counts = ledger.get("counts") or {}
    required_counts = {
        "candidateCount", "uniqueCandidateCount", "duplicateCandidateCount",
        "rankedCount", "selectedCount", "enrichedCount", "projectedCount",
        "groundedCount", "providerVisibleCount", "discardedCount",
    }
    reasons = ledger.get("lossReasons") or {}
    projection = ledger.get("projection") or {}
    provider = ledger.get("providerOutcome") or {}
    telemetry = ledger.get("evaluationTelemetry")
    warnings = projection.get("warnings") or []
    validation_statuses = provider.get("validationStatuses")
    incomplete_reasons = provider.get("incompleteReasons")
    budgets = ledger.get("budgets") or {}
    snapshot_budget = row.get("snapshotBudget") or {}
    provider_budget = snapshot_budget.get("providerBudgetAccounting") or {}
    observed_budget = row.get("observedContextBudget")
    confidence = row.get("confidence")
    elapsed_ms = row.get("elapsedMs")
    return bool(
        isinstance(row.get("taskId"), str)
        and 1 <= len(row["taskId"]) <= 128
        and row.get("scenario") in EXPECTED_SCENARIOS
        and type(row.get("repetition")) is int
        and 1 <= row["repetition"] <= EXPECTED_REPETITIONS
        and row.get("status") in TERMINAL_STATUSES
        and type(elapsed_ms) is int and 0 <= elapsed_ms <= MAX_ELAPSED_MS
        and (observed_budget is None or type(observed_budget) is int and observed_budget >= 0)
        and (confidence is None or confidence in {"HIGH", "MEDIUM", "LOW"})
        and isinstance(source_revision.get("revision"), str)
        and source_revision["revision"] not in {"UNKNOWN", "UNSPECIFIED"}
        and len(source_revision["revision"]) >= 7
        and re.fullmatch(r"[0-9a-f]{7,64}", source_revision["revision"]) is not None
        and (expected_revision is None or source_revision["revision"] == expected_revision)
        and isinstance(row.get("contextDigest"), str)
        and re.fullmatch(r"[0-9a-f]{64}", row["contextDigest"]) is not None
        and isinstance(row.get("projectionDigest"), str)
        and re.fullmatch(r"[0-9a-f]{64}", row["projectionDigest"]) is not None
        and isinstance(counts, dict)
        and required_counts.issubset(counts)
        and all(type(counts[key]) is int and counts[key] >= 0 for key in required_counts)
        and ledger.get("version") == "story-context-loss-ledger-v1"
        and isinstance(ledger.get("digest"), str)
        and re.fullmatch(r"[0-9a-f]{64}", ledger["digest"]) is not None
        and ledger.get("maximumDetailEntries") == 256
        and type(ledger.get("detailOverflowCount")) is int
        and ledger["detailOverflowCount"] >= 0
        and valid_evaluation_telemetry(
            telemetry, expected_revision, row.get("taskId"), row.get("contextDigest"),
            row.get("projectionDigest"), counts.get("candidateCount"))
        and isinstance(budgets, dict)
        and len(budgets) <= 32
        and all(isinstance(key, str) and len(key) <= 80 and bounded_scalar(value)
                 for key, value in budgets.items())
        and isinstance(reasons, dict)
        and len(reasons) <= 64
        and all(isinstance(key, str) and len(key) <= 100
                and type(value) is int and value >= 0 for key, value in reasons.items())
        and isinstance(projection, dict)
        and all(key in PROJECTION_SCALAR_KEYS or key == "warnings" for key in projection)
        and all(
            type(value) is int and value >= 0
            if key in PROJECTION_COUNTER_KEYS
            else bounded_scalar(value)
            for key, value in projection.items() if key != "warnings"
        )
        and projection.get("projectionDigest") == row["projectionDigest"]
        and isinstance(warnings, list)
        and len(warnings) <= 32
        and all(isinstance(item, dict) and isinstance(item.get("code"), str)
                and isinstance(item.get("message"), str)
                and len(item["code"]) <= 80 and len(item["message"]) <= 160 for item in warnings)
        and isinstance(provider, dict)
        and all(key in PROVIDER_OUTCOME_KEYS for key in provider)
        and isinstance(provider.get("status"), str)
        and provider["status"] in PROVIDER_STATUSES
        and isinstance(provider.get("visibility"), str)
        and provider["visibility"] in PROVIDER_VISIBILITIES
        and isinstance(provider.get("outcomeSource"), str)
        and provider["outcomeSource"] in PROVIDER_OUTCOME_SOURCES
        and ("visibilityEvidence" not in provider
             or isinstance(provider["visibilityEvidence"], str)
             and len(provider["visibilityEvidence"]) <= 160)
        and ("traceCount" not in provider
             or type(provider["traceCount"]) is int and 0 <= provider["traceCount"] <= 256)
        and ("validationStatuses" not in provider or (
            isinstance(validation_statuses, list)
            and len(validation_statuses) <= 16
            and all(isinstance(value, str) and len(value) <= 40 for value in validation_statuses)
        ))
        and ("incompleteReasons" not in provider or (
            isinstance(incomplete_reasons, list)
            and len(incomplete_reasons) <= 16
            and all(isinstance(value, str) and len(value) <= 160 for value in incomplete_reasons)
        ))
        and isinstance(snapshot_budget, dict)
        and isinstance(provider_budget, dict)
        and all(key in PROVIDER_BUDGET_KEYS for key in provider_budget)
        and all(bounded_scalar(value) for value in provider_budget.values())
        and ("preflightOutcome" not in provider_budget
             or isinstance(provider_budget["preflightOutcome"], str)
             and len(provider_budget["preflightOutcome"]) <= 40)
        and isinstance(freshness.get("state"), str)
        and len(freshness["state"]) <= 32
        and isinstance(source_revision.get("kind"), str)
        and len(source_revision["kind"]) <= 40
        and isinstance(source_revision.get("project"), str)
        and len(source_revision["project"]) <= 100
    )


def valid_evaluation_telemetry(
    value: Any,
    expected_revision: str | None = None,
    expected_task_id: str | None = None,
    expected_context_digest: str | None = None,
    expected_projection_digest: str | None = None,
    expected_candidate_count: int | None = None,
) -> bool:
    if not isinstance(value, dict):
        return False
    if value.get("version") != "story-context-evaluation-telemetry-v1":
        return False
    if not isinstance(value.get("digest"), str) or re.fullmatch(r"[0-9a-f]{64}", value["digest"]) is None:
        return False
    for field in ("stageCounts", "trustCounts"):
        counts = value.get(field)
        if not isinstance(counts, dict) or len(counts) > 128:
            return False
        if not all(
            isinstance(key, str) and 1 <= len(key) <= 100
            and type(count) is int and count >= 0
            for key, count in counts.items()
        ):
            return False
        allowed_stages = {"CANDIDATE", "SELECTED", "REJECTED", "PROJECTED", "GROUNDED"}
        allowed_trust = {"TRUSTED", "HUMAN_AUTHORED", "TECHNICAL_EVIDENCE", "SYSTEM_METADATA",
                         "UNVALIDATED", "TRANSIENT_AI", "UNKNOWN"}
        for key in counts:
            prefix, separator, stage = key.rpartition(":")
            if not separator or not prefix or stage not in allowed_stages:
                return False
            if field == "trustCounts" and prefix not in allowed_trust:
                return False
    grounding = value.get("grounding")
    if not isinstance(grounding, dict) or any(
        type(grounding.get(key)) is not int or grounding[key] < 0
        for key in ("authorizedCount", "projectedCount", "projectedGroundedCount")
    ):
        return False
    if not grounding["projectedGroundedCount"] <= grounding["projectedCount"] <= grounding["authorizedCount"]:
        return False
    fixture = value.get("candidateFixture")
    if not isinstance(fixture, dict) or fixture.get("version") != "story-context-candidate-fixture-v1":
        return False
    if (not isinstance(fixture.get("taskId"), str) or not fixture["taskId"].strip()
            or not isinstance(fixture.get("targetRevision"), str) or not fixture["targetRevision"].strip()
            or not isinstance(fixture.get("contextDigest"), str)
            or re.fullmatch(r"[0-9a-f]{64}", fixture["contextDigest"]) is None
            or not isinstance(fixture.get("projectionDigest"), str)
            or re.fullmatch(r"[0-9a-f]{64}", fixture["projectionDigest"]) is None):
        return False
    if (expected_revision is not None and fixture["targetRevision"] != expected_revision
            or expected_task_id is not None and fixture["taskId"] != expected_task_id
            or expected_context_digest is not None and fixture["contextDigest"] != expected_context_digest
            or expected_projection_digest is not None and fixture["projectionDigest"] != expected_projection_digest):
        return False
    candidates = fixture.get("candidates")
    if (not isinstance(candidates, list) or len(candidates) > 4096
            or not isinstance(fixture.get("digest"), str)
            or re.fullmatch(r"[0-9a-f]{64}", fixture["digest"]) is None):
        return False
    if expected_candidate_count is not None and len(candidates) != expected_candidate_count:
        return False
    references = []
    for candidate in candidates:
        if (not isinstance(candidate, dict)
                or not isinstance(candidate.get("reference"), str)
                or not 1 <= len(candidate["reference"]) <= 512
                or candidate.get("kind") not in {
                    "ANALYSIS", "CHALLENGE", "CHANGED_FILE", "COMMIT", "CONFIGURATION_FILES",
                    "CONFIG_FILE", "DECISION", "ENGINEERING_STORY", "FACT", "FILE_EXTENSIONS",
                    "INSIGHT", "MODULE", "MODULE_SUMMARY", "SOURCE_DIRECTORIES", "SOURCE_FILE",
                    "TEST_DIRECTORIES", "TEST_FILE"}
                or type(candidate.get("score")) is not int
                or not -(2**31) <= candidate["score"] <= 2**31 - 1
                or candidate.get("trust") not in {
                    "TRUSTED", "HUMAN_AUTHORED", "TECHNICAL_EVIDENCE", "SYSTEM_METADATA",
                    "UNVALIDATED", "TRANSIENT_AI", "UNKNOWN"}
                or candidate.get("reason") not in {
                    "CATEGORY_CONCENTRATION_LIMIT", "EVIDENCE_ITEM_BUDGET_EXCEEDED",
                    "TOKEN_BUDGET_EXCEEDED", "INSUFFICIENT_RELEVANCE", "SELECTED_BY_CATEGORY_FLOOR", "SELECTED_BY_DIVERSITY",
                    "SELECTED_BY_RANK", "SELECTED_BY_STRONG_RELEVANCE", "SELECTED_BY_REQUESTED_FILE", "NOT_RECORDED"}
                or (candidate.get("path") is not None
                    and (not isinstance(candidate["path"], str)
                         or not 1 <= len(candidate["path"]) <= 160
                         or not _canonical_path(candidate["path"])))):
            return False
        if any(type(candidate.get(key)) is not bool
               for key in ("grounded", "selected", "projected")):
            return False
        if candidate["projected"] and not candidate["selected"]:
            return False
        if candidate["grounded"] and not candidate["projected"]:
            return False
        references.append(candidate["reference"])
    if len(set(references)) != len(references) or _fixture_digest(fixture) != fixture["digest"]:
        return False
    outcomes = value.get("requestedFileOutcomes")
    if not isinstance(outcomes, list) or len(outcomes) > 32:
        return False
    return all(
        isinstance(item, dict)
        and isinstance(item.get("path"), str) and 1 <= len(item["path"]) <= 160
        and _canonical_path(item["path"])
        and all(type(item.get(key)) is bool for key in ("found", "selected", "projected", "grounded"))
        and (item.get("absenceReason") is None or
             isinstance(item.get("absenceReason"), str) and len(item["absenceReason"]) <= 80)
        and (not item.get("selected") or item.get("found"))
        and (not item.get("projected") or item.get("selected"))
        and (not item.get("grounded") or item.get("projected"))
        and ((item.get("absenceReason") is None) == item.get("grounded"))
        for item in outcomes
    ) and _telemetry_digest(value) == value["digest"]


def _telemetry_digest(value: dict[str, Any]) -> str:
    result = value["version"]
    result += "|stage:" + "".join(f"{key}={value['stageCounts'][key]};" for key in sorted(value["stageCounts"]))
    result += "|trust:" + "".join(f"{key}={value['trustCounts'][key]};" for key in sorted(value["trustCounts"]))
    result += "|requested:" + "".join(
        f"{item['path']},{str(item['found']).lower()},{str(item['selected']).lower()},"
        f"{str(item['projected']).lower()},{str(item['grounded']).lower()},"
        f"{item.get('absenceReason') if item.get('absenceReason') is not None else '-'};"
        for item in value["requestedFileOutcomes"]
    )
    grounding = value["grounding"]
    result += f"|grounding:{grounding['authorizedCount']},{grounding['projectedCount']},{grounding['projectedGroundedCount']}"
    fixture = value["candidateFixture"]
    result += f"|correlation:{fixture['taskId']},{fixture['targetRevision']},{fixture['contextDigest']},{fixture['projectionDigest']}"
    result += f"|fixture:{fixture['digest']}"
    return hashlib.sha256(result.encode()).hexdigest()


def _fixture_digest(fixture: dict[str, Any]) -> str:
    rows = sorted(fixture["candidates"], key=lambda item: item["reference"])
    canonical = "|".join(
        ",".join(
            f"{len(value.encode('utf-8'))}:{value}"
            for value in (row["reference"], row["kind"], row.get("path") or "-",
                          str(row["score"]), row["trust"], str(row["grounded"]).lower(),
                          str(row["selected"]).lower(), str(row["projected"]).lower(), row["reason"])
        )
        for row in rows
    )
    return hashlib.sha256(canonical.encode()).hexdigest()


def _canonical_path(value: str) -> bool:
    return ("\\" not in value and not value.startswith("/") and "//" not in value
            and value != ".." and "../" not in value and not value.endswith("/.."))


def valid_row(row: dict[str, Any], expected_revision: str | None = None) -> bool:
    return (
        row.get("validCapture") is True
        and isinstance(row.get("readiness"), dict)
        and row["readiness"].get("status") == "READY"
        and capture_fields_valid(row, expected_revision)
    )


def summarize(rows: list[dict[str, Any]], expected_revision: str | None = None) -> dict[str, Any]:
    task_ids = [row.get("taskId") for row in rows if row.get("taskId")]
    duplicate_task_ids = sorted({task_id for task_id in task_ids if task_ids.count(task_id) > 1})
    if duplicate_task_ids:
        raise ValueError(f"duplicate task IDs in captures: {duplicate_task_ids}")
    identities = [(row.get("scenario"), row.get("repetition")) for row in rows]
    duplicate_identities = sorted({identity for identity in identities if identities.count(identity) > 1})
    if duplicate_identities:
        raise ValueError(f"duplicate scenario repetitions in captures: {duplicate_identities}")
    valid = [row for row in rows if valid_row(row, expected_revision)]
    invalid = len(rows) - len(valid)
    revisions = {
        (row.get("freshness") or {}).get("sourceRevision", {}).get("revision")
        for row in valid
    }
    statuses = Counter(str(row.get("status")) for row in rows)
    scenarios: dict[str, dict[str, Any]] = defaultdict(
        lambda: {
            "rows": 0,
            "validRows": 0,
            "validAnalysis": 0,
            "invalidCaptures": 0,
            "statuses": Counter(),
            "providerOutcomes": Counter(),
            "preflightOutcomes": Counter(),
            "projectionWarnings": Counter(),
            "lossReasons": Counter(),
            "stageCounts": Counter(),
            "trustCounts": Counter(),
            "requestedFileOutcomes": Counter(),
        }
    )
    loss_totals: Counter[str] = Counter()
    count_totals: Counter[str] = Counter()
    provider_statuses: Counter[str] = Counter()
    preflight_statuses: Counter[str] = Counter()
    projection_warnings: Counter[str] = Counter()
    stage_counts: Counter[str] = Counter()
    trust_counts: Counter[str] = Counter()
    requested_file_outcomes: Counter[str] = Counter()
    elapsed: list[int] = []
    for row in rows:
        scenario = scenarios[str(row.get("scenario"))[:80]]
        scenario["rows"] += 1
        scenario["statuses"][str(row.get("status"))] += 1
        row_is_valid = valid_row(row, expected_revision)
        if not row_is_valid:
            scenario["invalidCaptures"] += 1
            continue
        raw_ledger = row.get("lossAccounting") or {}
        raw_provider = raw_ledger.get("providerOutcome") or {}
        if isinstance(raw_provider, dict) and isinstance(raw_provider.get("status"), str):
            provider_statuses[raw_provider["status"]] += 1
            scenario["providerOutcomes"][raw_provider["status"]] += 1
        raw_preflight = (row.get("snapshotBudget") or {}).get("providerBudgetAccounting", {})
        if isinstance(raw_preflight, dict) and isinstance(raw_preflight.get("preflightOutcome"), str):
            preflight_statuses[raw_preflight["preflightOutcome"]] += 1
            scenario["preflightOutcomes"][raw_preflight["preflightOutcome"]] += 1
        for warning in raw_ledger.get("projection", {}).get("warnings", []):
            if isinstance(warning, dict) and isinstance(warning.get("message"), str):
                projection_warnings[warning["message"]] += 1
                scenario["projectionWarnings"][warning["message"]] += 1
        scenario["validRows"] += 1
        scenario["validAnalysis"] += int(
            row.get("status") == "COMPLETED" and row.get("analysisAvailable")
        )
        ledger = row["lossAccounting"]
        telemetry = ledger.get("evaluationTelemetry") or {}
        for name, count in (telemetry.get("stageCounts") or {}).items():
            if type(count) is int and count >= 0:
                stage_counts[str(name)] += count
                scenario["stageCounts"][str(name)] += count
        for name, count in (telemetry.get("trustCounts") or {}).items():
            if type(count) is int and count >= 0:
                trust_counts[str(name)] += count
                scenario["trustCounts"][str(name)] += count
        for outcome in telemetry.get("requestedFileOutcomes") or []:
            if not isinstance(outcome, dict) or not isinstance(outcome.get("path"), str):
                continue
            state = "GROUNDED" if outcome.get("grounded") else (
                "PROJECTED" if outcome.get("projected") else (
                    "SELECTED" if outcome.get("selected") else (
                        "FOUND" if outcome.get("found") else "NOT_FOUND")))
            key = f"{outcome['path']}:{state}"
            requested_file_outcomes[key] += 1
            scenario["requestedFileOutcomes"][key] += 1
        for reason, count in ledger.get("lossReasons", {}).items():
            if type(count) is int and count >= 0:
                loss_totals[str(reason)] += count
                scenario["lossReasons"][str(reason)] += count
        for name, count in ledger.get("counts", {}).items():
            if type(count) is int and count >= 0:
                count_totals[str(name)] += count
        if type(row.get("elapsedMs")) is int and row["elapsedMs"] >= 0:
            elapsed.append(row["elapsedMs"])

    def normalize_scenario(value: dict[str, Any]) -> dict[str, Any]:
        return {
            "rows": value["rows"],
            "validRows": value["validRows"],
            "validAnalysis": value["validAnalysis"],
            "invalidCaptures": value["invalidCaptures"],
            "statuses": dict(sorted(value["statuses"].items())),
            "taskStatuses": dict(sorted(value["statuses"].items())),
            "providerOutcomes": dict(sorted(value["providerOutcomes"].items())),
            "preflightOutcomes": dict(sorted(value["preflightOutcomes"].items())),
            "projectionWarnings": dict(sorted(value["projectionWarnings"].items())),
            "lossReasons": dict(sorted(value["lossReasons"].items())),
            "stageCounts": dict(sorted(value["stageCounts"].items())),
            "trustCounts": dict(sorted(value["trustCounts"].items())),
            "requestedFileOutcomes": dict(sorted(value["requestedFileOutcomes"].items())),
        }

    if len(revisions) > 1:
        raise ValueError(f"mixed repository revisions in valid captures: {sorted(revisions)}")

    return {
        "captureCount": len(rows),
        "validCaptureCount": len(valid),
        "invalidCaptureCount": invalid,
        "revisionSet": sorted(value for value in revisions if value),
        "singleRevision": len(revisions) == 1 and None not in revisions,
        "statuses": dict(sorted(statuses.items())),
        "taskStatuses": dict(sorted(statuses.items())),
        "providerOutcomes": dict(sorted(provider_statuses.items())),
        "preflightOutcomes": dict(sorted(preflight_statuses.items())),
        "projectionWarnings": dict(sorted(projection_warnings.items())),
        "evaluationTelemetry": {
            "stageCounts": dict(sorted(stage_counts.items())),
            "trustCounts": dict(sorted(trust_counts.items())),
            "requestedFileOutcomes": dict(sorted(requested_file_outcomes.items())),
        },
        "counts": dict(sorted(count_totals.items())),
        "lossReasons": dict(sorted(loss_totals.items())),
        "elapsedMs": {
            "count": len(elapsed),
            "min": min(elapsed) if elapsed else None,
            "max": max(elapsed) if elapsed else None,
            "average": round(sum(elapsed) / len(elapsed)) if elapsed else None,
        },
        "scenarios": {
            key: normalize_scenario(value) for key, value in sorted(scenarios.items())
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", type=Path)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rows = load_rows(args.captures)
    readiness = readiness_report(rows, args.revision)
    if not readiness["campaignEvaluable"]:
        report = {"campaignEvaluable": False, "readiness": readiness}
    else:
        report = summarize(rows, args.revision)
        report["campaignEvaluable"] = True
        report["readiness"] = readiness
    encoded = json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True)
    if args.output:
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
