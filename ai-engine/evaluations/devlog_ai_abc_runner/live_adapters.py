"""Non-executable descriptions of the existing A/B/C live entry points."""

from __future__ import annotations

from typing import Any, Mapping

from .live import LIVE_ENTRYPOINTS, LiveIntegrationBlocked


class LiveTreatmentAdapter:
    """Preserve live entrypoint identity without pretending to execute it."""

    def __init__(self, condition: str) -> None:
        match = next((item for item in LIVE_ENTRYPOINTS if item.condition == condition), None)
        if match is None:
            raise ValueError(f"unknown live condition: {condition}")
        self.condition = condition
        self.live_status = "LIVE_ENTRYPOINT_IDENTIFIED"
        self.entrypoint = match

    def execute(self, observation: Mapping[str, Any], manifest: Mapping[str, Any]) -> dict[str, Any]:
        raise LiveIntegrationBlocked(
            f"LIVE_INTEGRATION_NOT_CONNECTED: {self.condition} requires its existing production/evaluation path"
        )


def live_adapters() -> dict[str, LiveTreatmentAdapter]:
    return {item.condition: LiveTreatmentAdapter(item.condition) for item in LIVE_ENTRYPOINTS}
