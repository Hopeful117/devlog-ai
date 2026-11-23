"""Provider-free validation tests for Core-owned live preparation plans."""

from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner.prepared_plan import PreparedPlanError, validate_prepared_plan
from evaluations.devlog_ai_abc_runner.runner import sha256_json
from evaluations.devlog_ai_abc_runner.live import load_q1_oracle
from evaluations.devlog_ai_abc_runner.provenance import Q1_RUNTIME_IDENTITIES


ROOT = Path(__file__).parents[1].parent
MANIFEST_PATH = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
ORACLE_PATH = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
PRICING_PATH = ROOT / "docs/evaluation/devlog-ai-q1-chatgpt-oauth-pricing-draft-v1.json"
REVISION = "a" * 64


def _manifest() -> dict:
    return json.loads(MANIFEST_PATH.read_text())


def _plan_response() -> dict:
    manifest = _manifest()
    oracle = load_q1_oracle(ORACLE_PATH, expected_revision=manifest["repositoryRevision"])
    plan = {
        "benchmarkId": manifest["benchmarkId"],
        "benchmarkVersion": manifest["benchmarkVersion"],
        "caseId": manifest["caseId"],
        "manifestId": manifest["manifestId"],
        "manifestVersion": manifest["version"],
        "manifestDigest": sha256_json(manifest),
        "oracleStatus": "ACCEPTED",
        "oracleDigest": oracle["canonicalDigest"],
        "sourceRevision": manifest["repositoryRevision"],
        "runtimeRevision": REVISION,
        "runtimeAttestation": {"runtimeRevision": REVISION, "coreVerified": True},
        "runtimeIdentities": Q1_RUNTIME_IDENTITIES,
        "operationalLimits": manifest["technicalBudgets"],
        "provider": manifest["provider"],
        "model": manifest["model"],
        "observationIds": [item["observationId"] for item in manifest["observations"]],
        "pricingContract": json.loads(PRICING_PATH.read_text()),
        "expiresAt": (datetime.now(timezone.utc) + timedelta(minutes=5)).isoformat(),
        "providerCalls": 0,
        "networkCalls": 0,
    }
    return {"status": "READY", "plan": plan, "planDigest": sha256_json(plan)}


def test_prepared_plan_validates_without_external_calls(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(
        "evaluations.devlog_ai_abc_runner.prepared_plan.current_head",
        lambda _root: REVISION,
    )
    monkeypatch.setattr(
        "evaluations.devlog_ai_abc_runner.prepared_plan.assert_reproducible_worktree",
        lambda _root: None,
    )

    plan = validate_prepared_plan(
        _plan_response(),
        manifest=_manifest(),
        oracle={**load_q1_oracle(ORACLE_PATH, expected_revision=_manifest()["repositoryRevision"]), "status": "ACCEPTED"},
        repository_root=ROOT,
    )

    assert plan["providerCalls"] == 0
    assert plan["networkCalls"] == 0


@pytest.mark.parametrize("field", ["planDigest", "runtimeRevision", "manifestDigest"])
def test_prepared_plan_rejects_mismatch(monkeypatch: pytest.MonkeyPatch, field: str) -> None:
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.current_head", lambda _root: REVISION)
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.assert_reproducible_worktree", lambda _root: None)
    response = _plan_response()
    if field == "planDigest":
        response[field] = "0" * 64
    else:
        response["plan"][field] = "0" * 64
        response["planDigest"] = sha256_json(response["plan"])

    with pytest.raises(PreparedPlanError):
        validate_prepared_plan(
            response,
            manifest=_manifest(),
            oracle={**load_q1_oracle(ORACLE_PATH, expected_revision=_manifest()["repositoryRevision"]), "status": "ACCEPTED"},
            repository_root=ROOT,
        )


def test_prepared_plan_rejects_expiration_and_non_ready_status(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.current_head", lambda _root: REVISION)
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.assert_reproducible_worktree", lambda _root: None)
    response = _plan_response()
    response["status"] = "BLOCKED"

    with pytest.raises(PreparedPlanError, match="not READY"):
        validate_prepared_plan(
            response,
            manifest=_manifest(),
            oracle={"status": "ACCEPTED", "canonicalDigest": response["plan"]["oracleDigest"]},
            repository_root=ROOT,
        )


def test_prepared_plan_rejects_campaign_binding_mismatch(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.current_head", lambda _root: REVISION)
    monkeypatch.setattr("evaluations.devlog_ai_abc_runner.prepared_plan.assert_reproducible_worktree", lambda _root: None)
    response = _plan_response()
    authorization = {
        "runtimeRevision": "b" * 64,
        "repositoryRevision": response["plan"]["sourceRevision"],
        "authorizedObservationIds": response["plan"]["observationIds"],
        "limits": response["plan"]["operationalLimits"],
        "pricingDigest": response["plan"]["pricingContract"]["digest"],
        "provider": response["plan"]["provider"],
        "model": response["plan"]["model"],
    }

    with pytest.raises(PreparedPlanError, match="runtime revision"):
        validate_prepared_plan(
            response,
            manifest=_manifest(),
            oracle={**load_q1_oracle(ORACLE_PATH, expected_revision=_manifest()["repositoryRevision"]), "status": "ACCEPTED"},
            repository_root=ROOT,
            authorization=authorization,
        )
