"""Explicit six-slot V4 pilot runner.

This command is intentionally separate from the official 18-slot collection.
It performs identity checks before constructing the live provider transport and
never retries an assignment or expands the frozen pilot plan.
"""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from evaluations.comparative_baseline.infrastructure import load_manifest
from evaluations.repository_paths import TRADING_OS_REPOSITORY
from evaluations.comparative_baseline.live_adapters import (
    ComparativeJavaGroundingAuthority,
    FrozenDevlogContextAdapter,
    OpenAIProviderTransport,
    PilotStorage,
    PinnedGitRepositoryTools,
    live_preflight,
)

from .protocol import (
    SafetyCeilings,
    replay_observation,
    validate_v4_identity,
    validate_v4_pilot_plan,
    v4_experiment_identity,
    v4_pilot_assignment_matrix,
    v4_pilot_identity,
)
from .runtime import V4CollectionOrchestrator, V4CollectionRuntime


EXPECTED_EXPERIMENT_IDENTITY = "9b45c5d2a7c6d87a612eda7f4eea694d90af7c55e4106d7b8767adc8806cea13"
EXPECTED_RUNTIME_DIGEST = "99f1c7dfe7e0e4f205f081150a1c8c943b924c53f15f9bd25bfad81e2c01f1ca"
EXPECTED_EXECUTION_HASH = "93ac73ac27e89ad329aa4da5210931c198f1227b0be3f5980e773f673f203534"
EXPECTED_PILOT_IDENTITY = "211bae21fbfcaadbe49f1faf6e5edb9729dfff926962aee823ae0a111fe2e0ad"


def _attempt_id() -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"v4-pilot-{timestamp}-{uuid4().hex[:8]}"


def run_pilot(*, repository: str | Path, pilot_root: str | Path) -> dict[str, object]:
    manifest = validate_v4_identity()
    ceilings = SafetyCeilings.from_manifest(manifest)
    experiment = v4_experiment_identity(safety=ceilings)
    pilot_plan = v4_pilot_assignment_matrix()
    validate_v4_pilot_plan(pilot_plan)
    pilot_identity = v4_pilot_identity(safety=ceilings)
    execution_hash = manifest["executionConfiguration"]["sha256"]
    if experiment["sha256"] != EXPECTED_EXPERIMENT_IDENTITY:
        raise RuntimeError("frozen experiment identity mismatch")
    if execution_hash != EXPECTED_EXECUTION_HASH:
        raise RuntimeError("frozen execution configuration hash mismatch")
    if pilot_identity["sha256"] != EXPECTED_PILOT_IDENTITY:
        raise RuntimeError("frozen pilot-plan identity mismatch")

    repository = Path(repository).resolve()
    attempt_id = _attempt_id()
    storage = PilotStorage(Path(pilot_root), attempt_id, Path("data/comparative-baseline"))
    grounding = ComparativeJavaGroundingAuthority(repository_root=Path(__file__).resolve().parents[3])
    live = live_preflight(repository=repository, grounding=grounding, pilot_storage=storage, environment=os.environ)
    if live.provider != manifest["provider"] or live.model != manifest["model"] or live.repository_revision != manifest["repositoryRevision"]:
        raise RuntimeError("live provider/model/repository identity mismatch")

    questions = load_manifest()["questions"]
    contexts = {question["questionId"]: FrozenDevlogContextAdapter(repository).build(question) for question in questions}
    provider = OpenAIProviderTransport(api_key=os.environ["LLM_API_KEY"], model=manifest["model"])
    runtime = V4CollectionRuntime(
        provider,
        ceilings=ceilings,
        devlog_contexts=contexts,
        tool_factory=lambda _assignment: PinnedGitRepositoryTools(repository),
        run_id=attempt_id,
        provider_timeout_seconds=manifest["executionConfiguration"]["nativeProviderTimeoutSeconds"],
        tool_timeout_seconds=manifest["executionConfiguration"]["nativeRepositoryTimeoutSeconds"],
    )
    orchestrator = V4CollectionOrchestrator(runtime, run_id=attempt_id, ledger_path=storage.ledger_path, artifact_writer=storage.write_v4_observation)
    results = orchestrator.run(pilot_plan)
    replay = [replay_observation(observation) for observation in results]
    ledger = json.loads(storage.ledger_path.read_text(encoding="utf-8"))
    return {
        "attemptId": attempt_id,
        "attemptRoot": str(storage.root / "live-pilot" / attempt_id),
        "experimentIdentity": experiment,
        "executionConfigurationHash": execution_hash,
        "pilotIdentity": pilot_identity,
        "pilotPlan": pilot_plan,
        "observations": results,
        "replay": replay,
        "ledger": ledger,
        "providerCalls": sum(item["resources"]["providerTransportAttempts"] for item in results),
        "networkCalls": 0,
        "livePreflight": live.__dict__,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=str(TRADING_OS_REPOSITORY))
    parser.add_argument("--pilot-root", default="data/comparative-baseline-v4")
    args = parser.parse_args()
    result = run_pilot(repository=args.repository, pilot_root=args.pilot_root)
    output = {key: value for key, value in result.items() if key not in {"observations", "ledger"}}
    print(json.dumps(output, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
