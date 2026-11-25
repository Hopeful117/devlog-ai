"""Minimal isolated orchestrator for the provider-free DevLog AI Q1 pilot."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Mapping, Protocol

from evaluations.devlog_ai_abc_normalization import (
    normalize_direct_investigator,
    normalize_story_context,
)
from evaluations.comparative_baseline.collection_runtime import provider_failure_attribution

from .campaign import (
    CampaignError,
    assignment_matrix,
    batch_observation_ids,
    case_of,
    is_campaign,
    validate_campaign_structure,
)
from .fixtures import fixture_for
from .provenance import (
    Q1_RUNTIME_IDENTITIES,
    Q1_SOURCE_REVISION,
    ProvenanceError,
    execution_identity,
    verify_commit,
)

DRY_RUN = "DRY_RUN"
INTEGRATION_SIMULATION = "INTEGRATION_SIMULATION"
LIVE = "LIVE"
LIVE_PREFLIGHT = "LIVE_PREFLIGHT"
LIVE_PREPARE = "LIVE_PREPARE"
_EXPECTED_OBSERVATIONS = (
    "Q1-A-DEVLOG-CURRENT-r1",
    "Q1-B-DIRECT-INVESTIGATOR-r1",
    "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1",
)
_CONDITIONS = {
    "Q1-A-DEVLOG-CURRENT-r1": "A_DEVLOG_CURRENT",
    "Q1-B-DIRECT-INVESTIGATOR-r1": "B_DIRECT_INVESTIGATOR",
    "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1": "C_DEVLOG_ENGINEERING_ANALYST",
}
_LIVE_STATUS = {
    "A_DEVLOG_CURRENT": "LIVE_ENTRYPOINT_IDENTIFIED",
    "B_DIRECT_INVESTIGATOR": "LIVE_ENTRYPOINT_IDENTIFIED",
    "C_DEVLOG_ENGINEERING_ANALYST": "LIVE_ENTRYPOINT_IDENTIFIED",
}


class ManifestValidationError(ValueError):
    """Raised when the frozen Q1 manifest cannot safely drive the runner."""


class LiveNotAuthorized(RuntimeError):
    """Raised when live execution lacks its separate authorization contract."""


class TreatmentExecutionError(RuntimeError):
    """Raised by an evaluation adapter when its treatment fails."""


class TreatmentAdapter(Protocol):
    condition: str
    live_status: str

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        ...


@dataclass(frozen=True)
class FixtureTreatmentAdapter:
    condition: str
    live_status: str
    fixture_factory: Callable[[], dict[str, Any]]

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        return copy.deepcopy(self.fixture_factory())


@dataclass(frozen=True)
class RunResult:
    run_root: Path
    summary: dict[str, Any]
    integrity: dict[str, Any]


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_json(value: Any) -> str:
    return sha256_bytes(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8"))


def _write_json_exclusive(path: Path, value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2).encode("utf-8")
    with path.open("xb") as handle:
        handle.write(encoded)
    return sha256_bytes(encoded)


def load_manifest(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise ManifestValidationError(f"cannot load manifest: {path}") from error
    if not isinstance(value, dict):
        raise ManifestValidationError("manifest must be an object")
    if value.get("status") != "PROPOSED_DO_NOT_EXECUTE":
        raise ManifestValidationError("manifest must remain PROPOSED_DO_NOT_EXECUTE")
    if value.get("experimentType") != "END_TO_END_SYSTEM_COMPARISON":
        raise ManifestValidationError("manifest must use END_TO_END_SYSTEM_COMPARISON")
    observations = value.get("observations")
    if is_campaign(value):
        # Story 0237 AC-6: the 8 x 3 x 3 campaign is a deterministic matrix.
        try:
            validate_campaign_structure(value)
        except CampaignError as error:
            raise ManifestValidationError(str(error)) from error
    else:
        if not isinstance(observations, list) or len(observations) != 3:
            raise ManifestValidationError("manifest must contain exactly three observations")
        identities = tuple(item.get("observationId") for item in observations if isinstance(item, dict))
        if identities != _EXPECTED_OBSERVATIONS:
            raise ManifestValidationError("manifest observation identities are not the frozen Q1 set")
        if any(item.get("condition") != _CONDITIONS[item["observationId"]] for item in observations):
            raise ManifestValidationError("manifest observation conditions are not the frozen Q1 set")
    if any(item.get("status") != "PREPARED_NOT_EXECUTED" for item in observations):
        raise ManifestValidationError("observations must be PREPARED_NOT_EXECUTED")
    budgets = value.get("technicalBudgets")
    if not isinstance(budgets, dict) or any(not isinstance(item, int) or item <= 0 for item in budgets.values()):
        raise ManifestValidationError("technical budgets must be positive integers")
    if value.get("evaluationNormalization", {}).get("scope") != "evaluation_only":
        raise ManifestValidationError("normalization must remain evaluation-only")
    source_snapshot = value.get("sourceSnapshot")
    if not isinstance(source_snapshot, dict) or source_snapshot.get("revision") != value.get("repositoryRevision"):
        raise ManifestValidationError("source snapshot identity must match repositoryRevision")
    if value.get("runtimeIdentities") != Q1_RUNTIME_IDENTITIES:
        raise ManifestValidationError("runtime identities do not match the frozen Q1 treatment set")
    harness = value.get("harnessIdentity")
    if not isinstance(harness, dict) or harness.get("identityVersion") != "devlog-ai-q1-harness-identity-v1":
        raise ManifestValidationError("harness identity is missing")
    if _contains_oracle_payload(value):
        raise ManifestValidationError("oracle material must not be embedded in the treatment manifest")
    return value


def _contains_oracle_payload(value: Any) -> bool:
    if isinstance(value, dict):
        forbidden_keys = {"oracle", "requiredClaims", "requiredEvidenceReferences", "forbiddenClaims"}
        if forbidden_keys.intersection(value):
            return True
        return any(_contains_oracle_payload(item) for item in value.values())
    if isinstance(value, list):
        return any(_contains_oracle_payload(item) for item in value)
    return False


def repository_revision(repository_root: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(repository_root), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise ManifestValidationError("repository revision could not be resolved") from error
    return result.stdout.strip()


def _default_adapters() -> dict[str, TreatmentAdapter]:
    return {
        "A_DEVLOG_CURRENT": FixtureTreatmentAdapter("A_DEVLOG_CURRENT", _LIVE_STATUS["A_DEVLOG_CURRENT"], lambda: fixture_for("A_DEVLOG_CURRENT")),
        "B_DIRECT_INVESTIGATOR": FixtureTreatmentAdapter("B_DIRECT_INVESTIGATOR", _LIVE_STATUS["B_DIRECT_INVESTIGATOR"], lambda: fixture_for("B_DIRECT_INVESTIGATOR")),
        "C_DEVLOG_ENGINEERING_ANALYST": FixtureTreatmentAdapter("C_DEVLOG_ENGINEERING_ANALYST", _LIVE_STATUS["C_DEVLOG_ENGINEERING_ANALYST"], lambda: fixture_for("C_DEVLOG_ENGINEERING_ANALYST")),
    }


def _normalize(condition: str, envelope: dict[str, Any]) -> dict[str, Any]:
    return normalize_direct_investigator(envelope) if condition == "B_DIRECT_INVESTIGATOR" else normalize_story_context(envelope)


def _validate_integrity(run_root: Path, observations: list[dict[str, Any]]) -> dict[str, Any]:
    checked: list[dict[str, Any]] = []
    for item in observations:
        observation_root = run_root / "observations" / item["observationId"]
        raw_path = observation_root / "raw" / "answer.json"
        derived_path = observation_root / "derived" / "normalized.json"
        raw_bytes = raw_path.read_bytes()
        derived = json.loads(derived_path.read_text(encoding="utf-8"))
        raw = json.loads(raw_bytes)
        raw_hash = sha256_bytes(raw_bytes)
        expected_derived_hash = derived["derivedSha256"]
        derived_without_hash = {key: value for key, value in derived.items() if key != "derivedSha256"}
        checks = {
            "rawFileSha256": raw_hash,
            "rawPayloadSha256": sha256_json(raw["rawOutput"]),
            "rawEnvelopeHashMatches": raw.get("rawSha256") == sha256_json(raw["rawOutput"]),
            "derivedRawPayloadHashMatches": derived["rawArtifact"]["rawPayloadSha256"] == sha256_json(raw["rawOutput"]),
            "derivedHashMatches": expected_derived_hash == sha256_json(derived_without_hash),
        }
        if not all(value for key, value in checks.items() if key.endswith("Matches")):
            raise RuntimeError(f"integrity check failed for {item['observationId']}")
        checked.append({"observationId": item["observationId"], **checks})
    return {"status": "PASS", "observations": checked}


def replay_run(*, manifest_path: str | Path, run_root: str | Path) -> dict[str, Any]:
    """Verify hashes and reproduce DERIVED output without executing a treatment."""

    manifest = load_manifest(Path(manifest_path))
    root = Path(run_root)
    # A campaign batch run records only its own cells; replay what was recorded.
    recorded = [
        item for item in manifest["observations"]
        if (root / "observations" / item["observationId"]).is_dir()
    ]
    if not recorded:
        raise RuntimeError("run contains no manifest observation")
    integrity = _validate_integrity(root, recorded)
    for observation in recorded:
        observation_id = observation["observationId"]
        condition = observation["condition"]
        observation_root = root / "observations" / observation_id
        raw = json.loads((observation_root / "raw" / "answer.json").read_text(encoding="utf-8"))
        stored = json.loads((observation_root / "derived" / "normalized.json").read_text(encoding="utf-8"))
        reproduced = _normalize(condition, raw)
        if reproduced != stored:
            raise RuntimeError(f"replay mismatch for {observation_id}")
    return {"status": "PASS", "integrity": integrity, "providerCalls": 0, "networkCalls": 0}


def run_dry_run(*, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, adapters: Mapping[str, TreatmentAdapter] | None = None, mode: str = DRY_RUN, observation_ids: tuple[str, ...] | None = None, case_id: str | None = None) -> RunResult:
    manifest = load_manifest(Path(manifest_path))
    if case_id is not None:
        try:
            batch = tuple(batch_observation_ids(manifest, case_id))
        except CampaignError as error:
            raise ManifestValidationError(str(error)) from error
        if observation_ids is not None and not set(observation_ids) <= set(batch):
            raise ManifestValidationError("observation selection is outside the case batch")
        observation_ids = observation_ids or batch
    repository_root = Path(repository_root).resolve()
    actual_revision = repository_revision(repository_root)
    try:
        verify_commit(repository_root, str(manifest["repositoryRevision"]))
    except ProvenanceError as error:
        raise ManifestValidationError(
            f"manifest source revision is unavailable: {manifest['repositoryRevision']}"
        ) from error
    run_root = Path(output_root).resolve()
    if run_root.exists():
        raise FileExistsError(f"refusing to overwrite existing evaluation run: {run_root}")
    run_root.mkdir(parents=True)
    (run_root / "observations").mkdir()
    manifest_file_hash = _write_json_exclusive(run_root / "manifest.json", manifest)
    provenance = execution_identity(repository_root, runtime_revision=actual_revision)
    selected = dict(adapters or _default_adapters())
    observations = [
        item for item in manifest["observations"]
        if observation_ids is None or item["observationId"] in observation_ids
    ]
    if not observations:
        raise ManifestValidationError("observation selection does not match the manifest")
    results: list[dict[str, Any]] = []
    for observation in observations:
        observation_id = observation["observationId"]
        condition = observation["condition"]
        adapter = selected.get(condition)
        if adapter is None:
            raise ManifestValidationError(f"no adapter selected for {condition}")
        if adapter.condition != condition:
            raise ManifestValidationError(f"adapter condition mismatch for {condition}")
        observation_root = run_root / "observations" / observation_id
        for directory in ("raw", "derived", "trace"):
            (observation_root / directory).mkdir(parents=True)
        envelope = {
            "observationId": observation_id,
            "caseId": case_of(manifest, observation),
            "treatment": condition,
            "repositoryRevision": manifest["repositoryRevision"],
        }
        try:
            payload = adapter.execute(observation, manifest)
            envelope["rawOutput"] = payload
            envelope["rawSha256"] = sha256_json(payload)
            technical_status = payload.get("executionStatus", "NOT_EVALUATED")
            error = None
        except Exception as failure:  # The failed treatment is preserved; no retry is attempted.
            diagnostic = provider_failure_attribution(failure, request_phase="RUNNER")
            envelope["rawOutput"] = {
                "executionStatus": "RUNTIME_FAILURE",
                "semanticEligibility": "NOT_EVALUATED",
                "errorType": type(failure).__name__,
                "errorMessage": diagnostic["safeMessage"],
                "failureDiagnostic": diagnostic,
            }
            envelope["rawSha256"] = sha256_json(envelope["rawOutput"])
            technical_status = "RUNTIME_FAILURE"
            error = {"type": type(failure).__name__, "message": diagnostic["safeMessage"], "diagnostic": diagnostic}
        raw_path = observation_root / "raw" / "answer.json"
        raw_file_hash = _write_json_exclusive(raw_path, envelope)
        final_response = envelope["rawOutput"].get("finalResponse") or {}
        trace = envelope["rawOutput"].get("investigationTrace", envelope["rawOutput"].get("toolTrace", final_response.get("toolTrace", [])))
        _write_json_exclusive(observation_root / "trace" / "events.json", trace)
        derived = _normalize(condition, envelope)
        _write_json_exclusive(observation_root / "derived" / "normalized.json", derived)
        acceptability = derived.get("acceptability", {})
        status = {
            "observationId": observation_id,
            "condition": condition,
            "mode": mode,
            "technicalStatus": technical_status,
            "integrationStatus": envelope["rawOutput"].get("integrationStatus", "NOT_APPLICABLE"),
            "liveEntrypointStatus": adapter.live_status,
            "rawFileSha256": raw_file_hash,
            "retryCount": 0,
            "providerCalls": envelope["rawOutput"].get("providerCalls", 0),
            "networkCalls": envelope["rawOutput"].get("networkCalls", 0),
            "simulatedProviderCalls": envelope["rawOutput"].get("simulatedProviderCalls", 0),
            "externalProviderCalls": envelope["rawOutput"].get("externalProviderCalls", 0),
            "externalNetworkCalls": envelope["rawOutput"].get("externalNetworkCalls", 0),
            "acceptabilityStatus": acceptability.get("status", "NOT_EVALUATED"),
            "error": error,
        }
        _write_json_exclusive(observation_root / "status.json", status)
        results.append(status)
    integrity = _validate_integrity(run_root, observations)
    integration_passed = (
        mode == INTEGRATION_SIMULATION
        and all(item["integrationStatus"] == "PASS" for item in results)
        and all(item["technicalStatus"] == "COMPLETED" for item in results)
    )
    summary = {
        "manifestId": manifest["manifestId"],
        "manifestVersion": manifest["version"],
        "manifestFileSha256": manifest_file_hash,
        "mode": mode,
        "status": _summary_status(mode, results, integration_passed, campaign=is_campaign(manifest)),
        "repositoryRevision": manifest["repositoryRevision"],
        "provenance": provenance,
        "providerCalls": sum(item["providerCalls"] for item in results),
        "networkCalls": sum(item["networkCalls"] for item in results),
        "simulatedProviderCalls": sum(item["simulatedProviderCalls"] for item in results),
        "externalProviderCalls": sum(item["externalProviderCalls"] for item in results),
        "externalNetworkCalls": sum(item["externalNetworkCalls"] for item in results),
        "observations": results,
        "acceptabilityCounts": _acceptability_counts(results),
        "replay": "READY",
        "semanticQualityEvaluated": False,
        "integrationStatus": (
            "PASS"
            if integration_passed
            else ("PARTIAL" if mode == INTEGRATION_SIMULATION else "NOT_APPLICABLE")
        ),
    }
    if is_campaign(manifest):
        summary["caseId"] = case_id
        summary["assignmentMatrix"] = assignment_matrix(manifest, {
            item["observationId"]: {
                **item,
                "semanticEligibility": json.loads((run_root / "observations" / item["observationId"]
                                                   / "derived" / "normalized.json").read_text(encoding="utf-8"))
                .get("semanticEligibility"),
                "runRoot": str(run_root),
            }
            for item in results
        })
    _write_json_exclusive(run_root / "integrity-report.json", integrity)
    _write_json_exclusive(run_root / "execution-summary.json", summary)
    return RunResult(run_root=run_root, summary=summary, integrity=integrity)


def _summary_status(mode: str, results: list[dict[str, Any]], integration_passed: bool,
                    *, campaign: bool = False) -> str:
    """Name the run after the mode that actually executed (Story 0237 AC-5)."""

    prefix = "CAMPAIGN" if campaign else "Q1"
    if mode == LIVE:
        completed = all(item["technicalStatus"] == "COMPLETED" for item in results)
        return f"{prefix}_LIVE_EXECUTION_COMPLETED" if completed else f"{prefix}_LIVE_EXECUTION_PARTIAL"
    if mode == INTEGRATION_SIMULATION:
        return f"{prefix}_INTEGRATION_SIMULATION_PASS" if integration_passed else f"{prefix}_INTEGRATION_SIMULATION_PARTIAL"
    return f"{prefix}_RUNNER_DRY_RUN_READY"


def _acceptability_counts(results: list[dict[str, Any]]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for result in results:
        status = result["acceptabilityStatus"]
        counts[status] = counts.get(status, 0) + 1
    return counts


def _result_payload(result: RunResult | dict[str, Any]) -> dict[str, Any]:
    """Return the JSON payload exposed by the command-line runner."""

    if isinstance(result, RunResult):
        return result.summary
    # ``python -m`` can load this module once as ``__main__`` and once under
    # its package name when live adapters import it. Handle that second class
    # identity without weakening the JSON boundary for ordinary values.
    summary = getattr(result, "summary", None)
    if isinstance(summary, dict):
        return summary
    return result


def run_integration_simulation(*, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, case_id: str | None = None, observation_ids: tuple[str, ...] | None = None) -> RunResult:
    """Exercise local boundaries with deterministic provider/Core doubles."""

    from .integration import integration_simulation_adapters

    root = Path(repository_root).resolve()
    return run_dry_run(
        manifest_path=manifest_path,
        output_root=output_root,
        repository_root=root,
        adapters=integration_simulation_adapters(root, Path(output_root).resolve()),
        mode=INTEGRATION_SIMULATION,
        case_id=case_id,
        observation_ids=observation_ids,
    )


def run_live_preflight(*, manifest_path: str | Path, oracle_path: str | Path, output_root: str | Path, repository_root: str | Path, authorization: Mapping[str, Any] | None = None, pricing_contract: Mapping[str, Any] | None = None, activate_live: bool = False) -> dict[str, Any]:
    from .live import live_preflight

    return live_preflight(
        manifest_path=manifest_path,
        oracle_path=oracle_path,
        output_root=output_root,
        repository_root=repository_root,
        authorization=authorization,
        pricing_contract=pricing_contract,
        activate_live=activate_live,
    )


def run_live_prepare(
    *,
    manifest_path: str | Path,
    oracle_path: str | Path | None,
    repository_root: str | Path,
    output_root: str | Path,
    pricing_contract: Mapping[str, Any] | None,
    case_id: str | None = None,
) -> dict[str, Any]:
    if oracle_path is None:
        raise ManifestValidationError("LIVE_PREPARE requires the Q1 oracle path")
    if pricing_contract is None:
        raise ManifestValidationError("LIVE_PREPARE requires the approved pricing contract")
    from .live_execution import prepare_live_evaluation

    manifest = load_manifest(Path(manifest_path))
    batch_case = _batch_case(manifest, case_id)
    oracle = load_batch_oracle(oracle_path, manifest, batch_case)
    return prepare_live_evaluation(
        manifest=manifest,
        oracle=oracle,
        repository_root=repository_root,
        output_root=output_root,
        pricing_contract=pricing_contract,
        case_id=batch_case,
    )


def _batch_case(manifest: Mapping[str, Any], case_id: str | None) -> str:
    """A campaign executes one case batch at a time; the Q1 manifest is its own batch."""

    if is_campaign(manifest):
        if case_id is None:
            raise ManifestValidationError("a campaign live step requires --case-id")
        if case_id not in manifest["caseIds"]:
            raise ManifestValidationError(f"case {case_id} is not part of the campaign")
        return case_id
    if case_id not in (None, manifest["caseId"]):
        raise ManifestValidationError("case selection does not match the manifest case")
    return manifest["caseId"]


def load_batch_oracle(oracle_path: str | Path, manifest: Mapping[str, Any], case_id: str) -> dict[str, Any]:
    from .campaign import load_case_oracle
    from .live import LiveAuthorizationError, load_q1_oracle

    if not is_campaign(manifest):
        return load_q1_oracle(oracle_path, expected_revision=manifest["repositoryRevision"])
    try:
        oracle = load_case_oracle(oracle_path, case_id, expected_revision=manifest["repositoryRevision"])
    except CampaignError as error:
        raise LiveAuthorizationError(str(error)) from error
    if oracle.get("status") != "ACCEPTED":
        raise LiveAuthorizationError(f"{case_id} oracle is not explicitly accepted")
    return oracle


def run(*, mode: str, manifest_path: str | Path, output_root: str | Path, repository_root: str | Path, adapters: Mapping[str, TreatmentAdapter] | None = None, oracle_path: str | Path | None = None, authorization: Mapping[str, Any] | None = None, prepared_plan: Mapping[str, Any] | None = None, pricing_contract: Mapping[str, Any] | None = None, activate_live: bool = False, observation_ids: tuple[str, ...] | None = None, case_id: str | None = None) -> RunResult | dict[str, Any]:
    if mode == LIVE_PREPARE:
        if not activate_live:
            raise LiveNotAuthorized("LIVE_PREPARE requires the explicit --activate-live flag")
        return run_live_prepare(
            manifest_path=manifest_path,
            oracle_path=oracle_path,
            repository_root=repository_root,
            output_root=output_root,
            pricing_contract=pricing_contract,
            case_id=case_id,
        )
    if mode == LIVE:
        if authorization is None:
            raise LiveNotAuthorized("LIVE_NOT_AUTHORIZED: Q1 live execution requires a separate explicit authorization")
        from .live import validate_live_authorization
        from .live_execution import run_live_execution

        manifest_value = load_manifest(Path(manifest_path))
        if oracle_path is None:
            raise ManifestValidationError("LIVE requires the Q1 oracle path")
        batch_case = _batch_case(manifest_value, case_id)
        oracle = load_batch_oracle(oracle_path, manifest_value, batch_case)
        validate_live_authorization(
            authorization,
            manifest=manifest_value,
            oracle=oracle,
            pricing_contract=pricing_contract or manifest_value.get("pricingContract"),
            case_id=batch_case,
        )
        if is_campaign(manifest_value):
            batch = tuple(batch_observation_ids(manifest_value, batch_case))
            if observation_ids is not None and tuple(observation_ids) != batch:
                raise ManifestValidationError("a campaign live step executes exactly one whole case batch")
            observation_ids = batch
        return run_live_execution(
            manifest_path=manifest_path,
            output_root=output_root,
            repository_root=repository_root,
            manifest=manifest_value,
            authorization=authorization,
            prepared_plan=prepared_plan,
            oracle=oracle,
            pricing_contract=pricing_contract or manifest_value.get("pricingContract"),
            observation_ids=observation_ids,
        )
    if mode == LIVE_PREFLIGHT:
        if oracle_path is None:
            raise ManifestValidationError("LIVE_PREFLIGHT requires the Q1 oracle path")
        return run_live_preflight(
            manifest_path=manifest_path,
            oracle_path=oracle_path,
            output_root=output_root,
            repository_root=repository_root,
            authorization=authorization,
            pricing_contract=pricing_contract,
            activate_live=activate_live,
        )
    if mode == INTEGRATION_SIMULATION:
        return run_integration_simulation(
            manifest_path=manifest_path,
            output_root=output_root,
            repository_root=repository_root,
            case_id=case_id,
            observation_ids=observation_ids,
        )
    if mode != DRY_RUN:
        raise ValueError(f"unsupported runner mode: {mode}")
    return run_dry_run(
        manifest_path=manifest_path,
        output_root=output_root,
        repository_root=repository_root,
        adapters=adapters,
        observation_ids=observation_ids,
        case_id=case_id,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the provider-free DevLog AI Q1 A/B/C runner")
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--output-root", required=True)
    parser.add_argument("--repository-root", required=True)
    parser.add_argument("--mode", choices=(DRY_RUN, INTEGRATION_SIMULATION, LIVE_PREFLIGHT, LIVE_PREPARE, LIVE), default=DRY_RUN)
    parser.add_argument("--oracle")
    parser.add_argument("--authorization")
    parser.add_argument("--prepared-plan")
    parser.add_argument("--pricing")
    parser.add_argument("--activate-live", action="store_true", help="explicitly authorize live preflight activation")
    parser.add_argument("--env-file", help="load a dotenv file without overriding explicit environment variables")
    parser.add_argument("--observation-id", action="append", dest="observation_ids",
                        help="limit execution to one or more frozen observation IDs")
    parser.add_argument("--case-id", help="campaign case batch to execute (Story 0237 AC-6)")
    args = parser.parse_args()
    if args.env_file:
        from .live_execution import LiveConfigurationError, load_environment_file

        try:
            load_environment_file(args.env_file)
        except LiveConfigurationError as error:
            raise SystemExit(str(error)) from error
    authorization = None
    if args.authorization:
        from .live import load_authorization

        authorization = load_authorization(args.authorization)
    pricing_contract = None
    if args.pricing:
        try:
            pricing_contract = json.loads(Path(args.pricing).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise SystemExit("pricing contract cannot be loaded") from error
    prepared_plan = None
    if args.prepared_plan:
        try:
            prepared_plan = json.loads(Path(args.prepared_plan).read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as error:
            raise SystemExit("prepared plan cannot be loaded") from error
    result = run(
        mode=args.mode,
        manifest_path=args.manifest,
        output_root=args.output_root,
        repository_root=args.repository_root,
        oracle_path=args.oracle,
        authorization=authorization,
        prepared_plan=prepared_plan,
        pricing_contract=pricing_contract,
        activate_live=args.activate_live,
        observation_ids=tuple(args.observation_ids) if args.observation_ids else None,
        case_id=args.case_id,
    )
    payload = _result_payload(result)
    print(json.dumps(payload, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
