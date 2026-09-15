"""Capture the existing Core-owned agent context path without changing production."""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import httpx

from .loader import load_benchmark


DEFAULT_BASE_URL = "http://localhost:18080"


def _revision(payload: dict[str, Any]) -> str | None:
    sources = payload.get("freshness", {}).get("checkedSources", [])
    if not sources:
        return None
    source = sources[0].get("source", {})
    return source.get("ingestedRevision") or source.get("currentRevision")


def _evidence(payload: dict[str, Any]) -> list[dict[str, Any]]:
    return payload.get("repositoryContext", {}).get("evidence", [])


def _canonical_evidence(case: dict[str, Any], evidence: list[dict[str, Any]], revision: str | None) -> list[str]:
    """Map only exact path/SHA identities; prose is never interpreted."""
    returned: set[str] = set()
    expected = case["expectedEvidence"]
    for item in evidence:
        reference = str(item.get("reference", ""))
        origin = item.get("provenance", {}).get("originatingFile")
        for artifact in expected:
            commit_sha = artifact.split(":", 1)[1] if artifact.startswith("commit:") else None
            reference_sha = reference.rsplit(":", 1)[-1]
            if commit_sha and reference_sha == commit_sha:
                returned.add(artifact)
            elif artifact == origin or reference.endswith(f":{artifact}"):
                returned.add(artifact)
    return sorted(returned)


def capture(base_url: str, benchmark: dict[str, Any]) -> dict[str, Any]:
    project_id = benchmark["projectId"]
    cases: list[dict[str, Any]] = []
    observed_revisions: list[str | None] = []
    with httpx.Client(base_url=base_url.rstrip("/"), timeout=60.0) as client:
        for case in benchmark["cases"]:
            response = client.post(
                f"/api/projects/{project_id}/engineering-story-context",
                params={"detail": "AGENT"},
                json={"description": case["question"]},
            )
            response.raise_for_status()
            payload = response.json()
            evidence = _evidence(payload)
            observed_revision = _revision(payload)
            observed_revisions.append(observed_revision)
            accounting = payload.get("repositoryContext", {}).get("accounting", {})
            cases.append({
                "caseId": case["caseId"],
                "status": "EXECUTED_CONTEXT_ONLY",
                "observedRepositoryRevision": observed_revision,
                "returnedEvidence": _canonical_evidence(case, evidence, _revision(payload)),
                "returnedConstraintIds": [],
                "returnedCausalLinkIds": [],
                "returnedAffectedComponentIds": [],
                "returnedAffectedTestIds": [],
                "typedReferencesResolve": all(bool(item.get("reference")) for item in evidence),
                "groundingValid": False,
                "groundingStatus": "NOT_APPLICABLE_CONTEXT_ONLY",
                "unsupportedClaimsMeasurement": "NOT_MEASURED",
                "selectedEvidenceCount": payload.get("repositoryContext", {}).get("selectedCount"),
                "estimatedTokens": accounting.get("estimatedTokens"),
                "followUpSearches": 0,
                "toolCalls": 1,
                "warnings": payload.get("repositoryContext", {}).get("warnings", []),
                "truncated": payload.get("repositoryContext", {}).get("truncated"),
                "rawContextDigest": payload.get("repositoryContext", {}).get("projectionDigest"),
            })
    return {
        "captureVersion": "1.0.0",
        "status": "EXECUTED_CONTEXT_ONLY",
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "project": benchmark["project"],
        "projectId": project_id,
        "repositoryId": benchmark["repositoryId"],
        "expectedRepositoryRevision": benchmark["repositoryRevision"],
        "actualRepositoryRevision": observed_revisions[0] if observed_revisions and len(set(observed_revisions)) == 1 else None,
        "mode": "DEVLOG_LIVE_ENGINEERING_STORY_CONTEXT_AGENT",
        "provider": None,
        "model": None,
        "generationSettings": None,
        "cases": cases,
        "humanUtility": {"status": "NOT_MEASURED"},
    }


def build_artifact_manifest(benchmark: dict[str, Any], capture_result: dict[str, Any]) -> dict[str, Any]:
    observed = {reference for case in capture_result["cases"] for reference in case.get("returnedEvidence", [])}
    resolutions = []
    for case in benchmark["cases"]:
        for artifact in case["expectedEvidence"]:
            resolved = artifact in observed
            if artifact.startswith("commit:"):
                artifact_type = "COMMIT"
            elif "/test/" in artifact or "/tests/" in artifact:
                artifact_type = "TEST_SOURCE"
            elif artifact.endswith(".md"):
                artifact_type = "DOCUMENT"
            else:
                artifact_type = "SOURCE_FILE"
            resolutions.append({
                "artifact": artifact,
                "status": "RESOLVED" if resolved else "NOT_FOUND",
                "canonicalId": artifact if resolved else None,
                "artifactType": artifact_type,
                "repositoryRevision": benchmark["repositoryRevision"],
                "resolutionSource": "devlog-live-engineering-story-context-agent",
                "evidenceCases": [case["caseId"]] if resolved else [],
                "reason": None if resolved else "Not returned by the live DevLog context projection; repository existence is not independently established.",
            })
    # The same artifact may occur in multiple cases; retain one deterministic row.
    unique = {item["artifact"]: item for item in resolutions}
    return {
        "manifestVersion": "1.0.0",
        "status": "MEASURED_BUT_NOT_COMPLETE",
        "project": benchmark["project"],
        "projectId": benchmark["projectId"],
        "repositoryId": benchmark["repositoryId"],
        "repositoryRevision": benchmark["repositoryRevision"],
        "resolutionSource": "DevLog live POST /api/projects/{projectId}/engineering-story-context?detail=AGENT",
        "expectedArtifactCount": len(unique),
        "resolvedArtifactCount": sum(item["status"] == "RESOLVED" for item in unique.values()),
        "unresolvedArtifactCount": sum(item["status"] != "RESOLVED" for item in unique.values()),
        "artifactResolutions": [unique[key] for key in sorted(unique)],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest-output", type=Path)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    result = capture(args.base_url, load_benchmark(args.benchmark))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.manifest_output:
        manifest = build_artifact_manifest(load_benchmark(args.benchmark), result)
        args.manifest_output.parent.mkdir(parents=True, exist_ok=True)
        args.manifest_output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"status": result["status"], "cases": len(result["cases"]), "output": str(args.output)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
