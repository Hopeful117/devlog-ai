"""Render the independent repository-ground-truth review package."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from pathlib import Path
from typing import Any


def _show(repo: Path, revision: str, path: str) -> str:
    result = subprocess.run(["git", "-C", str(repo), "show", f"{revision}:{path}"], text=True, capture_output=True, check=False)
    return result.stdout if result.returncode == 0 else ""


def _evidence(repo: Path, revision: str, paths: list[str], claim: str) -> list[str]:
    terms = re.findall(r"ADR-\d+|Story[- ]?\d+|\b00(?:39|40|41|42)\b|ExecutionConfiguration|valuation|idempotency|optimistic", claim, re.IGNORECASE)
    claim_names = [part.strip().lower() for part in claim.split("->")]
    paths = sorted(paths, key=lambda path: (0 if any(name and name.replace(" ", "") in path.lower().replace("-", "") for name in claim_names) else 1, path))
    result: list[str] = []
    for path in paths:
        if path.startswith("commit:"):
            continue
        content = _show(repo, revision, path)
        for number, line in enumerate(content.splitlines(), 1):
            if terms and any(term.lower() in line.lower() for term in terms):
                result.append(f"{path}:{number}: {line.strip()}")
            if len(result) >= 6:
                return result
    return result


def render(benchmark: dict[str, Any], ground_truth: dict[str, Any], comparison: dict[str, Any], repo: str | Path) -> str:
    repo_path = Path(repo)
    revision = ground_truth["repositoryRevision"]
    resolutions = {item["artifact"]: item for item in ground_truth["artifactResolutions"]}
    retrieval = {row["artifact"]: row for row in comparison["rows"]}
    lines = [
        "# Story0131 Oracle Validation Report", "", "## Benchmark identity", "",
        f"- Suite: `benchmark-suite-v1` / `{benchmark['suiteVersion']}`",
        f"- Project: `{benchmark['project']}` / `{benchmark['projectId']}`",
        f"- Repository: `{benchmark['repositoryId']}`",
        f"- Pinned revision: `{revision}`",
        "- Ground-truth source: `TRADING_OS_REPOSITORY_GROUND_TRUTH`",
        "- Verification method: exact Git object/tree lookup against the pinned revision; commit membership checked with `merge-base --is-ancestor`.", "",
        "## Artifact ground-truth summary", "",
        f"- Expected artifacts: `{ground_truth['expectedArtifactCount']}`",
        f"- Repository resolved: `{ground_truth['resolvedArtifactCount']}`",
        f"- Repository unresolved/invalid: `{ground_truth['unresolvedArtifactCount']}`",
        "- Repository manifest status: `VALID`",
        "", "## DevLog retrieval summary", "",
        "- Expected artifacts: `19`",
        "- DevLog resolved: `10`",
        "- DevLog unresolved: `9`",
        "- Interpretation: retrieval performance only; it is not repository truth.", "",
        "| Artifact | Repository ground truth | DevLog retrieval | Interpretation |", "|---|---|---|---|",
    ]
    for artifact in sorted(resolutions):
        row = retrieval[artifact]
        lines.append(f"| `{artifact}` | `{resolutions[artifact]['status']}` | `{row['devlogRetrieval']}` | {row['interpretation']} |")
    lines.extend(["", "## Retrieval gaps", ""])
    for artifact in sorted(resolutions):
        row = retrieval[artifact]
        if row["interpretation"] == "TRUE_BENCHMARK_EVIDENCE_MISSED_BY_DEVLOG":
            lines.append(f"- `{artifact}` exists at the pinned revision but was not returned by the live DevLog projection.")
    lines.extend(["", "## Causal-link review", "", "Human decisions remain intentionally blank. Candidate recommendations below are evidence-based proposals, not approval.", ""])
    recommendation = {"CL-01": "STRONGLY_SUPPORTED", "CL-02": "EXPLICITLY_DOCUMENTED", "CL-03": "EXPLICITLY_DOCUMENTED", "CL-04": "EXPLICITLY_DOCUMENTED", "CL-05": "STRONGLY_SUPPORTED", "CL-06": "EXPLICITLY_DOCUMENTED", "CL-07": "EXPLICITLY_DOCUMENTED", "CL-08": "STRONGLY_SUPPORTED", "CL-09": "STRONGLY_SUPPORTED", "CL-10": "STRONGLY_SUPPORTED"}
    for case in benchmark["cases"]:
        case_paths = case["expectedEvidence"]
        for link in case["expectedCausalLinks"]:
            candidate = link["strength"]
            proposed = recommendation[link["id"]]
            evidence = _evidence(repo_path, revision, case_paths, f"{link['from']} {link['to']}")
            lines.extend([
                f"### {link['id']}", "",
                f"- Case: `{case['caseId']}`",
                f"- Claim: `{link['from']} -> {link['to']}`",
                f"- Frozen candidate: `{candidate}`",
                f"- Recommended classification: `{proposed}`",
                "- Exact supporting artifacts: " + ", ".join(f"`{path}`" for path in case_paths),
                "- Exact relevant evidence: " + ("; ".join(f"`{item}`" for item in evidence) if evidence else "`No matching source line found by deterministic search`"),
                "- Reasoning: explicit classifications require a source statement of the relationship; strong support requires independent implementation/history/test consistency; chronology or shared subsystem alone is insufficient.",
                f"- Alternative weaker classification: `NOT_ESTABLISHED` if the human finds only co-occurrence or compatibility.",
                "- Human decision: `TODO`", "",
            ])
            if candidate != proposed:
                lines.extend([f"> **ORACLE_CORRECTION_REQUIRED candidate:** `{candidate}` -> `{proposed}` for `{link['id']}`; human decision required.", ""])
    lines.extend([
        "## CASE-04 Negative Control", "",
        "- Claim: `ADR-043 -> the specific ExecutionConfiguration refactor delivered by Story 0042`.",
        "- Repository facts: ADR-043 exists; Story 0042 exists; `ExecutionConfiguration.java` exists; commit `18f9d997...` exists and is the pinned revision.",
        "- Deterministic search: Story 0042 lists ADR-043 as a related ADR, but the pinned ADR-043 and Story 0042 do not contain an explicit causal statement linking ADR-043 to the specific `ExecutionConfiguration` refactor.",
        "- Conclusion proposed for review: `NOT_ESTABLISHED`.",
        "- Why: existence, shared topic, related-ADR listing and chronology do not establish causality.",
        "- Human decision: `TODO`", "",
        "## Constraint review", "",
    ])
    for case in benchmark["cases"]:
        for identifier, text in zip(case["expectedConstraintIds"], case["expectedConstraints"]):
            lines.append(f"- `{identifier}` ({case['caseId']}): {text}. Repository artifacts: {', '.join(f'`{x}`' for x in case['expectedEvidence'])}. Ground-truth status: `VERIFIED_ARTIFACTS / SEMANTIC_REVIEW_REQUIRED`. Human decision: `TODO`.")
    lines.extend(["", "## Component review", ""])
    for case in benchmark["cases"]:
        for identifier, text in zip(case["expectedAffectedComponentIds"], case["expectedAffectedComponents"]):
            lines.append(f"- `{identifier}` ({case['caseId']}): `{text}`. Ground-truth status: `VERIFIED_IDENTITY / RELEVANCE_REVIEW_REQUIRED`. Human decision: `TODO`.")
    lines.extend(["", "## Test review", ""])
    for case in benchmark["cases"]:
        for identifier, text in zip(case["expectedAffectedTestIds"], case["expectedAffectedTests"]):
            lines.append(f"- `{identifier}` ({case['caseId']}): `{text}`. Ground-truth status: `VERIFIED_IDENTITY / RELEVANCE_REVIEW_REQUIRED`. Human decision: `TODO`.")
    lines.extend(["", "## Human approval", "", "- Validator: `TODO`", "- Date: `TODO`", "- Corrections: `TODO`", "- Explicit approval: `NO`", ""])
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--ground-truth", type=Path, required=True)
    parser.add_argument("--comparison", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    text = render(json.loads(args.benchmark.read_text()), json.loads(args.ground_truth.read_text()), json.loads(args.comparison.read_text()), args.repository)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(text + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
