"""Credential-free integration simulation adapters for the Q1 runner.

The simulation exercises the local orchestration boundaries only. A and C run
the real Java Core integration harness while keeping external AI, workspace,
authorization, and policy boundaries deterministic. B binds the unchanged V4
direct runtime to the pinned Q1 repository tools with a scripted provider. No
live provider or external network is used by this module.
"""

from __future__ import annotations

from copy import deepcopy
from pathlib import Path
from typing import Any, Mapping

from .fixtures import fixture_for
from .core_bridge import run_core_harness
from .q1_b_binding import run_q1_b_binding
from .provenance import current_head
from .runner import TreatmentAdapter


class IntegrationSimulationAdapter:
    """Adapter protocol implementation with an explicit simulation status."""

    def __init__(self, condition: str, *, core_boundary: str, repository_root: Path, output_root: Path) -> None:
        self.condition = condition
        self.live_status = "INTEGRATION_SIMULATION_BOUNDARY_DOUBLE"
        self.core_boundary = core_boundary
        self.repository_root = repository_root
        self.output_root = output_root

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        observation_id = str(observation["observationId"])
        core_artifact = run_core_harness(
            observation_id=observation_id,
            repository_root=self.repository_root,
            output_path=self.output_root / "observations" / observation_id / "core" / "core.json",
            repository_revision=current_head(self.repository_root),
        )
        payload = deepcopy(fixture_for(self.condition))
        payload["executionMode"] = "INTEGRATION_SIMULATION"
        payload["integrationStatus"] = "PASS"
        payload["boundaryDouble"] = self.core_boundary
        payload["coreIntegration"] = core_artifact
        payload["providerType"] = "SIMULATED_PROVIDER"
        payload["simulatedProviderCalls"] = core_artifact.get("simulatedProviderCalls", 1)
        payload["externalProviderCalls"] = 0
        payload["externalNetworkCalls"] = 0
        payload["providerCalls"] = 0
        payload["networkCalls"] = 0
        return payload


class BDirectRuntimeSimulationAdapter:
    condition = "B_DIRECT_INVESTIGATOR"
    live_status = "INTEGRATION_SIMULATION_EXECUTED"

    def __init__(self, repository_root: Path) -> None:
        self.repository_root = repository_root

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        result = run_q1_b_binding(self.repository_root)
        result["observationId"] = observation["observationId"]
        result["manifestRepositoryRevision"] = manifest["repositoryRevision"]
        return result


def integration_simulation_adapters(repository_root: Path, output_root: Path) -> dict[str, TreatmentAdapter]:
    """Return the three Q1 adapters without constructing any live transport."""

    return {
        "A_DEVLOG_CURRENT": IntegrationSimulationAdapter(
            "A_DEVLOG_CURRENT", core_boundary="core_submission_callback_persistence_double", repository_root=repository_root, output_root=output_root
        ),
        "B_DIRECT_INVESTIGATOR": BDirectRuntimeSimulationAdapter(repository_root),
        "C_DEVLOG_ENGINEERING_ANALYST": IntegrationSimulationAdapter(
            "C_DEVLOG_ENGINEERING_ANALYST", core_boundary="core_investigation_action_callback_persistence_double", repository_root=repository_root, output_root=output_root
        ),
    }
