"""Controlled A/B/C adapters used to prove the live execution boundaries.

These adapters reuse the existing Core, V4, and Python/Core integration paths.
They deliberately use the provider-free doubles supplied by ``integration``;
they are not an authorization mechanism and never construct external provider
clients.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Mapping

from .integration import integration_simulation_adapters
from .live import LIVE_ENTRYPOINTS
from .runner import TreatmentAdapter


CONTROLLED_ADAPTER_AVAILABLE = "CONTROLLED_ADAPTER_AVAILABLE"


class LiveTreatmentAdapter:
    """Expose one existing controlled execution path through the live contract."""

    def __init__(self, condition: str, delegate: TreatmentAdapter) -> None:
        match = next((item for item in LIVE_ENTRYPOINTS if item.condition == condition), None)
        if match is None:
            raise ValueError(f"unknown live condition: {condition}")
        if delegate.condition != condition:
            raise ValueError(f"adapter condition mismatch for {condition}")
        self.condition = condition
        self.live_status = CONTROLLED_ADAPTER_AVAILABLE
        self.entrypoint = match
        self._delegate = delegate

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        return self._delegate.execute(observation, manifest)


def live_adapters(repository_root: Path, output_root: Path) -> dict[str, LiveTreatmentAdapter]:
    """Build controlled adapters from the already-tested integration paths."""

    delegates = integration_simulation_adapters(repository_root, output_root)
    return {
        item.condition: LiveTreatmentAdapter(item.condition, delegates[item.condition])
        for item in LIVE_ENTRYPOINTS
    }
