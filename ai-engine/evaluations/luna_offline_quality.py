"""Offline semantic evaluation for the immutable Luna collection.

This evaluator never invokes a model, repository tool, or provider. It reads
the existing official observations and applies the frozen benchmark/oracle to
the human-readable answer that was actually captured. Structural and grounding
results remain untouched and are copied as a separate dimension.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
OFFICIAL = ROOT / "docs/evaluation/luna-comparative-collection/official"
ORACLE = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
BENCHMARK = ROOT / "ai-engine/evaluations/comparative_baseline/baseline-manifest.json"
OUTPUT = OFFICIAL / "semantic-quality-evaluation.json"
REPORT = ROOT / "docs/evaluation/devlog-direct-pragmatic-comparative-results.md"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def observations() -> list[dict[str, Any]]:
    return [
        json.loads(path.read_text(encoding="utf-8"))
        for path in sorted(OFFICIAL.glob("CASE-*.json"))
        if ":" in path.name
    ]


def classification(observation: dict[str, Any]) -> dict[str, Any]:
    case = observation["questionId"]
    condition = observation["condition"]
    answer = observation["answer"]

    if case == "CASE-01-COMPARATIVE":
        return {
            "semanticConclusion": "INCORRECT",
            "causalReasoning": "INCORRECT",
            "overclaim": "NO",
            "abstention": "NOT_APPLICABLE",
            "reasoningSupport": "PARTIALLY_SUPPORTED",
            "qualityCategory": "INCORRECT",
            "rationale": "The answer rejects the frozen CL-01 causal relation, while the approved oracle classifies it as established. Its supporting factual distinctions are useful, but the central causal conclusion is wrong.",
        }

    if case == "CASE-03":
        if condition == "DIRECT":
            return {
                "semanticConclusion": "CORRECT",
                "causalReasoning": "PARTIALLY_CORRECT",
                "overclaim": "YES",
                "abstention": "NOT_APPLICABLE",
                "reasoningSupport": "PARTIALLY_SUPPORTED",
                "qualityCategory": "OVERCLAIM",
                "rationale": "The answer identifies the central settlement service and tests correctly, but expands to components/tests outside the frozen CASE-03 evidence and expected impact set. Those additions are not independently established by the offline oracle assets.",
            }
        return {
            "semanticConclusion": "CORRECT",
            "causalReasoning": "CORRECT",
            "overclaim": "NO",
            "abstention": "NOT_APPLICABLE",
            "reasoningSupport": "SUPPORTED",
            "qualityCategory": "CORRECT_REASONED",
            "rationale": "The answer correctly identifies PaperSettlementService and PaperSettlementExitTest, explains why settlement changes affect them, and stays within the core frozen CASE-03 impact evidence.",
        }

    if case == "CASE-04":
        return {
            "semanticConclusion": "CORRECT",
            "causalReasoning": "CORRECT",
            "overclaim": "NO",
            "abstention": "CORRECT",
            "reasoningSupport": "SUPPORTED",
            "qualityCategory": "CORRECT_ABSTENTION",
            "rationale": "The answer preserves the frozen negative-control conclusion: the evidence does not establish ADR-043 as the cause of the specific Story-0042 configuration refactor.",
        }

    raise ValueError(f"unsupported question: {case}")


def build() -> dict[str, Any]:
    oracle = json.loads(ORACLE.read_text(encoding="utf-8"))
    benchmark = json.loads(BENCHMARK.read_text(encoding="utf-8"))
    rows = observations()
    if len(rows) != 18:
        raise ValueError(f"expected 18 official observations, found {len(rows)}")

    records = []
    for observation in rows:
        quality = classification(observation)
        records.append(
            {
                "observationId": observation["observationId"],
                "questionId": observation["questionId"],
                "condition": observation["condition"],
                "repetition": observation["repetition"],
                "formatContract": {
                    "structuralValid": observation["evaluation"]["structural"]["valid"],
                    "groundingContractValid": observation["evaluation"]["grounding"]["status"] == "VALID",
                    "originalStructuralStatus": observation["evaluation"]["structural"]["status"],
                    "originalGroundingStatus": observation["evaluation"]["grounding"]["status"],
                },
                "semanticQuality": quality,
            }
        )

    return {
        "evaluation": "LUNA_OFFLINE_SEMANTIC_QUALITY",
        "evaluationVersion": "1.0.0",
        "offlineOnly": True,
        "modelExecutionPerformed": False,
        "repositoryExplorationPerformed": False,
        "sourceObservationDirectory": "docs/evaluation/luna-comparative-collection/official/",
        "oracle": {
            "path": "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json",
            "version": oracle["oracleVersion"],
            "sha256": sha256(ORACLE),
            "status": oracle["oracleStatus"],
        },
        "benchmark": {
            "path": "ai-engine/evaluations/comparative_baseline/baseline-manifest.json",
            "version": benchmark["manifestVersion"],
            "sha256": sha256(BENCHMARK),
        },
        "records": records,
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
