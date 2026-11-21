"""Minimal provider-free runner for the DevLog AI Q1 A/B/C pilot."""

__all__ = [
    "DRY_RUN",
    "INTEGRATION_SIMULATION",
    "LIVE",
    "LIVE_PREFLIGHT",
    "LIVE_PREPARE",
    "LiveNotAuthorized",
    "ManifestValidationError",
    "RunResult",
    "replay_run",
    "run",
    "run_dry_run",
    "run_integration_simulation",
    "run_live_preflight",
    "run_live_prepare",
]


def __getattr__(name: str):
    if name in __all__:
        from . import runner

        return getattr(runner, name)
    raise AttributeError(name)
