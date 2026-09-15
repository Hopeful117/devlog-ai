"""Generate deterministic human-readable Oracle and baseline reports."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def _manifest_rows(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item["artifact"]: item for item in manifest.get("artifactResolutions", [])}


def oracle_report(benchmark: dict[str, Any], manifest: dict[str, Any]) -> str:
    rows = _manifest_rows(manifest)
    lines = [
        "# Oracle Validation Report v1", "", "## Status", "",
        "`MECHANICAL_REVIEW_COMPLETE - HUMAN_SEMANTIC_APPROVAL_REQUIRED`", "",
        f"Pinned revision: `{benchmark['repositoryRevision']}`", "",
        "Mechanical artifact resolutions are observations from the live DevLog context path. `NOT_FOUND` means that this product projection did not return the artifact; it does not prove repository absence.", "",
        "| Oracle ID | Case | Type | Claim/Expectation | Evidence references | Resolved artifacts | Proposed classification | Reason | Human decision |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    for case in benchmark["cases"]:
        resolved = [artifact for artifact in case["expectedEvidence"] if rows.get(artifact, {}).get("status") == "RESOLVED"]
        for item in case["expectedCausalLinks"]:
            refs = ", ".join(case["expectedEvidence"])
            lines.append(f"| `{item['id']}` | {case['caseId']} | CAUSAL_LINK | {item['from']} -> {item['to']} | {refs} | {', '.join(resolved) or 'NONE'} | {item['strength']} | Frozen candidate classification; inspect cited artifacts and distinguish causality from co-occurrence. | `TODO: HUMAN DECISION` |")
        for identifier, label, values in [
            (None, "CONSTRAINT", case["expectedConstraintIds"]),
            (None, "COMPONENT", case["expectedAffectedComponentIds"]),
            (None, "TEST", case["expectedAffectedTestIds"]),
        ]:
            for value in values:
                lines.append(f"| `{value}` | {case['caseId']} | {label} | Frozen benchmark expectation | {', '.join(case['expectedEvidence'])} | {', '.join(resolved) or 'NONE'} | CANDIDATE | Mechanical support is listed above; semantic scope remains human-reviewed. | `TODO: HUMAN DECISION` |")
    lines.extend(["", "## HUMAN ORACLE REVIEW REQUIRED", "", "Approve or correct every proposed causal classification, verify each artifact identity/type at the pinned revision, and confirm CASE-04 remains `NOT_ESTABLISHED`. The harness must not fill the Human decision column."])
    return "\n".join(lines) + "\n"


def baseline_report(benchmark: dict[str, Any], capture: dict[str, Any], evaluation: dict[str, Any]) -> str:
    lines = ["# Preliminary Product Value Baseline Report v1", "", "`PRELIMINARY - FORMAL ACCEPTANCE BLOCKED`", "", "## Conditions", "", "| Case | Direct repository | DevLog |", "|---|---|---|"]
    for case in benchmark["cases"]:
        live = next(item for item in capture["cases"] if item["caseId"] == case["caseId"])
        lines.append(f"| {case['caseId']} | `NOT_EXECUTED` | `{live['status']}`; evidence={len(live.get('returnedEvidence', []))}; selected={live.get('selectedEvidenceCount')}; tokens={live.get('estimatedTokens')}; follow-ups={live.get('followUpSearches')} |")
    lines.extend(["", "## Evaluation", "", f"- Evaluator status: `{evaluation.get('status')}`", "- Oracle approval: `NO`", "- Human Utility: `NOT_MEASURED`", "- Unsupported claim rate: `NOT_MEASURED` (context-only path has no generated claims)", "- Evidence stability: `NOT_MEASURED` (single live execution)", "", "## Interpretation", "", "The live path returned bounded agent context, not a benchmark answer. It selected evidence but returned no canonical constraints, causal links or impact identifiers, and reported truncation/budget warnings. This is a preliminary observed limitation, not a formal AI utility score. The direct repository condition was not executed because no pinned Trading OS checkout is available in this workspace.", ""])
    return "\n".join(lines)


def experimental_matrix_report(evaluation: dict[str, Any]) -> str:
    """Render the comparable condition matrix without inventing missing metrics."""

    lines = [
        "# Product Evaluation Condition Matrix",
        "",
        "`CONTEXT_QUALITY_AND_INTERPRETATION_QUALITY_SEPARATED`",
        "",
        "| Metric | DIRECT_REPOSITORY | DEVLOG_CONTEXT | GROUND_TRUTH_CONTEXT |",
        "|---|---:|---:|---:|",
    ]
    rows = {
        "Context evidence recall": "contextEvidenceRecall",
        "Causal reasoning accuracy": "causalReasoningAccuracy",
        "Context constraint recall": "contextConstraintRecall",
        "Interpretation constraint recall": "interpretationConstraintRecall",
        "Context impact recall": "contextImpactRecall",
        "Interpretation impact recall": "interpretationImpactRecall",
        "Evidence-claim grounding": "evidenceClaimGrounding",
        "Unsupported inference rate": "unsupportedInferenceRate",
        "Failure attribution": "failureAttribution",
    }
    conditions = ["DIRECT_REPOSITORY", "DEVLOG_CONTEXT", "GROUND_TRUTH_CONTEXT"]
    for label, key in rows.items():
        values = []
        for condition in conditions:
            cases = evaluation.get("conditions", {}).get(condition, {}).get("cases", [])
            observed = [case.get(key) for case in cases if case.get(key) is not None]
            values.append(str(observed[0] if len(set(map(str, observed))) == 1 and observed else "NOT_MEASURED"))
        lines.append(f"| {label} | {values[0]} | {values[1]} | {values[2]} |")
    lines.extend([
        "",
        "Context metrics describe supplied evidence. Interpretation metrics describe structured output given that evidence.",
        "Human / agent utility remains separate and is not measured by this matrix.",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--evaluation", type=Path, required=True)
    parser.add_argument("--oracle-output", type=Path, required=True)
    parser.add_argument("--baseline-output", type=Path, required=True)
    args = parser.parse_args()
    benchmark = json.loads(args.benchmark.read_text(encoding="utf-8"))
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    capture = json.loads(args.capture.read_text(encoding="utf-8"))
    evaluation = json.loads(args.evaluation.read_text(encoding="utf-8"))
    args.oracle_output.write_text(oracle_report(benchmark, manifest), encoding="utf-8")
    args.baseline_output.write_text(baseline_report(benchmark, capture, evaluation), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
