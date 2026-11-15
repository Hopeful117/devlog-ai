"""Provider-free tests for Q1 source/runtime/harness provenance."""

from __future__ import annotations

import subprocess
from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_runner.provenance import (
    ProvenanceError,
    Q1_SOURCE_REVISION,
    snapshot_probe,
    worktree_state,
)


ROOT = Path(__file__).parents[1].parent


def test_q1_probe_reads_the_declared_source_commit() -> None:
    probe = snapshot_probe(ROOT)

    assert probe["repositoryId"] == "git:github.com/Hopeful117/devlog-ai"
    assert probe["revision"] == Q1_SOURCE_REVISION
    assert probe["readMode"] == "git_object"
    assert probe["probeContainsAnalyzeStoryContextUseCase"] is True
    assert len(probe["probeSha256"]) == 64


def test_relevant_worktree_changes_are_not_reproducible() -> None:
    state = worktree_state(ROOT)

    assert state.reproducible is False
    assert any(path.startswith("ai-engine/evaluations/devlog_ai_abc_runner") for path in state.relevant_changes)


def test_unknown_repository_snapshot_fails_closed() -> None:
    with pytest.raises(ProvenanceError, match="cannot inspect Git provenance"):
        snapshot_probe(ROOT, "0" * 40)


def test_clean_temporary_repository_has_reproducible_state(tmp_path: Path) -> None:
    (tmp_path / "tracked.txt").write_text("tracked\n")
    subprocess.run(["git", "-C", str(tmp_path), "init", "-q"], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "config", "user.email", "q1@example.invalid"], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "config", "user.name", "Q1 Test"], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "add", "tracked.txt"], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "commit", "-qm", "fixture"], check=True)

    assert worktree_state(tmp_path).reproducible is True

    (tmp_path / "ai-engine/evaluations/devlog_ai_abc_runner").mkdir(parents=True)
    (tmp_path / "ai-engine/evaluations/devlog_ai_abc_runner/new.py").write_text("new\n")
    assert worktree_state(tmp_path).reproducible is False
