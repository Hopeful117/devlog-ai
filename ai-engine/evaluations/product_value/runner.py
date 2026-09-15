"""CLI for scoring a captured product-value evaluation run."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from .loader import BenchmarkValidationError, apply_frozen_oracle, load_benchmark, validate_artifact_manifest
from .scorer import evaluate_capture, evaluate_experimental_capture


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--capture", type=Path)
    parser.add_argument("--experimental-capture", type=Path, help="three-condition interpretation capture")
    parser.add_argument("--artifact-manifest", type=Path)
    parser.add_argument("--oracle-approved", action="store_true", help="explicit human approval is required")
    parser.add_argument("--oracle-freeze", type=Path, help="frozen Story0131 human oracle overlay")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        benchmark = load_benchmark(args.benchmark)
        if args.oracle_freeze:
            oracle = json.loads(args.oracle_freeze.read_text(encoding="utf-8"))
            benchmark = apply_frozen_oracle(benchmark, oracle)
        oracle_approved = args.oracle_approved or bool(args.oracle_freeze)
        if args.artifact_manifest:
            manifest = json.loads(args.artifact_manifest.read_text(encoding="utf-8"))
            errors = validate_artifact_manifest(benchmark, manifest)
            result = {"status": "ORACLE_VALID" if not errors else "ORACLE_DEFECT_FOUND", "errors": errors}
        elif args.experimental_capture:
            capture = json.loads(args.experimental_capture.read_text(encoding="utf-8"))
            result = evaluate_experimental_capture(benchmark, capture, oracle_approved=oracle_approved)
        elif args.capture:
            capture = json.loads(args.capture.read_text(encoding="utf-8"))
            result = evaluate_capture(benchmark, capture, oracle_approved=oracle_approved)
        else:
            parser.error("one of --capture or --artifact-manifest is required")
    except (OSError, json.JSONDecodeError, BenchmarkValidationError) as exc:
        parser.error(str(exc))
    print(json.dumps(result, indent=2, sort_keys=True))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if result["status"] in {"SCORED", "ORACLE_VALID"} else 2


if __name__ == "__main__":
    raise SystemExit(main())
