"""Provider-free tests for Q1 live readiness and authorization boundaries."""

from __future__ import annotations

import json
import math
import socket
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner import LIVE, LIVE_PREFLIGHT, run
from evaluations.devlog_ai_abc_runner.live import (
    LiveAuthorizationError,
    canonical_manifest_digest,
    live_preflight,
    load_q1_oracle,
    validate_live_authorization,
)
from evaluations.devlog_ai_abc_runner.live_execution import LiveConfiguration, LiveConfigurationError
from evaluations.devlog_ai_abc_runner.pricing import canonical_pricing_digest


ROOT = Path(__file__).parents[1].parent
MANIFEST_PATH = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
ORACLE_PATH = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
REVISION = "60fae3e0e1104d0ffbe3832c1f578ed532b1268a"


def manifest() -> dict:
    return json.loads(MANIFEST_PATH.read_text())


def accepted_oracle() -> dict:
    oracle = load_q1_oracle(ORACLE_PATH, expected_revision=REVISION)
    return {**oracle, "status": "ACCEPTED"}


def approved_pricing() -> dict:
    value = {
        "status": "APPROVED",
        "version": "q1-test-pricing-v1",
        "currency": "USD",
        "inputPerMillion": "2",
        "outputPerMillion": "4",
    }
    value["digest"] = canonical_pricing_digest(value)
    return value


def valid_authorization() -> dict:
    value = manifest()
    oracle = accepted_oracle()
    return {
        "authorizationStatus": "AUTHORIZED",
        "approverId": "q1-test-approver",
        "approvedAt": "2026-01-01T00:00:00Z",
        "campaignId": value["manifestId"],
        "manifestId": value["manifestId"],
        "manifestVersion": value["version"],
        "manifestDigest": canonical_manifest_digest(value),
        "oracleStatus": "Q1_ORACLE_ACCEPTED",
        "oracleDigest": oracle["canonicalDigest"],
        "authorizedObservationIds": [item["observationId"] for item in value["observations"]],
        "authorizedConditions": value["conditions"],
        "repositoryRevision": value["repositoryRevision"],
        "provider": value["provider"],
        "model": value["model"],
        "budgets": value["technicalBudgets"],
        "limits": value["technicalBudgets"],
        "pricingStatus": "APPROVED",
        "pricingVersion": approved_pricing()["version"],
        "pricingDigest": approved_pricing()["digest"],
        "costCeiling": {"currency": "USD", "maxAmount": 1.0},
        "expiresAtEpochSeconds": 4_000_000_000,
    }


def test_live_preflight_is_static_and_fails_closed(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LLM_API_KEY", "must-not-be-read")

    def fail_network(*_args: object, **_kwargs: object) -> None:
        raise AssertionError("network access is forbidden in LIVE_PREFLIGHT")

    monkeypatch.setattr(socket.socket, "connect", fail_network)
    report = run(
        mode=LIVE_PREFLIGHT,
        manifest_path=MANIFEST_PATH,
        oracle_path=ORACLE_PATH,
        output_root=tmp_path / "q1-live-run",
        repository_root=ROOT,
    )

    assert report["status"] == "Q1_LIVE_PREFLIGHT_BLOCKED"
    assert report["runnerStatus"] == "Q1_LIVE_ADAPTERS_AVAILABLE"
    assert report["oracleStatus"] == "Q1_ORACLE_ACCEPTED"
    assert report["authorizationStatus"] == "Q1_LIVE_NOT_AUTHORIZED"
    assert report["providerCalls"] == 0
    assert report["networkCalls"] == 0
    assert len(report["entrypoints"]) == 3
    assert all(item["status"] == "LIVE_ENTRYPOINT_IDENTIFIED" for item in report["entrypoints"])
    assert all(item["adapterStatus"] == "CONTROLLED_ADAPTER_AVAILABLE" for item in report["entrypoints"])
    assert report["provenance"]["sourceSnapshot"]["revision"] == REVISION
    assert report["provenance"]["runtimeRevision"] != REVISION
    assert "executed runtime revision does not match the frozen manifest" in report["criticalBlockers"]
    assert all(item["status"] == "LIVE_ENTRYPOINT_IDENTIFIED" for item in report["entrypoints"])
    assert not (tmp_path / "q1-live-run").exists()


def test_valid_future_authorization_contract_is_accepted_by_validator_only() -> None:
    validate_live_authorization(
        valid_authorization(),
        manifest=manifest(),
        oracle=accepted_oracle(),
        pricing_contract=approved_pricing(),
        now_epoch_seconds=3_000_000_000,
    )


def test_non_metered_oauth_authorization_uses_operational_ceiling_only() -> None:
    authorization = valid_authorization()
    pricing = {
        "status": "APPROVED",
        "version": "q1-chatgpt-oauth-pricing-v1",
        "currency": "NOT_APPLICABLE",
        "billingModel": "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
        "inputPerMillion": None,
        "outputPerMillion": None,
        "costCeilingPolicy": "OPERATIONAL_LIMITS_ONLY",
    }
    pricing["digest"] = canonical_pricing_digest(pricing)
    authorization.update({
        "pricingVersion": pricing["version"],
        "pricingDigest": pricing["digest"],
        "costCeiling": {"currency": "NOT_APPLICABLE", "maxAmount": "NOT_MEASURED"},
    })

    validate_live_authorization(
        authorization,
        manifest=manifest(),
        oracle=accepted_oracle(),
        pricing_contract=pricing,
        now_epoch_seconds=3_000_000_000,
    )


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("manifestDigest", "0" * 64, "manifest digest"),
        ("repositoryRevision", "other-revision", "repository revision"),
        ("authorizedObservationIds", ["Q1-A-DEVLOG-CURRENT-r1"], "observation set"),
        ("expiresAtEpochSeconds", 2_000_000_000, "expired"),
        ("budgets", {"maxModelTurns": 9}, "limits"),
        ("pricingStatus", "PENDING", "pricing"),
        ("apiKey", "secret", "credentials"),
    ],
)
def test_invalid_authorization_is_rejected_closed(field: str, value: object, message: str) -> None:
    authorization = valid_authorization()
    authorization[field] = value
    with pytest.raises(LiveAuthorizationError, match=message):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_pending_oracle_cannot_authorize_live_execution() -> None:
    pending_oracle = {**load_q1_oracle(ORACLE_PATH, expected_revision=REVISION), "status": "CORRECTION_PROPOSED"}
    with pytest.raises(LiveAuthorizationError, match="not explicitly accepted"):
        validate_live_authorization(
            valid_authorization(),
            manifest=manifest(),
            oracle=pending_oracle,
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_authorization_requires_the_exact_approved_pricing_artifact() -> None:
    authorization = valid_authorization()
    authorization["pricingDigest"] = "0" * 64
    with pytest.raises(LiveAuthorizationError, match="pricing digest"):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_non_finite_cost_ceiling_is_rejected() -> None:
    authorization = valid_authorization()
    authorization["costCeiling"]["maxAmount"] = math.inf
    with pytest.raises(LiveAuthorizationError, match="maxAmount"):
        validate_live_authorization(
            authorization,
            manifest=manifest(),
            oracle=accepted_oracle(),
            pricing_contract=approved_pricing(),
            now_epoch_seconds=3_000_000_000,
        )


def test_live_configuration_requires_core_boundary_and_bounded_files() -> None:
    with pytest.raises(LiveConfigurationError, match="DEVLOG_CORE_BASE_URL"):
        LiveConfiguration.from_environment({})

    configuration = LiveConfiguration.from_environment({
        "DEVLOG_CORE_BASE_URL": "http://localhost:8080/",
        "DEVLOG_CORE_BEARER_TOKEN": "runtime-token",
        "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
        "DEVLOG_LIVE_FILES": "docs/a.md, docs/b.md",
        "DEVLOG_LIVE_B_TASK_ID": "00000000-0000-0000-0000-000000000001",
    })
    assert configuration.core_base_url == "http://localhost:8080"
    assert configuration.files == ("docs/a.md", "docs/b.md")
    assert str(configuration.b_task_id) == "00000000-0000-0000-0000-000000000001"


def test_live_mode_remains_disabled_even_when_a_future_contract_is_valid(tmp_path: Path) -> None:
    with pytest.raises(LiveAuthorizationError, match="approved pricing contract is missing"):
        run(
            mode=LIVE,
            manifest_path=MANIFEST_PATH,
            oracle_path=ORACLE_PATH,
            output_root=tmp_path / "q1-live-run",
            repository_root=ROOT,
            authorization=valid_authorization(),
        )
