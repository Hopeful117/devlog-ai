"""Provider-agnostic V4 runtime with an explicit multi-turn conversation."""

from __future__ import annotations

import hashlib
import json
import time
import re
from pathlib import Path
from typing import Any

from evaluations.comparative_baseline.collection_runtime import (
    ProviderRequest,
    ProviderResponse,
    RuntimeContractError,
    TransportFailure,
    provider_failure_attribution,
)

from .protocol import (
    ExecutionStatus,
    EvidenceItem,
    ResourceAccounting,
    SafetyCeilings,
    V4Assignment,
    V4_TOOL_OPERATIONS,
    V4_TOOL_OPERATION_MATRIX,
    V4_TOOL_FIELD_CONSTRAINTS,
    build_observation,
    condition_input,
    evaluate_answer,
    not_evaluated,
    replay_observation,
    validate_v4_identity,
    v4_experiment_identity,
    v4_execution_configuration,
    v4_assignment_matrix,
    validate_provider_payload,
    write_derived_projection,
    write_observation,
)

V4_NATIVE_PROVIDER_TIMEOUT_SECONDS = 30.0
V4_NATIVE_TOOL_TIMEOUT_SECONDS = 15.0


def _tool_schema() -> list[dict[str, Any]]:
    json_types = {"string": ["string", "null"], "integer": ["integer", "null"], "string[]": ["array", "null"]}
    tools = []
    for operation in V4_TOOL_OPERATIONS:
        matrix = V4_TOOL_OPERATION_MATRIX[operation]
        fields = {**matrix["required"], **matrix["optional"]}
        properties = {}
        for name, kind in fields.items():
            value_type = json_types[kind]
            constraints = V4_TOOL_FIELD_CONSTRAINTS.get(operation, {}).get(name, {})
            properties[name] = {
                "type": value_type,
                **({"items": {"type": "string", **constraints["items"]}} if kind == "string[]" else {}),
                **{key: value for key, value in constraints.items() if key != "items"},
            }
        tools.append({
            "type": "function",
            "name": operation,
            "description": f"Read-only pinned repository operation: {operation}.",
            "strict": True,
            "parameters": {"type": "object", "additionalProperties": False, "properties": properties, "required": list(fields)},
        })
    return tools


def _canonical_bytes(value: Any) -> int:
    import json

    return len(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8"))


def _contract_error(operation: str | None, arguments: Any) -> str | None:
    if not isinstance(operation, str):
        return "tool request must be canonical: {operation: <string>, arguments: <object>} (operation must be a string)"
    if operation not in V4_TOOL_OPERATIONS:
        return f"unsupported repository operation: {operation}; expected one of {', '.join(V4_TOOL_OPERATIONS)}"
    if not isinstance(arguments, dict):
        return f"tool request for '{operation}' must be canonical: {{operation: '{operation}', arguments: <object>}}"
    matrix = V4_TOOL_OPERATION_MATRIX[operation]
    unknown = sorted(set(arguments) - set(matrix["required"]) - set(matrix["optional"]))
    if unknown:
        return f"unexpected tool argument field(s) for '{operation}': {', '.join(unknown)}"
    missing = [key for key in matrix["required"] if key not in arguments or arguments[key] is None]
    if missing:
        return f"missing required tool argument field(s) for '{operation}': {', '.join(missing)}"
    for name, kind in {**matrix["required"], **matrix["optional"]}.items():
        value = arguments.get(name)
        if value is None:
            continue
        valid = isinstance(value, str) if kind == "string" else isinstance(value, int) and not isinstance(value, bool) if kind == "integer" else isinstance(value, list) and all(isinstance(item, str) for item in value)
        if not valid:
            return f"tool argument '{name}' for '{operation}' must be {kind}"
        constraints = V4_TOOL_FIELD_CONSTRAINTS.get(operation, {}).get(name, {})
        if "minLength" in constraints and len(value) < constraints["minLength"]:
            return f"tool argument '{name}' for '{operation}' must have at least {constraints['minLength']} character"
        if "minimum" in constraints and value < constraints["minimum"]:
            return f"tool argument '{name}' for '{operation}' must be at least {constraints['minimum']}"
        pattern = constraints.get("pattern")
        if pattern and not re.fullmatch(pattern, value):
            return f"tool argument '{name}' for '{operation}' does not satisfy the required format"
        if kind == "string[]":
            item_constraints = constraints.get("items", {})
            for item in value:
                if item_constraints.get("minLength", 0) and len(item) < item_constraints["minLength"]:
                    return f"tool argument '{name}' for '{operation}' contains an empty path"
                item_pattern = item_constraints.get("pattern")
                if item_pattern and not re.fullmatch(item_pattern, item):
                    return f"tool argument '{name}' for '{operation}' contains an unsafe path"
    return None


def _trace_error(operation: Any, arguments: Any, *, status: str, error: str) -> dict[str, Any]:
    return {
        "operationIndex": None,
        "operationType": operation,
        "targetOrQuery": arguments,
        "executionStatus": status,
        "resultIdentity": None,
        "resultByteCount": 0,
        "resultSha256": None,
        "durationMs": 0,
        "delivered": False,
        "errorState": "ToolContractError" if status == "NOT_EXECUTED_INVALID_REQUEST" else "ToolExecutionError",
        "errorMessage": error,
    }


class V4CollectionRuntime:
    """Execute one V4 assignment without importing V3 budgets or semantics."""

    def __init__(self, provider: Any, *, ceilings: SafetyCeilings, devlog_contexts: dict[str, Any] | None = None, tool_factory: Any | None = None, run_id: str = "UNASSIGNED", test_only: bool = False, provider_timeout_seconds: float = V4_NATIVE_PROVIDER_TIMEOUT_SECONDS, tool_timeout_seconds: float = V4_NATIVE_TOOL_TIMEOUT_SECONDS):
        self.provider = provider
        self.ceilings = ceilings
        self.devlog_contexts = devlog_contexts or {}
        self.tool_factory = tool_factory
        self.run_id = run_id
        self.manifest = validate_v4_identity()
        if provider_timeout_seconds <= 0 or tool_timeout_seconds <= 0:
            raise ValueError("technical timeouts must be positive")
        self.provider_timeout_seconds = provider_timeout_seconds
        self.tool_timeout_seconds = tool_timeout_seconds
        manifest_safety = self.manifest["safetyCeilings"]
        if manifest_safety["status"] != "NO_EXPERIMENT_LIMITS":
            if not test_only:
                raise ValueError("V4 collection requires the unbounded DIRECT configuration")
        else:
            approved = SafetyCeilings.from_manifest(self.manifest)
            if self.ceilings.as_dict() != approved.as_dict():
                raise ValueError("V4 safety ceilings do not match the frozen experiment identity")
        if not test_only:
            execution = self.manifest["executionConfiguration"]
            if provider_timeout_seconds != execution["nativeProviderTimeoutSeconds"] or tool_timeout_seconds != execution["nativeRepositoryTimeoutSeconds"]:
                raise ValueError("V4 native timeout configuration does not match the frozen experiment identity")

    @staticmethod
    def _assignment(value: Any) -> V4Assignment:
        if isinstance(value, V4Assignment):
            return value
        if isinstance(value, dict):
            return V4Assignment(value["assignmentId"], value["questionId"], value["questionVersion"], value["caseId"], value["condition"], value["repetition"])
        return V4Assignment(value.assignment_id, value.question_id, value.question_version, value.case_id, "AGENT_DIRECT_OPEN" if value.condition == "AGENT_DIRECT" else value.condition, value.repetition)

    def _request(
        self,
        assignment: V4Assignment,
        turn: int,
        payload: dict[str, Any],
        tool_schema: dict[str, Any] | None,
        output_tokens: int,
        *,
        on_transport_attempt: Any | None = None,
        on_provider_response: Any | None = None,
    ) -> ProviderResponse:
        validate_provider_payload(assignment.condition, payload)
        request = ProviderRequest(
            assignment=assignment,
            mode=assignment.condition,
            turn_index=turn,
            input_payload=payload,
            tool_schema=tool_schema,
            max_output_tokens=output_tokens,
            on_transport_attempt=on_transport_attempt,
            on_provider_response=on_provider_response,
        )
        if on_transport_attempt is not None:
            on_transport_attempt()
        return self.provider.complete(request)

    def _observation(self, assignment: V4Assignment, raw: Any, resources: ResourceAccounting, status: ExecutionStatus, evaluation: dict[str, Any] | None = None, provider_failure: dict[str, Any] | None = None) -> dict[str, Any]:
        resources.assignment_latency_ms = round((time.perf_counter() - self._started) * 1000)
        if isinstance(raw, dict):
            raw = {**raw, "providerAttempts": resources.provider_attempts_as_raw()}
        else:
            raw = {"events": raw, "providerAttempts": resources.provider_attempts_as_raw()}
        return build_observation(assignment=assignment.identity(), run_id=self.run_id, raw_output=raw, evaluation=evaluation or not_evaluated(), resources=resources, execution_status=status, experiment_identity=v4_experiment_identity(safety=self.ceilings), provider_failure=provider_failure)

    def _run_devlog(self, assignment: V4Assignment) -> dict[str, Any]:
        context = self.devlog_contexts.get(assignment.question_id)
        if context is None or context.question_version != assignment.question_version:
            raise RuntimeContractError("V4 DEVLOG context identity is unavailable")
        resources = ResourceAccounting()
        payload = context.as_input()
        config = v4_execution_configuration()
        attempt = None
        try:
            started = time.perf_counter()
            resources.record_request(_canonical_bytes(payload))
            attempt = resources.begin_provider_attempt(1)
            response = self._request(assignment, 1, payload, None, config["finalOutputTokens"], on_transport_attempt=lambda: resources.record_transport_attempt(attempt), on_provider_response=lambda usage, diagnostics: resources.record_provider_response(attempt, usage, diagnostics))
            latency = round((time.perf_counter() - started) * 1000)
            resources.record_usable_provider(attempt, response, final=response.kind == "FINAL", latency_ms=latency)
            resources.record_attempt_latency(attempt, latency)
        except TransportFailure as error:
            if attempt is not None:
                resources.record_attempt_latency(attempt, round((time.perf_counter() - started) * 1000))
            resources.stop("PROVIDER_FAILURE")
            return self._observation(assignment, [], resources, ExecutionStatus.PROVIDER_FAILURE, provider_failure=error.attribution or provider_failure_attribution(error))
        except TimeoutError as error:
            if attempt is not None:
                resources.record_attempt_latency(attempt, round((time.perf_counter() - started) * 1000))
            resources.provider_timeouts += 1
            resources.stop("PROVIDER_TIMEOUT")
            return self._observation(assignment, [{"technicalEvent": "PROVIDER_TIMEOUT", "message": str(error)}], resources, ExecutionStatus.PROVIDER_TIMEOUT, provider_failure=getattr(error, "attribution", None) or provider_failure_attribution(error, category="TIMEOUT"))
        if response.kind != "FINAL":
            resources.stop("PROVIDER_NO_FINAL_ANSWER")
            return self._observation(assignment, [response.raw_response], resources, ExecutionStatus.PROVIDER_FAILURE)
        evaluation = evaluate_answer(response.payload, assignment, [EvidenceItem(item.reference, item.content, source_type="DEVLOG_CONTEXT") for item in context.evidence])
        context_evidence = [{"reference": item.reference, "content": item.content, "contentSha256": item.sha256, "authorized": True, "resolved": True} for item in context.evidence]
        return self._observation(assignment, {"responses": [response.raw_response], "finalResponse": response.payload, "evidence": context_evidence}, resources, ExecutionStatus.COMPLETED, evaluation)

    def _run_direct(self, assignment: V4Assignment) -> dict[str, Any]:
        if self.tool_factory is None:
            raise RuntimeContractError("V4 AGENT_DIRECT_OPEN tool server is unavailable")
        tools = self.tool_factory(assignment)
        resources = ResourceAccounting()
        responses: list[dict[str, Any]] = []
        tool_trace: list[dict[str, Any]] = []
        evidence: list[dict[str, Any]] = []
        base = condition_input(assignment.condition, assignment.question_id, assignment.repetition)
        conversation: list[dict[str, Any]] = [{"role": "user", "content": base["question"]}]
        final: dict[str, Any] | None = None
        provider_failure: dict[str, Any] | None = None
        status = ExecutionStatus.CENSORED
        self._started = time.perf_counter()
        turn = 0
        while final is None:
            turn += 1
            payload = {**base, "conversation": list(conversation)}
            request_size = _canonical_bytes(payload)
            resources.record_request(request_size)
            attempt = None
            try:
                call_started = time.perf_counter()
                config = v4_execution_configuration()
                attempt = resources.begin_provider_attempt(turn)
                response = self._request(assignment, turn, payload, _tool_schema(), config["finalOutputTokens"] if turn == 1 else config["intermediateOutputTokens"], on_transport_attempt=lambda: resources.record_transport_attempt(attempt), on_provider_response=lambda usage, diagnostics: resources.record_provider_response(attempt, usage, diagnostics))
                latency = round((time.perf_counter() - call_started) * 1000)
                resources.record_usable_provider(attempt, response, final=response.kind == "FINAL", latency_ms=latency)
                resources.record_attempt_latency(attempt, latency)
            except TransportFailure as error:
                if attempt is not None:
                    resources.record_attempt_latency(attempt, round((time.perf_counter() - call_started) * 1000))
                resources.stop("PROVIDER_FAILURE")
                status = ExecutionStatus.PROVIDER_FAILURE
                provider_failure = error.attribution or provider_failure_attribution(error)
                break
            except TimeoutError as error:
                if attempt is not None:
                    resources.record_attempt_latency(attempt, round((time.perf_counter() - call_started) * 1000))
                responses.append({"technicalEvent": "PROVIDER_TIMEOUT", "message": str(error)})
                resources.provider_timeouts += 1
                resources.stop("PROVIDER_TIMEOUT")
                status = ExecutionStatus.PROVIDER_TIMEOUT
                provider_failure = getattr(error, "attribution", None) or provider_failure_attribution(error, category="TIMEOUT")
                break
            except Exception:
                if attempt is not None:
                    resources.record_attempt_latency(attempt, round((time.perf_counter() - call_started) * 1000))
                resources.stop("RUNTIME_INFRASTRUCTURE_FAILURE")
                status = ExecutionStatus.RUNTIME_FAILURE
                break
            responses.append(response.raw_response)
            if response.kind == "FINAL":
                final = response.payload
                status = ExecutionStatus.COMPLETED
                conversation.append({"role": "assistant", "kind": "FINAL", "content": response.payload})
                break
            if response.kind != "TOOL_CALL":
                resources.stop("MODEL_TOOL_CONTRACT_FAILURE")
                status = ExecutionStatus.MODEL_FAILURE
                break
            calls = response.payload.get("calls", [response.payload]) if isinstance(response.payload, dict) else []
            if not isinstance(calls, list) or not calls:
                calls = [response.payload]
            call_ids = [f"v4-turn-{turn}-call-{index}" for index in range(len(calls))]
            conversation.append({"role": "assistant", "kind": "TOOL_CALL", "content": response.payload, "toolCallIds": call_ids})
            stop_after_turn = False
            for index, call in enumerate(calls):
                operation = call.get("operation") if isinstance(call, dict) else None
                arguments = call.get("arguments") if isinstance(call, dict) else None
                if isinstance(call, dict) and ("operation" not in call or "arguments" not in call):
                    arguments = None
                error = _contract_error(operation, arguments)
                if error:
                    trace = _trace_error(operation, arguments, status="NOT_EXECUTED_INVALID_REQUEST", error=error)
                    tool_trace.append(trace)
                    resources.record_tool(operation, valid=False, executed=False)
                    # Policy A: only the deterministic contract error is exposed.
                    conversation.append({"role": "tool", "operation": operation, "toolCallId": call_ids[index], "error": error})
                    continue
                operation_index = resources.tool_calls + 1
                started = time.perf_counter()
                try:
                    result = tools.execute(operation, {**arguments, "repositoryRevision": base["repositoryRevision"]})
                except RuntimeContractError as error:
                    trace = _trace_error(operation, arguments, status="EXECUTED_RUNTIME_FAILURE", error=str(error))
                    trace["operationIndex"] = operation_index
                    resources.record_tool(operation, valid=True, executed=True)
                    tool_trace.append(trace)
                    resources.stop("RUNTIME_INFRASTRUCTURE_FAILURE")
                    status = ExecutionStatus.RUNTIME_FAILURE
                    stop_after_turn = True
                    break
                except TimeoutError as error:
                    trace = _trace_error(operation, arguments, status="EXECUTED_TOOL_TIMEOUT", error=str(error))
                    trace["operationIndex"] = operation_index
                    trace["durationMs"] = round((time.perf_counter() - started) * 1000)
                    resources.tool_timeouts += 1
                    resources.record_tool(operation, valid=True, executed=True)
                    tool_trace.append(trace)
                    resources.stop("TOOL_TIMEOUT")
                    status = ExecutionStatus.TOOL_TIMEOUT
                    stop_after_turn = True
                    break
                except Exception as error:
                    trace = _trace_error(operation, arguments, status="EXECUTED_TOOL_FAILURE", error=str(error))
                    trace["operationIndex"] = operation_index
                    trace["durationMs"] = round((time.perf_counter() - started) * 1000)
                    resources.record_tool(operation, valid=True, executed=True)
                    tool_trace.append(trace)
                    conversation.append({"role": "tool", "operation": operation, "toolCallId": call_ids[index], "error": str(error)})
                    continue
                result_size = _canonical_bytes(result)
                result_hash = hashlib.sha256(canonical_text(result).encode("utf-8")).hexdigest()
                trace = {"operationIndex": operation_index, "operationType": operation, "targetOrQuery": arguments, "repositoryRevision": base["repositoryRevision"], "executionStatus": "EXECUTED_SUCCESS", "resultIdentity": result_hash, "resultByteCount": result_size, "resultSha256": result_hash, "durationMs": round((time.perf_counter() - started) * 1000), "delivered": True, "result": result}
                tool_trace.append(trace)
                resources.record_tool(operation, valid=True, executed=True, result_bytes=result_size, delivered_bytes=result_size)
                conversation.append({"role": "tool", "operation": operation, "toolCallId": call_ids[index], "content": result})
                if result.get("reference") and result.get("content") is not None:
                    content = str(result["content"])
                    evidence.append({"reference": result["reference"], "content": content, "contentSha256": hashlib.sha256(content.encode("utf-8")).hexdigest(), "authorized": True, "resolved": True})
            if stop_after_turn:
                break
        raw = {"responses": responses, "conversation": conversation, "toolTrace": tool_trace, "evidence": evidence}
        if final is not None:
            raw["finalResponse"] = final
            evaluation = evaluate_answer(final, assignment, evidence)
            if evaluation["structural"]["valid"] is False:
                status = ExecutionStatus.MODEL_FAILURE
                resources.stop("MODEL_STRUCTURAL_FAILURE")
            return self._observation(assignment, raw, resources, status, evaluation, provider_failure=provider_failure)
        return self._observation(assignment, raw, resources, status, not_evaluated(), provider_failure=provider_failure)

    def run_assignment(self, assignment: Any) -> dict[str, Any]:
        self.ceilings.require_approved()
        self._started = time.perf_counter()
        v4_assignment = self._assignment(assignment)
        return self._run_devlog(v4_assignment) if v4_assignment.condition == "DEVLOG" else self._run_direct(v4_assignment)


def canonical_text(value: Any) -> str:
    import json

    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


class V4CollectionOrchestrator:
    """Offline-capable scheduler/ledger boundary; it never selects ceilings."""

    def __init__(self, runtime: V4CollectionRuntime, *, run_id: str, ledger_path: str | Path | None = None, output_dir: str | Path | None = None, artifact_writer: Any | None = None):
        self.runtime = runtime
        self.run_id = run_id
        self.ledger_path = Path(ledger_path) if ledger_path else None
        self.output_dir = Path(output_dir) if output_dir else None
        self.artifact_writer = artifact_writer

    def _load_ledger(self) -> dict[str, Any]:
        if not self.ledger_path or not self.ledger_path.exists():
            return {"runId": self.run_id, "finalized": {}}
        ledger = json.loads(self.ledger_path.read_text(encoding="utf-8"))
        if ledger.get("runId") != self.run_id:
            raise RuntimeContractError("V4 run ledger identity mismatch")
        return ledger

    def _save_ledger(self, ledger: dict[str, Any]) -> None:
        if self.ledger_path:
            self.ledger_path.parent.mkdir(parents=True, exist_ok=True)
            self.ledger_path.write_text(canonical_text(ledger) + "\n", encoding="utf-8")

    def run(self, rows: list[dict[str, Any]] | None = None) -> list[dict[str, Any]]:
        rows = rows or v4_assignment_matrix()
        results: list[dict[str, Any]] = []
        ledger = self._load_ledger()
        planned = [row["assignmentId"] for row in rows]
        from .protocol import validate_v4_plan
        validate_v4_plan(rows)
        previous_plan = ledger.get("plannedAssignmentIds")
        if previous_plan is not None and previous_plan != planned:
            raise RuntimeContractError("V4 run ledger plan differs on resume")
        ledger["plannedAssignmentIds"] = planned
        for row in rows:
            if row["assignmentId"] in ledger["finalized"]:
                continue
            observation = self.runtime.run_assignment(row)
            results.append(observation)
            if self.artifact_writer:
                self.artifact_writer(observation)
            elif self.output_dir:
                destination = self.output_dir / f"{observation['assignment']['assignmentId']}.json"
                write_observation(destination, observation)
                write_derived_projection(self.output_dir / "derived" / f"{observation['assignment']['assignmentId']}.json", observation)
            ledger["finalized"][row["assignmentId"]] = {
                "assignmentId": row["assignmentId"],
                "observationId": observation["observationId"],
                "executionStatus": observation["executionStatus"],
                "condition": row["condition"],
                "rawOutputSha256": observation["rawOutputSha256"],
            }
            self._save_ledger(ledger)
        ledger["summary"] = self.summary(list(ledger.get("finalized", {}).values()), planned_assignment_ids=planned)
        ledger["plannedAssignmentCount"] = len(rows)
        ledger["completedAssignmentCount"] = len(ledger["finalized"])
        self._save_ledger(ledger)
        return results

    @staticmethod
    def summary(observations: list[dict[str, Any]], *, planned_assignment_ids: list[str] | None = None) -> dict[str, Any]:
        by_status: dict[str, int] = {}
        by_condition: dict[str, int] = {}
        observation_ids: set[str] = set()
        duplicate_observation_ids: list[str] = []
        assignment_ids: set[str] = set()
        for observation in observations:
            by_status[observation["executionStatus"]] = by_status.get(observation["executionStatus"], 0) + 1
            condition = observation.get("assignment", {}).get("condition", observation.get("condition", "UNKNOWN"))
            by_condition[condition] = by_condition.get(condition, 0) + 1
            assignment_id = observation.get("assignment", {}).get("assignmentId", observation.get("assignmentId"))
            if assignment_id is not None:
                assignment_ids.add(assignment_id)
            observation_id = observation.get("observationId")
            if observation_id in observation_ids:
                duplicate_observation_ids.append(observation_id)
            observation_ids.add(observation_id)
        planned = set(planned_assignment_ids or [])
        return {
            "assignmentCount": len(observations),
            "byExecutionStatus": by_status,
            "byCondition": by_condition,
            "plannedAssignmentCount": len(planned),
            "missingAssignmentIds": sorted(planned - assignment_ids) if planned_assignment_ids is not None else [],
            "unexpectedAssignmentIds": sorted(assignment_ids - planned) if planned_assignment_ids is not None else [],
            "duplicateObservationIds": sorted(set(duplicate_observation_ids)),
            "complete": planned_assignment_ids is not None and planned == assignment_ids and not duplicate_observation_ids,
        }
