import json

import pytest

from evaluations.comparative_baseline import live_adapters
from evaluations.comparative_baseline.collection_runtime import Assignment, RuntimeContractError
from evaluations.comparative_baseline.infrastructure import assert_official_baseline_eligible, load_manifest


def test_common_schema_is_strict_and_matches_frozen_answer_fields():
    assignment = Assignment("a", "CASE-01-COMPARATIVE", "1.0.0", "CASE-01", "DEVLOG", 1)
    schema = live_adapters.common_answer_schema(assignment)
    assert schema["additionalProperties"] is False
    assert schema["required"] == [
        "questionId", "questionVersion", "answerText", "relationshipResult",
        "abstention", "claims", "evidence", "confidence",
    ]
    assert schema["properties"]["questionId"]["enum"] == ["CASE-01-COMPARATIVE"]
    assert schema["properties"]["questionVersion"]["enum"] == ["1.0.0"]
    branches = schema["properties"]["evidence"]["items"]["properties"]["locator"]["anyOf"]
    commit_branch = next(branch for branch in branches if branch["properties"]["kind"]["enum"] == ["COMMIT_HUNK"])
    assert "header" in commit_branch["required"]
    assert commit_branch["properties"]["header"] == {"type": "string"}


def test_devlog_adapter_preserves_case01_projection_identity(monkeypatch, tmp_path):
    identity = {"revision": "context-revision", "digest": "a" * 64}
    items = [
        {"reference": f"ref-{index}", "content": "x" * 8100, "sourceType": "DOCUMENT", "contentByteLength": 8100}
        for index in range(4)
    ]
    items[-1]["content"] += "x"
    items[-1]["contentByteLength"] += 1

    monkeypatch.setattr("evaluations.product_value.v3_design_c.context_selection_identity", lambda: identity)
    monkeypatch.setattr("evaluations.product_value.v3_design_c.projection_digest", lambda _case: "b" * 64)
    monkeypatch.setattr("evaluations.product_value.v3_design_c.projection_items", lambda *_args: items)

    question = load_manifest()["questions"][0]
    context = live_adapters.FrozenDevlogContextAdapter(tmp_path).build(question)

    assert context.question_id == "CASE-01-COMPARATIVE"
    assert context.evidence_bytes == 32401
    assert len(context.evidence) == 4
    assert context.context_digest == "a" * 64
    assert context.projection_revision == "CASE-01:" + "b" * 64
    assert "QUESTION_ID\nCASE-01-COMPARATIVE" in context.prompt_user
    assert "QUESTION_VERSION\n1.0.0" in context.prompt_user


def test_devlog_adapter_uses_per_question_projection_cardinality():
    adapter = live_adapters.FrozenDevlogContextAdapter("/home/ludo/Bureau/workspace/trading-os")
    contexts = {
        question["questionId"]: adapter.build(question)
        for question in load_manifest()["questions"]
    }
    assert len(contexts["CASE-01-COMPARATIVE"].evidence) == 4
    assert len(contexts["CASE-03"].evidence) == 3
    assert len(contexts["CASE-04"].evidence) == 4
    for question_id, context in contexts.items():
        assert f"QUESTION_ID\n{question_id}" in context.prompt_user
        assert f"QUESTION_VERSION\n{context.question_version}" in context.prompt_user


def test_pinned_repository_rejects_wrong_revision_before_tools(monkeypatch, tmp_path):
    monkeypatch.setattr(live_adapters, "_git_command", lambda *_args, **_kwargs: live_adapters.FROZEN_REPOSITORY_REVISION + "\n")
    tools = live_adapters.PinnedGitRepositoryTools(tmp_path)
    with pytest.raises(RuntimeContractError):
        tools.execute("read_file", {"path": "docs/example.md", "repositoryRevision": "0" * 40})


def test_pinned_repository_search_parses_matches_with_colons_and_empty_results():
    tools = live_adapters.PinnedGitRepositoryTools("/home/ludo/Bureau/workspace/trading-os")
    matches = tools.execute("search_repository", {"query": "ADR-042", "maxMatches": 20})["matches"]
    assert len(matches) == 20
    assert all(isinstance(item["line"], int) for item in matches)
    assert any(":" in item["text"] for item in matches)
    assert tools.execute("search_repository", {"query": "__story0136_no_match__"})["matches"] == []


def test_pinned_repository_search_rejects_malformed_output(monkeypatch, tmp_path):
    monkeypatch.setattr(live_adapters, "_git_command", lambda *_args, **_kwargs: "malformed")
    tools = live_adapters.PinnedGitRepositoryTools.__new__(live_adapters.PinnedGitRepositoryTools)
    tools.repository = tmp_path
    tools.revision = live_adapters.FROZEN_REPOSITORY_REVISION
    with pytest.raises(RuntimeContractError, match="search output is malformed"):
        tools.execute("search_repository", {"query": "anything"})


def test_live_preflight_requires_explicit_core_grounding(monkeypatch, tmp_path):
    monkeypatch.setattr(live_adapters, "PinnedGitRepositoryTools", lambda *_args, **_kwargs: object())
    environment = {"LLM_PROVIDER": "openai", "LLM_MODEL": "gpt-4.1-mini", "LLM_API_KEY": "test-key"}
    pilot = live_adapters.PilotStorage(tmp_path / "pilot", "run", tmp_path / "official")
    with pytest.raises(RuntimeContractError, match="approved comparative"):
        live_adapters.live_preflight(repository=tmp_path, grounding=None, environment=environment)
    with pytest.raises(RuntimeContractError, match="approved comparative"):
        live_adapters.live_preflight(repository=tmp_path, grounding=object(), pilot_storage=pilot, environment=environment)


def test_live_preflight_requires_pilot_storage(monkeypatch, tmp_path):
    monkeypatch.setattr(live_adapters, "PinnedGitRepositoryTools", lambda *_args, **_kwargs: object())
    grounding = object.__new__(live_adapters.ComparativeJavaGroundingAuthority)
    environment = {"LLM_PROVIDER": "openai", "LLM_MODEL": "gpt-4.1-mini", "LLM_API_KEY": "test-key"}
    with pytest.raises(RuntimeContractError, match="isolated pilot storage"):
        live_adapters.live_preflight(repository=tmp_path, grounding=grounding, environment=environment)


def test_pilot_storage_isolated_and_baseline_ineligible(tmp_path):
    pilot = live_adapters.PilotStorage(tmp_path / "pilot", "run", tmp_path / "official")
    metadata = pilot.metadata("CASE-01-COMPARATIVE:AGENT_DIRECT:r1")
    assert metadata["executionClass"] == "LIVE_PILOT"
    assert metadata["baselineEligible"] is False
    assert metadata["assignmentId"].startswith("LIVE_PILOT:")
    assert pilot.ledger_path != tmp_path / "official" / "ledger.json"
    with pytest.raises(ValueError, match="live pilot"):
        assert_official_baseline_eligible(metadata)
    with pytest.raises(RuntimeContractError):
        pilot.validate_artifact({"executionClass": "OFFICIAL_BASELINE", "baselineEligible": True})


def test_pilot_artifact_is_write_once_and_retains_isolation_metadata(tmp_path):
    pilot = live_adapters.PilotStorage(tmp_path / "pilot", "run", tmp_path / "official")
    artifact = {**pilot.metadata("CASE-03:AGENT_DIRECT:r1"), "observation": {"status": "SYNTHETIC"}}
    destination = pilot.write_artifact("CASE-03:AGENT_DIRECT:r1", artifact)
    loaded = json.loads(destination.read_text(encoding="utf-8"))
    pilot.validate_artifact(loaded)
    assert loaded["executionClass"] == "LIVE_PILOT"
    assert loaded["baselineEligible"] is False
    with pytest.raises(FileExistsError):
        pilot.write_artifact("CASE-03:AGENT_DIRECT:r1", artifact)


def test_openai_transport_does_not_construct_without_a_key():
    with pytest.raises(RuntimeContractError, match="LLM_API_KEY"):
        live_adapters.OpenAIProviderTransport(api_key="")


def test_live_runtime_requires_explicit_authorization_before_construction(tmp_path):
    with pytest.raises(RuntimeContractError, match="explicit authorization"):
        live_adapters.build_live_runtime(repository=tmp_path, grounding=object(), authorized=False)
