import copy
import hashlib
import json
from types import SimpleNamespace

import pytest

from evaluations.comparative_baseline.collection_runtime import InMemoryRepositoryTools, ProviderResponse, RuntimeContractError, TransportFailure, provider_failure_attribution
from evaluations.comparative_baseline.infrastructure import assignment_matrix, load_manifest
from evaluations.comparative_baseline.testing import ScriptedProvider
from evaluations.comparative_baseline_v4.protocol import (
    ExecutionStatus,
    EvidenceItem,
    ResourceAccounting,
    SafetyCeilings,
    V4Assignment,
    V4_RUNTIME_CONTRACT,
    build_observation,
    condition_input,
    evaluate_answer,
    evaluate_grounding,
    replay_observation,
    reconstruct_historical_accounting,
    v4_experiment_identity,
    validate_v4_identity,
    validate_v4_plan,
    validate_v4_pilot_plan,
    v4_assignment_matrix,
    v4_pilot_assignment_matrix,
    v4_pilot_identity,
    v4_runtime_contract_identity,
    write_derived_projection,
    write_observation,
)
from evaluations.comparative_baseline_v4.runtime import V4CollectionOrchestrator, V4CollectionRuntime
from evaluations.comparative_baseline_v4.runtime import _contract_error, _tool_schema


REVISION = load_manifest()["repositoryRevision"]
AUTHORIZED_CASE03 = "trading-core/src/main/java/com/hope/trading/trading_core/execution/application/service/PaperSettlementService.java"


def _assignment(question_id="CASE-03", condition="AGENT_DIRECT_OPEN"):
    row = next(row for row in assignment_matrix() if row["questionId"] == question_id and row["condition"] == "AGENT_DIRECT")
    return V4Assignment.from_row({**row, "condition": "AGENT_DIRECT" if condition == "AGENT_DIRECT_OPEN" else "DEVLOG"})


def _answer(question_id="CASE-03", reference=AUTHORIZED_CASE03, *, relationship="NOT_APPLICABLE", text=None):
    if text is None:
        text = "PaperSettlementService ExecuteTradeService ExecutionIntent ExecutionConfiguration position API/valuation PaperSettlementExitTest PaperExecutionVerticalRegressionTest PositionControllerTest are affected because settlement behavior crosses these components and tests."
    return {
        "questionId": question_id,
        "questionVersion": "1.0.0",
        "answerText": text,
        "relationshipResult": relationship,
        "abstention": False,
        "claims": [{"text": "The inspected evidence supports this bounded answer.", "claimType": "FACT", "references": [reference]}],
        "evidence": [{"reference": reference, "locator": {"kind": "LINE_RANGE", "startLine": 1, "endLine": 2}, "excerpt": "captured direct evidence", "role": "DIRECT"}],
        "confidence": "MEDIUM",
    }


def _tools(files=None):
    return InMemoryRepositoryTools(revision=REVISION, files=files or {AUTHORIZED_CASE03: "captured direct evidence\nsecond line"})


def _ceilings(**overrides):
    assert not overrides
    return SafetyCeilings()


def test_v4_identity_and_open_condition_are_frozen_without_v3_budget():
    manifest = validate_v4_identity()
    assert manifest["manifestVersion"] == "comparative-baseline-v4-3.0.0"
    assert manifest["toolRecoveryPolicy"] == "NATURAL_MODEL_RECOVERY"
    direct = condition_input("AGENT_DIRECT_OPEN", "CASE-03", 1)
    assert direct["condition"] == "AGENT_DIRECT_OPEN"
    assert "repositoryByteBudget" not in direct
    assert len(assignment_matrix()) == 18
    assert manifest["safetyCeilings"]["status"] == "NO_EXPERIMENT_LIMITS"
    assert manifest["executionConfiguration"]["safetyCeilings"]["maxReadBytesPerOperation"] is None


def test_experiment_identity_binds_execution_and_safety_configuration():
    first = v4_experiment_identity(safety=_ceilings())
    assert first["identityVersion"] == "comparative-v4-experiment-identity-2.0.0"
    assert first["inputs"]["conditions"] == ["DEVLOG", "AGENT_DIRECT_OPEN"]
    assert first["inputs"]["safety"] == {
        "maxToolOperations": None,
        "maxModelTurns": None,
        "maxWallClockSeconds": None,
        "maxProviderCallsPerObservation": None,
        "maxReadBytesPerOperation": None,
    }


def test_instrumentation_identity_is_new_but_frozen_semantics_are_unchanged():
    identity = v4_experiment_identity(safety=_ceilings())
    pilot = v4_pilot_identity(safety=_ceilings())
    assert v4_runtime_contract_identity()["identityVersion"] == "comparative-v4-live-runtime-contract-5.1.0"
    assert identity["sha256"] == "9b45c5d2a7c6d87a612eda7f4eea694d90af7c55e4106d7b8767adc8806cea13"
    assert pilot["sha256"] == "211bae21fbfcaadbe49f1faf6e5edb9729dfff926962aee823ae0a111fe2e0ad"
    assert identity["sha256"] != "01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6"


def test_collection_plan_rejects_unfrozen_or_duplicate_cells():
    rows = v4_assignment_matrix()[:2]
    validate_v4_plan(rows)
    with pytest.raises(ValueError, match="duplicate"):
        validate_v4_plan(rows + [rows[0]])
    changed = {**rows[0], "assignmentId": "unexpected"}
    with pytest.raises(ValueError, match="frozen V4"):
        validate_v4_plan([changed])


def test_pilot_plan_is_six_slots_and_has_its_own_identity():
    pilot = v4_pilot_assignment_matrix()
    validate_v4_pilot_plan(pilot)
    assert len(pilot) == 6
    assert {row["condition"] for row in pilot} == {"DEVLOG", "AGENT_DIRECT_OPEN"}
    assert {row["repetition"] for row in pilot} == {1}
    assert v4_pilot_identity(safety=_ceilings())["sha256"] == v4_pilot_identity(safety=_ceilings())["sha256"]


def test_runtime_contract_binds_ceiling_ordering_and_preflight_mismatch(monkeypatch):
    from evaluations.comparative_baseline_v4 import protocol

    original = v4_experiment_identity(safety=_ceilings())["sha256"]
    monkeypatch.setitem(V4_RUNTIME_CONTRACT, "ceilingEnforcement", "comparative-v4-tool-ceiling-order-1.0.0")
    changed = v4_experiment_identity(safety=_ceilings())["sha256"]
    assert changed != original
    with pytest.raises(ValueError, match="runtime contract identity"):
        protocol.validate_v4_identity()


def test_direct_has_no_experiment_defined_safety_ceiling():
    ceilings = SafetyCeilings()
    assert ceilings.approved
    ceilings.require_approved()


def test_runtime_accepts_unbounded_direct_configuration():
    provider = ScriptedProvider([])
    runtime = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools())
    assert runtime.manifest["safetyCeilings"]["status"] == "NO_EXPERIMENT_LIMITS"


def test_quality_first_ceiling_propagates_to_every_v4_provider_response():
    devlog_context = SimpleNamespace(
        question_version="1.0.0",
        evidence=(EvidenceItem(AUTHORIZED_CASE03, "captured direct evidence"),),
        as_input=lambda: {"condition": "DEVLOG", "prompt": {"system": "system", "user": "user"}},
    )
    devlog_provider = ScriptedProvider([ProviderResponse("FINAL", _answer(), {})])
    V4CollectionRuntime(
        devlog_provider,
        ceilings=_ceilings(),
        devlog_contexts={"CASE-03": devlog_context},
        tool_factory=lambda _: _tools(),
        test_only=True,
    ).run_assignment(V4Assignment("devlog", "CASE-03", "1.0.0", "CASE-03", "DEVLOG", 1))
    assert [request.max_output_tokens for request in devlog_provider.requests] == [32768]

    direct_provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}),
        ProviderResponse("FINAL", _answer(), {}),
    ])
    V4CollectionRuntime(
        direct_provider,
        ceilings=_ceilings(),
        tool_factory=lambda _: _tools(),
        test_only=True,
    ).run_assignment(_assignment())
    assert [request.max_output_tokens for request in direct_provider.requests] == [32768, 32768]


def test_typed_repository_schema_matches_operation_matrix():
    schema = _tool_schema()
    assert [tool["name"] for tool in schema] == ["read_file", "search_repository", "git_log", "git_show", "git_diff", "inspect_commit"]
    read = schema[0]["parameters"]
    assert read["required"] == ["path", "startLine", "endLine"]
    assert read["additionalProperties"] is False
    assert read["properties"]["path"]["type"] == ["string", "null"]
    assert schema[1]["parameters"]["properties"]["paths"]["items"]["type"] == "string"


def test_typed_schema_exposes_provider_visible_runtime_constraints():
    schema = {tool["name"]: tool["parameters"]["properties"] for tool in _tool_schema()}
    assert schema["read_file"]["startLine"]["minimum"] == 1
    assert schema["read_file"]["endLine"]["minimum"] == 1
    assert schema["search_repository"]["query"]["minLength"] == 1
    assert schema["search_repository"]["maxMatches"]["minimum"] == 1
    assert schema["git_log"]["commit"]["pattern"] == "^[0-9a-f]{40}$"
    assert schema["git_diff"]["parent"]["pattern"] == "^[0-9a-f]{40}$"


@pytest.mark.parametrize("operation,arguments", [
    ("read_file", {"path": "x", "startLine": 0}),
    ("read_file", {"path": "x", "endLine": 0}),
    ("search_repository", {"query": ""}),
    ("search_repository", {"query": "x", "paths": ["../x"]}),
    ("search_repository", {"query": "x", "maxMatches": 0}),
    ("git_log", {"commit": "18f9d99"}),
    ("git_diff", {"parent": "G" * 40}),
])
def test_provider_visible_constraints_match_contract_error(operation, arguments):
    assert _contract_error(operation, arguments) is not None


def test_git_revision_contract_accepts_only_full_lowercase_object_ids():
    revision = "a" * 40
    assert _contract_error("git_log", {"commit": revision}) is None
    assert _contract_error("git_show", {"commit": revision, "path": "src/Main.java"}) is None
    assert _contract_error("git_diff", {"commit": revision, "parent": revision}) is None
    assert _contract_error("inspect_commit", {"commit": revision}) is None


def test_static_constraints_do_not_replace_state_dependent_repository_failures():
    revision = "a" * 40
    assert _contract_error("git_show", {"commit": revision}) is None
    tools = InMemoryRepositoryTools(revision=REVISION, files={})
    with pytest.raises(FileNotFoundError):
        tools.execute("git_show", {"commit": revision, "repositoryRevision": REVISION})


@pytest.mark.parametrize("operation,arguments", [
    ("read_file", {"path": "docs/example.md"}),
    ("search_repository", {"query": "PAPER"}),
    ("git_log", {}),
    ("git_show", {}),
    ("git_diff", {}),
    ("inspect_commit", {}),
])
def test_every_repository_operation_accepts_canonical_valid_shape(operation, arguments):
    assert _contract_error(operation, arguments) is None


@pytest.mark.parametrize("operation,arguments,expected", [
    ("read_file", {}, "path"),
    ("search_repository", {}, "query"),
    ("read_file", {"path": 1}, "must be string"),
    ("search_repository", {"query": "x", "paths": [1]}, "must be string[]"),
    ("git_diff", {"parent": False}, "must be string"),
    ("read_file", {"path": "x", "unexpected": True}, "unexpected"),
])
def test_repository_contract_rejects_missing_wrong_type_and_unexpected_fields(operation, arguments, expected):
    assert expected in (_contract_error(operation, arguments) or "")


@pytest.mark.parametrize("operation,arguments", [(None, None), (1, {}), ("unknown", {}), ("read_file", None)])
def test_repository_contract_rejects_missing_non_string_and_unknown_envelopes(operation, arguments):
    error = _contract_error(operation, arguments)
    assert error and ("canonical" in error or "unsupported" in error)


@pytest.mark.parametrize("malformed", [{"query": "PAPER"}, {"path": "docs/example.md"}, {"operation": None, "arguments": {}}])
def test_historical_malformed_top_level_and_operation_fixtures_are_not_executable(malformed):
    assert _contract_error(malformed.get("operation"), malformed.get("arguments")) is not None


@pytest.mark.parametrize("malformed", [{"query": "PAPER"}, {"path": "docs/example.md"}, {"operation": None, "arguments": {}}])
def test_malformed_repository_requests_are_rejected_without_execution_or_result_accounting(malformed):
    class SpyTools:
        executions = 0

        def execute(self, _operation, _arguments):
            self.executions += 1
            return {}

    tools = SpyTools()
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", malformed, {}), ProviderResponse("FINAL", _answer(), {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: tools, run_id="malformed", test_only=True).run_assignment(_assignment())
    assert tools.executions == 0
    assert observation["resources"]["invalidToolRequests"] == 1
    assert observation["resources"]["executedToolOperations"] == 0
    assert observation["resources"]["resultBytesProduced"] == 0


def test_provider_technical_timeout_is_not_safety_censoring():
    class BlockingProvider:
        def complete(self, _request):
            raise TimeoutError("native provider timeout")

    observation = V4CollectionRuntime(BlockingProvider(), ceilings=_ceilings(), tool_factory=lambda _: _tools(), test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "PROVIDER_TIMEOUT"
    assert observation["primaryDiagnostic"] == "PROVIDER_TIMEOUT"
    assert observation["stoppingReason"] == "PROVIDER_TIMEOUT"
    assert observation["resources"]["providerTimeouts"] == 1
    assert observation["resources"]["providerTransportAttempts"] == 1
    assert observation["resources"]["providerResponsesReceived"] == 0
    assert observation["resources"]["totalProviderCalls"] == 0


def test_provider_failure_is_safely_attributed_and_persisted():
    class RateLimitError(Exception):
        status_code = 429
        code = "rate_limit_exceeded"

    attribution = provider_failure_attribution(RateLimitError("Bearer sk-12345678901234567890"))
    assert attribution["category"] == "RATE_LIMIT"
    assert attribution["httpStatus"] == 429
    assert attribution["providerErrorCode"] == "rate_limit_exceeded"
    assert "sk-12345678901234567890" not in attribution["safeMessage"]
    assert "Bearer" not in attribution["safeMessage"]

    provider = ScriptedProvider([])
    provider.complete = lambda _request: (_ for _ in ()).throw(TransportFailure("transport", attribution=attribution))
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "PROVIDER_FAILURE"
    assert observation["providerFailure"] == attribution
    assert observation["rawSchemaVersion"] == "comparative-v4-raw-observation-4.0.0"


@pytest.mark.parametrize(
    ("error", "category"),
    [
        (ConnectionError("connection refused"), "CONNECTION_ERROR"),
        (type("HttpError", (Exception,), {"status_code": 500})(), "PROVIDER_SERVER_ERROR"),
        (TimeoutError("timed out"), "TIMEOUT"),
        (ValueError("unknown"), "UNKNOWN_PROVIDER_FAILURE"),
    ],
)
def test_provider_failure_taxonomy_is_deterministic(error, category):
    assert provider_failure_attribution(error)["category"] == category


def test_provider_failure_parser_category_is_explicit():
    diagnostic = provider_failure_attribution(ValueError("invalid JSON"), category="RESPONSE_PARSE_ERROR", request_phase="RESPONSE_PARSE")
    assert diagnostic["category"] == "RESPONSE_PARSE_ERROR"
    assert diagnostic["requestPhase"] == "RESPONSE_PARSE"


def test_tool_technical_timeout_is_traced_and_not_safety_censoring():
    class BlockingTools:
        def execute(self, _operation, _arguments):
            raise TimeoutError("native tool timeout")

    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: BlockingTools(), test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "TOOL_TIMEOUT"
    assert observation["primaryDiagnostic"] == "TOOL_TIMEOUT"
    assert observation["rawOutput"]["toolTrace"][0]["executionStatus"] == "EXECUTED_TOOL_TIMEOUT"
    assert observation["resources"]["toolTimeouts"] == 1


def test_resource_accounting_separates_provider_and_tool_work():
    resources = ResourceAccounting()
    response = ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "x"}}, {}, {"inputTokens": 4, "outputTokens": 2})
    resources.record_provider(response, final=False, latency_ms=3)
    resources.record_tool("search_repository", valid=True, executed=True, result_bytes=20, delivered_bytes=20)
    resources.record_tool("search_repository", valid=False, executed=False)
    values = resources.as_dict()
    assert values["totalProviderCalls"] == values["navigationProviderCalls"] == 1
    assert values["finalAnswerProviderCalls"] == 0
    assert values["toolCalls"] == 2
    assert values["invalidToolCalls"] == 1
    assert values["inputTokens"] == 4
    assert values["bytesDeliveredToModel"] == 20


@pytest.mark.parametrize(
    ("usage", "expected"),
    [
        ({"inputTokens": 123, "outputTokens": 456, "totalTokens": 579}, (123, 456, 579)),
        ({"inputTokens": 0, "outputTokens": 0, "totalTokens": 0}, (0, 0, 0)),
        ({}, ("NOT_MEASURED", "NOT_MEASURED", "NOT_MEASURED")),
    ],
)
def test_incomplete_provider_response_is_accounted_without_becoming_usable(usage, expected):
    class IncompleteProvider:
        def complete(self, request):
            request.on_provider_response(usage, {"providerResponseStatus": "incomplete", "providerIncompleteReason": "max_output_tokens", "outputItemCount": 0})
            raise TransportFailure("incomplete", attribution={"category": "PROVIDER_INCOMPLETE_RESPONSE", "requestPhase": "RESPONSE_STATE", "providerIncompleteReason": "max_output_tokens"})

    executed = []
    tools = lambda _assignment: type("SpyTools", (), {"execute": lambda _self, *_args: executed.append(True)})()
    observation = V4CollectionRuntime(IncompleteProvider(), ceilings=_ceilings(), tool_factory=tools, run_id="incomplete", test_only=True).run_assignment(_assignment("CASE-04"))
    resources = observation["resources"]
    assert observation["executionStatus"] == "PROVIDER_FAILURE"
    assert resources["providerTransportAttempts"] == 1
    assert resources["providerResponsesReceived"] == 1
    assert resources["usableProviderResponses"] == 0
    assert resources["responseBearingModelTurns"] == 0
    assert resources["totalProviderCalls"] == 0
    assert resources["modelTurns"] == 0
    assert (resources["inputTokens"], resources["outputTokens"], resources["totalTokens"]) == expected
    assert observation["rawOutput"]["providerAttempts"][0]["providerResponseDiagnostics"]["outputItemCount"] == 0
    assert executed == []
    assert observation["semanticCorrect"] == "NOT_EVALUATED"


def test_incomplete_provider_attempts_replay_without_provider_access():
    class IncompleteProvider:
        def complete(self, request):
            request.on_provider_response({"inputTokens": 1, "outputTokens": 2, "totalTokens": 3}, {"providerResponseStatus": "incomplete"})
            raise TransportFailure("incomplete", attribution={"category": "PROVIDER_INCOMPLETE_RESPONSE"})

    observation = V4CollectionRuntime(IncompleteProvider(), ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="incomplete-replay", test_only=True).run_assignment(_assignment("CASE-04"))
    replay = replay_observation(observation)
    assert replay["providerCalls"] == 0
    assert replay["networkCalls"] == 0
    assert observation["resources"]["providerTransportAttempts"] == 1
    assert observation["resources"]["providerResponsesReceived"] == 1


def test_multi_turn_accounting_separates_three_attempts_from_two_usable_turns():
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}, {"inputTokens": 1, "outputTokens": 2, "totalTokens": 3}),
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}, {"inputTokens": 4, "outputTokens": 5, "totalTokens": 9}),
        TransportFailure("incomplete", attribution={"category": "PROVIDER_INCOMPLETE_RESPONSE"}),
    ])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="multi-turn-accounting", test_only=True).run_assignment(_assignment())
    resources = observation["resources"]
    assert resources["providerTransportAttempts"] == 3
    assert resources["providerResponsesReceived"] == 2
    assert resources["usableProviderResponses"] == 2
    assert resources["responseBearingModelTurns"] == 2
    assert resources["totalProviderCalls"] == 2
    assert resources["modelTurns"] == 2
    assert resources["inputTokens"] == 5
    assert resources["outputTokens"] == 7
    assert resources["totalTokens"] == 12


def test_grounding_and_semantics_are_independent():
    answer = _answer()
    evidence = [{"reference": AUTHORIZED_CASE03, "content": "captured direct evidence\nsecond line", "contentSha256": hashlib.sha256(b"captured direct evidence\nsecond line").hexdigest()}]
    result = evaluate_answer(answer, _assignment(), evidence)
    assert result["structural"]["status"] == "PASS"
    assert result["grounding"]["status"] == "PASS"
    assert result["semantic"]["semanticCorrect"] is True
    broken = copy.deepcopy(answer)
    broken["evidence"][0]["excerpt"] = "not present"
    result = evaluate_answer(broken, _assignment(), evidence)
    assert result["grounding"]["status"] == "FAIL"
    assert result["semantic"]["semanticEvaluated"] is True
    assert result["semantic"]["semanticCorrect"] is True
    assert result["correctGroundedAnswer"] is False


def test_censored_observation_is_not_structural_failure():
    resources = ResourceAccounting()
    resources.stop("SAFETY_MAX_MODEL_TURNS")
    assignment = _assignment()
    observation = build_observation(assignment=assignment.identity(), run_id="test", raw_output={}, evaluation={"structural": {"valid": None, "status": "NOT_EVALUATED"}, "grounding": {"status": "NOT_EVALUATED"}, "semantic": {"semanticEvaluated": False, "semanticCorrect": "NOT_EVALUATED"}, "correctGroundedAnswer": "NOT_EVALUATED"}, resources=resources, execution_status=ExecutionStatus.CENSORED)
    assert observation["executionStatus"] == "CENSORED"
    assert observation["structuralValid"] == "NOT_EVALUATED"
    assert observation["semanticCorrect"] == "NOT_EVALUATED"


def test_direct_results_are_available_on_next_provider_turn_and_accounted():
    answer = _answer()
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {"kind": "tool"}),
        lambda request: (pytest.fail("tool result was not propagated") if not any(message.get("role") == "tool" and message.get("content", {}).get("reference") == AUTHORIZED_CASE03 for message in request.input_payload["conversation"]) else ProviderResponse("FINAL", answer, {"kind": "final"}, {"inputTokens": 20, "outputTokens": 30})),
    ])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="direct", test_only=True).run_assignment(_assignment())
    assert observation["assignment"]["condition"] == "AGENT_DIRECT_OPEN"
    assert observation["executionStatus"] == "COMPLETED"
    assert observation["resources"]["totalProviderCalls"] == 2
    assert observation["resources"]["navigationProviderCalls"] == 1
    assert observation["resources"]["finalAnswerProviderCalls"] == 1
    assert observation["resources"]["repositoryReads"] == 1
    assert observation["resources"]["inputTokens"] == 20


def test_policy_a_returns_contract_error_and_allows_natural_recovery():
    answer = _answer()
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"pattern": "PAPER"}}, {}),
        lambda request: (pytest.fail("contract error was not propagated") if not any(message.get("role") == "tool" and ("missing required" in message.get("error", "") or "unexpected" in message.get("error", "")) for message in request.input_payload["conversation"]) else ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "PAPER"}}, {})),
        ProviderResponse("FINAL", answer, {}),
    ])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="recover", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "COMPLETED"
    assert observation["resources"]["invalidToolCalls"] == 1
    assert observation["resources"]["toolCalls"] == 2
    assert any(trace["executionStatus"] == "NOT_EXECUTED_INVALID_REQUEST" for trace in observation["rawOutput"]["toolTrace"])


def test_repeated_malformed_calls_are_not_censored_by_an_experiment_ceiling():
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"pattern": "PAPER"}}, {})] * 48)
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="repeat", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "PROVIDER_FAILURE"
    assert observation["stoppingReason"] == "PROVIDER_FAILURE"
    assert observation["structuralValid"] == "NOT_EVALUATED"
    assert observation["resources"]["invalidToolCalls"] == 48


def test_tool_operations_are_not_censored_by_an_experiment_ceiling():
    call = {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"calls": [call]}, {})] * 97 + [ProviderResponse("FINAL", _answer(), {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="tool-ceiling", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "COMPLETED"
    assert observation["resources"]["toolAttempts"] == 97
    assert observation["resources"]["executedToolOperations"] == 97


def test_invalid_requests_are_not_censored_by_an_experiment_ceiling():
    invalid = {"operation": "unsupported_operation", "arguments": {}}
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"calls": [invalid]}, {})] * 97 + [ProviderResponse("FINAL", _answer(), {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="invalid-ceiling", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "COMPLETED"
    assert observation["resources"]["toolAttempts"] == 97
    assert observation["resources"]["invalidToolRequests"] == 97
    assert observation["resources"]["skippedToolOperations"] == 0
    assert observation["resources"]["ceilingCountedOperations"] == 97


def test_large_read_result_is_delivered_without_an_experiment_byte_ceiling():
    huge_tools = lambda _assignment: _tools({AUTHORIZED_CASE03: "x" * 70000})
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}), ProviderResponse("FINAL", _answer(), {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=huge_tools, run_id="read-ceiling", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "COMPLETED"
    assert observation["rawOutput"]["toolTrace"][0]["delivered"] is True
    assert observation["resources"]["bytesDeliveredToModel"] > 65536


def test_multiple_individually_valid_results_do_not_trigger_per_operation_ceiling():
    class BigTools:
        def execute(self, _operation, _arguments):
            return {"operation": "read_file", "reference": AUTHORIZED_CASE03, "content": "x" * 40000}

    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}),
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}),
        ProviderResponse("FINAL", {}, {}),
    ])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: BigTools(), run_id="cumulative-bytes", test_only=True).run_assignment(_assignment())
    assert observation["resources"]["cumulativeRepositoryBytesDelivered"] > 65536
    assert observation["resources"]["largestSingleResultBytesDelivered"] < 65536
    assert observation["resources"]["resultBytesRejected"] == 0
    assert observation["stoppingReason"] == "MODEL_STRUCTURAL_FAILURE"


def test_legacy_accounting_is_projected_without_rewriting_raw():
    legacy = {"rawOutput": {"toolTrace": [
        {"executionStatus": "EXECUTED_SUCCESS", "operationType": "search_repository", "resultByteCount": 10, "delivered": True},
        {"executionStatus": "NOT_EXECUTED_INVALID_REQUEST", "operationType": "read_file", "resultByteCount": 0, "delivered": False},
        {"executionStatus": "NOT_EXECUTED_SAFETY_CEILING", "operationType": "read_file", "resultByteCount": 0, "delivered": False},
    ]}}
    projected = reconstruct_historical_accounting(legacy)
    assert projected["toolAttempts"] == 3
    assert projected["executedToolOperations"] == 1
    assert projected["invalidToolRequests"] == 1
    assert projected["skippedToolOperations"] == 1
    assert projected["ceilingCountedOperations"] == 2
    assert projected["reconstructability"]["bytesRequested"] == "NOT_RECONSTRUCTABLE"
    assert projected["reconstructability"]["providerFailureCause"] == "NOT_RECONSTRUCTABLE"


def test_large_file_ranges_and_tool_trace_are_preserved():
    files = {AUTHORIZED_CASE03: "\n".join(["prefix"] * 100 + ["captured direct evidence"] + ["suffix"] * 100)}
    answer = _answer()
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "captured"}}, {}),
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03, "startLine": 101, "endLine": 101}}, {}),
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03, "startLine": 1, "endLine": 2}}, {}),
        ProviderResponse("FINAL", answer, {}),
    ])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(files), run_id="ranges", test_only=True).run_assignment(_assignment())
    assert observation["executionStatus"] == "COMPLETED"
    assert [trace["operationType"] for trace in observation["rawOutput"]["toolTrace"]] == ["search_repository", "read_file", "read_file"]
    assert all(trace["delivered"] for trace in observation["rawOutput"]["toolTrace"])


def test_replay_recomputes_deterministic_evaluation(tmp_path):
    answer = _answer()
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": AUTHORIZED_CASE03}}, {}), ProviderResponse("FINAL", answer, {})])
    observation = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="replay", test_only=True).run_assignment(_assignment())
    path = tmp_path / "observation.json"
    write_observation(path, observation)
    replay = replay_observation(observation)
    assert replay["providerCalls"] == 0
    assert replay["networkCalls"] == 0
    assert replay["deterministicEvaluationRecomputed"] is True
    tampered = copy.deepcopy(observation)
    tampered["semanticCorrect"] = False
    with pytest.raises(ValueError, match="replay mismatch"):
        replay_observation(tampered)
    projection = tmp_path / "derived.json"
    write_derived_projection(projection, observation)
    with pytest.raises(FileExistsError):
        write_derived_projection(projection, observation)


def test_orchestrator_supports_v4_slot_identity_and_write_once(tmp_path):
    answer = _answer()
    provider = ScriptedProvider([ProviderResponse("FINAL", answer, {})])
    runtime = V4CollectionRuntime(provider, ceilings=_ceilings(), tool_factory=lambda _: _tools(), run_id="orchestrated", test_only=True)
    rows = [next(row for row in v4_assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "AGENT_DIRECT_OPEN" and row["repetition"] == 1)]
    ledger = tmp_path / "ledger.json"
    results = V4CollectionOrchestrator(runtime, run_id="orchestrated", ledger_path=ledger, output_dir=tmp_path).run(rows)
    assert results[0]["assignment"]["condition"] == "AGENT_DIRECT_OPEN"
    assert (tmp_path / f"{rows[0]['assignmentId']}.json").exists()
    ledger_data = json.loads(ledger.read_text())
    assert ledger_data["completedAssignmentCount"] == 1
    assert ledger_data["summary"]["complete"] is True
    assert V4CollectionOrchestrator(V4CollectionRuntime(ScriptedProvider([]), ceilings=_ceilings(), tool_factory=lambda _: _tools(), test_only=True), run_id="orchestrated", ledger_path=ledger, output_dir=tmp_path).run(rows) == []
