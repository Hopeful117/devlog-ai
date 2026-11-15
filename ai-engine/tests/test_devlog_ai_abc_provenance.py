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


def _initialized_repository(root: Path) -> None:
    (root / "tracked.txt").write_text("tracked\n")
    subprocess.run(["git", "-C", str(root), "init", "-q"], check=True)
    subprocess.run(["git", "-C", str(root), "config", "user.email", "q1@example.invalid"], check=True)
    subprocess.run(["git", "-C", str(root), "config", "user.name", "Q1 Test"], check=True)
    subprocess.run(["git", "-C", str(root), "add", "tracked.txt"], check=True)
    subprocess.run(["git", "-C", str(root), "commit", "-qm", "fixture"], check=True)


def test_q1_probe_reads_the_declared_source_commit() -> None:
    probe = snapshot_probe(ROOT)

    assert probe["repositoryId"] == "git:github.com/Hopeful117/devlog-ai"
    assert probe["revision"] == Q1_SOURCE_REVISION
    assert probe["readMode"] == "git_object"
    assert probe["probeContainsAnalyzeStoryContextUseCase"] is True
    assert len(probe["probeSha256"]) == 64


def test_relevant_worktree_changes_are_not_reproducible(tmp_path: Path) -> None:
    _initialized_repository(tmp_path)
    relevant = tmp_path / "ai-engine/evaluations/devlog_ai_abc_runner"
    relevant.mkdir(parents=True)
    source = relevant / "new.py"
    source.write_text("new\n")

    state = worktree_state(tmp_path)
    assert state.reproducible is False
    assert any(path.startswith("ai-engine/evaluations/devlog_ai_abc_runner") for path in state.relevant_changes)


def test_unknown_repository_snapshot_fails_closed() -> None:
    with pytest.raises(ProvenanceError, match="cannot inspect Git provenance"):
        snapshot_probe(ROOT, "0" * 40)


def test_clean_temporary_repository_has_reproducible_state(tmp_path: Path) -> None:
    _initialized_repository(tmp_path)
    assert worktree_state(tmp_path).reproducible is True


def test_out_of_scope_worktree_changes_remain_reproducible(tmp_path: Path) -> None:
    _initialized_repository(tmp_path)
    excluded = tmp_path / "docs/investigations"
    excluded.mkdir(parents=True)
    (excluded / "notes.md").write_text("notes\n")

    state = worktree_state(tmp_path)

    assert state.reproducible is True
    assert state.relevant_changes == ()


def test_generated_python_files_are_excluded_but_source_is_relevant(tmp_path: Path) -> None:
    _initialized_repository(tmp_path)
    relevant = tmp_path / "ai-engine/evaluations/devlog_ai_abc_runner"
    cache = relevant / "__pycache__"
    cache.mkdir(parents=True)
    (cache / "generated.cpython-312.pyc").write_bytes(b"bytecode")
    (relevant / "source.py").write_text("source\n")

    state = worktree_state(tmp_path)

    assert state.reproducible is False
    assert "ai-engine/evaluations/devlog_ai_abc_runner/source.py" in state.relevant_changes
    assert all("__pycache__" not in path and not path.endswith(".pyc") for path in state.relevant_changes)
