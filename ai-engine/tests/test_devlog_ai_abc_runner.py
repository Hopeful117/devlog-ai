"""Provider-free tests for the minimal Q1 A/B/C evaluation runner."""

from __future__ import annotations

import json
import socket
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner import (
    DRY_RUN,
    INTEGRATION_SIMULATION,
    LIVE,
    LiveNotAuthorized,
    ManifestValidationError,
    replay_run,
    run,
)
from evaluations.devlog_ai_abc_runner.runner import FixtureTreatmentAdapter


ROOT = Path(__file__).parents[1].parent
MANIFEST = ROOT / "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json"
REPOSITORY = ROOT
OBSERVATIONS = (
    "Q1-A-DEVLOG-CURRENT-r1",
    "Q1-B-DIRECT-INVESTIGATOR-r1",
    "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1",
)


def test_dry_run_executes_three_isolated_observations_and_replays(tmp_path: Path) -> None:
    result = run(
        mode=DRY_RUN,
        manifest_path=MANIFEST,
        output_root=tmp_path / "q1-run",
        repository_root=REPOSITORY,
    )

    assert result.summary["replay"] == "READY"
    assert result.summary["providerCalls"] == 0
    assert result.summary["networkCalls"] == 0
    assert result.summary["acceptabilityCounts"] == {"NOT_EVALUATED": 3}
    assert result.integrity["status"] == "PASS"
    assert replay_run(manifest_path=MANIFEST, run_root=result.run_root)["status"] == "PASS"

    for observation_id in OBSERVATIONS:
        observation_root = result.run_root / "observations" / observation_id
        assert (observation_root / "raw" / "answer.json").is_file()
        assert (observation_root / "derived" / "normalized.json").is_file()
        assert (observation_root / "trace" / "events.json").is_file()
        assert (observation_root / "status.json").is_file()
        derived = json.loads((observation_root / "derived" / "normalized.json").read_text())
        assert derived["observationId"] == observation_id
        assert derived["semanticEligibility"] == "NOT_EVALUATED"
        assert derived["acceptability"]["status"] == "NOT_EVALUATED"


def test_dry_run_preserves_native_contracts_and_selects_a_b_c(tmp_path: Path) -> None:
    result = run(
        mode=DRY_RUN,
        manifest_path=MANIFEST,
        output_root=tmp_path / "q1-run",
        repository_root=REPOSITORY,
    )

    treatments = {}
    for observation_id in OBSERVATIONS:
        path = result.run_root / "observations" / observation_id / "derived" / "normalized.json"
        treatments[observation_id] = json.loads(path.read_text())["treatment"]
    assert treatments == {
        "Q1-A-DEVLOG-CURRENT-r1": "A_DEVLOG_CURRENT",
        "Q1-B-DIRECT-INVESTIGATOR-r1": "B_DIRECT_INVESTIGATOR",
        "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1": "C_DEVLOG_ENGINEERING_ANALYST",
    }


def test_invalid_manifest_is_rejected_before_creating_run(tmp_path: Path) -> None:
    value = json.loads(MANIFEST.read_text())
    value["observations"] = value["observations"][:2]
    manifest = tmp_path / "invalid.json"
    manifest.write_text(json.dumps(value))

    with pytest.raises(ManifestValidationError, match="exactly three"):
        run(mode=DRY_RUN, manifest_path=manifest, output_root=tmp_path / "run", repository_root=REPOSITORY)
    assert not (tmp_path / "run").exists()


def test_manifest_with_oracle_payload_is_rejected(tmp_path: Path) -> None:
    value = json.loads(MANIFEST.read_text())
    value["oracle"] = {"requiredClaims": ["must not be supplied"]}
    manifest = tmp_path / "oracle.json"
    manifest.write_text(json.dumps(value))

    with pytest.raises(ManifestValidationError, match="oracle material"):
        run(mode=DRY_RUN, manifest_path=manifest, output_root=tmp_path / "run", repository_root=REPOSITORY)


def test_live_is_rejected_before_loading_or_constructing_any_transport(tmp_path: Path) -> None:
    with pytest.raises(LiveNotAuthorized, match="LIVE_NOT_AUTHORIZED"):
        run(mode=LIVE, manifest_path=MANIFEST, output_root=tmp_path / "run", repository_root=REPOSITORY)
    assert not (tmp_path / "run").exists()


def test_integration_simulation_exercises_all_three_boundaries_without_external_calls(tmp_path: Path) -> None:
    result = run(
        mode=INTEGRATION_SIMULATION,
        manifest_path=MANIFEST,
        output_root=tmp_path / "q1-simulation",
        repository_root=REPOSITORY,
    )

    assert result.summary["integrationStatus"] == "PASS"
    assert result.summary["providerCalls"] == 2
    assert result.summary["externalProviderCalls"] == 0
    assert result.summary["externalNetworkCalls"] == 0
    statuses = {item["condition"]: item for item in result.summary["observations"]}
    assert statuses["A_DEVLOG_CURRENT"]["integrationStatus"] == "PASS"
    assert statuses["B_DIRECT_INVESTIGATOR"]["integrationStatus"] == "PASS"
    assert statuses["C_DEVLOG_ENGINEERING_ANALYST"]["integrationStatus"] == "PASS"
    b_raw = json.loads((result.run_root / "observations" / "Q1-B-DIRECT-INVESTIGATOR-r1" / "raw" / "answer.json").read_text())["rawOutput"]
    assert b_raw["bindingStatus"] == "Q1_B_RUNTIME_INTEGRATION_PASS"
    assert b_raw["sourceSnapshot"]["revision"] == "60fae3e0e1104d0ffbe3832c1f578ed532b1268a"
    assert b_raw["toolTrace"][0]["executionStatus"] == "EXECUTED_SUCCESS"
    assert b_raw["toolTrace"][0]["repositoryRevision"] == "60fae3e0e1104d0ffbe3832c1f578ed532b1268a"
    assert b_raw["externalProviderCalls"] == 0
    assert b_raw["externalNetworkCalls"] == 0
    assert (result.run_root / "observations" / "Q1-B-DIRECT-INVESTIGATOR-r1" / "trace" / "events.json").read_text() != "[]"
    assert replay_run(manifest_path=MANIFEST, run_root=result.run_root)["status"] == "PASS"


def test_integration_simulation_artifacts_identify_boundary_doubles(tmp_path: Path) -> None:
    result = run(
        mode=INTEGRATION_SIMULATION,
        manifest_path=MANIFEST,
        output_root=tmp_path / "q1-simulation",
        repository_root=REPOSITORY,
    )

    for observation_id in OBSERVATIONS:
        raw = json.loads((result.run_root / "observations" / observation_id / "raw" / "answer.json").read_text())
        output = raw["rawOutput"]
        assert output["executionMode"] == INTEGRATION_SIMULATION
        assert output["boundaryDouble"]
        assert output["networkCalls"] == 0


def test_integration_simulation_does_not_change_live_gate(tmp_path: Path) -> None:
    with pytest.raises(LiveNotAuthorized, match="LIVE_NOT_AUTHORIZED"):
        run(mode=LIVE, manifest_path=MANIFEST, output_root=tmp_path / "live", repository_root=REPOSITORY)


def test_dry_run_does_not_read_credentials_or_use_network(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    monkeypatch.setenv("LLM_API_KEY", "must-not-be-read")

    def fail_network(*_args: object, **_kwargs: object) -> None:
        raise AssertionError("network access is forbidden in the dry-run")

    monkeypatch.setattr(socket.socket, "connect", fail_network)
    result = run(
        mode=DRY_RUN,
        manifest_path=MANIFEST,
        output_root=tmp_path / "q1-run",
        repository_root=REPOSITORY,
    )
    assert result.summary["providerCalls"] == 0
    assert result.summary["networkCalls"] == 0


def test_existing_run_directory_is_never_overwritten(tmp_path: Path) -> None:
    output = tmp_path / "q1-run"
    run(mode=DRY_RUN, manifest_path=MANIFEST, output_root=output, repository_root=REPOSITORY)
    with pytest.raises(FileExistsError, match="refusing to overwrite"):
        run(mode=DRY_RUN, manifest_path=MANIFEST, output_root=output, repository_root=REPOSITORY)


def test_failed_treatment_is_recorded_without_retry(tmp_path: Path) -> None:
    class FailingAdapter:
        condition = "B_DIRECT_INVESTIGATOR"
        live_status = "LIVE_ENTRYPOINT_IDENTIFIED"

        def __init__(self) -> None:
            self.calls = 0

        def execute(self, observation: dict, manifest: dict) -> dict:
            self.calls += 1
            raise RuntimeError("fixture failure")

    failing = FailingAdapter()
    adapters = {
        "A_DEVLOG_CURRENT": FixtureTreatmentAdapter("A_DEVLOG_CURRENT", "LIVE_ENTRYPOINT_IDENTIFIED", lambda: {"executionStatus": "COMPLETED"}),
        "B_DIRECT_INVESTIGATOR": failing,
        "C_DEVLOG_ENGINEERING_ANALYST": FixtureTreatmentAdapter("C_DEVLOG_ENGINEERING_ANALYST", "LIVE_ENTRYPOINT_IDENTIFIED", lambda: {"executionStatus": "COMPLETED"}),
    }
    result = run(mode=DRY_RUN, manifest_path=MANIFEST, output_root=tmp_path / "q1-run", repository_root=REPOSITORY, adapters=adapters)
    assert failing.calls == 1
    status = json.loads((result.run_root / "observations" / "Q1-B-DIRECT-INVESTIGATOR-r1" / "status.json").read_text())
    assert status["technicalStatus"] == "RUNTIME_FAILURE"
    assert status["retryCount"] == 0
    assert status["error"]["message"] == "fixture failure"


def test_oracle_material_is_not_present_in_run_artifacts(tmp_path: Path) -> None:
    result = run(mode=DRY_RUN, manifest_path=MANIFEST, output_root=tmp_path / "q1-run", repository_root=REPOSITORY)
    treatment_files = [
        path
        for path in result.run_root.rglob("*.json")
        if path.name != "manifest.json"
    ]
    content = "".join(path.read_text() for path in treatment_files)
    assert "requiredClaims" not in content
    assert "requiredEvidenceReferences" not in content
