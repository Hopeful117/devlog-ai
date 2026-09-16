import copy

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


def test_common_answer_contract_rejects_extra_oracle_fields():
    answer = final_answer("CASE-03", "1.0.0").payload
    assignment = Assignment.from_manifest_row(_row())
    validate_answer(answer, assignment)
    contaminated = copy.deepcopy(answer)
    contaminated["expectedEvidence"] = []
    with pytest.raises(RuntimeContractError):
        validate_answer(contaminated, assignment)


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


def test_repository_tools_pin_revision_and_reject_unsafe_operations():
    revision = load_manifest()["repositoryRevision"]
    tools = InMemoryRepositoryTools(revision=revision, files={"docs/example.md": "Evidence"})
    result = tools.execute("read_file", {"path": "docs/example.md", "repositoryRevision": revision})
    assert result["reference"] == "docs/example.md"
    with pytest.raises(RuntimeContractError):
        tools.execute("shell", {})
    with pytest.raises(RuntimeContractError):
        tools.execute("read_file", {"path": "docs/example.md", "repositoryRevision": "other"})
