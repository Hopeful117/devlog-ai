"""Reproducibility checks for the provider-free Q1 campaign.

The source snapshot, treatment runtimes, and benchmark harness are separate
identities. This module hashes only repository artifacts and never reads
credentials or runtime configuration values.
"""

from __future__ import annotations

import hashlib
import json
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


Q1_SOURCE_REVISION = "0673d1d32433cdaff054d5f1defd1bedd01c0039"
Q1_REPOSITORY_ID = "git:github.com/Hopeful117/devlog-ai"

Q1_RUNTIME_IDENTITIES = {
    "A": {
        "runtime": "java-core-story-context-one-shot",
        "entrypoint": "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java",
        "promptVersion": "story-context-agent-protocol/v1",
        "answerContract": "story-context-analysis-result-v1",
    },
    "B": {
        "runtime": "comparative-v4-direct-investigator",
        "entrypoint": "ai-engine/evaluations/comparative_baseline_v4/runtime.py",
        "configuration": "docs/evaluation/devlog-ai-q1-b-direct-config-v1.json",
        "promptVersion": "comparative-v4-common-answer-1.0.0",
        "answerContract": "comparative-v4-common-answer-1.0.0",
    },
    "C": {
        "runtime": "python-core-bounded-investigation",
        "entrypoint": "ai-engine/app/services/story_context_analysis_generation_service.py",
        "promptVersion": "story-context-agent-protocol/v2",
        "answerContract": "story-context-analysis-result-v1",
    },
}

Q1_HARNESS_PATHS = (
    "ai-engine/evaluations/devlog_ai_abc_runner",
    "ai-engine/evaluations/devlog_ai_abc_normalization",
    "ai-engine/tests/test_devlog_ai_abc_runner.py",
    "ai-engine/tests/test_devlog_ai_abc_preflight.py",
    "ai-engine/tests/test_devlog_ai_abc_live_preflight.py",
    "docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json",
    "docs/evaluation/devlog-ai-q1-b-direct-config-v1.json",
    "docs/evaluation/devlog-ai-abc-benchmark-v1.json",
    "docs/evaluation/devlog-ai-abc-campaign-manifest-v1.json",
    "backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/StoryContextCoreIntegrationTest.java",
)

Q1_RUNTIME_PATHS = (
    "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis",
    "ai-engine/app/services/story_context_analysis_generation_service.py",
    "ai-engine/app/prompts/story_context_analysis.py",
    "ai-engine/app/schemas/story_context_investigation.py",
    "ai-engine/app/clients/core_callback_client.py",
    "ai-engine/evaluations/comparative_baseline_v4",
    "ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json",
)


class ProvenanceError(ValueError):
    """Raised when effective Q1 provenance cannot be established."""


@dataclass(frozen=True)
class WorktreeState:
    modified: tuple[str, ...]
    untracked: tuple[str, ...]
    ignored: tuple[str, ...]

    @property
    def relevant_changes(self) -> tuple[str, ...]:
        paths = (*self.modified, *self.untracked, *self.ignored)
        return tuple(sorted(path for path in paths if _is_relevant(path)))

    @property
    def reproducible(self) -> bool:
        return not self.relevant_changes


def _git(root: Path, *args: str) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(root), *args],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.CalledProcessError) as error:
        raise ProvenanceError("cannot inspect Git provenance") from error
    return result.stdout


def _is_relevant(path: str) -> bool:
    normalized = path.replace("\\", "/")
    if "/__pycache__/" in f"/{normalized}/" or normalized.endswith(".pyc"):
        return False
    return any(
        normalized == prefix or normalized.startswith(prefix.rstrip("/") + "/")
        for prefix in (*Q1_HARNESS_PATHS, *Q1_RUNTIME_PATHS)
    )


def worktree_state(root: Path) -> WorktreeState:
    lines = _git(root.resolve(), "status", "--porcelain=v1", "--untracked-files=all", "--ignored").splitlines()
    modified: list[str] = []
    untracked: list[str] = []
    ignored: list[str] = []
    for line in lines:
        if len(line) < 4:
            continue
        code = line[:2]
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        if code == "!!":
            ignored.append(path)
        elif code == "??":
            untracked.append(path)
        else:
            modified.append(path)
    return WorktreeState(tuple(sorted(modified)), tuple(sorted(untracked)), tuple(sorted(ignored)))


def current_head(root: Path) -> str:
    return _git(root.resolve(), "rev-parse", "HEAD").strip()


def verify_commit(root: Path, revision: str) -> None:
    actual = _git(root.resolve(), "rev-parse", "--verify", f"{revision}^{{commit}}").strip()
    if actual != revision:
        raise ProvenanceError(f"repository snapshot is unavailable: {revision}")


def snapshot_probe(root: Path, revision: str = Q1_SOURCE_REVISION) -> dict[str, Any]:
    """Read a bounded known file from the pinned commit, not the worktree."""

    verify_commit(root, revision)
    path = "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java"
    try:
        content = _git(root.resolve(), "show", f"{revision}:{path}")
    except ProvenanceError as error:
        raise ProvenanceError("Q1 source snapshot cannot be read") from error
    return {
        "repositoryId": Q1_REPOSITORY_ID,
        "revision": revision,
        "readMode": "git_object",
        "probePath": path,
        "probeSha256": hashlib.sha256(content.encode("utf-8")).hexdigest(),
        "probeContainsAnalyzeStoryContextUseCase": "class AnalyzeStoryContextUseCase" in content,
    }


def harness_identity(root: Path) -> dict[str, Any]:
    files: list[dict[str, str]] = []
    for entry in Q1_HARNESS_PATHS:
        path = root / entry
        if path.is_file():
            files.append({"path": entry, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
        elif path.is_dir():
            for child in sorted(
                item
                for item in path.rglob("*")
                if item.is_file()
                and "__pycache__" not in item.parts
                and item.suffix != ".pyc"
            ):
                relative = child.relative_to(root).as_posix()
                files.append({"path": relative, "sha256": hashlib.sha256(child.read_bytes()).hexdigest()})
        else:
            files.append({"path": entry, "sha256": "MISSING"})
    canonical = json.dumps(files, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return {
        "identityVersion": "devlog-ai-q1-harness-identity-v1",
        "files": files,
        "sha256": hashlib.sha256(canonical).hexdigest(),
    }


def execution_identity(root: Path, *, runtime_revision: str | None = None) -> dict[str, Any]:
    state = worktree_state(root)
    head = current_head(root)
    return {
        "sourceSnapshot": snapshot_probe(root),
        "runtimeRevision": runtime_revision or head,
        "runtimeIdentities": Q1_RUNTIME_IDENTITIES,
        "harness": harness_identity(root),
        "worktree": {
            "reproducible": state.reproducible,
            "relevantChanges": list(state.relevant_changes),
        },
    }


def assert_reproducible_worktree(root: Path) -> WorktreeState:
    state = worktree_state(root)
    if not state.reproducible:
        raise ProvenanceError(
            "Q1 effective worktree is not reproducible: " + ", ".join(state.relevant_changes)
        )
    return state
