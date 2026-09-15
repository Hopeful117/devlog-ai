"""Load and mechanically validate the frozen product-value benchmark."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any


class BenchmarkValidationError(ValueError):
    """Raised when a benchmark is not safe to score."""


def load_benchmark(path: str | Path) -> dict[str, Any]:
    source = Path(path)
    try:
        value = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise BenchmarkValidationError(f"cannot read benchmark: {source}") from exc
    validate_benchmark(value)
    return value


def validate_benchmark(value: Any) -> None:
    if not isinstance(value, dict):
        raise BenchmarkValidationError("benchmark must be an object")
    required = ("suiteId", "suiteVersion", "projectId", "repositoryId", "repositoryRevision", "oracleStatus", "frozenBeforeImplementation", "cases")
    missing = [key for key in required if key not in value]
    if missing:
        raise BenchmarkValidationError(f"benchmark missing fields: {', '.join(missing)}")
    cases = value["cases"]
    if not isinstance(cases, list) or not cases:
        raise BenchmarkValidationError("benchmark cases must be a non-empty array")
    ids: set[str] = set()
    for case in cases:
        if not isinstance(case, dict):
            raise BenchmarkValidationError("each case must be an object")
        case_id = case.get("caseId")
        if not isinstance(case_id, str) or not case_id or case_id in ids:
            raise BenchmarkValidationError(f"invalid or duplicate caseId: {case_id!r}")
        ids.add(case_id)
        for key in ("expectedEvidence", "expectedConstraintIds", "expectedCausalLinks", "expectedAffectedComponentIds", "expectedAffectedTestIds"):
            if not isinstance(case.get(key), list):
                raise BenchmarkValidationError(f"{case_id}.{key} must be an array")
        if case.get("negativeCase") and case.get("expectedOutcome") != "NOT_ESTABLISHED":
            raise BenchmarkValidationError(f"{case_id} negative case must expect NOT_ESTABLISHED")
        revision = case.get("scope", {}).get("revision")
        if revision != value["repositoryRevision"]:
            raise BenchmarkValidationError(f"{case_id} scope revision differs from suite revision")


def validate_artifact_manifest(benchmark: dict[str, Any], manifest: dict[str, Any]) -> list[str]:
    """Validate resolutions supplied by a live/imported evidence collector.

    The evaluator deliberately does not infer artifact existence from names. The
    manifest must be produced by a collector operating on the pinned revision.
    """
    errors: list[str] = []
    if manifest.get("repositoryRevision") != benchmark["repositoryRevision"]:
        errors.append("artifact manifest revision differs from benchmark revision")
    expected = {artifact for case in benchmark["cases"] for artifact in case["expectedEvidence"]}
    resolutions = manifest.get("artifactResolutions")
    if isinstance(resolutions, list):
        by_artifact = {item.get("artifact"): item for item in resolutions if isinstance(item, dict)}
        errors.extend(f"missing manifest entry: {artifact}" for artifact in sorted(expected - by_artifact.keys()))
        for artifact in sorted(expected & by_artifact.keys()):
            item = by_artifact[artifact]
            if item.get("status") != "RESOLVED":
                errors.append(f"unresolved expected artifact: {artifact}")
            if item.get("repositoryRevision") != benchmark["repositoryRevision"]:
                errors.append(f"artifact revision mismatch: {artifact}")
            if not item.get("canonicalId") or not item.get("artifactType"):
                errors.append(f"incomplete artifact identity: {artifact}")
    else:
        resolved = set(manifest.get("resolvedArtifacts", []))
        errors.extend(f"unresolved expected artifact: {artifact}" for artifact in sorted(expected - resolved))
    return errors


def apply_frozen_oracle(benchmark: dict[str, Any], oracle: dict[str, Any]) -> dict[str, Any]:
    """Apply a separately frozen human oracle without mutating Story0130."""

    if oracle.get("oracleStatus") != "HUMAN_APPROVED" or not oracle.get("oracleFrozen") or not oracle.get("explicitApproval"):
        raise BenchmarkValidationError("oracle freeze is not explicitly human-approved")
    for key in ("suiteId", "suiteVersion", "projectId", "repositoryId", "repositoryRevision"):
        if oracle.get(key) != benchmark.get(key):
            raise BenchmarkValidationError(f"oracle freeze {key} differs from benchmark")
    result = json.loads(json.dumps(benchmark))
    result["oracleStatus"] = "APPROVED"
    result["oracleVersion"] = oracle["oracleVersion"]
    result["oracleFrozen"] = True
    classifications = oracle.get("causalClassifications", {})
    for case in result["cases"]:
        for link in case.get("expectedCausalLinks", []):
            if link["id"] not in classifications:
                raise BenchmarkValidationError(f"missing frozen classification: {link['id']}")
            link["strength"] = classifications[link["id"]]
        if case.get("negativeCase") and "CASE-04" in classifications:
            case["expectedOutcome"] = classifications["CASE-04"]
    validate_benchmark(result)
    return result
