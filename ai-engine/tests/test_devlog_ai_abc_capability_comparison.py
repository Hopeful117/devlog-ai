"""Provider-free Q1 search/read comparison tests."""

from pathlib import Path

import pytest

from evaluations.devlog_ai_abc_capability_comparison import (
    C_MAX_BYTES,
    C_MAX_TOKENS,
    PinnedQ1Repository,
    project_c_read,
    validate_c_action,
)
from evaluations.devlog_ai_abc_capability_comparison.q1_fixture import CapabilityFixtureError


ROOT = Path(__file__).resolve().parents[2]
REVISION = "fa33f03d9af42e3ac6e64112ecebe38d86de7b9b"


@pytest.fixture
def repository() -> PinnedQ1Repository:
    return PinnedQ1Repository(ROOT, REVISION)


def test_b_search_finds_q1_java_python_and_adr_content(repository: PinnedQ1Repository) -> None:
    assert repository.b_search("class AnalyzeStoryContextUseCase")["matches"]
    assert repository.b_search("InvestigationPlanProposal")["matches"]
    assert repository.b_search("Java/Core owns", path_prefix="docs/decisions")["matches"]


def test_b_read_and_c_read_projection_match_on_same_authorized_content(repository: PinnedQ1Repository) -> None:
    path = "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java"
    b_result = repository.b_read(path, start_line=105, end_line=110)
    b_full = repository.b_read(path)
    c_error = validate_c_action(
        "READ_EVIDENCE_RANGE",
        revision=REVISION,
        expected_revision=REVISION,
        path=b_result["path"],
        allowed_path_prefixes=("backend",),
        start_line=105,
        end_line=110,
    )
    assert c_error is None
    c_result = project_c_read(b_full, path=b_result["path"], start_line=105, end_line=110, revision=REVISION)
    assert c_result["content"] == b_result["content"]
    assert c_result["reference"] == b_result["reference"]
    assert c_result["revision"] == b_result["revision"]


def test_no_result_is_consistent_for_direct_search_fixture(repository: PinnedQ1Repository) -> None:
    absent_query = "__q1_absent_" + "probe__"
    assert repository.b_search(absent_query)["matches"] == []


@pytest.mark.parametrize(
    ("kwargs", "expected"),
    [
        ({"revision": "wrong", "expected_revision": REVISION}, "REVISION_MISMATCH"),
        ({"revision": REVISION, "expected_revision": REVISION, "path": "backend/X.java", "allowed_path_prefixes": ("docs",)}, "PATH_OUT_OF_SCOPE"),
        ({"revision": REVISION, "expected_revision": REVISION, "path": "backend/X.java", "allowed_path_prefixes": ("backend",), "start_line": 1, "end_line": 501}, "INVALID_LOCATOR"),
        ({"revision": REVISION, "expected_revision": REVISION, "path": "backend/X.java", "allowed_path_prefixes": ("backend",), "start_line": 1, "end_line": 2, "maximum_bytes": C_MAX_BYTES + 1}, "CAPABILITY_LIMIT_EXCEEDED"),
    ],
)
def test_c_rejects_revision_scope_range_and_limit_violations(kwargs: dict, expected: str) -> None:
    assert validate_c_action("READ_EVIDENCE_RANGE", **kwargs) == expected


def test_b_rejects_invalid_path_and_missing_file(repository: PinnedQ1Repository) -> None:
    with pytest.raises(CapabilityFixtureError):
        repository.b_read("../outside.txt")
    with pytest.raises(CapabilityFixtureError):
        repository.b_read("docs/__q1_missing__.txt")


def test_c_projection_rejects_missing_or_wrong_revision_evidence() -> None:
    evidence = {"reference": "docs/example.md", "path": "docs/example.md", "revision": REVISION, "content": "one\ntwo"}
    with pytest.raises(CapabilityFixtureError, match="UNAUTHORIZED_RESULT"):
        project_c_read(evidence, path="docs/other.md", start_line=1, end_line=1, revision=REVISION)
    with pytest.raises(CapabilityFixtureError, match="UNAUTHORIZED_RESULT"):
        project_c_read(evidence, path="docs/example.md", start_line=1, end_line=1, revision="wrong")


def test_c_token_and_byte_bounds_are_explicit() -> None:
    assert validate_c_action("SEARCH_EVIDENCE", revision=REVISION, expected_revision=REVISION, maximum_bytes=C_MAX_BYTES, maximum_tokens=C_MAX_TOKENS) is None
    assert validate_c_action("SEARCH_EVIDENCE", revision=REVISION, expected_revision=REVISION, maximum_tokens=C_MAX_TOKENS + 1) == "CAPABILITY_LIMIT_EXCEEDED"
