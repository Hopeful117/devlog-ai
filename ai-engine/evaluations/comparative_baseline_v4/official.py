"""Explicit runner for the frozen V4 official 18-slot collection."""

from __future__ import annotations

import argparse
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from evaluations.comparative_baseline.live_adapters import (
    ComparativeJavaGroundingAuthority,
    FrozenDevlogContextAdapter,
    OpenAIProviderTransport,
    PinnedGitRepositoryTools,
    live_preflight,
)
from evaluations.comparative_baseline.infrastructure import load_manifest
from evaluations.repository_paths import TRADING_OS_REPOSITORY

from .protocol import (
    SafetyCeilings,
    v4_assignment_matrix,
    v4_experiment_identity,
    v4_official_plan_identity,
    raw_output_hash,
    validate_v4_identity,
    validate_v4_plan,
    write_derived_projection,
)
from evaluations.comparative_baseline.infrastructure import write_immutable_json
from .runtime import V4CollectionOrchestrator, V4CollectionRuntime


EXPECTED_EXPERIMENT_IDENTITY = "9b45c5d2a7c6d87a612eda7f4eea694d90af7c55e4106d7b8767adc8806cea13"
EXPECTED_RUNTIME_DIGEST = "99f1c7dfe7e0e4f205f081150a1c8c943b924c53f15f9bd25bfad81e2c01f1ca"
EXPECTED_EXECUTION_HASH = "93ac73ac27e89ad329aa4da5210931c198f1227b0be3f5980e773f673f203534"
EXPECTED_OFFICIAL_PLAN_IDENTITY = "6cd9936ec521f20a17fdd0fa9aca51deb663ae4f05a6a1cb749b49d62163f8ca"


def _attempt_id() -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"v4-official-{timestamp}-{uuid4().hex[:8]}"


class OfficialStorage:
    """Write-once official artifacts, isolated from pilot storage."""

    def __init__(self, root: Path, run_id: str, *, plan_identity: dict[str, object], experiment_identity: dict[str, object]):
        self.root = root.resolve()
        self.run_id = run_id
        self.plan_identity = plan_identity
        self.experiment_identity = experiment_identity
        self.run_root = self.root / "official-collection" / run_id

    @property
    def ledger_path(self) -> Path:
        return self.run_root / "ledger.json"

    def write_v4_observation(self, observation: dict[str, object]) -> Path:
        assignment_id = str(observation["assignment"]["assignmentId"])
        artifact = {
            "artifactVersion": observation["rawSchemaVersion"],
            "immutable": True,
            "executionClass": "OFFICIAL_COLLECTION",
            "baselineEligible": True,
            "attemptId": self.run_id,
            "officialPlanIdentity": self.plan_identity,
            "experimentIdentity": self.experiment_identity,
            "observation": observation,
        }
        destination = self.run_root / "artifacts" / f"{assignment_id}.json"
        if destination.exists():
            raise FileExistsError(f"official artifact already exists: {destination}")
        destination.parent.mkdir(parents=True, exist_ok=True)
        artifact["artifactSha256"] = raw_output_hash({key: value for key, value in artifact.items() if key != "artifactSha256"})
        write_immutable_json(destination, artifact)
        write_derived_projection(self.run_root / "derived" / f"{assignment_id}.json", observation)
        return destination

    def write_manifest(self, manifest: dict[str, object]) -> None:
        destination = self.run_root / "collection-manifest.json"
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def run_official(*, repository: str | Path, root: str | Path) -> dict[str, object]:
    manifest = validate_v4_identity()
    ceilings = SafetyCeilings.from_manifest(manifest)
    experiment = v4_experiment_identity(safety=ceilings)
    plan = v4_assignment_matrix()
    validate_v4_plan(plan)
    plan_identity = v4_official_plan_identity(safety=ceilings)
    runtime_digest = experiment["inputs"]["runtimeContract"]["sha256"]
    execution_hash = manifest["executionConfiguration"]["sha256"]
    if experiment["sha256"] != EXPECTED_EXPERIMENT_IDENTITY:
        raise RuntimeError("frozen experiment identity mismatch")
    if runtime_digest != EXPECTED_RUNTIME_DIGEST:
        raise RuntimeError("frozen runtime contract mismatch")
    if execution_hash != EXPECTED_EXECUTION_HASH:
        raise RuntimeError("frozen execution configuration mismatch")
    if len(plan) != 18 or plan_identity["sha256"] != EXPECTED_OFFICIAL_PLAN_IDENTITY or plan_identity["inputs"]["experimentIdentity"] != EXPECTED_EXPERIMENT_IDENTITY:
        raise RuntimeError("frozen official plan mismatch")

    repository = Path(repository).resolve()
    run_id = _attempt_id()
    storage = OfficialStorage(Path(root), run_id, plan_identity=plan_identity, experiment_identity=experiment)
    grounding = ComparativeJavaGroundingAuthority(repository_root=Path(__file__).resolve().parents[3])
    live = live_preflight(repository=repository, grounding=grounding, pilot_storage=storage, environment=os.environ)
    if live.provider != manifest["provider"] or live.model != manifest["model"] or live.repository_revision != manifest["repositoryRevision"]:
        raise RuntimeError("live provider/model/repository identity mismatch")
    questions = load_manifest()["questions"]
    contexts = {question["questionId"]: FrozenDevlogContextAdapter(repository).build(question) for question in questions}
    storage.write_manifest({
        "executionClass": "OFFICIAL_COLLECTION",
        "attemptId": run_id,
        "experimentIdentity": experiment,
        "officialPlanIdentity": plan_identity,
        "executionConfigurationHash": execution_hash,
        "repositoryRevision": manifest["repositoryRevision"],
        "assignmentCount": len(plan),
    })
    provider = OpenAIProviderTransport(api_key=os.environ["LLM_API_KEY"], model=manifest["model"])
    runtime = V4CollectionRuntime(
        provider,
        ceilings=ceilings,
        devlog_contexts=contexts,
        tool_factory=lambda _assignment: PinnedGitRepositoryTools(repository),
        run_id=run_id,
        provider_timeout_seconds=manifest["executionConfiguration"]["nativeProviderTimeoutSeconds"],
        tool_timeout_seconds=manifest["executionConfiguration"]["nativeRepositoryTimeoutSeconds"],
    )
    orchestrator = V4CollectionOrchestrator(runtime, run_id=run_id, ledger_path=storage.ledger_path, artifact_writer=storage.write_v4_observation)
    results = orchestrator.run(plan)
    return {"attemptId": run_id, "attemptRoot": str(storage.run_root), "experimentIdentity": experiment, "executionConfigurationHash": execution_hash, "officialPlanIdentity": plan_identity, "observations": results, "livePreflight": live.__dict__, "providerCalls": sum(item["resources"]["providerTransportAttempts"] for item in results), "networkCalls": 0}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", default=str(TRADING_OS_REPOSITORY))
    parser.add_argument("--root", default="data/comparative-baseline-v4")
    args = parser.parse_args()
    result = run_official(repository=args.repository, root=args.root)
    print(json.dumps({key: value for key, value in result.items() if key != "observations"}, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
