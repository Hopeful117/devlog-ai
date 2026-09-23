import hashlib
import json

import pytest

from evaluations.comparative_baseline import live_adapters
from evaluations.comparative_baseline.collection_runtime import Assignment, RuntimeContractError
from evaluations.comparative_baseline.infrastructure import assert_official_baseline_eligible, load_manifest
from evaluations.repository_paths import TRADING_OS_REPOSITORY


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
    adapter = live_adapters.FrozenDevlogContextAdapter(TRADING_OS_REPOSITORY)
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
    tools = live_adapters.PinnedGitRepositoryTools(TRADING_OS_REPOSITORY)
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
    tools.timeout_seconds = live_adapters.NATIVE_REPOSITORY_TIMEOUT_SECONDS
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


def test_v4_provider_adapter_serializes_ordered_conversation_without_network():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    payload = {
        "condition": "AGENT_DIRECT_OPEN",
        "conversation": [
            {"role": "user", "content": "question"},
            {"role": "assistant", "kind": "TOOL_CALL", "content": {"operation": "search_repository", "arguments": {"query": "PAPER"}}},
            {"role": "tool", "operation": "search_repository", "content": {"matches": [{"path": "x", "line": 1}]}},
            {"role": "assistant", "kind": "TOOL_CALL", "content": {"operation": "read_file", "arguments": {"path": "x", "startLine": 1, "endLine": 2}}},
            {"role": "tool", "operation": "read_file", "content": {"reference": "x", "content": "PAPER"}},
        ],
    }
    serialized = live_adapters.serialize_v4_conversation(payload)
    assert [item.get("role", item.get("type")) for item in serialized] == ["user", "function_call", "function_call_output", "function_call", "function_call_output"]
    assert '"path":"x"' in serialized[3]["arguments"]
    assert '"reference":"x"' in serialized[4]["output"]


def test_v4_provider_adapter_accepts_v4_mode_with_deterministic_fake_client():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Item:
        def __init__(self, data):
            self.data = data
            self.type = data["type"]
            self.arguments = data.get("arguments")

        def model_dump(self, **_kwargs):
            return self.data

    class Response:
        def __init__(self, output, output_text=None):
            self.output = output
            self.output_text = output_text
            self.usage = None

        def model_dump(self, **_kwargs):
            return {"output": [item.model_dump() for item in self.output], "output_text": self.output_text}

    class Responses:
        def __init__(self):
            self.requests = []
            self.responses = [
                Response([Item({"type": "function_call", "arguments": json.dumps({"operation": "search_repository", "arguments": {"query": "PAPER"}})})]),
                Response([], json.dumps({"questionId": "CASE-03", "questionVersion": "1.0.0", "answerText": "answer", "relationshipResult": "NOT_APPLICABLE", "abstention": False, "claims": [], "evidence": [], "confidence": "LOW"})),
            ]

        def create(self, **kwargs):
            self.requests.append(kwargs)
            return self.responses.pop(0)

    class Client:
        def __init__(self):
            self.responses = Responses()

    client = Client()
    adapter = live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=client)
    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {"schema": "fixture"}, 512)
    first = adapter.complete(request)
    assert first.kind == "TOOL_CALL"
    assert client.responses.requests[0]["input"][0]["role"] == "user"
    second = adapter.complete(ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 2, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}, {"role": "tool", "content": {"matches": []}}]}, {"schema": "fixture"}, 512))
    assert second.kind == "FINAL"
    assert client.responses.requests[1]["input"][1]["type"] == "function_call_output"


def test_v4_provider_adapter_normalizes_native_and_multiple_function_arguments():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class NativeItem:
        type = "function_call"

        def __init__(self, arguments):
            self.arguments = arguments

    class Response:
        output_text = None
        usage = None

        def __init__(self, output):
            self.output = output

        def model_dump(self, **_kwargs):
            return {"output": [{"type": "function_call"}], "output_text": None}

    class Responses:
        def __init__(self):
            self.responses = [
                Response([NativeItem({"operation": "search_repository", "arguments": {"query": "PAPER"}})]),
                Response([NativeItem("{}")] * 2),
            ]

        def create(self, **_kwargs):
            return self.responses.pop(0)

    class Client:
        def __init__(self):
            self.responses = Responses()

    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    adapter = live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client())
    assert adapter.complete(request).payload["operation"] == "search_repository"
    assert adapter.complete(request).payload == {"calls": [{}, {}]}


def test_v4_provider_adapter_normalizes_typed_tool_name_to_canonical_envelope():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Item:
        type = "function_call"
        name = "read_file"
        arguments = '{"path":"docs/example.md"}'

    class Response:
        output = [Item()]
        output_text = None
        usage = None

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, [], 512)
    response = live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert response.payload == {"operation": "read_file", "arguments": {"path": "docs/example.md"}}


def test_v4_provider_adapter_reports_parse_boundary_without_secrets():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Item:
        type = "function_call"
        arguments = '{"operation":'

    class Response:
        output = [Item()]
        output_text = None
        usage = None

        def model_dump(self, **_kwargs):
            return {"output": [{"type": "function_call", "arguments": self.output[0].arguments}]}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert captured.value.attribution["category"] == "RESPONSE_PARSE_ERROR"
    assert captured.value.attribution["sdkItemType"] == "function_call"
    assert captured.value.attribution["valueRepresentation"] == "JSON_TEXT"
    assert "fixture-key" not in json.dumps(captured.value.attribution)


def test_incomplete_response_is_classified_before_final_output_parsing(monkeypatch):
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class IncompleteDetails:
        reason = "max_output_tokens"

    class Response:
        output = []
        output_text = ""
        usage = None
        id = "resp-incomplete"
        status = "incomplete"
        _request_id = "req-incomplete"
        incomplete_details = IncompleteDetails()

        def model_dump(self, **_kwargs):
            return {
                "id": self.id,
                "status": self.status,
                "incomplete_details": {"reason": self.incomplete_details.reason},
                "output": [],
                "output_text": self.output_text,
            }

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    monkeypatch.setattr(live_adapters.json, "loads", lambda *_args, **_kwargs: pytest.fail("final-output parser was invoked"))
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    diagnostic = captured.value.attribution
    assert diagnostic["category"] == "PROVIDER_INCOMPLETE_RESPONSE"
    assert diagnostic["requestPhase"] == "RESPONSE_STATE"
    assert diagnostic["parseBoundary"] == "NOT_REACHED"
    assert diagnostic["providerResponseStatus"] == "incomplete"
    assert diagnostic["providerIncompleteReason"] == "max_output_tokens"
    assert diagnostic["valueRepresentation"] == "NOT_REACHED"
    assert diagnostic["providerResponseId"] == "resp-incomplete"
    assert diagnostic["providerRequestId"] == "req-incomplete"


def test_completed_malformed_final_output_still_parses_and_classifies_parse_error():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Response:
        output = []
        output_text = "not-json"
        usage = None
        status = "completed"
        id = "resp-completed"
        _request_id = "req-completed"

        def model_dump(self, **_kwargs):
            return {"id": self.id, "status": self.status, "output": [], "output_text": self.output_text}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert captured.value.attribution["category"] == "RESPONSE_PARSE_ERROR"
    assert captured.value.attribution["parseBoundary"] == "FINAL_OUTPUT_TEXT"


def test_completed_empty_final_output_remains_parse_error():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Response:
        output = []
        output_text = ""
        usage = None
        status = "completed"

        def model_dump(self, **_kwargs):
            return {"status": self.status, "output": [], "output_text": self.output_text}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert captured.value.attribution["category"] == "RESPONSE_PARSE_ERROR"


def test_incomplete_partial_output_is_not_treated_as_a_completed_answer(monkeypatch):
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class IncompleteDetails:
        reason = "content_filter"

    class Response:
        output = []
        output_text = '{"partial":'
        usage = None
        status = "incomplete"
        incomplete_details = IncompleteDetails()

        def model_dump(self, **_kwargs):
            return {"status": self.status, "incomplete_details": {"reason": "content_filter"}, "output": [], "output_text": self.output_text}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    monkeypatch.setattr(live_adapters.json, "loads", lambda *_args, **_kwargs: pytest.fail("partial incomplete output was parsed"))
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert captured.value.attribution["category"] == "PROVIDER_INCOMPLETE_RESPONSE"
    assert captured.value.attribution["providerIncompleteReason"] == "content_filter"


def test_incomplete_diagnostics_preserve_usage_and_bounded_output_items_without_arguments():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Usage:
        def model_dump(self, **_kwargs):
            return {"input_tokens": 123, "output_tokens": 456, "total_tokens": 579}

    class Item:
        type = "function_call"
        status = "in_progress"
        name = "read_file"
        id = "item-1"
        call_id = "call-1"
        arguments = '{"path":"docs/example.md"}'

    class Details:
        reason = "max_output_tokens"

    class Response:
        output = [Item()]
        output_text = None
        usage = Usage()
        id = "resp-incomplete"
        model = "gpt-4.1-mini-2025-04-14"
        status = "incomplete"
        _request_id = "req-incomplete"
        incomplete_details = Details()
        max_output_tokens = 32768

        def model_dump(self, **_kwargs):
            return {
                "id": self.id,
                "model": self.model,
                "status": self.status,
                "incomplete_details": {"reason": self.incomplete_details.reason},
                "usage": self.usage.model_dump(),
                "output": [{"type": "function_call", "status": self.output[0].status, "name": self.output[0].name, "id": self.output[0].id, "call_id": self.output[0].call_id, "arguments": self.output[0].arguments}],
            }

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    captured_callback = []
    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 32768, on_provider_response=lambda usage, diagnostics: captured_callback.append((usage, diagnostics)))
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    diagnostic = captured.value.attribution
    assert diagnostic["providerUsage"] == {"inputTokens": 123, "outputTokens": 456, "totalTokens": 579}
    assert diagnostic["outputItemCount"] == 1
    assert diagnostic["outputItems"] == [{"type": "function_call", "status": "in_progress", "name": "read_file", "id": "item-1", "call_id": "call-1"}]
    assert "arguments" not in json.dumps(diagnostic)
    assert captured_callback[0][0] == diagnostic["providerUsage"]


def test_incomplete_diagnostics_represent_absent_usage_as_unknown():
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Details:
        reason = "max_output_tokens"

    class Response:
        output = []
        usage = None
        status = "incomplete"
        incomplete_details = Details()

        def model_dump(self, **_kwargs):
            return {"status": self.status, "incomplete_details": {"reason": "max_output_tokens"}, "output": []}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-04", "1.0.0", "CASE-04", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 32768)
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    assert captured.value.attribution["providerUsage"] == {"inputTokens": "NOT_MEASURED", "outputTokens": "NOT_MEASURED", "totalTokens": "NOT_MEASURED"}


@pytest.mark.parametrize(
    ("text", "boundary", "expected_empty", "expected_whitespace", "expected_position", "expected_line", "expected_column"),
    [
        ('{"operation":', "FUNCTION_CALL_ARGUMENTS", False, False, 13, 1, 14),
        ("", "FINAL_OUTPUT_TEXT", True, False, 0, 1, 1),
        (" \n\t", "FINAL_OUTPUT_TEXT", False, True, 3, 2, 2),
    ],
)
def test_v4_parse_diagnostics_are_bounded_and_deterministic(text, boundary, expected_empty, expected_whitespace, expected_position, expected_line, expected_column):
    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    class Item:
        type = "function_call"
        arguments = text
        id = "item-fixture"

    class Response:
        output = [Item()] if boundary == "FUNCTION_CALL_ARGUMENTS" else []
        output_text = None if boundary == "FUNCTION_CALL_ARGUMENTS" else text
        usage = None
        id = "resp-fixture"
        status = "completed"
        _request_id = "req-fixture"

        def model_dump(self, **_kwargs):
            return {"output": [{"type": "function_call", "arguments": text}] if self.output else [], "output_text": self.output_text}

    class Client:
        class Responses:
            def create(self, **_kwargs):
                return Response()
        responses = Responses()

    assignment = V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1)
    request = ProviderRequest(assignment, "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    with pytest.raises(live_adapters.TransportFailure) as captured:
        live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client()).complete(request)
    diagnostic = captured.value.attribution
    assert diagnostic["parseBoundary"] == boundary
    assert diagnostic["runtimeType"] == "STRING"
    assert diagnostic["textLength"] == len(text)
    assert diagnostic["textEmpty"] is expected_empty
    assert diagnostic["textWhitespaceOnly"] is expected_whitespace
    assert diagnostic["textSha256"] == hashlib.sha256(text.encode("utf-8")).hexdigest()
    assert diagnostic["jsonErrorPosition"] == expected_position
    assert diagnostic["jsonErrorLine"] == expected_line
    assert diagnostic["jsonErrorColumn"] == expected_column
    assert diagnostic["providerResponseId"] == "resp-fixture"
    assert diagnostic["providerResponseStatus"] == "completed"
    assert diagnostic["providerRequestId"] == "req-fixture"
    if boundary == "FUNCTION_CALL_ARGUMENTS":
        assert diagnostic["providerItemId"] == "item-fixture"
    if text:
        assert text not in json.dumps(diagnostic)


def test_native_timeout_and_retry_configuration_is_frozen_without_network():
    assert live_adapters.NATIVE_PROVIDER_TIMEOUT_SECONDS == 30.0
    assert live_adapters.NATIVE_PROVIDER_CONNECT_TIMEOUT_SECONDS == 5.0
    assert live_adapters.NATIVE_PROVIDER_POOL_TIMEOUT_SECONDS == 5.0
    assert live_adapters.NATIVE_REPOSITORY_TIMEOUT_SECONDS == 15.0

    class APITimeoutError(Exception):
        pass

    class Responses:
        def create(self, **_kwargs):
            raise APITimeoutError("fixture timeout")

    class Client:
        responses = Responses()

    from evaluations.comparative_baseline.collection_runtime import ProviderRequest
    from evaluations.comparative_baseline_v4.protocol import V4Assignment

    adapter = live_adapters.OpenAIProviderTransport(api_key="fixture-key", client=Client())
    request = ProviderRequest(V4Assignment("slot", "CASE-03", "1.0.0", "CASE-03", "AGENT_DIRECT_OPEN", 1), "AGENT_DIRECT_OPEN", 1, {"condition": "AGENT_DIRECT_OPEN", "conversation": [{"role": "user", "content": "question"}]}, {}, 512)
    with pytest.raises(live_adapters.NativeProviderTimeout):
        adapter.complete(request)
    assert adapter.retry_count == 0


def test_native_repository_timeout_is_typed(monkeypatch, tmp_path):
    def timeout(*_args, **_kwargs):
        import subprocess
        raise subprocess.TimeoutExpired("git", 15)

    monkeypatch.setattr(live_adapters.subprocess, "run", timeout)
    with pytest.raises(live_adapters.NativeToolTimeout):
        live_adapters._git_command(tmp_path, "show", "example", timeout=live_adapters.NATIVE_REPOSITORY_TIMEOUT_SECONDS)
