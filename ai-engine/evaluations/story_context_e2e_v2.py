"""Aggregate bounded Story 0210 protocol v2 captures.

This report deliberately keeps direct-context availability separate from
OAuth-generated answer quality. Infrastructure outcomes never enter a quality
denominator.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any


CONDITIONS = ("DIRECT_CONTEXT_OAUTH", "DEVLOG_ASSISTED_OAUTH")
OUTCOMES = {"SUCCESS", "FAILED", "NOT_EVALUATED", "INVALID_CAPTURE"}


def capture_digest(capture: dict[str, Any]) -> str:
    unsigned = {key: value for key, value in capture.items() if key != "captureDigest"}
    encoded = json.dumps(unsigned, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    return hashlib.sha256(encoded.encode()).hexdigest()


def load_captures(directory: Path, manifest: dict[str, Any]) -> list[dict[str, Any]]:
    expected = {
        (scenario["scenarioId"], condition, repetition)
        for scenario in manifest["scenarios"]
        for condition in CONDITIONS
        for repetition in range(1, manifest["repetitions"] + 1)
    }
    captures = []
    for path in sorted(directory.glob("*.jsonl")):
        lines = path.read_text(encoding="utf-8").splitlines()
        if len(lines) != 1:
            raise ValueError(f"capture must contain exactly one JSONL record: {path}")
        capture = json.loads(lines[0])
        key = (capture.get("scenarioId"), capture.get("condition"), capture.get("repetition"))
        if key not in expected:
            raise ValueError(f"unexpected capture identity: {path}")
        if capture.get("evaluationOutcome") not in OUTCOMES:
            raise ValueError(f"unknown evaluation outcome: {path}")
        if capture.get("captureDigest") != capture_digest(capture):
            raise ValueError(f"capture digest mismatch: {path}")
        captures.append(capture)
    if {(
        capture["scenarioId"], capture["condition"], capture["repetition"]
    ) for capture in captures} != expected:
        raise ValueError("capture matrix is incomplete")
    return captures


def aggregate(captures: list[dict[str, Any]], manifest: dict[str, Any]) -> dict[str, Any]:
    by_condition = {}
    for condition in CONDITIONS:
        values = [capture for capture in captures if capture["condition"] == condition]
        outcomes = Counter(value["evaluationOutcome"] for value in values)
        successful = [value for value in values if value["evaluationOutcome"] == "SUCCESS"]
        by_condition[condition] = {
            "assigned": len(values),
            "outcomes": dict(sorted(outcomes.items())),
            "executionCompleteness": round(len(successful) / len(values), 3) if values else 0.0,
            "revisionConsistent": all(
                value.get("repositoryRevision") == manifest["targetRevision"]
                for value in successful
            ),
        }
        if condition == "DIRECT_CONTEXT_OAUTH":
            by_condition[condition]["contextAvailabilityRate"] = round(
                sum(value.get("contextAvailable", False) for value in successful)
                / len(successful),
                3,
            ) if successful else 0.0
            by_condition[condition]["truncatedRate"] = round(
                sum(value.get("contextTruncated", False) for value in successful)
                / len(successful),
                3,
            ) if successful else 0.0
    return {
        "protocolVersion": manifest["protocolVersion"],
        "evaluationVersion": manifest["evaluationVersion"],
        "targetRevision": manifest["targetRevision"],
        "captureCount": len(captures),
        "conditions": by_condition,
        "qualityMetrics": {
            "status": "HUMAN_REVIEW_REQUIRED",
            "eligibleCondition": "DEVLOG_ASSISTED_OAUTH",
            "directContextQualityComparison": "NOT_APPLICABLE",
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("captures", type=Path)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--target-revision", required=True)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    manifest["targetRevision"] = args.target_revision
    print(json.dumps(aggregate(load_captures(args.captures, manifest), manifest), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
