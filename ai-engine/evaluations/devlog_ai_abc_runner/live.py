"""Closed-by-default live readiness and authorization checks for Q1.

This module only describes existing live entry points and validates a future,
campaign-scoped authorization document. It never constructs a provider client,
opens a network connection, or executes a treatment.
"""

from __future__ import annotations

import hashlib
import json
import math
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from .runner import load_manifest, repository_revision, sha256_json
from .provenance import ProvenanceError, execution_identity
from .pricing import NON_METERED_BILLING_MODEL, PricingContractError, validate_pricing_contract

LIVE_PREFLIGHT = "LIVE_PREFLIGHT"
Q1_ORACLE_ACCEPTED = "Q1_ORACLE_ACCEPTED"
Q1_ORACLE_PENDING_HUMAN_REVIEW = "Q1_ORACLE_PENDING_HUMAN_REVIEW"
Q1_LIVE_NOT_AUTHORIZED = "Q1_LIVE_NOT_AUTHORIZED"
Q1_LIVE_ADAPTERS_AVAILABLE = "Q1_LIVE_ADAPTERS_AVAILABLE"


class LiveAuthorizationError(ValueError):
    """Raised when a proposed live authorization fails closed validation."""


class LiveIntegrationBlocked(RuntimeError):
    """Raised when a requested execution path is not connected."""


@dataclass(frozen=True)
class LiveEntrypoint:
    condition: str
    name: str
    entrypoint: str
    transport: str
    output_contract: str
    dependencies: tuple[str, ...]


LIVE_ENTRYPOINTS = (
    LiveEntrypoint(
        "A_DEVLOG_CURRENT",
        "Core Story Context one-shot",
        "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java",
        "Java Core -> AI Engine callback",
        "story-context-analysis-result-v1",
        (
            "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextSubmissionService.java",
            "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextCallbackService.java",
        ),
    ),
    LiveEntrypoint(
        "B_DIRECT_INVESTIGATOR",
        "V4 direct investigator runtime",
        "ai-engine/evaluations/comparative_baseline_v4/runtime.py",
        "evaluation provider transport + direct repository tools",
        "comparative-v4-common-answer-1.0.0",
        (
            "ai-engine/evaluations/comparative_baseline_v4/pilot.py",
            "ai-engine/evaluations/comparative_baseline/live_adapters.py",
        ),
    ),
    LiveEntrypoint(
        "C_DEVLOG_ENGINEERING_ANALYST",
        "Core-authorized bounded investigation",
        "ai-engine/app/services/story_context_analysis_generation_service.py",
        "Python AI Engine -> Core investigation callbacks",
        "story-context-analysis-result-v1",
        (
            "ai-engine/app/schemas/story_context_investigation.py",
            "ai-engine/app/clients/core_callback_client.py",
            "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextInvestigationActionService.java",
        ),
    ),
)

_REQUIRED_AUTH_KEYS = {
    "authorizationStatus",
    "approverId",
    "approvedAt",
    "campaignId",
    "manifestId",
    "manifestVersion",
    "manifestDigest",
    "oracleStatus",
    "oracleDigest",
    "authorizedObservationIds",
    "authorizedConditions",
    "repositoryRevision",
    "runtimeRevision",
    "provider",
    "model",
    "budgets",
    "limits",
    "pricingStatus",
    "pricingVersion",
    "pricingDigest",
    "costCeiling",
    "expiresAtEpochSeconds",
}
_SECRET_KEYS = {"apiKey", "token", "password", "secret", "credential", "credentials"}


def canonical_manifest_digest(manifest: Mapping[str, Any]) -> str:
    """Return the digest used to bind a future authorization to this manifest."""

    return sha256_json(dict(manifest))


def _is_sha256(value: object) -> bool:
    return isinstance(value, str) and len(value) == 64 and all(
        character in "0123456789abcdef" for character in value
    )


def _is_runtime_revision(value: object) -> bool:
    return isinstance(value, str) and len(value) in (40, 64) and all(
        character in "0123456789abcdef" for character in value
    )


def load_q1_oracle(path: str | Path, *, expected_revision: str) -> dict[str, Any]:
    try:
        document = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise LiveAuthorizationError("Q1 oracle cannot be loaded") from error
    oracle = next((item for item in document.get("oracles", []) if item.get("caseId") == "Q1"), None)
    if not isinstance(oracle, dict):
        raise LiveAuthorizationError("Q1 oracle entry is missing")
    if oracle.get("version") != "1.1.0":
        raise LiveAuthorizationError("Q1 oracle version is not 1.1.0")
    if document.get("repositoryRevision") != expected_revision:
        raise LiveAuthorizationError("oracle repository revision mismatch")
    canonical = {key: value for key, value in oracle.items() if key != "canonicalDigest"}
    actual_digest = hashlib.sha256(
        json.dumps(canonical, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()
    if oracle.get("canonicalDigest") != actual_digest:
        raise LiveAuthorizationError("Q1 oracle digest mismatch")
    return oracle


def load_authorization(path: str | Path) -> dict[str, Any]:
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise LiveAuthorizationError("live authorization cannot be loaded") from error
    if not isinstance(value, dict):
        raise LiveAuthorizationError("live authorization must be an object")
    return value


def validate_live_authorization(
    authorization: Mapping[str, Any],
    *,
    manifest: Mapping[str, Any],
    oracle: Mapping[str, Any],
    pricing_contract: Mapping[str, Any] | None = None,
    now_epoch_seconds: int | None = None,
) -> None:
    """Validate one future campaign authorization without reading credentials."""

    missing = sorted(_REQUIRED_AUTH_KEYS - set(authorization))
    if missing:
        raise LiveAuthorizationError(f"authorization is missing fields: {', '.join(missing)}")
    if any(key in authorization for key in _SECRET_KEYS):
        raise LiveAuthorizationError("authorization must not contain credentials")
    if authorization["authorizationStatus"] != "AUTHORIZED":
        raise LiveAuthorizationError("authorization status is not AUTHORIZED")
    for key in ("approverId", "approvedAt"):
        if not isinstance(authorization[key], str) or not authorization[key].strip():
            raise LiveAuthorizationError(f"authorization {key} is invalid")
    if authorization["campaignId"] != manifest["manifestId"]:
        raise LiveAuthorizationError("authorization campaign does not match manifest")
    if authorization["manifestId"] != manifest["manifestId"] or authorization["manifestVersion"] != manifest["version"]:
        raise LiveAuthorizationError("authorization manifest identity mismatch")
    if authorization["manifestDigest"] != canonical_manifest_digest(manifest):
        raise LiveAuthorizationError("authorization manifest digest mismatch")
    if authorization["oracleStatus"] != Q1_ORACLE_ACCEPTED or oracle.get("status") != "ACCEPTED":
        raise LiveAuthorizationError("Q1 oracle is not explicitly accepted")
    if authorization["oracleDigest"] != oracle.get("canonicalDigest"):
        raise LiveAuthorizationError("authorization oracle digest mismatch")
    if authorization["authorizedObservationIds"] != [
        item["observationId"] for item in manifest["observations"]
    ]:
        raise LiveAuthorizationError("authorization observation set mismatch")
    if authorization["authorizedConditions"] != manifest["conditions"]:
        raise LiveAuthorizationError("authorization condition set mismatch")
    if authorization["repositoryRevision"] != manifest["repositoryRevision"]:
        raise LiveAuthorizationError("authorization repository revision mismatch")
    if not _is_runtime_revision(authorization["runtimeRevision"]):
        raise LiveAuthorizationError("authorization runtime revision is invalid")
    if authorization["provider"] != manifest["provider"] or authorization["model"] != manifest["model"]:
        raise LiveAuthorizationError("authorization provider/model mismatch")
    if authorization["budgets"] != manifest["technicalBudgets"] or authorization["limits"] != manifest["technicalBudgets"]:
        raise LiveAuthorizationError("authorization limits do not exactly match manifest budgets")
    if authorization["pricingStatus"] != "APPROVED":
        raise LiveAuthorizationError("pricing is not explicitly approved")
    if not isinstance(pricing_contract, Mapping):
        raise LiveAuthorizationError("approved pricing contract is missing")
    try:
        validate_pricing_contract(pricing_contract)
    except PricingContractError as error:
        raise LiveAuthorizationError(str(error)) from error
    if not isinstance(authorization["pricingVersion"], str) or not authorization["pricingVersion"].strip():
        raise LiveAuthorizationError("pricing version is invalid")
    if authorization["pricingVersion"] != pricing_contract.get("version"):
        raise LiveAuthorizationError("pricing version does not match approved contract")
    if authorization["pricingDigest"] != pricing_contract.get("digest") or not _is_sha256(authorization["pricingDigest"]):
        raise LiveAuthorizationError("pricing digest does not match approved contract")
    if not isinstance(authorization["costCeiling"], dict):
        raise LiveAuthorizationError("cost ceiling is missing")
    if not isinstance(authorization["costCeiling"].get("currency"), str) \
            or not authorization["costCeiling"]["currency"].strip():
        raise LiveAuthorizationError("cost ceiling currency is invalid")
    if authorization["costCeiling"]["currency"] != pricing_contract.get("currency"):
        raise LiveAuthorizationError("cost ceiling currency does not match approved pricing")
    maximum_cost = authorization["costCeiling"].get("maxAmount")
    if pricing_contract.get("billingModel") == NON_METERED_BILLING_MODEL:
        if authorization["costCeiling"].get("currency") != "NOT_APPLICABLE" or maximum_cost != "NOT_MEASURED":
            raise LiveAuthorizationError("non-metered pricing requires an explicit non-monetary ceiling")
        return
    if isinstance(maximum_cost, bool) or not isinstance(maximum_cost, (int, float)) or not math.isfinite(maximum_cost) or maximum_cost <= 0:
        raise LiveAuthorizationError("cost ceiling maxAmount is invalid")
    now = int(time.time()) if now_epoch_seconds is None else now_epoch_seconds
    if not isinstance(authorization["expiresAtEpochSeconds"], int) or authorization["expiresAtEpochSeconds"] <= now:
        raise LiveAuthorizationError("authorization is expired or has an invalid expiration")


def live_preflight(
    *,
    manifest_path: str | Path,
    oracle_path: str | Path,
    repository_root: str | Path,
    output_root: str | Path,
    authorization: Mapping[str, Any] | None = None,
    pricing_contract: Mapping[str, Any] | None = None,
) -> dict[str, Any]:
    """Perform static live-readiness checks; never contacts a remote service."""

    manifest = load_manifest(Path(manifest_path))
    root = Path(repository_root).resolve()
    actual_revision = repository_revision(root)
    oracle = load_q1_oracle(oracle_path, expected_revision=manifest["repositoryRevision"])
    try:
        identity = execution_identity(root, runtime_revision=actual_revision)
    except ProvenanceError as error:
        identity = {"error": str(error)}
        blockers = ["effective Q1 provenance cannot be established"]
    else:
        blockers = []
    entrypoints: list[dict[str, Any]] = []
    for item in LIVE_ENTRYPOINTS:
        files = (item.entrypoint, *item.dependencies)
        files_present = all((root / path).is_file() for path in files)
        if not files_present:
            blockers.append(f"missing live entrypoint dependency for {item.condition}")
        entrypoints.append(
            {
                "condition": item.condition,
                "name": item.name,
                "entrypoint": item.entrypoint,
                "transport": item.transport,
                "outputContract": item.output_contract,
                "status": "LIVE_ENTRYPOINT_IDENTIFIED" if files_present else "LIVE_ENTRYPOINT_NOT_READY",
                "filesPresent": files_present,
            }
        )
    try:
        from .live_adapters import live_adapters

        controlled_adapters = live_adapters(root, Path(output_root).resolve())
        controlled_ready = all(
            adapter.live_status == "CONTROLLED_ADAPTER_AVAILABLE"
            for adapter in controlled_adapters.values()
        )
    except Exception as error:
        controlled_ready = False
        blockers.append(f"controlled A/B/C adapters are unavailable: {type(error).__name__}")
    if not controlled_ready:
        blockers.append("controlled A/B/C integration adapters are not ready")
    if controlled_ready:
        for item in entrypoints:
            if item["filesPresent"]:
                item["adapterStatus"] = "CONTROLLED_ADAPTER_AVAILABLE"
    normalization_ready = all(
        (root / path).is_file()
        for path in (
            "ai-engine/evaluations/devlog_ai_abc_normalization/__init__.py",
            "ai-engine/evaluations/devlog_ai_abc_normalization/normalizer.py",
        )
    )
    contracts_ready = manifest.get("answerContracts") == {
        "A": "story-context-analysis-result-v1",
        "B": "comparative-v4-common-answer-1.0.0",
        "C": "story-context-analysis-result-v1",
    }
    budgets_ready = isinstance(manifest.get("technicalBudgets"), dict) and all(
        isinstance(value, int) and value > 0 for value in manifest["technicalBudgets"].values()
    )
    pricing_contract = pricing_contract or manifest.get("pricingContract")
    pricing_ready = False
    if isinstance(pricing_contract, Mapping):
        try:
            validate_pricing_contract(pricing_contract)
        except PricingContractError:
            pass
        else:
            pricing_ready = True
    configuration_ready = bool(manifest.get("provider")) and bool(manifest.get("model"))
    if not normalization_ready:
        blockers.append("evaluation normalization adapter is not available")
    if not contracts_ready:
        blockers.append("native answer contract identities do not match Q1")
    if not budgets_ready:
        blockers.append("manifest budgets are missing or invalid")
    if not pricing_ready:
        blockers.append("approved pricing contract is missing")
    if not configuration_ready:
        blockers.append("provider/model configuration identity is incomplete")
    source_snapshot = identity.get("sourceSnapshot", {})
    if source_snapshot.get("revision") != manifest["repositoryRevision"]:
        blockers.append("source snapshot does not match the frozen manifest")
    if not isinstance(identity.get("runtimeRevision"), str) or not identity["runtimeRevision"]:
        blockers.append("runtime revision cannot be identified")
    elif authorization is None or authorization.get("runtimeRevision") != identity["runtimeRevision"]:
        blockers.append("executed runtime revision does not match the frozen manifest")
    harness = identity.get("harness", {})
    if not isinstance(harness.get("sha256"), str) or len(harness["sha256"]) != 64:
        blockers.append("harness identity cannot be established")
    if identity.get("worktree", {}).get("reproducible") is not True:
        blockers.append("effective Q1 worktree contains relevant modified, untracked, or ignored files")
    if any(item.get("sha256") == "MISSING" for item in identity.get("harness", {}).get("files", [])):
        blockers.append("declared Q1 harness file is missing")
    if oracle.get("status") != "ACCEPTED":
        blockers.append("Q1 oracle has no explicit human ACCEPTED decision")
    if authorization is None:
        authorization_status = Q1_LIVE_NOT_AUTHORIZED
        blockers.append("no campaign-scoped live authorization was supplied")
    else:
        try:
            validate_live_authorization(
                authorization,
                manifest=manifest,
                oracle=oracle,
                pricing_contract=pricing_contract,
            )
        except LiveAuthorizationError as error:
            authorization_status = Q1_LIVE_NOT_AUTHORIZED
            blockers.append(str(error))
        else:
            authorization_status = Q1_LIVE_NOT_AUTHORIZED
            blockers.append("live activation is disabled in this evaluation runner")
    if not (Path(output_root).parent.exists() and not Path(output_root).exists()):
        blockers.append("artifact output parent is missing or target already exists")
    runner_status = Q1_LIVE_ADAPTERS_AVAILABLE if controlled_ready else "Q1_LIVE_ADAPTERS_PARTIAL"
    return {
        "status": "Q1_LIVE_PREFLIGHT_BLOCKED",
        "runnerStatus": runner_status,
        "authorizationStatus": authorization_status,
        "oracleStatus": Q1_ORACLE_ACCEPTED if oracle.get("status") == "ACCEPTED" else Q1_ORACLE_PENDING_HUMAN_REVIEW,
        "repositoryRevision": actual_revision,
        "provenance": identity,
        "manifestDigest": canonical_manifest_digest(manifest),
        "oracleDigest": oracle["canonicalDigest"],
        "entrypoints": entrypoints,
        "staticChecks": {
            "normalizationReady": normalization_ready,
            "nativeContractsReady": contracts_ready,
            "budgetsReady": budgets_ready,
            "pricingReady": pricing_ready,
            "providerModelIdentityPresent": configuration_ready,
            "credentialsRead": False,
            "remoteServicesContacted": False,
        },
        "artifactIsolation": "READY_IF_AUTHORIZED" if Path(output_root).parent.exists() else "BLOCKED",
        "providerCalls": 0,
        "networkCalls": 0,
        "criticalBlockers": blockers,
    }
