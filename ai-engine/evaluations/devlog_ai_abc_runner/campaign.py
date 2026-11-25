"""Story 0223 / Story 0237 AC-6 - the 8 x 3 x 3 A/B/C campaign matrix.

The campaign is executed as eight Core-authorized case batches (Story 0239
D2): one batch is every condition x every repetition of one case. This module
owns the deterministic assignment matrix, the per-case oracle lookup and the
aggregation of batch runs into explicit assignment states. It never executes a
treatment and never contacts a provider.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

CAMPAIGN_MANIFEST_PATH = Path("docs/evaluation/devlog-ai-abc-campaign-manifest-v1.json")
CAMPAIGN_ID = "devlog-ai-abc-campaign"
CONDITIONS = ("A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST")

ASSIGNED = "ASSIGNED"
EXECUTED = "EXECUTED"
SEMANTIC_ELIGIBLE = "SEMANTIC_ELIGIBLE"
INVALID = "INVALID"
MISSING = "MISSING"


class CampaignError(ValueError):
    """Raised when the campaign contract or a batch selection is not executable."""


def is_campaign(manifest: Mapping[str, Any]) -> bool:
    return "caseIds" in manifest


def observation_id(case_id: str, condition: str, repetition: int) -> str:
    """Same derivation as Core ``LiveEvaluationContract.observationIds``."""

    return f"{case_id}-{condition.replace('_', '-')}-r{repetition}"


def derive_observations(case_ids: Iterable[str], conditions: Iterable[str], repetitions: int) -> list[dict[str, Any]]:
    """Case-major, then repetition-major, then condition order."""

    observations: list[dict[str, Any]] = []
    condition_list = list(conditions)
    for case_id in case_ids:
        for repetition in range(1, repetitions + 1):
            for condition in condition_list:
                observations.append({
                    "observationId": observation_id(case_id, condition, repetition),
                    "caseId": case_id,
                    "condition": condition,
                    "repetition": repetition,
                    "status": "PREPARED_NOT_EXECUTED",
                })
    return observations


def validate_campaign_structure(manifest: Mapping[str, Any]) -> None:
    case_ids = manifest.get("caseIds")
    repetitions = manifest.get("repetitions")
    conditions = manifest.get("conditions")
    if not isinstance(case_ids, list) or not case_ids or len(set(case_ids)) != len(case_ids):
        raise CampaignError("campaign caseIds must be a non-empty list of distinct cases")
    if not isinstance(repetitions, int) or isinstance(repetitions, bool) or repetitions < 1:
        raise CampaignError("campaign repetitions must be a positive integer")
    if conditions != list(CONDITIONS):
        raise CampaignError("campaign conditions must be the frozen A/B/C set")
    versions = manifest.get("caseVersions")
    if not isinstance(versions, dict) or set(versions) != set(case_ids):
        raise CampaignError("campaign caseVersions must cover every case")
    if manifest.get("observations") != derive_observations(case_ids, conditions, repetitions):
        raise CampaignError("campaign observations are not the deterministic matrix")
    if "caseId" in manifest:
        raise CampaignError("a campaign manifest must not declare a single caseId")


def case_of(manifest: Mapping[str, Any], observation: Mapping[str, Any]) -> str:
    return str(observation.get("caseId") or manifest["caseId"])


def batch_observation_ids(manifest: Mapping[str, Any], case_id: str) -> list[str]:
    if is_campaign(manifest):
        if case_id not in manifest["caseIds"]:
            raise CampaignError(f"case {case_id} is not part of the campaign")
        return [item["observationId"] for item in manifest["observations"] if item["caseId"] == case_id]
    if case_id != manifest.get("caseId"):
        raise CampaignError(f"case {case_id} is not the manifest case")
    return [item["observationId"] for item in manifest["observations"]]


def case_version(manifest: Mapping[str, Any], case_id: str) -> str:
    if is_campaign(manifest):
        return str(manifest["caseVersions"][case_id])
    return str(manifest["caseVersion"])


def _canonical_digest(oracle: Mapping[str, Any]) -> str:
    canonical = {key: value for key, value in oracle.items() if key != "canonicalDigest"}
    return hashlib.sha256(
        json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def load_case_oracle(path: str | Path, case_id: str, *, expected_revision: str,
                     expected_version: str | None = None) -> dict[str, Any]:
    """Load one accepted oracle and verify its frozen canonical digest."""

    try:
        document = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise CampaignError(f"{case_id} oracle cannot be loaded") from error
    oracle = next((item for item in document.get("oracles", []) if item.get("caseId") == case_id), None)
    if not isinstance(oracle, dict):
        raise CampaignError(f"{case_id} oracle entry is missing")
    if expected_version is not None and oracle.get("version") != expected_version:
        raise CampaignError(f"{case_id} oracle version is not {expected_version}")
    if document.get("repositoryRevision") != expected_revision:
        raise CampaignError("oracle repository revision mismatch")
    if oracle.get("canonicalDigest") != _canonical_digest(oracle):
        raise CampaignError(f"{case_id} oracle digest mismatch")
    return oracle


def assignment_matrix(manifest: Mapping[str, Any], statuses: Mapping[str, Mapping[str, Any]]) -> dict[str, Any]:
    """Explicit Story 0223 assignment states for every manifest cell.

    ``statuses`` maps observation ids to their recorded ``status.json`` plus the
    normalized ``semanticEligibility``. A cell without a record stays MISSING;
    it is never converted into a semantic failure.
    """

    cells: list[dict[str, Any]] = []
    counts = {ASSIGNED: 0, EXECUTED: 0, SEMANTIC_ELIGIBLE: 0, INVALID: 0, MISSING: 0}
    for observation in manifest["observations"]:
        record = statuses.get(observation["observationId"])
        states = [ASSIGNED]
        if record is None:
            states.append(MISSING)
        elif record.get("technicalStatus") == "COMPLETED":
            states.append(EXECUTED)
            if record.get("semanticEligibility") == "SEMANTICALLY_ELIGIBLE":
                states.append(SEMANTIC_ELIGIBLE)
        else:
            states.append(INVALID)
        for state in states:
            counts[state] += 1
        cells.append({
            "observationId": observation["observationId"],
            "caseId": case_of(manifest, observation),
            "condition": observation["condition"],
            "repetition": observation.get("repetition", 1),
            "states": states,
            "technicalStatus": None if record is None else record.get("technicalStatus"),
            "runRoot": None if record is None else record.get("runRoot"),
        })
    return {"assigned": len(cells), "counts": counts, "cells": cells}


def collect_statuses(run_roots: Iterable[str | Path]) -> dict[str, dict[str, Any]]:
    """Read recorded statuses from batch runs; a duplicated cell fails closed."""

    statuses: dict[str, dict[str, Any]] = {}
    for run_root in run_roots:
        root = Path(run_root)
        for status_path in sorted((root / "observations").glob("*/status.json")):
            status = json.loads(status_path.read_text(encoding="utf-8"))
            derived_path = status_path.parent / "derived" / "normalized.json"
            derived = json.loads(derived_path.read_text(encoding="utf-8")) if derived_path.is_file() else {}
            observation = status["observationId"]
            if observation in statuses:
                raise CampaignError(f"observation {observation} is recorded by more than one run")
            statuses[observation] = {
                **status,
                "semanticEligibility": derived.get("semanticEligibility"),
                "runRoot": str(root),
            }
    return statuses


def campaign_report(manifest: Mapping[str, Any], run_roots: Iterable[str | Path]) -> dict[str, Any]:
    statuses = collect_statuses(run_roots)
    unknown = sorted(set(statuses) - {item["observationId"] for item in manifest["observations"]})
    if unknown:
        raise CampaignError(f"runs contain observations outside the campaign: {', '.join(unknown)}")
    return {
        "campaignId": manifest["manifestId"],
        "manifestVersion": manifest["version"],
        "matrix": assignment_matrix(manifest, statuses),
        "semanticQualityEvaluated": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Aggregate campaign batch runs into the assignment matrix")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--run-root", action="append", dest="run_roots", default=[])
    args = parser.parse_args()
    from .runner import load_manifest

    manifest = load_manifest(Path(args.manifest))
    report = campaign_report(manifest, args.run_roots)
    print(json.dumps({**report, "matrix": {k: v for k, v in report["matrix"].items() if k != "cells"}},
                     indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
