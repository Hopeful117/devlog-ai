"""Aggregate bounded Story Context runtime captures.

The input is JSONL produced by ``story_context_budget_live.py``. The report
rejects rows without a task, revision, or digests and emits only aggregate
counts and normalized bounded diagnostics.
"""

from __future__ import annotations

import argparse
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


def bounded_scalar(value: Any) -> bool:
    return (
        isinstance(value, bool)
        or type(value) is int and value >= 0
        or isinstance(value, str) and len(value) <= 160
    )


def capture_fields_valid(row: dict[str, Any], expected_revision: str | None = None) -> bool:
    try:
        return _capture_fields_valid(row, expected_revision)
    except (AttributeError, KeyError, TypeError, ValueError):
        return False


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


def valid_row(row: dict[str, Any], expected_revision: str | None = None) -> bool:
    return row.get("validCapture") is True and capture_fields_valid(row, expected_revision)


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
        }
    )
    loss_totals: Counter[str] = Counter()
    count_totals: Counter[str] = Counter()
    provider_statuses: Counter[str] = Counter()
    preflight_statuses: Counter[str] = Counter()
    projection_warnings: Counter[str] = Counter()
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
            "providerOutcomes": dict(sorted(value["providerOutcomes"].items())),
            "preflightOutcomes": dict(sorted(value["preflightOutcomes"].items())),
            "projectionWarnings": dict(sorted(value["projectionWarnings"].items())),
            "lossReasons": dict(sorted(value["lossReasons"].items())),
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
        "providerOutcomes": dict(sorted(provider_statuses.items())),
        "preflightOutcomes": dict(sorted(preflight_statuses.items())),
        "projectionWarnings": dict(sorted(projection_warnings.items())),
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
    report = summarize(load_rows(args.captures), args.revision)
    encoded = json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True)
    if args.output:
        args.output.write_text(encoded + "\n", encoding="utf-8")
    print(encoded)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
