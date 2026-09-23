"""Sequential GPT-4.1-mini DEVLOG-only model-effect collection.

The provider/runtime implementation is reused from the frozen V4 collection.
The DEVLOG input is loaded from the already-persisted historical exact-input
projection, so this collection does not rebuild or enrich repository context.
No DIRECT treatment and no OpenCode process are involved.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Any

from evaluations.comparative_baseline.collection_runtime import DevlogContext, EvidenceItem
from evaluations.comparative_baseline.infrastructure import assignment_matrix, load_manifest
from evaluations.comparative_baseline.live_adapters import OpenAIProviderTransport
from evaluations.comparative_baseline_v4.protocol import (
    SafetyCeilings,
    V4Assignment,
    validate_v4_identity,
    write_observation,
)
from evaluations.comparative_baseline_v4.runtime import V4CollectionRuntime


ROOT = Path(__file__).resolve().parents[2]
MODEL = "gpt-4.1-mini"
FROZEN_REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"
QUESTIONS = ("CASE-01-COMPARATIVE", "CASE-03", "CASE-04")
SOURCE_ROOT = ROOT / "data/comparative-baseline/story0133-comparative-baseline-1.0.0/raw"
DEFAULT_OUTPUT = ROOT / "docs/evaluation/gpt-baseline-collection/official"


def _historical_exact_inputs() -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    for question_id in QUESTIONS:
        paths = sorted(SOURCE_ROOT.glob(f"{question_id}:DEVLOG:r?.json"))
        if len(paths) != 3:
            raise RuntimeError(f"expected three persisted context projections for {question_id}")
        inputs = [json.loads(path.read_text(encoding="utf-8"))["observation"]["exactInput"] for path in paths]
        canonical_inputs = {json.dumps(value, sort_keys=True, ensure_ascii=False) for value in inputs}
        if len(canonical_inputs) != 1:
            raise RuntimeError(f"historical DEVLOG context differs across repetitions for {question_id}")
        exact_input = inputs[0]
        if exact_input["condition"] != "DEVLOG" or exact_input["contextRevision"] != "story0132-v3-design-c-context-selection-1.0.0":
            raise RuntimeError(f"unexpected frozen context identity for {question_id}")
        result[question_id] = exact_input
    return result


def _context(exact_input: dict[str, Any]) -> DevlogContext:
    evidence = tuple(
        EvidenceItem(
            item["reference"],
            item["content"],
            item.get("sourceType", "DOCUMENT"),
            item.get("locatorContractVersion", "story0134-locator-1.0.0"),
            item.get("providerVisibleSourceIdentity", item["reference"]),
        )
        for item in exact_input["providerVisibleEvidence"]
    )
    prompt = exact_input["prompt"]
    return DevlogContext(
        question_id=exact_input["questionId"],
        question_version=exact_input["questionVersion"],
        context_strategy=exact_input["contextStrategy"],
        context_digest=exact_input["contextDigest"],
        evidence=evidence,
        prompt_system=prompt["system"],
        prompt_user=prompt["user"],
        context_revision=exact_input["contextRevision"],
        projection_revision=exact_input["projectionRevision"],
    )


def collect(*, output: Path, api_key: str, provider_timeout_seconds: float = 90.0) -> dict[str, Any]:
    manifest = load_manifest()
    v4_manifest = validate_v4_identity()
    if manifest["repositoryRevision"] != FROZEN_REVISION:
        raise RuntimeError("frozen repository revision mismatch")
    if output.exists() and any(output.iterdir()):
        raise RuntimeError(f"refusing to overwrite existing collection: {output}")
    output.mkdir(parents=True, exist_ok=True)

    exact_inputs = _historical_exact_inputs()
    contexts = {question_id: _context(value) for question_id, value in exact_inputs.items()}
    provider = OpenAIProviderTransport(api_key=api_key, model=MODEL, timeout_seconds=provider_timeout_seconds)
    runtime = V4CollectionRuntime(
        provider,
        ceilings=SafetyCeilings.from_manifest(v4_manifest),
        devlog_contexts=contexts,
        run_id="gpt-baseline-devlog-official",
        provider_timeout_seconds=provider_timeout_seconds,
        tool_timeout_seconds=v4_manifest["executionConfiguration"]["nativeRepositoryTimeoutSeconds"],
        test_only=provider_timeout_seconds != v4_manifest["executionConfiguration"]["nativeProviderTimeoutSeconds"],
    )

    rows = [
        row
        for repetition in (1, 2, 3)
        for question_id in QUESTIONS
        for row in assignment_matrix(manifest)
        if row["condition"] == "DEVLOG" and row["questionId"] == question_id and row["repetition"] == repetition
    ]
    if len(rows) != 9:
        raise RuntimeError(f"expected nine DEVLOG assignments, found {len(rows)}")

    observations = []
    for row in rows:
        observation = runtime.run_assignment(V4Assignment.from_row(row))
        destination = output / f"{row['assignmentId']}.json"
        write_observation(destination, observation)
        observations.append(observation)

    manifest_output = {
        "dataset": "GPT_BASELINE_DEVLOG_COLLECTION",
        "model": MODEL,
        "condition": "DEVLOG",
        "repositoryRevision": FROZEN_REVISION,
        "assignmentCount": len(rows),
        "sequential": True,
        "openCodeInvoked": False,
        "contextSource": "data/comparative-baseline/story0133-comparative-baseline-1.0.0/raw/*:DEVLOG:r*.json exactInput",
        "contextIdentities": {
            question_id: {
                "contextDigest": value["contextDigest"],
                "projectionRevision": value["projectionRevision"],
                "providerVisibleEvidenceItems": len(value["providerVisibleEvidence"]),
                "providerVisibleEvidenceBytes": sum(item["contentByteLength"] for item in value["providerVisibleEvidence"]),
            }
            for question_id, value in exact_inputs.items()
        },
        "observationIds": [observation["observationId"] for observation in observations],
        "rawOutputSha256": [observation["rawOutputSha256"] for observation in observations],
    }
    (output / "collection-manifest.json").write_text(json.dumps(manifest_output, indent=2) + "\n", encoding="utf-8")
    return manifest_output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--api-key", default=os.environ.get("LLM_API_KEY"))
    parser.add_argument("--provider-timeout-seconds", type=float, default=90.0)
    args = parser.parse_args()
    if not args.api_key:
        raise SystemExit("LLM_API_KEY is required")
    print(json.dumps(collect(output=args.output, api_key=args.api_key, provider_timeout_seconds=args.provider_timeout_seconds), indent=2))


if __name__ == "__main__":
    main()
