"""Validation of an immutable Core-owned live evaluation preparation plan."""

from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Mapping

from .pricing import PricingContractError, validate_pricing_contract
from .provenance import Q1_RUNTIME_IDENTITIES, assert_reproducible_worktree, current_head, execution_identity
from .runner import sha256_json


class PreparedPlanError(ValueError):
    """Raised when a live preparation plan cannot authorize the runner boundary."""


def validate_prepared_plan(
    plan_response: Mapping[str, Any],
    *,
    manifest: Mapping[str, Any],
    oracle: Mapping[str, Any],
    authorization: Mapping[str, Any] | None = None,
    pricing_contract: Mapping[str, Any] | None = None,
    repository_root: str | Path,
    now: datetime | None = None,
) -> Mapping[str, Any]:
    """Validate a Core plan and local runtime identity without provider access."""

    if not isinstance(plan_response, Mapping):
        raise PreparedPlanError("preparation response must be an object")
    if plan_response.get("status") != "READY":
        raise PreparedPlanError("preparation plan is not READY")
    plan = plan_response.get("plan")
    if not isinstance(plan, Mapping):
        raise PreparedPlanError("preparation plan payload is missing")
    if plan_response.get("planDigest") != sha256_json(plan):
        raise PreparedPlanError("preparation plan digest mismatch")
    expires_at = plan.get("expiresAt")
    try:
        expires = datetime.fromisoformat(str(expires_at).replace("Z", "+00:00"))
    except ValueError as error:
        raise PreparedPlanError("preparation plan expiration is invalid") from error
    current_time = now or datetime.now(timezone.utc)
    if expires.tzinfo is None or expires <= current_time:
        raise PreparedPlanError("preparation plan is expired")
    if plan.get("providerCalls") != 0 or plan.get("networkCalls") != 0:
        raise PreparedPlanError("preparation plan permits external calls")
    if plan.get("manifestId") != manifest.get("manifestId") or plan.get("manifestVersion") != manifest.get("version"):
        raise PreparedPlanError("preparation manifest identity mismatch")
    if plan.get("manifestDigest") != sha256_json(manifest):
        raise PreparedPlanError("preparation manifest digest mismatch")
    if plan.get("oracleStatus") != "ACCEPTED" or oracle.get("status") != "ACCEPTED":
        raise PreparedPlanError("preparation oracle is not accepted")
    if plan.get("oracleDigest") != oracle.get("canonicalDigest"):
        raise PreparedPlanError("preparation oracle digest mismatch")
    if plan.get("sourceRevision") != manifest.get("repositoryRevision"):
        raise PreparedPlanError("preparation source revision mismatch")
    if plan.get("benchmarkId") != manifest.get("benchmarkId") or plan.get("benchmarkVersion") != manifest.get("benchmarkVersion"):
        raise PreparedPlanError("preparation benchmark identity mismatch")
    if plan.get("caseId") != manifest.get("caseId"):
        raise PreparedPlanError("preparation case identity mismatch")
    if plan.get("observationIds") != [item["observationId"] for item in manifest["observations"]]:
        raise PreparedPlanError("preparation observation set mismatch")
    if plan.get("operationalLimits") != manifest.get("technicalBudgets"):
        raise PreparedPlanError("preparation operational limits mismatch")
    if plan.get("provider") != manifest.get("provider") or plan.get("model") != manifest.get("model"):
        raise PreparedPlanError("preparation provider/model mismatch")
    if plan.get("runtimeIdentities") != Q1_RUNTIME_IDENTITIES:
        raise PreparedPlanError("preparation runtime identities mismatch")
    plan_pricing = plan.get("pricingContract")
    pricing_artifact = pricing_contract or plan_pricing
    try:
        validate_pricing_contract(pricing_artifact)
    except (PricingContractError, TypeError) as error:
        raise PreparedPlanError("preparation pricing contract is invalid") from error
    if not isinstance(plan_pricing, Mapping) or not isinstance(pricing_artifact, Mapping) \
            or plan_pricing.get("digest") != pricing_artifact.get("digest"):
        raise PreparedPlanError("preparation pricing digest mismatch")
    root = Path(repository_root).resolve()
    actual_revision = current_head(root)
    if actual_revision != plan.get("runtimeRevision"):
        raise PreparedPlanError("local runtime revision does not match preparation plan")
    try:
        assert_reproducible_worktree(root)
    except ValueError as error:
        raise PreparedPlanError(str(error)) from error
    attestation = plan.get("runtimeAttestation")
    if not isinstance(attestation, Mapping) or attestation.get("coreVerified") is not True:
        raise PreparedPlanError("preparation runtime attestation is invalid")
    if authorization is not None:
        if authorization.get("runtimeRevision") != plan.get("runtimeRevision"):
            raise PreparedPlanError("preparation runtime revision is not campaign-authorized")
        if authorization.get("repositoryRevision") != plan.get("sourceRevision"):
            raise PreparedPlanError("preparation source revision is not campaign-authorized")
        if authorization.get("authorizedObservationIds") != plan.get("observationIds"):
            raise PreparedPlanError("preparation observations are not campaign-authorized")
        if authorization.get("limits") != plan.get("operationalLimits"):
            raise PreparedPlanError("preparation limits are not campaign-authorized")
        pricing = plan.get("pricingContract")
        if not isinstance(pricing, Mapping) or authorization.get("pricingDigest") != pricing.get("digest"):
            raise PreparedPlanError("preparation pricing is not campaign-authorized")
        if authorization.get("provider") != plan.get("provider") or authorization.get("model") != plan.get("model"):
            raise PreparedPlanError("preparation provider/model is not campaign-authorized")
    return plan
