"""Resolve frozen benchmark artifacts directly from an exact Git revision."""

from __future__ import annotations

import argparse
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .experimental import EvaluationCondition, build_ground_truth_context
from .loader import apply_frozen_oracle, load_benchmark


def _git(repo: Path, *args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(["git", "-C", str(repo), *args], text=True, capture_output=True, check=False)


def _artifact_type(artifact: str) -> str:
    if artifact.startswith("commit:"):
        return "COMMIT"
    if "/test/" in artifact or "/tests/" in artifact:
        return "TEST_SOURCE"
    if artifact.endswith(".md"):
        return "DOCUMENT"
    return "SOURCE_FILE"


def _resolve_commit(repo: Path, revision: str, artifact: str) -> dict[str, Any]:
    sha = artifact.split(":", 1)[1]
    exists = _git(repo, "cat-file", "-e", f"{sha}^{{commit}}").returncode == 0
    if not exists:
        return {"status": "NOT_FOUND", "reason": "commit object does not exist"}
    ancestor = _git(repo, "merge-base", "--is-ancestor", sha, revision).returncode == 0
    return {
        "status": "RESOLVED" if ancestor else "INVALID",
        "resolvedCanonicalId": f"commit:{sha}" if ancestor else None,
        "resolvedHash": sha,
        "reason": None if ancestor else "commit exists but is not an ancestor of pinned revision",
    }


def _resolve_path(repo: Path, revision: str, artifact: str) -> dict[str, Any]:
    object_type = _git(repo, "cat-file", "-t", f"{revision}:{artifact}")
    if object_type.returncode != 0:
        return {"status": "NOT_FOUND", "reason": "exact path is absent from pinned tree"}
    expected_type = "blob"
    if object_type.stdout.strip() != expected_type:
        return {"status": "INVALID", "reason": f"expected {expected_type}, got {object_type.stdout.strip()}"}
    blob = _git(repo, "rev-parse", f"{revision}:{artifact}").stdout.strip()
    return {"status": "RESOLVED", "resolvedCanonicalId": artifact, "resolvedPath": artifact, "resolvedHash": blob, "reason": None}


def resolve_repository(benchmark: dict[str, Any], repo_path: str | Path) -> dict[str, Any]:
    repo = Path(repo_path).resolve()
    revision = benchmark["repositoryRevision"]
    revision_check = _git(repo, "cat-file", "-e", f"{revision}^{{commit}}")
    if revision_check.returncode != 0:
        raise ValueError(f"pinned revision is unavailable: {revision}")
    expected = sorted({artifact for case in benchmark["cases"] for artifact in case["expectedEvidence"]})
    resolutions = []
    for artifact in expected:
        result = _resolve_commit(repo, revision, artifact) if artifact.startswith("commit:") else _resolve_path(repo, revision, artifact)
        resolutions.append({
            "artifact": artifact,
            "artifactType": _artifact_type(artifact),
            "expectedCanonicalIdentity": artifact,
            "repositoryRevision": revision,
            "verificationMechanism": "git cat-file commit+merge-base ancestor" if artifact.startswith("commit:") else "git cat-file exact revision:path+rev-parse blob",
            **result,
        })
    resolved = sum(item["status"] == "RESOLVED" for item in resolutions)
    return {
        "manifestVersion": "1.0.0",
        "status": "VALID" if resolved == len(resolutions) else "INCOMPLETE",
        "resolutionSource": "TRADING_OS_REPOSITORY_GROUND_TRUTH",
        "repositoryPath": str(repo),
        "repositoryRevision": revision,
        "verificationRevision": revision,
        "verifiedAt": datetime.now(timezone.utc).isoformat(),
        "expectedArtifactCount": len(resolutions),
        "resolvedArtifactCount": resolved,
        "unresolvedArtifactCount": len(resolutions) - resolved,
        "artifactResolutions": resolutions,
    }


def compare_resolution(ground_truth: dict[str, Any], devlog_manifest: dict[str, Any]) -> dict[str, Any]:
    devlog = {item["artifact"]: item for item in devlog_manifest.get("artifactResolutions", [])}
    rows = []
    for item in ground_truth["artifactResolutions"]:
        devlog_status = devlog.get(item["artifact"], {}).get("status", "NOT_FOUND")
        repository_status = item["status"]
        if repository_status == "RESOLVED" and devlog_status != "RESOLVED":
            interpretation = "TRUE_BENCHMARK_EVIDENCE_MISSED_BY_DEVLOG"
        elif repository_status != "RESOLVED":
            interpretation = "REPOSITORY_ARTIFACT_NOT_RESOLVED"
        else:
            interpretation = "RETRIEVED_BY_DEVLOG"
        rows.append({"artifact": item["artifact"], "repositoryGroundTruth": repository_status, "devlogRetrieval": devlog_status, "interpretation": interpretation})
    return {"repositoryRevision": ground_truth["repositoryRevision"], "rows": rows}


def _artifact_content(repo: Path, revision: str, artifact: str) -> str:
    if artifact.startswith("commit:"):
        return _git(repo, "show", "--format=fuller", "--stat", "--patch", artifact.split(":", 1)[1]).stdout
    return _git(repo, "show", f"{revision}:{artifact}").stdout


def build_ground_truth_contexts(benchmark: dict[str, Any], repo_path: str | Path) -> dict[str, Any]:
    """Build complete evidence-only contexts from the pinned Git tree."""

    repo = Path(repo_path).resolve()
    contexts = []
    for case in benchmark["cases"]:
        artifacts = [{
            "reference": artifact,
            "artifactType": _artifact_type(artifact),
            "content": _artifact_content(repo, benchmark["repositoryRevision"], artifact),
        } for artifact in case["expectedEvidence"]]
        contexts.append({"caseId": case["caseId"], "context": build_ground_truth_context(benchmark, case, artifacts)})
    return {
        "captureVersion": "1.0.0",
        "condition": EvaluationCondition.GROUND_TRUTH_CONTEXT.value,
        "project": benchmark["project"],
        "projectId": benchmark["projectId"],
        "repositoryId": benchmark["repositoryId"],
        "repositoryRevision": benchmark["repositoryRevision"],
        "cases": contexts,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--devlog-manifest", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--comparison-output", type=Path)
    parser.add_argument("--oracle-freeze", type=Path)
    parser.add_argument("--context-output", type=Path)
    args = parser.parse_args()
    benchmark = load_benchmark(args.benchmark)
    if args.oracle_freeze:
        benchmark = apply_frozen_oracle(benchmark, json.loads(args.oracle_freeze.read_text(encoding="utf-8")))
    ground_truth = resolve_repository(benchmark, args.repository)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(ground_truth, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.devlog_manifest and args.comparison_output:
        comparison = compare_resolution(ground_truth, json.loads(args.devlog_manifest.read_text(encoding="utf-8")))
        args.comparison_output.parent.mkdir(parents=True, exist_ok=True)
        args.comparison_output.write_text(json.dumps(comparison, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if args.context_output:
        if not args.oracle_freeze:
            raise ValueError("--context-output requires --oracle-freeze")
        contexts = build_ground_truth_contexts(benchmark, args.repository)
        args.context_output.parent.mkdir(parents=True, exist_ok=True)
        args.context_output.write_text(json.dumps(contexts, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"status": ground_truth["status"], "expected": ground_truth["expectedArtifactCount"], "resolved": ground_truth["resolvedArtifactCount"], "unresolved": ground_truth["unresolvedArtifactCount"]}))
    return 0 if ground_truth["status"] == "VALID" else 2


if __name__ == "__main__":
    raise SystemExit(main())
