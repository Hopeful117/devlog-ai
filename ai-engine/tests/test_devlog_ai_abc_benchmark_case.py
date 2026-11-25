"""Provider-free tests for Story 0237 question fidelity, scope parity and freshness gating."""

from __future__ import annotations

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from evaluations.devlog_ai_abc_runner.benchmark import (
    BenchmarkCase,
    BenchmarkCaseError,
    assert_question_fidelity,
    citable_scope_instruction,
    load_benchmark,
    load_case,
    parse_case,
)
from evaluations.devlog_ai_abc_runner.live_execution import (
    CoreLiveTreatmentAdapter,
    LiveConfigurationError,
    load_live_case,
    require_known_freshness,
    resolve_case_story,
)


ROOT = Path(__file__).parents[1].parent
ORACLES = ROOT / "docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json"
RUNNER = ROOT / "ai-engine/evaluations/devlog_ai_abc_runner"
CASE_IDS = [f"Q{index}" for index in range(1, 9)]


def _config() -> SimpleNamespace:
    return SimpleNamespace(project_slug="devlog-ai", story_id=None, story_number=223,
                           story_path=None, files=("docs/stories/0223-agentic-quality-real-story-pilot/story.md",))


class RecordingMcp:
    def __init__(self, responses: dict | None = None) -> None:
        self.calls: list[tuple[str, dict]] = []
        self._responses = responses or {}

    def tool(self, name: str, arguments: dict) -> dict:
        self.calls.append((name, arguments))
        if name in self._responses:
            return self._responses[name]
        return {"status": "COMPLETED", "aiTaskId": "task-1",
                "scope": {"resolvedRevision": "0673d1d32433cdaff054d5f1defd1bedd01c0039"},
                "result": {"analysis": {"objectiveUnderstanding": {"summary": "bounded"}}}}


@pytest.mark.parametrize("case_id", CASE_IDS)
def test_every_benchmark_case_declares_an_executable_scope(case_id: str) -> None:
    case = load_case(ROOT, case_id)

    assert case.question.endswith("?")
    assert case.scope_files
    assert case.scope_review_status == "ACCEPTED"
    for prefix in case.scope_files:
        assert (ROOT / prefix).exists(), prefix


def test_case_scopes_are_not_derived_from_oracle_evidence() -> None:
    oracle = json.loads(ORACLES.read_text(encoding="utf-8"))
    benchmark_text = json.dumps(load_benchmark(ROOT))
    for entry in oracle["oracles"]:
        case = load_case(ROOT, entry["caseId"])
        evidence_files = {
            reference.split("#", 1)[0]
            for claim in entry["requiredClaims"]
            for reference in claim.get("evidence", [])
        }
        assert not evidence_files.intersection(case.scope_files), entry["caseId"]
        for claim in entry["requiredClaims"]:
            assert claim["statement"] not in benchmark_text


def test_q1_scope_binds_no_unrelated_story() -> None:
    assert load_case(ROOT, "Q1").story_number is None
    assert load_case(ROOT, "Q5").story_number == 184


@pytest.mark.parametrize("question", [
    None,
    "",
    "Q1 bounded live evaluation",
    "too short?",
])
def test_placeholder_or_missing_question_is_rejected(question) -> None:
    case = {"caseId": "Q1", "version": "1.0.0", "question": question,
            "scopeFiles": ["docs/decisions"], "storyNumber": None,
            "scopeReviewStatus": "PROPOSED_HUMAN_REVIEW"}

    with pytest.raises(BenchmarkCaseError):
        parse_case(case)


@pytest.mark.parametrize("path", ["/etc", "docs/", "docs//x", "../docs", "docs/./x", " docs"])
def test_scope_paths_must_be_normalized_relative_prefixes(path: str) -> None:
    case = {"caseId": "Q1", "version": "1.0.0", "question": load_case(ROOT, "Q1").question,
            "scopeFiles": [path], "storyNumber": None, "scopeReviewStatus": "PROPOSED_HUMAN_REVIEW"}

    with pytest.raises(BenchmarkCaseError):
        parse_case(case)


def test_case_with_oracle_material_is_rejected() -> None:
    case = {"caseId": "Q1", "version": "1.0.0", "question": load_case(ROOT, "Q1").question,
            "scopeFiles": ["docs"], "storyNumber": None, "scopeReviewStatus": "PROPOSED_HUMAN_REVIEW",
            "requiredClaims": []}

    with pytest.raises(BenchmarkCaseError, match="oracle"):
        parse_case(case)


def test_question_fidelity_requires_the_exact_frozen_text() -> None:
    case = load_case(ROOT, "Q1")

    assert_question_fidelity(case.question, case)
    with pytest.raises(BenchmarkCaseError, match="differs"):
        assert_question_fidelity(case.question + " Explain briefly.", case)
    with pytest.raises(BenchmarkCaseError, match="placeholder"):
        assert_question_fidelity("Q1 bounded live evaluation", case)


@pytest.mark.parametrize("condition", ["A_DEVLOG_CURRENT", "C_DEVLOG_ENGINEERING_ANALYST"])
def test_core_conditions_receive_frozen_question_and_case_scope(condition: str) -> None:
    case = load_case(ROOT, "Q1")
    mcp = RecordingMcp()
    manifest = {"repositoryRevision": "0673d1d32433cdaff054d5f1defd1bedd01c0039"}
    adapter = CoreLiveTreatmentAdapter(condition, _config(), manifest, mcp, case=case)

    result = adapter.execute({"observationId": f"Q1-{condition}-r1"}, manifest)

    request = mcp.calls[0][1]
    assert request["question"] == case.question
    assert request["files"] == list(case.scope_files)
    assert "storyId" not in request
    assert result["benchmarkCase"]["questionSha256"] == case.question_sha256
    assert result["storyReference"]["storyId"] is None
    if condition == "C_DEVLOG_ENGINEERING_ANALYST":
        assert request["guidance"]["investigation"]["allowedPathPrefixes"] == list(case.scope_files)
    else:
        assert "investigation" not in request["guidance"]


def test_case_story_is_sent_only_for_story_scoped_cases() -> None:
    case = load_case(ROOT, "Q5")
    mcp = RecordingMcp()
    adapter = CoreLiveTreatmentAdapter("A_DEVLOG_CURRENT", _config(), {}, mcp, case=case,
                                       case_story_id="00000000-0000-0000-0000-000000000184")

    adapter.execute({"observationId": "Q5-A-r1"}, {})

    assert mcp.calls[0][1]["storyId"] == "00000000-0000-0000-0000-000000000184"


def test_b_scope_instruction_matches_core_scope_and_lists_no_oracle_paths() -> None:
    case = load_case(ROOT, "Q1")
    instruction = citable_scope_instruction(case)
    runner_text = "".join(path.read_text(encoding="utf-8") for path in RUNNER.glob("*.py"))

    for prefix in case.scope_files:
        assert prefix in instruction
    assert "Q1-authorized paths" not in runner_text
    assert "StoryContextResultValidator.java" not in runner_text


def test_resolve_case_story_binds_none_without_story() -> None:
    mcp = RecordingMcp()

    assert resolve_case_story(load_case(ROOT, "Q1"), _config(), mcp) is None
    assert mcp.calls == []


def test_resolve_case_story_rejects_a_different_story() -> None:
    mcp = RecordingMcp({"resolve_engineering_story": {
        "id": "00000000-0000-0000-0000-000000000185", "storyNumber": 185}})

    with pytest.raises(LiveConfigurationError, match="unexpected Story"):
        resolve_case_story(load_case(ROOT, "Q5"), _config(), mcp)


@pytest.mark.parametrize("state", ["UNKNOWN", "NOT_ESTABLISHED", "NO_BASELINE", None])
def test_unknown_freshness_blocks_live_execution(state) -> None:
    mcp = RecordingMcp({"story_context_agent_get_projection": {"freshness": {"state": state}}})

    with pytest.raises(LiveConfigurationError, match="Q1_FRESHNESS_UNKNOWN"):
        require_known_freshness(load_case(ROOT, "Q1"), _config(), mcp, "00000000-0000-0000-0000-000000000223")
    name, arguments = mcp.calls[0]
    assert name == "story_context_agent_get_projection"
    assert arguments["files"] == list(load_case(ROOT, "Q1").scope_files)


def test_known_freshness_allows_live_execution() -> None:
    mcp = RecordingMcp({"story_context_agent_get_projection": {
        "freshness": {"state": "PARTIALLY_FRESH", "revisionConsistency": "CONSISTENT"}}})

    gate = require_known_freshness(load_case(ROOT, "Q1"), _config(), mcp, "00000000-0000-0000-0000-000000000223")

    assert gate == {"state": "PARTIALLY_FRESH", "revisionConsistency": "CONSISTENT"}


def test_freshness_gate_without_story_fails_closed() -> None:
    with pytest.raises(LiveConfigurationError, match="Q1_FRESHNESS_GATE_UNAVAILABLE"):
        require_known_freshness(load_case(ROOT, "Q1"), _config(), RecordingMcp(), None)


def test_live_case_must_match_manifest_case_version() -> None:
    assert load_live_case(ROOT, {"caseId": "Q1", "caseVersion": "1.0.0"}).case_id == "Q1"
    with pytest.raises(LiveConfigurationError, match="version"):
        load_live_case(ROOT, {"caseId": "Q1", "caseVersion": "2.0.0"})


def test_unknown_freshness_never_consumes_the_authorization(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    from dataclasses import replace

    from evaluations.devlog_ai_abc_runner import live_execution

    calls: list[str] = []

    class FakeMcp:
        def __init__(self, *args, **kwargs) -> None:
            pass

        def tool(self, name: str, arguments: dict) -> dict:
            calls.append(name)
            if name == "get_bounded_live_evaluation_preparation":
                return {"planId": "plan-1", "projectSlug": "devlog-ai", "planDigest": "digest-1"}
            if name == "story_context_agent_get_projection":
                return {"freshness": {"state": "UNKNOWN"}}
            raise AssertionError(name)

        def close(self) -> None:
            pass

    monkeypatch.setattr(live_execution, "McpClient", FakeMcp)
    monkeypatch.setattr(live_execution, "current_head", lambda root: "rev-1")
    monkeypatch.setattr(live_execution, "assert_reproducible_worktree", lambda root: None)
    monkeypatch.setattr(live_execution, "require_live_provider_environment", lambda env: None)
    monkeypatch.setattr(live_execution, "validate_prepared_plan", lambda *a, **k: None)
    monkeypatch.setattr(
        live_execution, "resolve_live_story",
        lambda config, mcp: replace(config, story_id="00000000-0000-0000-0000-000000000223"))

    with pytest.raises(LiveConfigurationError, match="Q1_FRESHNESS_UNKNOWN"):
        live_execution.run_live_execution(
            manifest_path=tmp_path / "manifest.json",
            output_root=tmp_path / "out",
            repository_root=ROOT,
            manifest={"model": "gpt-5.6-luna", "caseId": "Q1", "caseVersion": "1.0.0", "observations": []},
            authorization={"planId": "plan-1", "planDigest": "digest-1", "runtimeRevision": "rev-1",
                           "authorizationId": "auth-1"},
            prepared_plan={"planId": "plan-1", "planDigest": "digest-1"},
            oracle={},
            environment={
                "DEVLOG_LIVE_PROJECT_SLUG": "devlog-ai",
                "DEVLOG_LIVE_STORY_NUMBER": "223",
                "DEVLOG_LIVE_FILES": "docs/stories/0223-agentic-quality-real-story-pilot/story.md",
                "DEVLOG_MCP_LAUNCHER": "/tmp/devlog-mcp-start.sh",
                "LLM_PROVIDER": "openai_chatgpt",
                "CHATGPT_OAUTH_ENABLED": "true",
                "DEVLOG_LIVE_REMOTE_PROVIDER": "true",
            },
        )

    assert "consume_bounded_live_evaluation_authorization" not in calls
    assert "story_context_agent_prepare_task_binding" not in calls
