"""Evaluation-only contracts for separating context from interpretation quality.

Nothing in this module is imported by a production service. Context bundles are
evidence-only inputs; oracle annotations remain evaluator-side data.
"""

from __future__ import annotations

import json
import re
from enum import Enum
from typing import Any, Iterable


class EvaluationCondition(str, Enum):
    DIRECT_REPOSITORY = "DIRECT_REPOSITORY"
    DEVLOG_CONTEXT = "DEVLOG_CONTEXT"
    GROUND_TRUTH_CONTEXT = "GROUND_TRUTH_CONTEXT"


class FailureAttribution(str, Enum):
    CONTEXT_FAILURE = "CONTEXT_FAILURE"
    INTERPRETATION_FAILURE = "INTERPRETATION_FAILURE"
    BOTH = "BOTH"
    NOT_ATTRIBUTABLE = "NOT_ATTRIBUTABLE"


_FORBIDDEN_KEYS = re.compile(
    r"(?:oracle|answer|expected|threshold|human.?decision|classification|"
    r"scoring.?id|constraint.?id|causal.?link.?id|component.?id|test.?id)",
    re.IGNORECASE,
)
_FORBIDDEN_VALUES = {
    "EXPLICITLY_DOCUMENTED",
    "STRONGLY_SUPPORTED",
    "NOT_ESTABLISHED",
    "HUMAN_APPROVED",
    "APPROVED",
}


def _walk(value: Any, path: str = "$") -> Iterable[tuple[str, Any]]:
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            yield from _walk(child, f"{path}.{key}")
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _walk(child, f"{path}[{index}]")


def validate_evidence_only_context(context: dict[str, Any]) -> list[str]:
    """Return mechanical leakage errors for a model-facing context bundle."""

    errors: list[str] = []
    for path, value in _walk(context):
        if isinstance(value, dict):
            for key in value:
                if _FORBIDDEN_KEYS.search(str(key)):
                    errors.append(f"forbidden answer-key field at {path}.{key}")
        elif isinstance(value, str):
            if value in _FORBIDDEN_VALUES:
                errors.append(f"forbidden oracle value at {path}")
            if re.search(r"\bCL-\d+\b|\bCASE-\d+\b|\bCOMP-[A-Z0-9-]+\b|\bTEST-[A-Z0-9-]+\b", value):
                errors.append(f"scoring identifier at {path}")
    return errors


def build_ground_truth_context(
    benchmark: dict[str, Any],
    case: dict[str, Any],
    artifacts: list[dict[str, Any]],
) -> dict[str, Any]:
    """Build a model-facing context from complete evidence without annotations.

    ``artifacts`` is supplied by the independently verified repository collector.
    The builder accepts only artifact identity/type/content and deliberately does
    not copy any benchmark expected-* fields into the result.
    """

    if benchmark.get("oracleStatus") != "APPROVED":
        raise ValueError("ground-truth context requires a human-approved oracle")
    expected = set(case["expectedEvidence"])
    supplied = []
    seen: set[str] = set()
    for artifact in artifacts:
        reference = artifact.get("reference")
        if not isinstance(reference, str) or reference not in expected or reference in seen:
            continue
        supplied.append({
            "reference": reference,
            "artifactType": artifact.get("artifactType", "UNKNOWN"),
            "content": artifact.get("content", ""),
        })
        seen.add(reference)
    context = {
        "contextVersion": "1.0.0",
        "condition": EvaluationCondition.GROUND_TRUTH_CONTEXT.value,
        "project": benchmark["project"],
        "repositoryRevision": benchmark["repositoryRevision"],
        "evidence": supplied,
    }
    errors = validate_evidence_only_context(context)
    if errors:
        raise ValueError("ground-truth context leakage: " + "; ".join(errors))
    return context


def serialize_context(context: dict[str, Any]) -> str:
    """Stable serialization used for leakage tests and capture digests."""

    errors = validate_evidence_only_context(context)
    if errors:
        raise ValueError("context leakage: " + "; ".join(errors))
    return json.dumps(context, ensure_ascii=True, sort_keys=True, separators=(",", ":"))


def validate_three_condition_capture(capture: dict[str, Any]) -> list[str]:
    """Validate that comparable case/question coverage exists for all conditions."""

    errors: list[str] = []
    runs = capture.get("conditions")
    if not isinstance(runs, dict):
        return ["capture.conditions must contain all three experimental conditions"]
    expected = {condition.value for condition in EvaluationCondition}
    if set(runs) != expected:
        errors.append(f"conditions must equal {sorted(expected)}")
        return errors
    case_sets: dict[str, set[str]] = {}
    for condition in EvaluationCondition:
        cases = runs.get(condition.value, {}).get("cases", [])
        case_sets[condition.value] = {item.get("caseId") for item in cases if isinstance(item, dict)}
        if not case_sets[condition.value]:
            errors.append(f"{condition.value} has no cases")
    if len({frozenset(ids) for ids in case_sets.values()}) > 1:
        errors.append("conditions do not cover the same case IDs")
    return errors


def validate_comparable_metadata(capture: dict[str, Any]) -> list[str]:
    """Require execution metadata needed to compare live interpretation runs."""

    required = {
        "provider", "model", "settings", "systemInstructions", "taskPrompt",
        "contextSize", "runTimestamp",
    }
    errors: list[str] = []
    instructions: list[str] = []
    for condition in EvaluationCondition:
        metadata = capture.get("conditions", {}).get(condition.value, {}).get("metadata")
        if not isinstance(metadata, dict):
            errors.append(f"{condition.value} missing comparable metadata")
            continue
        missing = sorted(required - set(metadata))
        errors.extend(f"{condition.value} missing metadata: {key}" for key in missing)
        if not missing:
            instructions.append(str(metadata["systemInstructions"]) + "\n" + str(metadata["taskPrompt"]))
    if instructions and len(set(instructions)) != 1:
        errors.append("interpretation instructions differ between conditions")
    return errors


def attribute_failure(*, context_recall: float | None, interpretation_recall: float | None) -> FailureAttribution:
    """Attribute only measurable misses; never infer beyond supplied metrics."""

    if context_recall is None or interpretation_recall is None:
        return FailureAttribution.NOT_ATTRIBUTABLE
    context_failed = context_recall < 1.0
    interpretation_failed = interpretation_recall < 1.0
    if context_failed and interpretation_failed:
        return FailureAttribution.BOTH
    if context_failed:
        return FailureAttribution.CONTEXT_FAILURE
    if interpretation_failed:
        return FailureAttribution.INTERPRETATION_FAILURE
    return FailureAttribution.NOT_ATTRIBUTABLE
