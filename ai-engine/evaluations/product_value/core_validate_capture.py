"""Validate an existing V2 capture through the Java Core evaluation bridge."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path

from .causal_mapping import build_frozen_causal_questions
from .repository_ground_truth import build_ground_truth_contexts
from .v2_live_runner import _preflight, _validate_through_core
from app.core.config import Settings


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--oracle", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--first-green", type=Path, required=True)
    parser.add_argument("--original-red", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    settings = Settings.from_environment()
    benchmark, contexts, preflight = _preflight(args, settings)
    capture = json.loads(args.capture.read_text(encoding="utf-8"))
    result = copy.deepcopy(capture)
    runs = result["baseline"]["runs"]
    bridge_summary = _validate_through_core(runs, contexts, benchmark)
    result["baseline"]["runs"] = runs
    result["coreValidation"] = {
        "status": "CORE_VALIDATED",
        "bridge": "CoreV2EvaluationBridgeTest",
        "preflight": preflight,
        "providerCalls": 0,
        "sourceRunId": capture["runId"],
        "sourceCaptureSha256": hashlib.sha256(args.capture.read_bytes()).hexdigest(),
        "newSemanticCalls": False,
        **bridge_summary,
        "unparsedPythonSlots": sum(item["normalizedResult"] is None for item in runs),
        "totalSlots": len(runs),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "status": "CORE_VALIDATED",
        "sourceRunId": capture["runId"],
        "observedSlots": len(runs),
        "output": str(args.output),
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
