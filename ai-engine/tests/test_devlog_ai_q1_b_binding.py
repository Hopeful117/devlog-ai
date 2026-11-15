"""Provider-free proof that Q1 B executes unchanged V4 on the Q1 snapshot."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner.q1_b_binding import (
    Q1_B_CONFIG,
    Q1_B_REFERENCE,
    Q1BBindingError,
    run_q1_b_binding,
)
from evaluations.devlog_ai_abc_runner.provenance import Q1_SOURCE_REVISION


ROOT = Path(__file__).parents[1].parent
V4_MANIFEST = ROOT / "ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json"


def test_binding_runs_native_v4_and_reads_real_q1_snapshot() -> None:
    before = hashlib.sha256(V4_MANIFEST.read_bytes()).hexdigest()

    result = run_q1_b_binding(ROOT)

    assert result["bindingStatus"] == "Q1_B_RUNTIME_INTEGRATION_PASS"
    assert result["sourceStatus"] == "Q1_B_SOURCE_SNAPSHOT_VERIFIED"
    assert result["nativeOutputStatus"] == "Q1_B_NATIVE_OUTPUT_CAPTURED"
    assert result["sourceSnapshot"]["revision"] == Q1_SOURCE_REVISION
    assert result["q1ObservationId"] == "Q1-B-DIRECT-INVESTIGATOR-r1"
    assert result["providerIdentity"] == "scripted-provider-v4-q1-b-v1"
    assert result["providerCalls"] == 2
    assert result["externalProviderCalls"] == 0
    assert result["externalNetworkCalls"] == 0
    assert result["repositoryOperations"][0]["operation"] == "read_file"
    assert result["repositoryOperations"][0]["arguments"]["repositoryRevision"] == Q1_SOURCE_REVISION
    assert result["repositoryOperations"][0]["result"]["path"] == Q1_B_REFERENCE
    assert "storycontextanalysis.usecase" in result["repositoryOperations"][0]["result"]["content"]
    assert result["nativeV4Output"]["executionStatus"] == "COMPLETED"
    assert result["nativeV4Output"]["rawOutput"]["toolTrace"][0]["executionStatus"] == "EXECUTED_SUCCESS"
    assert result["nativeV4Output"]["rawOutput"]["toolTrace"][0]["repositoryRevision"] == Q1_SOURCE_REVISION
    assert result["nativeV4Output"]["rawOutput"]["finalResponse"]["questionId"] == "CASE-03"
    assert result["nativeV4RepositoryRevision"] != Q1_SOURCE_REVISION
    assert hashlib.sha256(V4_MANIFEST.read_bytes()).hexdigest() == before


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (lambda value: value["repository"].update({"revision": "0" * 40}), "source revision mismatch"),
        (lambda value: value["runtime"].update({"runtime": "wrong-runtime"}), "runtime identity mismatch"),
        (lambda value: value.update({"bindingStatus": "CONFIGURED_NOT_CONNECTED"}), "binding status is not connected"),
    ],
)
def test_binding_rejects_incoherent_configuration(tmp_path: Path, mutation, message: str) -> None:
    value = json.loads((ROOT / Q1_B_CONFIG).read_text(encoding="utf-8"))
    mutation(value)
    config = tmp_path / "q1-b.json"
    config.write_text(json.dumps(value), encoding="utf-8")

    with pytest.raises(Q1BBindingError, match=message):
        run_q1_b_binding(ROOT, config.relative_to(ROOT) if config.is_relative_to(ROOT) else config)


def test_binding_rejects_unavailable_snapshot(tmp_path: Path) -> None:
    value = json.loads((ROOT / Q1_B_CONFIG).read_text(encoding="utf-8"))
    value["repository"]["revision"] = "0" * 40
    config = tmp_path / "q1-b.json"
    config.write_text(json.dumps(value), encoding="utf-8")

    with pytest.raises(Q1BBindingError, match="source revision mismatch"):
        run_q1_b_binding(ROOT, config)
