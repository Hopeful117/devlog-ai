"""Provider-free tests for Q1 live readiness and authorization boundaries."""

from __future__ import annotations

import json
import socket
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner import LIVE, LIVE_PREFLIGHT, LiveNotAuthorized, run
from evaluations.devlog_ai_abc_runner.live import (
    LiveAuthorizationError,
    canonical_manifest_digest,
    live_preflight,
    load_q1_oracle,
    validate_live_authorization,
)


ROOT = Path(__file__).parents[1].parent
MANIFEST_PATH = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
ORACLE_PATH = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
REVISION = "60fae3e0e1104d0ffbe3832c1f578ed532b1268a"


def manifest() -> dict:
    return json.loads(MANIFEST_PATH.read_text())


def accepted_oracle() -> dict:
    oracle = load_q1_oracle(ORACLE_PATH, expected_revision=REVISION)
    return {**oracle, "status": "ACCEPTED"}


def valid_authorization() -> dict:
    value = manifest()
    oracle = accepted_oracle()
    return {
        "authorizationStatus": "AUTHORIZED",
        "campaignId": value["manifestId"],
        "manifestId": value["manifestId"],
        "manifestVersion": value["version"],
        "manifestDigest": canonical_manifest_digest(value),
        "oracleStatus": "Q1_ORACLE_ACCEPTED",
        "oracleDigest": oracle["canonicalDigest"],
        "authorizedObservationIds": [item["observationId"] for item in value["observations"]],
        "repositoryRevision": value["repositoryRevision"],
        "provider": value["provider"],
        "model": value["model"],
        "budgets": value["technicalBudgets"],
        "limits": value["technicalBudgets"],
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
    assert report["runnerStatus"] == "Q1_LIVE_INTEGRATION_PARTIAL"
    assert report["oracleStatus"] == "Q1_ORACLE_PENDING_HUMAN_REVIEW"
    assert report["authorizationStatus"] == "Q1_LIVE_NOT_AUTHORIZED"
    assert report["providerCalls"] == 0
    assert report["networkCalls"] == 0
    assert len(report["entrypoints"]) == 3
    assert all(item["status"] == "LIVE_ENTRYPOINT_IDENTIFIED" for item in report["entrypoints"])
    assert not (tmp_path / "q1-live-run").exists()


def test_valid_future_authorization_contract_is_accepted_by_validator_only() -> None:
    validate_live_authorization(
        valid_authorization(),
        manifest=manifest(),
        oracle=accepted_oracle(),
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
            now_epoch_seconds=3_000_000_000,
        )


def test_pending_oracle_cannot_authorize_live_execution() -> None:
    with pytest.raises(LiveAuthorizationError, match="not explicitly accepted"):
        validate_live_authorization(
            valid_authorization(),
            manifest=manifest(),
            oracle=load_q1_oracle(ORACLE_PATH, expected_revision=REVISION),
            now_epoch_seconds=3_000_000_000,
        )


def test_live_mode_remains_disabled_even_when_a_future_contract_is_valid(tmp_path: Path) -> None:
    with pytest.raises(LiveNotAuthorized, match="LIVE_NOT_AUTHORIZED"):
        run(
            mode=LIVE,
            manifest_path=MANIFEST_PATH,
            oracle_path=ORACLE_PATH,
            output_root=tmp_path / "q1-live-run",
            repository_root=ROOT,
            authorization=valid_authorization(),
        )
