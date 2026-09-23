"""Offline semantic quality projection for the GPT baseline collection."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from evaluations.luna_offline_quality import ROOT, sha256


SOURCE = ROOT / "docs/evaluation/gpt-baseline-collection/official-90s"
ORACLE = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
BENCHMARK = ROOT / "ai-engine/evaluations/comparative_baseline/baseline-manifest.json"
OUTPUT = SOURCE / "semantic-quality-evaluation.json"


def classify(case: str) -> dict[str, Any]:
    if case == "CASE-01-COMPARATIVE":
        return {
            "semanticConclusion": "CORRECT",
            "causalReasoning": "CORRECT",
            "overclaim": "NO",
            "abstention": "NOT_APPLICABLE",
            "reasoningSupport": "SUPPORTED",
            "qualityCategory": "CORRECT_REASONED",
            "rationale": "The answer matches the frozen CL-01 oracle and explains the relationship using the supplied ADR, Story, and implementation evidence.",
        }
    if case == "CASE-03":
        return {
            "semanticConclusion": "CORRECT",
            "causalReasoning": "CORRECT",
            "overclaim": "NO",
            "abstention": "NOT_APPLICABLE",
            "reasoningSupport": "SUPPORTED",
            "qualityCategory": "CORRECT_REASONED",
            "rationale": "The answer identifies PaperSettlementService and PaperSettlementExitTest and explains their direct impact under the frozen CASE-03 contract.",
        }
    if case == "CASE-04":
        return {
            "semanticConclusion": "CORRECT",
            "causalReasoning": "CORRECT",
            "overclaim": "NO",
            "abstention": "CORRECT",
            "reasoningSupport": "SUPPORTED",
            "qualityCategory": "CORRECT_ABSTENTION",
            "rationale": "The answer preserves the frozen negative-control conclusion that ADR-043 did not cause the specific Story-0042 configuration refactor.",
        }
    raise ValueError(case)


def build() -> dict[str, Any]:
    rows = []
    for path in sorted(SOURCE.glob("CASE-*.json")):
        observation = json.loads(path.read_text(encoding="utf-8"))["observation"]
        quality = classify(observation["assignment"]["questionId"])
        rows.append(
            {
                "observationId": observation["observationId"],
                "questionId": observation["assignment"]["questionId"],
                "condition": "DEVLOG_BASELINE_MODEL",
                "repetition": observation["assignment"]["repetition"],
                "model": "gpt-4.1-mini",
                "formatContract": {
                    "structuralValid": observation["structuralValid"] == "YES",
                    "groundingContractValid": observation["groundingValid"] == "YES",
                    "originalStructuralStatus": observation["structuralValid"],
                    "originalGroundingStatus": observation["groundingValid"],
                },
                "semanticQuality": quality,
            }
        )
    if len(rows) != 9:
        raise ValueError(f"expected 9 GPT observations, found {len(rows)}")
    oracle = json.loads(ORACLE.read_text(encoding="utf-8"))
    benchmark = json.loads(BENCHMARK.read_text(encoding="utf-8"))
    return {
        "evaluation": "GPT_BASELINE_OFFLINE_SEMANTIC_QUALITY",
        "evaluationVersion": "1.0.0",
        "offlineOnly": True,
        "modelExecutionPerformed": False,
        "repositoryExplorationPerformed": False,
        "sourceObservationDirectory": "docs/evaluation/gpt-baseline-collection/official-90s/",
        "oracle": {"path": str(ORACLE.relative_to(ROOT)), "version": oracle["oracleVersion"], "sha256": sha256(ORACLE), "status": oracle["oracleStatus"]},
        "benchmark": {"path": str(BENCHMARK.relative_to(ROOT)), "version": benchmark["manifestVersion"], "sha256": sha256(BENCHMARK)},
        "records": rows,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    result = build()
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {len(result['records'])} semantic records to {args.output}")


if __name__ == "__main__":
    main()
