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


def snapshot_budget(snapshot: dict[str, Any]) -> dict[str, Any]:
    selected = snapshot.get("selectedKnowledgeSnapshot") or {}
    accounting = selected.get("accounting") or {}
    context_snapshot = snapshot.get("contextSnapshot") or {}
    return {
        "selectedKnowledgeBudget": accounting.get("budget"),
        "selectedKnowledgeTruncated": accounting.get("truncated"),
        "selectedKnowledgeWarnings": accounting.get("warnings", []),
        "snapshotBudget": (context_snapshot.get("accounting") or {}).get("budget"),
        "snapshotTruncated": (context_snapshot.get("accounting") or {}).get("truncated"),
        "providerBudgetAccounting": context_snapshot.get("budgetAccounting") or {},
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
                    question = (
                        f"{scenario['question']}\n\n"
                        f"Benchmark profile: {args.profile}; run nonce: {nonce}."
                    )
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
                    task_id = execution.get("aiTaskId")
                    snapshot = None
                    if task_id:
                        snapshot = mcp.tool(
                            "story_context_agent_get_snapshot_authenticated",
                            {"aiTaskId": task_id},
                        )
                    analysis = analysis_from_execution(execution)
                    budget = snapshot_budget(snapshot or {})
                    expected = PROFILES[args.profile]
                    observed = execution.get("accounting", {}).get("budget")
                    valid = (
                        execution.get("status") == "COMPLETED"
                        and analysis is not None
                        and observed == expected
                        and budget["selectedKnowledgeBudget"] == expected
                    )
                    row = {
                        "profile": args.profile,
                        "expectedContextBudget": expected,
                        "scenario": scenario["scenarioId"],
                        "repetition": repetition,
                        "taskId": task_id,
                        "status": execution.get("status"),
                        "validCapture": valid,
                        "elapsedMs": round((time.perf_counter() - started) * 1000),
                        "accounting": execution.get("accounting", {}),
                        "snapshotBudget": budget,
                        "analysisAvailable": analysis is not None,
                        "confidence": (analysis or {}).get("confidence"),
                        "missingInformation": len((analysis or {}).get("missingInformation", [])),
                        "implementationQuestions": len(
                            (analysis or {}).get("implementationQuestions", [])
                        ),
                        "freshness": (snapshot or {}).get("selectedKnowledgeSnapshot", {})
                        .get("freshness", {}),
                        "contextDigest": execution.get("contextDigest"),
                        "projectionDigest": execution.get("projectionDigest"),
                    }
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
