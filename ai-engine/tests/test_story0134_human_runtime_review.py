import copy
from pathlib import Path

import pytest

from evaluations.comparative_baseline.collection_runtime import (
    REPOSITORY_BYTE_BUDGETS,
    Assignment,
    CollectionRuntime,
    DevlogContext,
    EvidenceItem,
    GroundingAuthority,
    ProviderResponse,
    RunLedger,
    RuntimeContractError,
    SystemicInfrastructureFailure,
    TransportFailure,
    assert_official_collection_eligible,
    execution_order,
    replay_observation,
)
from evaluations.comparative_baseline.infrastructure import assignment_matrix, completeness, load_manifest
from evaluations.comparative_baseline.testing import final_answer


def _contexts():
    contexts = {}
    manifest = load_manifest()
    for question in manifest["questions"]:
        contexts[question["questionId"]] = DevlogContext(
            question_id=question["questionId"],
            question_version=question["questionVersion"],
            context_strategy="FROZEN_DEVLOG_EVALUATION_CONTEXT_PROJECTION",
            context_digest=(question["questionId"].replace("-", "") + "0" * 64)[:64],
            evidence=(EvidenceItem("docs/example.md", "captured direct evidence"),),
            prompt_system="Use only supplied context.",
            prompt_user=question["question"],
            context_revision="fixture-context-1.0.0",
            projection_revision="fixture-projection-1.0.0",
        )
    return contexts


def _rows_for_order(seed=134):
    rows = {row["assignmentId"]: row for row in assignment_matrix()}
    plan = execution_order(seed)
    return plan, [rows[assignment_id] for assignment_id in plan["assignments"]]


class DryRunTools:
    operations = ("search_repository", "read_file", "git_log", "git_show")

    def __init__(self, assignment):
        self.assignment = assignment

    def execute(self, operation, arguments):
        assert arguments["repositoryRevision"] == load_manifest()["repositoryRevision"]
        owner = self.assignment.assignment_id
        if operation == "search_repository":
            return {"operation": operation, "owner": owner, "matches": [{"path": "docs/example.md", "line": 1, "text": "evidence"}]}
        if operation == "read_file":
            return {"operation": operation, "owner": owner, "reference": "docs/example.md", "content": "captured direct evidence"}
        if operation == "git_log":
            return {"operation": operation, "owner": owner, "reference": "commit:" + "a" * 40, "commits": [{"id": "a" * 40}]}
        if operation == "git_show":
            return {"operation": operation, "owner": owner, "reference": "docs/example.md", "content": "captured direct evidence"}
        raise AssertionError(operation)


class DryRunProvider:
    def __init__(self):
        self.requests = []
        self.first_inputs = {}
        self.owner_by_request = {}

    def complete(self, request):
        assignment_id = request.assignment.assignment_id
        serialized = str(request.input_payload)
        if request.turn_index == 1:
            assert "previousToolResult" not in request.input_payload
            assert "finalResponse" not in serialized
            self.first_inputs[assignment_id] = copy.deepcopy(request.input_payload)
        for known_assignment_id in self.first_inputs:
            if known_assignment_id != assignment_id:
                assert known_assignment_id not in serialized
        if assignment_id in self.owner_by_request:
            previous_owner = self.owner_by_request[assignment_id]
            assert previous_owner == assignment_id
        self.owner_by_request[assignment_id] = assignment_id
        self.requests.append(request)
        if request.mode == "DEVLOG":
            answer = final_answer(request.assignment.question_id, request.assignment.question_version).payload
            if request.assignment.question_id == "CASE-04":
                answer["relationshipResult"] = "NOT_ESTABLISHED"
                answer["abstention"] = True
                answer["claims"] = []
                answer["evidence"] = []
            return ProviderResponse("FINAL", answer, {"request": assignment_id}, {"inputTokens": 10, "outputTokens": 20})
        if request.turn_index <= 4:
            operation = DryRunTools.operations[request.turn_index - 1]
            arguments = {"query": "evidence", "path": "docs/example.md", "commit": "a" * 40}
            return ProviderResponse("TOOL_CALL", {"operation": operation, "arguments": arguments}, {"request": assignment_id, "turn": request.turn_index})
        return final_answer(request.assignment.question_id, request.assignment.question_version)


class FailureProvider:
    def __init__(self, mode, fail_assignment):
        self.mode = mode
        self.fail_assignment = fail_assignment
        self.requests = []

    def complete(self, request):
        self.requests.append(request)
        if request.assignment.assignment_id == self.fail_assignment:
            if self.mode == "SYSTEMIC":
                raise SystemicInfrastructureFailure("provider unavailable for the run")
            if self.mode == "TRANSPORT":
                raise TransportFailure("temporary transport failure")
            if self.mode == "STRUCTURAL":
                return ProviderResponse("FINAL", {"invalid": True}, {"response": "invalid"})
        return final_answer(request.assignment.question_id, request.assignment.question_version)


class FixtureGrounding:
    def __init__(self, status="PASS"):
        self.status = status

    def validate(self, *, answer, evidence, assignment):
        if self.status == "PASS":
            return {"status": "PASS", "authority": "FIXTURE", "resolvedReferences": sorted(item.reference for item in evidence)}
        return {"status": "FAIL", "error": self.status}


def test_complete_18_assignment_dry_run_is_deterministic_isolated_and_complete(tmp_path):
    seed = 134
    plan, rows = _rows_for_order(seed)
    assert plan == execution_order(seed)
    changed_plan = execution_order(seed + 1)
    assert changed_plan["assignments"] != plan["assignments"]
    assert set(changed_plan["assignments"]) == set(plan["assignments"])
    assert len(rows) == 18
    assert len({row["assignmentId"] for row in rows}) == 18
    assert {row["repetition"] for row in rows} == {1, 2, 3}

    provider = DryRunProvider()
    runtime = CollectionRuntime(
        provider,
        devlog_contexts=_contexts(),
        tool_factory=lambda assignment: DryRunTools(assignment),
        run_id="dry-run-0134",
    )
    ledger = RunLedger(run_id="dry-run-0134", path=tmp_path / "dry-run-ledger.json")
    observations = runtime.run_assignments(rows, ledger=ledger)

    assert len(observations) == 18
    assert {observation["assignment"]["assignmentId"] for observation in observations} == set(plan["assignments"])
    assert all(observation["executionMode"] == "OFFLINE_DRY_RUN" for observation in observations)
    assert all(observation["stateHistory"][-1] == "FINALIZED" for observation in observations)
    assert runtime.provider_calls <= 99
    assert len(provider.first_inputs) == 18
    assert completeness(observations)["assignedObservationCount"] == 18
    assert completeness(observations)["executedObservationCount"] == 18
    assert completeness(observations)["semanticEligibleCount"] == 18
    assert completeness(observations)["pairedCompleteCount"] == 9
    for observation in observations:
        path = tmp_path / "dry-run-artifacts" / f"{observation['assignment']['assignmentId'].replace(':', '_')}.json"
        runtime.write_observation(observation, path)
        replay = replay_observation(observation)
        assert replay["providerCalls"] == 0
    with pytest.raises(RuntimeContractError):
        assert_official_collection_eligible(observations[0])


def test_devlog_forbidden_context_marker_fails_closed():
    context = _contexts()["CASE-03"]
    contaminated = DevlogContext(
        **{**context.__dict__, "prompt_user": "expectedEvidence must not be supplied"},
    )
    runtime = CollectionRuntime(
        DryRunProvider(),
        devlog_contexts={"CASE-03": contaminated},
    )
    with pytest.raises(RuntimeContractError):
        runtime.run_assignment(next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "DEVLOG"))

    called = False

    def forbidden_factory(_assignment):
        nonlocal called
        called = True
        raise AssertionError("DEVLOG attempted repository access")

    runtime = CollectionRuntime(
        DryRunProvider(),
        devlog_contexts={"CASE-03": context},
        tool_factory=forbidden_factory,
    )
    runtime.run_assignment(next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "DEVLOG"))
    assert called is False


def test_agent_direct_operation_seven_budget_byte_budget_devlog_and_mutation_fail_closed():
    row = next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "AGENT_DIRECT")

    class SevenOperations:
        def __init__(self):
            self.calls = 0

        def complete(self, request):
            self.calls += 1
            if self.calls <= 7:
                return ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": "docs/example.md"}}, {"call": self.calls})
            return final_answer("CASE-03", "1.0.0")

    observation = CollectionRuntime(SevenOperations(), tool_factory=lambda assignment: DryRunTools(assignment)).run_assignment(row)
    assert observation["executionStatus"] == "INVALID"
    assert observation["validationResults"]["error"] == "maximum tool operations exhausted"
    assert observation["toolOperations"] == 7

    class HugeTools(DryRunTools):
        def execute(self, operation, arguments):
            return {"reference": "docs/example.md", "content": "x" * 50000}

    observation = CollectionRuntime(
        SevenOperations(),
        tool_factory=lambda assignment: HugeTools(assignment),
    ).run_assignment(row)
    assert observation["executionStatus"] == "INVALID"
    assert "byte budget" in observation["validationResults"]["error"]

    class ForbiddenTools(DryRunTools):
        def execute(self, operation, arguments):
            return super().execute("write_file", arguments)

    observation = CollectionRuntime(
        DryRunProvider(),
        tool_factory=lambda assignment: ForbiddenTools(assignment),
    ).run_assignment(row)
    assert observation["executionStatus"] == "INVALID"


def test_failure_injection_continues_slots_and_stops_systemic_failure():
    rows = [row for row in assignment_matrix() if row["condition"] == "DEVLOG" and row["questionId"] == "CASE-03" and row["repetition"] in {1, 2}]
    transport = FailureProvider("TRANSPORT", rows[0]["assignmentId"])
    observations = CollectionRuntime(transport, devlog_contexts={"CASE-03": _contexts()["CASE-03"]}).run_assignments(rows)
    assert len(observations) == 2
    assert observations[0]["executionStatus"] == "INVALID"
    assert observations[0]["technicalRetryCalls"] == 1
    assert observations[1]["executionStatus"] == "EXECUTED"

    structural = FailureProvider("STRUCTURAL", rows[0]["assignmentId"])
    observations = CollectionRuntime(structural, devlog_contexts={"CASE-03": _contexts()["CASE-03"]}).run_assignments(rows)
    assert observations[0]["executionStatus"] == "INVALID"
    assert observations[0]["technicalRetryCalls"] == 0
    assert observations[1]["executionStatus"] == "EXECUTED"

    systemic = FailureProvider("SYSTEMIC", rows[0]["assignmentId"])
    observations = CollectionRuntime(systemic, devlog_contexts={"CASE-03": _contexts()["CASE-03"]}).run_assignments(rows)
    assert observations == []
    assert len(systemic.requests) == 1


def test_agent_retry_and_logical_call_accounting_are_distinct():
    row = next(row for row in assignment_matrix() if row["condition"] == "AGENT_DIRECT" and row["questionId"] == "CASE-03")

    class RetryProvider:
        def __init__(self):
            self.calls = 0

        def complete(self, request):
            self.calls += 1
            if self.calls == 1:
                raise TransportFailure("before first response")
            return final_answer("CASE-03", "1.0.0")

    observation = CollectionRuntime(
        RetryProvider(),
        tool_factory=lambda assignment: DryRunTools(assignment),
    ).run_assignment(row)
    assert observation["providerCalls"] == 2
    assert observation["logicalModelCalls"] == 1
    assert observation["technicalRetryCalls"] == 1
    assert observation["finalAnswerCount"] == 1

    class ResponseThenFailure:
        def __init__(self):
            self.calls = 0

        def complete(self, request):
            self.calls += 1
            if self.calls == 1:
                return ProviderResponse("TOOL_CALL", {"operation": "read_file", "arguments": {"path": "docs/example.md"}}, {"tool": "read"})
            raise TransportFailure("after response-bearing tool call")

    provider = ResponseThenFailure()
    observation = CollectionRuntime(provider, tool_factory=lambda assignment: DryRunTools(assignment)).run_assignment(row)
    assert observation["providerCalls"] == 2
    assert observation["logicalModelCalls"] == 1
    assert observation["technicalRetryCalls"] == 0
    assert observation["finalAnswerCount"] == 0


def test_grounding_classifications_and_raw_capture_order():
    row = next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "DEVLOG")
    for status in ("PASS", "INVALID_EVIDENCE", "UNAUTHORIZED_REFERENCE", "UNRESOLVABLE_REFERENCE"):
        provider = DryRunProvider()
        runtime = CollectionRuntime(provider, grounding=FixtureGrounding(status), devlog_contexts={"CASE-03": _contexts()["CASE-03"]})
        observation = runtime.run_assignment(row)
        assert observation["rawOutput"]["responses"]
        assert observation["rawOutputSha256"]
        assert observation["stateHistory"][1] == "RUNNING"
        assert observation["stateHistory"][2] == "RAW_CAPTURED"
        if status == "PASS":
            assert observation["stateHistory"][-2] == "SEMANTICALLY_ELIGIBLE"
        else:
            assert observation["semanticOutcome"] == "NOT_EVALUATED"
            assert observation["technicalRetryCalls"] == 0
            expected = "UNAUTHORIZED_REFERENCE" if status == "UNAUTHORIZED_REFERENCE" else "EVIDENCE_EXCERPT_MISMATCH"
            assert observation["primaryError"] == expected

    tampered = copy.deepcopy(observation)
    tampered["rawOutput"]["responses"][0]["rawResponse"]["tampered"] = True
    with pytest.raises((RuntimeContractError, ValueError)):
        replay_observation(tampered)


def test_resume_does_not_rerun_finalized_assignments_or_overwrite_partial_state(tmp_path):
    rows = [row for row in assignment_matrix() if row["condition"] == "DEVLOG" and row["questionId"] == "CASE-03" and row["repetition"] in {1, 2}]
    ledger_path = tmp_path / "resume-ledger.json"
    ledger = RunLedger(run_id="resume-run", path=ledger_path)
    ledger.register_plan([row["assignmentId"] for row in rows])
    first_provider = FailureProvider("SYSTEMIC", rows[1]["assignmentId"])
    first_runtime = CollectionRuntime(first_provider, devlog_contexts={"CASE-03": _contexts()["CASE-03"]}, run_id="resume-run")
    first_results = first_runtime.run_assignments(rows, ledger=ledger)
    assert len(first_results) == 1
    partial_trace = [{"operationIndex": 1, "resultSha256": "a" * 64}]
    ledger.record_partial(rows[1]["assignmentId"], "RAW_CAPTURED", 1, partial_trace)
    assert RunLedger(run_id="resume-run", path=ledger_path).data["assignments"][rows[1]["assignmentId"]]["partialToolTrace"] == partial_trace

    second_provider = FailureProvider("NONE", "never")
    second_runtime = CollectionRuntime(second_provider, devlog_contexts={"CASE-03": _contexts()["CASE-03"]}, run_id="resume-run")
    resumed = second_runtime.run_assignments(rows, ledger=RunLedger(run_id="resume-run", path=ledger_path))
    assert len(resumed) == 1
    assert resumed[0]["assignment"]["assignmentId"] == rows[1]["assignmentId"]
    assert first_provider.requests[0].assignment.assignment_id == rows[0]["assignmentId"]
    assert len(second_provider.requests) == 1


def test_representative_artifacts_have_future_data_science_join_fields():
    row = next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "DEVLOG")
    observation = CollectionRuntime(DryRunProvider(), devlog_contexts={"CASE-03": _contexts()["CASE-03"]}).run_assignment(row)
    required = {
        "questionId", "condition", "repetition", "modelConfiguration", "executionMode",
        "semanticOutcome", "validationResults", "inputTokens", "outputTokens",
        "logicalModelCalls", "technicalRetryCalls", "latencyMs", "visibleContextBytes",
        "repositoryBytesInspected", "toolOperations", "finalAnswerCount", "rawOutputSha256",
    }
    assert required.issubset(observation)
    assert observation["visibleContextBytes"] == len("captured direct evidence".encode())
    assert observation["repositoryBytesInspected"] == 0
    assert REPOSITORY_BYTE_BUDGETS["CASE-03"] == 31683

    direct_row = next(row for row in assignment_matrix() if row["questionId"] == "CASE-03" and row["condition"] == "AGENT_DIRECT")
    direct = CollectionRuntime(DryRunProvider(), tool_factory=lambda assignment: DryRunTools(assignment)).run_assignment(direct_row)
    assert direct["repositoryBytesInspected"] > 0
    assert direct["repositoryOperations"] == 4
    assert direct["toolOperations"] == 4


def test_secret_configuration_fails_before_persistence(tmp_path):
    from evaluations.comparative_baseline.collection_runtime import RuntimeConfiguration

    with pytest.raises(RuntimeContractError):
        CollectionRuntime(
            object(),
            configuration=RuntimeConfiguration(provider="fixture", model="fixture", temperature="sk-1234567890123456"),
        )
    assert not list(tmp_path.iterdir())
