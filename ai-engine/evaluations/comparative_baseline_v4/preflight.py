"""Command-line entry point for the provider-free V4 qualification."""

from __future__ import annotations

import json

from .protocol import run_preflight


def main() -> None:
    print(json.dumps(run_preflight(), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
