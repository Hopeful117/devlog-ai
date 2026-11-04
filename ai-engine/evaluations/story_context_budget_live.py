"""Run the bounded live Story Context budget comparison.

The runner treats the authenticated snapshot as authoritative for budget and
freshness validation. A direct execution response without an analysis result is
recorded as an invalid capture instead of being counted as a successful run.
"""

from __future__ import annotations

import argparse
import json
import time
import uuid
from pathlib import Path
from typing import Any

from story_context_specificity_live import McpClient
from story_context_runtime_report import capture_fields_valid


PROFILES = {
    "B6000": 6000,
    "B9000": 9000,
    "B12000": 12000,
    "B24000": 24000,
    "B32000": 32000,
    "C0-current": 32000,
    "C1-selection": 32000,
    "C2-file-content": 32000,
    "C3-documents": 32000,
    "C4-envelope": 32000,
    "C5-combined": 32000,
}
SCENARIO_MANIFEST = Path(__file__).parent / (
    "scenarios/story-0158-specificity-v1/manifest.json"
)


def analysis_from_execution(execution: dict[str, Any]) -> dict[str, Any] | None:
    result = execution.get("result")
    if not isinstance(result, dict):
        return None
    analysis = result.get("analysis")
    if not isinstance(analysis, dict):
        return None
    value = analysis.get("analysis")
    return value if isinstance(value, dict) else None


def bounded_warnings(value: Any) -> list[dict[str, str]]:
    if not isinstance(value, list):
        return []
    warnings = []
    for item in value[:32]:
        if not isinstance(item, dict):
            continue
        code = item.get("code")
        message = item.get("message")
        if isinstance(code, str) and isinstance(message, str):
            warnings.append({"code": code[:80], "message": message[:160]})
    return warnings


def bounded_map(value: Any, keys: set[str]) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    return {key: value[key] for key in keys if key in value}


def bounded_scalar(value: Any, limit: int = 160) -> Any:
    if isinstance(value, bool):
        return value
    if type(value) is int and value >= 0:
        return value
    if isinstance(value, str):
        return value[:limit]
    return None


def bounded_nonnegative_int(value: Any, maximum: int = 2**31 - 1) -> int | None:
    if type(value) is int and 0 <= value <= maximum:
        return value
    return None


def bounded_confidence(value: Any) -> str | None:
    return value if value in {"HIGH", "MEDIUM", "LOW"} else None


def bounded_digest(value: Any) -> str | None:
    if isinstance(value, str) and len(value) == 64 and all(
        character in "0123456789abcdef" for character in value
    ):
        return value
    return None


def bounded_task_id(value: Any) -> str | None:
    if isinstance(value, str) and 1 <= len(value) <= 128:
        return value
    return None


def bounded_count(value: Any) -> int:
    return min(len(value), 256) if isinstance(value, list) else 0


def bounded_scalar_map(value: Any, keys: set[str]) -> dict[str, Any]:
    if not isinstance(value, dict):
        return {}
    result = {}
    for key in keys:
        if key in value:
            scalar = bounded_scalar(value[key])
            if scalar is not None:
                result[key] = scalar
    return result


def bounded_integer_map(value: Any, keys: set[str]) -> dict[str, int]:
    if not isinstance(value, dict):
        return {}
    return {
        key: item for key in keys
        if type(item := value.get(key)) is int and item >= 0
    }


def bounded_freshness(snapshot: dict[str, Any]) -> dict[str, Any]:
    raw = (snapshot.get("selectedKnowledgeSnapshot") or {}).get("freshness") or {}
    source = raw.get("sourceRevision") if isinstance(raw, dict) else None
    if not isinstance(source, dict):
        return {}
    return {
        "state": str(raw.get("state", "UNKNOWN"))[:32],
        "sourceRevision": {
            "kind": str(source.get("kind", "PROJECT_REVISION"))[:40],
            "project": str(source.get("project", ""))[:100],
            "revision": str(source.get("revision", ""))[:64],
        },
    }


PROVIDER_BUDGET_KEYS = {
    "outputLimit", "inputEstimate", "modelCapacity", "complexityClass",
    "measurementUnit", "measurementStatus", "preflightOutcome",
    "outputLimitSource", "targetOutputLimit", "requiredOutputLimit",
    "promptPolicyVersion", "completionPolicyVersion", "measurementCertainty",
}
LEDGER_BUDGET_KEYS = {
    "selectionUsedTokens", "selectionMaximumTokens", "measurementUnit",
    "measurementStatus", "promptInputEstimate", "providerOutputLimit",
    "modelCapacity", "preflightOutcome", "providerVisibilityStatus",
}
PROJECTION_KEYS = {
    "status", "budget", "usedTokens", "selectedCount", "candidateCount",
    "discardedCount", "truncated", "measurementUnit", "measurementStatus",
    "projectionDigest", "warningCount",
}
PROVIDER_OUTCOME_KEYS = {
    "status", "visibility", "outcomeSource", "traceCount", "visibilityEvidence",
}


def snapshot_budget(snapshot: dict[str, Any]) -> dict[str, Any]:
    selected = snapshot.get("selectedKnowledgeSnapshot") or {}
    accounting = selected.get("accounting") or {}
    context_snapshot = snapshot.get("contextSnapshot") or {}
    return {
        "selectedKnowledgeBudget": bounded_scalar(accounting.get("budget")),
        "selectedKnowledgeTruncated": bounded_scalar(accounting.get("truncated")),
        "selectedKnowledgeWarnings": bounded_warnings(accounting.get("warnings", [])),
        "snapshotBudget": bounded_scalar((context_snapshot.get("accounting") or {}).get("budget")),
        "snapshotTruncated": bounded_scalar((context_snapshot.get("accounting") or {}).get("truncated")),
        "providerBudgetAccounting": bounded_scalar_map(
            context_snapshot.get("budgetAccounting"), PROVIDER_BUDGET_KEYS
        ),
    }


def snapshot_loss_accounting(snapshot: dict[str, Any]) -> dict[str, Any]:
    context_snapshot = snapshot.get("contextSnapshot") or {}
    ledger = context_snapshot.get("lossAccounting") or {}
    if not isinstance(ledger, dict):
        return {}
    projection = bounded_map(ledger.get("projection"), PROJECTION_KEYS)
    if isinstance(ledger.get("projection"), dict):
        projection["warnings"] = bounded_warnings(ledger["projection"].get("warnings"))
    provider = bounded_scalar_map(ledger.get("providerOutcome"), PROVIDER_OUTCOME_KEYS)
    if isinstance(ledger.get("providerOutcome"), dict):
        for field, limit in (("validationStatuses", 40), ("incompleteReasons", 160)):
            raw_values = ledger["providerOutcome"].get(field)
            if raw_values is None:
                continue
            provider[field] = (
                [value[:limit] for value in raw_values if isinstance(value, str)][:16]
                if isinstance(raw_values, list)
                else None
            )
    raw_reasons = ledger.get("lossReasons") or {}
    reasons = {
        str(key)[:100]: value for key, value in list(raw_reasons.items())[:64]
        if isinstance(key, str) and type(value) is int and value >= 0
    } if isinstance(raw_reasons, dict) else {}
    return {
        "version": str(ledger.get("version", ""))[:64],
        "maximumDetailEntries": bounded_scalar(ledger.get("maximumDetailEntries")),
        "detailOverflowCount": bounded_scalar(ledger.get("detailOverflowCount")),
        "counts": bounded_integer_map(ledger.get("counts"), {
            "candidateCount", "uniqueCandidateCount", "duplicateCandidateCount",
            "rankedCount", "selectedCount", "enrichedCount", "projectedCount",
            "groundedCount", "providerVisibleCount", "discardedCount",
        }),
        "lossReasons": reasons,
        "budgets": bounded_scalar_map(ledger.get("budgets"), LEDGER_BUDGET_KEYS),
        "projection": {
            key: bounded_scalar(projection[key])
            for key in PROJECTION_KEYS
            if key in projection and bounded_scalar(projection[key]) is not None
        } | {"warnings": projection.get("warnings", [])},
        "providerOutcome": provider,
        "digest": str(ledger.get("digest", ""))[:64],
    }


def run(args: argparse.Namespace) -> int:
    if args.profile not in PROFILES:
        raise SystemExit(f"Unknown profile: {args.profile}; choose from {sorted(PROFILES)}")

    scenarios = json.loads(SCENARIO_MANIFEST.read_text(encoding="utf-8"))["scenarios"]
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    mcp = McpClient(Path(args.mcp_jar), Path(args.mcp_launcher))
    try:
        with output.open("w", encoding="utf-8") as stream:
            for scenario in scenarios:
                for repetition in range(1, args.repetitions + 1):
                    nonce = str(uuid.uuid4())
                    question = scenario["question"]
                    key = f"story-context-budget-{args.profile}-{nonce}"
                    started = time.perf_counter()
                    execution = mcp.tool("devlog_story_agent", {
                        "projectSlug": args.project,
                        "intent": "engineering-story-context-analysis",
                        "question": question,
                        "files": scenario.get("files", []),
                        "guidance": {},
                        "idempotencyKey": key,
                    })
                    task_id = bounded_task_id(execution.get("aiTaskId"))
                    snapshot = None
                    if task_id:
                        snapshot = mcp.tool(
                            "story_context_agent_get_snapshot_authenticated",
                            {"aiTaskId": task_id},
                        )
                    analysis = analysis_from_execution(execution)
                    budget = snapshot_budget(snapshot or {})
                    loss_accounting = snapshot_loss_accounting(snapshot or {})
                    expected = PROFILES[args.profile]
                    observed = execution.get("accounting", {}).get("budget")
                    row = {
                        "profile": args.profile,
                        "expectedContextBudget": expected,
                        "observedContextBudget": bounded_nonnegative_int(observed),
                        "scenario": scenario["scenarioId"],
                        "repetition": repetition,
                        "taskId": task_id,
                        "status": bounded_scalar(execution.get("status")),
                        "validCapture": False,
                        "elapsedMs": round((time.perf_counter() - started) * 1000),
                        "accounting": bounded_scalar_map(execution.get("accounting"), {
                            "candidateCount", "usedTokens", "discardedCount", "truncated",
                            "budget", "selectedCount",
                        }),
                        "snapshotBudget": budget,
                        "analysisAvailable": analysis is not None,
                        "confidence": bounded_confidence((analysis or {}).get("confidence")),
                        "missingInformation": bounded_count((analysis or {}).get("missingInformation")),
                        "implementationQuestions": bounded_count(
                            (analysis or {}).get("implementationQuestions")
                        ),
                        "freshness": bounded_freshness(snapshot or {}),
                        "contextDigest": bounded_digest(execution.get("contextDigest")),
                        "projectionDigest": bounded_digest(execution.get("projectionDigest")),
                        "lossAccounting": loss_accounting,
                    }
                    row["validCapture"] = capture_fields_valid(row)
                    stream.write(json.dumps(row, ensure_ascii=False) + "\n")
                    stream.flush()
                    print(json.dumps(row, ensure_ascii=False), flush=True)
    finally:
        mcp.close()
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("profile", choices=sorted(PROFILES))
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--project", default="devlog-ai")
    parser.add_argument("--repetitions", default=3, type=int)
    parser.add_argument(
        "--mcp-jar",
        default=Path(__file__).parents[2] / "mcp-server/target/mcp-server-0.0.1-SNAPSHOT.jar",
        type=Path,
    )
    parser.add_argument(
        "--mcp-launcher",
        default=Path.home() / ".config/opencode/devlog-mcp-start.sh",
        type=Path,
    )
    return run(parser.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())
