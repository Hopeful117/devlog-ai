import subprocess

import pytest

from evaluations.product_value.repository_ground_truth import compare_resolution, resolve_repository


def make_repo(tmp_path):
    subprocess.run(["git", "init", "-q", str(tmp_path)], check=True)
    (tmp_path / "docs").mkdir()
    (tmp_path / "docs" / "a.md").write_text("a\n")
    subprocess.run(["git", "-C", str(tmp_path), "add", "."], check=True)
    subprocess.run(["git", "-C", str(tmp_path), "-c", "user.email=test@example.com", "-c", "user.name=test", "commit", "-qm", "one"], check=True)
    return subprocess.check_output(["git", "-C", str(tmp_path), "rev-parse", "HEAD"], text=True).strip()


def test_resolves_exact_files_and_commits_at_pinned_revision(tmp_path):
    revision = make_repo(tmp_path)
    benchmark = {"repositoryRevision": revision, "cases": [{"expectedEvidence": ["docs/a.md", f"commit:{revision}"]}]}
    result = resolve_repository(benchmark, tmp_path)
    assert result["status"] == "VALID"
    assert result["resolvedArtifactCount"] == 2


def test_missing_file_is_not_found_and_revision_mismatch_is_not_used(tmp_path):
    revision = make_repo(tmp_path)
    benchmark = {"repositoryRevision": revision, "cases": [{"expectedEvidence": ["docs/missing.md"]}]}
    result = resolve_repository(benchmark, tmp_path)
    assert result["artifactResolutions"][0]["status"] == "NOT_FOUND"


def test_devlog_not_found_does_not_change_repository_truth():
    ground_truth = {"repositoryRevision": "rev", "artifactResolutions": [{"artifact": "docs/a.md", "status": "RESOLVED"}]}
    comparison = compare_resolution(ground_truth, {"artifactResolutions": [{"artifact": "docs/a.md", "status": "NOT_FOUND"}]})
    assert comparison["rows"][0]["repositoryGroundTruth"] == "RESOLVED"
    assert comparison["rows"][0]["interpretation"] == "TRUE_BENCHMARK_EVIDENCE_MISSED_BY_DEVLOG"


def test_unavailable_pinned_revision_fails_closed(tmp_path):
    make_repo(tmp_path)
    with pytest.raises(ValueError, match="pinned revision is unavailable"):
        resolve_repository({"repositoryRevision": "0" * 40, "cases": [{"expectedEvidence": []}]}, tmp_path)
