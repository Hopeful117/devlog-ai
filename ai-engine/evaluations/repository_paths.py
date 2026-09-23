"""Portable paths for repositories used by evaluation fixtures."""

from __future__ import annotations

import os
from pathlib import Path


def trading_os_repository() -> Path:
    configured = os.environ.get("DEVLOG_TRADING_OS_REPOSITORY")
    candidates = [
        Path(configured) if configured else None,
        Path("/home/ludo/workspace/trading-os"),
        Path("/home/ludo/Bureau/workspace/trading-os"),
    ]
    for candidate in candidates:
        if candidate is not None and candidate.is_dir():
            return candidate
    return next(candidate for candidate in candidates if candidate is not None)


TRADING_OS_REPOSITORY = trading_os_repository()
