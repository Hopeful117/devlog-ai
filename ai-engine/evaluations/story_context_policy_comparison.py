"""Bounded offline comparison of Story Context selection alternatives.

This module consumes redacted candidate fixtures only. It never calls Core,
changes production policy, or reconstructs repository context.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from story_context_runtime_report import _fixture_digest


POLICIES = {
    "current": 20,
    "balanced": 35,
    "unrestricted": 100,
}
ALLOWED_KINDS = {
    "ANALYSIS", "CHALLENGE", "CHANGED_FILE", "COMMIT", "CONFIGURATION_FILES",
    "CONFIG_FILE", "DECISION", "ENGINEERING_STORY", "FACT", "FILE_EXTENSIONS",
    "INSIGHT", "MODULE", "MODULE_SUMMARY", "SOURCE_DIRECTORIES", "SOURCE_FILE",
    "VALIDATABLE_PROPOSAL",
    "TEST_DIRECTORIES", "TEST_FILE",
}


def compare(candidates: list[dict[str, Any]], requested_files: list[str], maximum_items: int = 60) -> dict[str, Any]:
    if len(candidates) > 4096 or len(requested_files) > 32 or maximum_items < 1 or maximum_items > 256:
        raise ValueError("offline comparison bounds exceeded")
    normalized = [_candidate(value) for value in candidates]
    if len({value["reference"] for value in normalized}) != len(normalized):
        raise ValueError("candidate references must be unique")
    requested = {_canonical_path(value) for value in requested_files}
    results = {}
    for name, share in POLICIES.items():
        ceiling = max(1, (maximum_items * share + 99) // 100)
        counts: dict[str, int] = {}
        if name == "current":
            selected = [candidate for candidate in normalized if candidate["selected"]]
            for candidate in selected:
                counts[candidate["kind"]] = counts.get(candidate["kind"], 0) + 1
        else:
            selected = []
            ordered = sorted(
                normalized,
                key=lambda value: (
                    not any(_path_matches(value["path"], requested_path) for requested_path in requested),
                    -value["score"],
                    value["reference"],
                ),
            )
            for candidate in ordered:
                if len(selected) >= maximum_items:
                    break
                category = candidate["kind"]
                if counts.get(category, 0) >= ceiling:
                    continue
                selected.append(candidate)
                counts[category] = counts.get(category, 0) + 1
        selected_paths = {value["path"] for value in selected if value["path"]}
        selected_references = {value["reference"] for value in selected}
        requested_outcomes = {
            path: {
                "found": any(_path_matches(value["path"], path) for value in normalized),
                "selected": any(_path_matches(value["path"], path) for value in selected),
                "grounded": any(_path_matches(value["path"], path) and value["grounded"] for value in selected),
            }
            for path in sorted(requested)
        }
        results[name] = {
            "categoryShare": share,
            "candidateByKind": _counts(value["kind"] for value in normalized),
            "rejectedByKind": _counts(
                value["kind"] for value in normalized if value["reference"] not in selected_references
            ),
            "selectedCount": len(selected),
            "selectedByKind": counts,
            "trustBySelected": _counts(value["trust"] for value in selected),
            "groundedSelectedCount": sum(value["grounded"] for value in selected),
            "requestedFileOutcomes": requested_outcomes,
        }
    return {"candidateCount": len(normalized), "maximumItems": maximum_items, "policies": results}


def _candidate(value: dict[str, Any]) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError("candidate must be an object")
    reference = value.get("reference")
    kind = value.get("kind")
    if not isinstance(reference, str) or not 1 <= len(reference) <= 512:
        raise ValueError("candidate reference is invalid")
    if kind not in ALLOWED_KINDS:
        raise ValueError("candidate kind is invalid")
    path = value.get("path")
    path = value.get("path")
    trust = value.get("trust")
    score = value.get("score")
    reason = value.get("reason")
    if (path is not None and (not isinstance(path, str) or not 1 <= len(path) <= 160
                             or not _canonical_path(path))
            or type(score) is not int or not -(2**31) <= score <= 2**31 - 1
            or trust not in {"TRUSTED", "HUMAN_AUTHORED", "TECHNICAL_EVIDENCE", "SYSTEM_METADATA",
                             "UNVALIDATED", "TRANSIENT_AI", "UNKNOWN"}
            or reason not in {"CATEGORY_CONCENTRATION_LIMIT", "EVIDENCE_ITEM_BUDGET_EXCEEDED", "TOKEN_BUDGET_EXCEEDED",
                              "INSUFFICIENT_RELEVANCE", "SELECTED_BY_CATEGORY_FLOOR", "SELECTED_BY_DIVERSITY",
                              "SELECTED_BY_RANK", "SELECTED_BY_STRONG_RELEVANCE", "SELECTED_BY_REQUESTED_FILE", "NOT_RECORDED"}
            or any(type(value.get(key)) is not bool for key in ("grounded", "selected", "projected"))):
        raise ValueError("candidate fields are invalid")
    if value["projected"] and not value["selected"]:
        raise ValueError("projected candidate must be selected")
    if value["grounded"] and not value["projected"]:
        raise ValueError("grounded candidate must be projected")
    return {
        "reference": reference,
        "kind": kind,
        "path": _canonical_path(path) if isinstance(path, str) else None,
        "score": score,
        "trust": trust,
        "grounded": value["grounded"],
        "selected": value["selected"],
    }


def _counts(values: Any) -> dict[str, int]:
    result: dict[str, int] = {}
    for value in values:
        result[value] = result.get(value, 0) + 1
    return dict(sorted(result.items()))


def _canonical_path(value: str) -> str:
    normalized = value.replace("\\", "/")
    if (not normalized or normalized.startswith("/") or "//" in normalized
            or normalized == ".." or "../" in normalized or normalized.endswith("/..")):
        raise ValueError("path is not canonical")
    return normalized


def _path_matches(candidate_path: str | None, requested_path: str) -> bool:
    return candidate_path == requested_path or (
        candidate_path is not None and candidate_path.startswith(requested_path + "/")
    )


def compare_file(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if "candidateFixture" in payload:
        return compare_telemetry(payload)
    return compare(payload.get("candidates", []), payload.get("requestedFiles", []), payload.get("maximumItems", 60))


def compare_telemetry(telemetry: dict[str, Any], maximum_items: int = 60) -> dict[str, Any]:
    fixture = telemetry.get("candidateFixture")
    if not isinstance(fixture, dict) or not isinstance(fixture.get("candidates"), list):
        raise ValueError("candidate fixture is missing")
    if fixture.get("version") != "story-context-candidate-fixture-v1":
        raise ValueError("unsupported candidate fixture version")
    if (not isinstance(fixture.get("taskId"), str) or not fixture["taskId"].strip()
            or not isinstance(fixture.get("targetRevision"), str) or not fixture["targetRevision"].strip()
            or not isinstance(fixture.get("contextDigest"), str)
            or len(fixture["contextDigest"]) != 64
            or not isinstance(fixture.get("projectionDigest"), str)
            or len(fixture["projectionDigest"]) != 64):
        raise ValueError("candidate fixture correlation is missing")
    candidates = fixture["candidates"]
    normalized = [_candidate(value) for value in candidates]
    if len({value["reference"] for value in normalized}) != len(normalized):
        raise ValueError("candidate references must be unique")
    if not isinstance(fixture.get("digest"), str) or fixture["digest"] != _fixture_digest(fixture):
        raise ValueError("candidate fixture digest mismatch")
    requested = [
        item["path"] for item in telemetry.get("requestedFileOutcomes", [])
        if isinstance(item, dict) and isinstance(item.get("path"), str)
    ]
    result = compare(candidates, requested, maximum_items)
    result["correlation"] = {
        "taskId": fixture["taskId"],
        "targetRevision": fixture["targetRevision"],
        "contextDigest": fixture["contextDigest"],
        "projectionDigest": fixture["projectionDigest"],
    }
    return result
