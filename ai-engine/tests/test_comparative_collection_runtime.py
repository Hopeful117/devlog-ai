import copy
import json

import pytest

from evaluations.comparative_baseline.collection_runtime import (
    AGENT_INTERMEDIATE_OUTPUT_TOKENS,
    FINAL_OUTPUT_TOKENS,
    MAX_PROVIDER_CALLS_FULL_BASELINE,
    MAX_TOOL_OPERATIONS,
    REPOSITORY_BYTE_BUDGETS,
    Assignment,
    CollectionRuntime,
    DevlogContext,
    EvidenceItem,
    InMemoryRepositoryTools,
    ObservationStateMachine,
    ProviderResponse,
    RuntimeConfiguration,
    RuntimeContractError,
    TransportFailure,
    assert_secret_free,
    execution_order,
    replay_observation,
    validate_answer,
)
from evaluations.comparative_baseline.infrastructure import assignment_matrix, load_manifest, validate_raw_observation
from evaluations.comparative_baseline.live_adapters import PilotStorage
from evaluations.comparative_baseline.testing import ScriptedProvider, final_answer


def _context(question_id="CASE-03"):
    question = next(item for item in load_manifest()["questions"] if item["questionId"] == question_id)
    return DevlogContext(
        question_id=question_id,
        question_version=question["questionVersion"],
        context_strategy="FROZEN_DEVLOG_EVALUATION_CONTEXT_PROJECTION",
        context_digest="a" * 64,
        evidence=(EvidenceItem("docs/example.md", "captured direct evidence"),),
        prompt_system="Answer only from the supplied evidence.",
        prompt_user=question["question"],
    )


def _row(condition="DEVLOG", question_id="CASE-03", repetition=1):
    return next(
        row for row in assignment_matrix() if row["condition"] == condition and row["questionId"] == question_id and row["repetition"] == repetition
    )


class ReferenceTools:
    def __init__(self, revision):
        self.calls = []
        self.revision = revision

    def execute(self, operation, arguments):
        self.calls.append((operation, arguments))
        if operation != "read_file":
            raise AssertionError("fixture only supports the requested read operation")
        return {"reference": "docs/example.md", "content": "captured direct evidence", "line": 1}


def test_configuration_freezes_runtime_limits_and_order_is_reproducible():
    configuration = RuntimeConfiguration()
    assert configuration.final_output_tokens == FINAL_OUTPUT_TOKENS == 1800
    assert configuration.intermediate_output_tokens == AGENT_INTERMEDIATE_OUTPUT_TOKENS == 512
    assert configuration.max_tool_operations == MAX_TOOL_OPERATIONS == 6
    assert MAX_PROVIDER_CALLS_FULL_BASELINE == 99
    assert execution_order(42) == execution_order(42)
    assert execution_order(42) != execution_order(43)
    assert set(execution_order(42)["assignments"]) == {row["assignmentId"] for row in assignment_matrix()}


def test_official_live_mode_is_explicit_and_contradictory_mode_fails_before_provider_call():
    row = _row()
    provider = ScriptedProvider([final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(
        provider,
        devlog_contexts={"CASE-03": _context()},
        run_id="official-mode-test",
        execution_class="OFFICIAL_BASELINE",
        baseline_eligible=True,
    )
    observation = runtime.run_assignment(row)
    assert observation["executionMode"] == "LIVE"
    assert observation["executionClass"] == "OFFICIAL_BASELINE"
    assert len(provider.requests) == 1

    contradictory = ScriptedProvider([final_answer("CASE-03", "1.0.0")])
    with pytest.raises(RuntimeContractError, match="execution class and execution mode"):
        CollectionRuntime(
            contradictory,
            devlog_contexts={"CASE-03": _context()},
            execution_class="OFFICIAL_BASELINE",
            execution_mode="OFFLINE_DRY_RUN",
            baseline_eligible=True,
        )
    assert contradictory.requests == []


def test_common_answer_contract_rejects_extra_oracle_fields():
    answer = final_answer("CASE-03", "1.0.0").payload
    assignment = Assignment.from_manifest_row(_row())
    validate_answer(answer, assignment)
    contaminated = copy.deepcopy(answer)
    contaminated["expectedEvidence"] = []
    with pytest.raises(RuntimeContractError):
        validate_answer(contaminated, assignment)


def test_wrong_or_missing_identity_is_rejected_without_repair():
    assignment = Assignment.from_manifest_row(_row())
    wrong = final_answer("story0134", "1.0").payload
    with pytest.raises(RuntimeContractError, match="question identity"):
        validate_answer(wrong, assignment)
    missing = final_answer("CASE-03", "1.0.0").payload
    missing.pop("questionId")
    with pytest.raises(RuntimeContractError, match="contract fields"):
        validate_answer(missing, assignment)


def test_commit_hunk_locator_requires_header_without_repair():
    answer = final_answer("CASE-03", "1.0.0").payload
    answer["evidence"][0]["locator"] = {
        "kind": "COMMIT_HUNK",
        "startLine": 1,
        "endLine": 2,
        "heading": None,
        "commit": "a" * 40,
        "path": "docs/example.md",
        "header": None,
    }
    with pytest.raises(RuntimeContractError, match="commit-hunk locator"):
        validate_answer(answer, Assignment.from_manifest_row(_row()))


def test_devlog_captures_raw_response_and_never_constructs_repository_tools():
    provider = ScriptedProvider([final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(provider, devlog_contexts={"CASE-03": _context()})
    observation = runtime.run_assignment(_row())
    validate_raw_observation(observation)
    assert observation["condition"] == "DEVLOG"
    assert observation["validationResults"]["structural"] == "PASS"
    assert observation["finalAnswerCount"] == 1
    assert observation["providerCalls"] == 1
    assert observation["rawOutput"]["responses"][0]["rawResponse"]
    assert observation["stateHistory"][-1] == "FINALIZED"
    assert observation["structuralValid"] == "YES"
    assert observation["groundingValid"] == "YES"
    assert observation["semanticCorrect"] == "NOT_EVALUATED"
    assert observation["comparativeGroundingContractVersion"] == "story0135-comparative-grounding-1.0.0"
    assert observation["comparativeScoringProjectionVersion"] == "story0135-comparative-scoring-projection-1.0.0"


def test_agent_direct_uses_bounded_tools_and_common_final_contract():
    tool = ReferenceTools(load_manifest()["repositoryRevision"])
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": "docs/example.md"}}, {"tool": "read"}),
        final_answer("CASE-03", "1.0.0"),
    ])
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: tool)
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    validate_raw_observation(observation)
    assert observation["condition"] == "AGENT_DIRECT"
    assert observation["toolOperations"] == 1
    assert len(tool.calls) == 1
    assert observation["logicalModelCalls"] == 2
    assert observation["technicalRetryCalls"] == 0
    assert observation["rawOutput"]["toolTrace"][0]["resultSha256"]


def test_agent_direct_question_budget_is_frozen_and_not_increased():
    assert REPOSITORY_BYTE_BUDGETS == {"CASE-01-COMPARATIVE": 32401, "CASE-03": 31683, "CASE-04": 13504}
    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": "docs/example.md"}}, {})] * 7)
    runtime = CollectionRuntime(provider, tool_factory=lambda assignment: ReferenceTools(load_manifest()["repositoryRevision"]))
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    assert observation["executionStatus"] == "INVALID"
    assert observation["primaryError"] == "INFRASTRUCTURE_FAILURE"
    assert observation["finalAnswerCount"] == 0


def test_technical_retry_is_separate_and_response_bearing_failure_is_not_retried():
    provider = ScriptedProvider([TransportFailure("temporary"), final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(provider, devlog_contexts={"CASE-03": _context()})
    observation = runtime.run_assignment(_row())
    assert observation["providerCalls"] == 2
    assert observation["logicalModelCalls"] == 1
    assert observation["technicalRetryCalls"] == 1

    invalid_answer = final_answer("CASE-03", "1.0.0").payload
    invalid_answer["confidence"] = "BROKEN"
    provider = ScriptedProvider([ProviderResponse("FINAL", invalid_answer, {"output": invalid_answer}), final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(provider, devlog_contexts={"CASE-03": _context()})
    observation = runtime.run_assignment(_row())
    assert observation["providerCalls"] == 1
    assert len(provider.requests) == 1
    assert observation["executionStatus"] == "INVALID"


def test_slot_local_failure_does_not_stop_next_assignment():
    provider = ScriptedProvider([
        ProviderResponse("FINAL", {"bad": True}, {"output": {"bad": True}}),
        final_answer("CASE-03", "1.0.0"),
    ])
    runtime = CollectionRuntime(provider, devlog_contexts={"CASE-03": _context()})
    observations = runtime.run_assignments([_row(), _row(repetition=2)])
    assert len(observations) == 2
    assert observations[0]["executionStatus"] == "INVALID"
    assert observations[1]["executionStatus"] == "EXECUTED"


def test_state_machine_rejects_illegal_transition():
    machine = ObservationStateMachine()
    with pytest.raises(RuntimeContractError):
        machine.transition("FINALIZED")


def test_write_once_replay_and_secret_guard(tmp_path):
    provider = ScriptedProvider([final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(provider, devlog_contexts={"CASE-03": _context()}, run_id="run-1")
    observation = runtime.run_assignment(_row())
    destination = tmp_path / "observation.json"
    runtime.write_observation(observation, destination)
    with pytest.raises(FileExistsError):
        runtime.write_observation(observation, destination)
    replay = replay_observation(observation)
    assert replay["providerCalls"] == 0
    assert replay["capturedRawOutputVerified"] is True
    with pytest.raises(RuntimeContractError):
        assert_secret_free({"Authorization": "Bearer secret-value"})


def test_tool_budget_counts_failed_attempts_and_rejects_seventh_execution():
    class FailingTools:
        def execute(self, operation, arguments):
            raise ValueError("adapter failure")

    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "x"}}, {})
    ] * 7)
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: FailingTools())
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    assert observation["toolOperations"] == 6
    assert observation["toolAttempts"] == 6
    assert observation["toolFailedOperations"] == 6
    assert observation["toolNotExecutedOperations"] == 1
    assert len(observation["rawOutput"]["toolTrace"]) == 7
    assert observation["rawOutput"]["toolTrace"][-1]["executionStatus"] == "NOT_EXECUTED_BUDGET_EXHAUSTED"
    assert len(provider.requests) == 7


def test_empty_tool_results_consume_attempt_budget():
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "missing"}}, {})
    ] * 7)
    tools = InMemoryRepositoryTools(revision=load_manifest()["repositoryRevision"], files={})
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: tools)
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    assert observation["toolOperations"] == 6
    assert observation["toolSuccessfulOperations"] == 6
    assert observation["toolFailedOperations"] == 0
    assert observation["rawOutput"]["toolTrace"][0]["executionStatus"] == "EXECUTED_EMPTY"


def test_multiple_tool_calls_execute_in_provider_order():
    calls = [
        {"operation": "read_file", "arguments": {"path": "docs/example.md"}},
        {"operation": "read_file", "arguments": {"path": "docs/example.md"}},
    ]
    provider = ScriptedProvider([
        ProviderResponse("TOOL_CALL", {"calls": calls}, {}),
        final_answer("CASE-03", "1.0.0"),
    ])
    tools = InMemoryRepositoryTools(revision=load_manifest()["repositoryRevision"], files={"docs/example.md": "captured direct evidence"})
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: tools)
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    assert observation["executionStatus"] == "EXECUTED"
    assert observation["toolOperations"] == 2
    assert [trace["executionStatus"] for trace in observation["rawOutput"]["toolTrace"]] == ["EXECUTED_SUCCESS", "EXECUTED_SUCCESS"]
    assert [trace["targetOrQuery"]["path"] for trace in observation["rawOutput"]["toolTrace"]] == ["docs/example.md", "docs/example.md"]


def test_multiple_tool_calls_record_unexecuted_call_at_remaining_budget():
    single = ProviderResponse("TOOL_CALL", {"operation": "search_repository", "arguments": {"query": "missing"}}, {})
    final_turn = ProviderResponse("TOOL_CALL", {"calls": [
        {"operation": "search_repository", "arguments": {"query": "missing"}},
        {"operation": "search_repository", "arguments": {"query": "also-missing"}},
    ]}, {})
    provider = ScriptedProvider([single] * 5 + [final_turn])
    tools = InMemoryRepositoryTools(revision=load_manifest()["repositoryRevision"], files={})
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: tools)
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    assert observation["toolOperations"] == 6
    assert observation["toolNotExecutedOperations"] == 1
    assert observation["rawOutput"]["toolTrace"][-1]["executionStatus"] == "NOT_EXECUTED_BUDGET_EXHAUSTED"
    assert observation["rawOutput"]["toolTrace"][-1]["targetOrQuery"] == {"query": "also-missing"}


def test_byte_budget_exhaustion_traces_remaining_provider_calls_in_order():
    calls = [
        {"operation": "read_file", "arguments": {"path": "docs/one.md"}},
        {"operation": "read_file", "arguments": {"path": "docs/two.md"}},
        {"operation": "read_file", "arguments": {"path": "docs/three.md"}},
        {"operation": "read_file", "arguments": {"path": "docs/four.md"}},
    ]

    class HugeTools:
        def execute(self, operation, arguments):
            return {"reference": arguments["path"], "content": "x" * 50000}

    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"calls": calls}, {})])
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: HugeTools())
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    trace = observation["rawOutput"]["toolTrace"]
    assert [item["targetOrQuery"]["path"] for item in trace] == [
        "docs/one.md", "docs/two.md", "docs/three.md", "docs/four.md",
    ]
    assert trace[0]["executionStatus"] == "EXECUTED_FAILURE"
    assert [item["executionStatus"] for item in trace[1:]] == [
        "NOT_EXECUTED_BUDGET_EXHAUSTED",
        "NOT_EXECUTED_BUDGET_EXHAUSTED",
        "NOT_EXECUTED_BUDGET_EXHAUSTED",
    ]
    assert observation["toolOperations"] == 1
    assert observation["toolFailedOperations"] == 1
    assert observation["toolNotExecutedOperations"] == 3


def test_byte_budget_trace_accounts_every_requested_call_without_executing_remainder():
    calls = [
        {"operation": "read_file", "arguments": {"path": "docs/one.md"}},
        {"operation": "read_file", "arguments": {"path": "docs/two.md"}},
    ]

    class HugeTools:
        def execute(self, operation, arguments):
            return {"reference": arguments["path"], "content": "x" * 50000}

    provider = ScriptedProvider([ProviderResponse("TOOL_CALL", {"calls": calls}, {})])
    runtime = CollectionRuntime(provider, tool_factory=lambda _assignment: HugeTools())
    observation = runtime.run_assignment(_row("AGENT_DIRECT"))
    statuses = [item["executionStatus"] for item in observation["rawOutput"]["toolTrace"]]
    assert len(calls) == statuses.count("EXECUTED_FAILURE") + statuses.count("NOT_EXECUTED_BUDGET_EXHAUSTED")


def test_live_pilot_runtime_classifies_writes_and_replays_outside_official_baseline(tmp_path):
    pilot = PilotStorage(tmp_path / "pilot", "run-1", tmp_path / "official")
    provider = ScriptedProvider([final_answer("CASE-03", "1.0.0")])
    runtime = CollectionRuntime(
        provider,
        devlog_contexts={"CASE-03": _context()},
        run_id="pilot-run-1",
        execution_class=pilot.execution_class,
        baseline_eligible=pilot.baseline_eligible,
        artifact_writer=pilot.write_observation,
    )

    observation = runtime.run_assignment(_row())
    assert observation["executionMode"] == "LIVE_PILOT"
    assert observation["executionClass"] == "LIVE_PILOT"
    assert observation["baselineEligible"] is False
    with pytest.raises(ValueError, match="live pilot"):
        validate_raw_observation(observation)

    artifact = json.loads(pilot.artifact_path(_row()["assignmentId"]).read_text(encoding="utf-8"))
    assert artifact["executionClass"] == "LIVE_PILOT"
    assert artifact["baselineEligible"] is False
    replay = replay_observation(artifact["observation"], enforce_official_baseline=False)
    assert replay["providerCalls"] == 0
    assert pilot.ledger_path.exists()


def test_repository_tools_pin_revision_and_reject_unsafe_operations():
    revision = load_manifest()["repositoryRevision"]
    tools = InMemoryRepositoryTools(revision=revision, files={"docs/example.md": "Evidence"})
    result = tools.execute("read_file", {"path": "docs/example.md", "repositoryRevision": revision})
    assert result["reference"] == "docs/example.md"
    with pytest.raises(RuntimeContractError):
        tools.execute("shell", {})
    with pytest.raises(RuntimeContractError):
        tools.execute("read_file", {"path": "docs/example.md", "repositoryRevision": "other"})
