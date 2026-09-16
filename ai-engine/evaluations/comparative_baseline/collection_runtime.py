"""Offline-testable collection runtime for the frozen Story0133 baseline.

This module only provides evaluation-specific orchestration and contracts. It
does not construct a provider client, access the network, execute shell
commands, or collect an official observation by itself.
"""

from __future__ import annotations

import hashlib
import json
import random
import re
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Literal, Protocol
from uuid import uuid4

from .infrastructure import (
    CONDITIONS,
    ERROR_TAXONOMY,
    EXECUTABLE_CONDITIONS,
    MISSING_STATES,
    assignment_matrix,
    assert_no_oracle_leakage,
    canonical,
    load_manifest,
    load_policies,
    raw_output_hash,
    validate_raw_observation,
    write_immutable_json,
)


FINAL_OUTPUT_TOKENS = 1800
AGENT_INTERMEDIATE_OUTPUT_TOKENS = 512
MAX_TOOL_OPERATIONS = 6
MAX_MODEL_TURNS = 8
MAX_TECHNICAL_RETRIES = 1
MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION = 2
MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION = 9
MAX_PROVIDER_CALLS_FULL_BASELINE = 99
REPOSITORY_REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"
REPOSITORY_BYTE_BUDGETS = {
    "CASE-01-COMPARATIVE": 32401,
    "CASE-03": 31683,
    "CASE-04": 13504,
}
ORDER_STRATEGY = "STORY0133_SEEDED_CONSTRAINED_SHUFFLE_V1"
PAIRING_POLICY = "story0133-ai-pairing-policy-1.0.0"
FINAL_SCHEMA = "story0134-common-answer-1.0.0"
TOOL_SCHEMA = "story0134-agent-direct-tools-1.0.0"
COMPARATIVE_GROUNDING_CONTRACT_VERSION = "story0135-comparative-grounding-1.0.0"
COMPARATIVE_SCORING_PROJECTION_VERSION = "story0135-comparative-scoring-projection-1.0.0"
ALLOWED_TOOL_OPERATIONS = (
    "read_file", "search_repository", "git_log", "git_show", "git_diff", "inspect_commit",
)

_SECRET_KEY = re.compile(
    r"(?i)^(authorization|api[_-]?key|password|secret|token|credential|cookie|access[_-]?token)$"
)
_SECRET_VALUE = re.compile(r"(?i)(bearer\s+\S+|sk-[a-z0-9_-]{16,})")
_ORACLE_MARKERS = (
    "expectedClassification", "expectedEvidence", "expectedOutcome", "oracle",
    "STRONGLY_SUPPORTED", "EXPLICITLY_DOCUMENTED", "NOT_ESTABLISHED",
    "DIRECT_REQUIRED", "SUPPORTING_ALLOWED", "CL-01", "CL-09",
)


class RuntimeContractError(ValueError):
    """Raised when an evaluation runtime contract is violated."""


class TransportFailure(RuntimeError):
    """A provider failure before a response-bearing output exists."""


class SystemicInfrastructureFailure(RuntimeError):
    """A run-level failure that must stop later assignments."""


class BudgetExhausted(RuntimeError):
    """Raised when a slot would exceed its frozen runtime budget."""


def sha256_value(value: Any) -> str:
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def copy_json(value: Any) -> Any:
    return json.loads(canonical(value))


def assert_secret_free(value: Any, *, path: str = "root") -> None:
    """Reject secrets instead of attempting lossy redaction at persistence time."""

    if isinstance(value, dict):
        for key, child in value.items():
            if _SECRET_KEY.match(str(key)):
                raise RuntimeContractError(f"secret-like field refused at {path}.{key}")
            assert_secret_free(child, path=f"{path}.{key}")
    elif isinstance(value, (list, tuple)):
        for index, child in enumerate(value):
            assert_secret_free(child, path=f"{path}[{index}]")
    elif isinstance(value, str) and _SECRET_VALUE.search(value):
        raise RuntimeContractError(f"secret-like value refused at {path}")


def assert_official_collection_eligible(observation: dict[str, Any]) -> None:
    if observation.get("executionMode") != "AUTHORIZED_COLLECTION":
        raise RuntimeContractError("offline dry-run artifact cannot enter official baseline")


@dataclass(frozen=True)
class RuntimeConfiguration:
    provider: str = "fixture-provider"
    model: str = "fixture-model"
    model_version: str = "NOT_EXPOSED"
    temperature: str | float = "PROVIDER_DEFAULT"
    top_p: str | float = "PROVIDER_DEFAULT"
    seed: str | int = "NOT_CONFIGURED"
    reasoning: str = "NOT_CONFIGURED"
    timeout_seconds: int = 90
    sdk_version: str = "NOT_CONFIGURED"
    api_mode: str = "responses.create"
    final_output_tokens: int = FINAL_OUTPUT_TOKENS
    intermediate_output_tokens: int = AGENT_INTERMEDIATE_OUTPUT_TOKENS
    max_tool_operations: int = MAX_TOOL_OPERATIONS
    max_model_turns: int = MAX_MODEL_TURNS
    max_technical_retries: int = MAX_TECHNICAL_RETRIES
    sdk_max_retries: int = 0
    adapter_max_retries: int = 0
    semantic_retry: str = "DISABLED"
    final_schema: str = FINAL_SCHEMA
    tool_schema: str = TOOL_SCHEMA

    def __post_init__(self) -> None:
        if self.final_output_tokens != FINAL_OUTPUT_TOKENS:
            raise RuntimeContractError("final output envelope is frozen at 1800")
        if self.intermediate_output_tokens != AGENT_INTERMEDIATE_OUTPUT_TOKENS:
            raise RuntimeContractError("intermediate output envelope is frozen at 512")
        if self.max_tool_operations != MAX_TOOL_OPERATIONS or self.max_model_turns != MAX_MODEL_TURNS:
            raise RuntimeContractError("agent tool-loop limits are frozen")
        if self.max_technical_retries != MAX_TECHNICAL_RETRIES:
            raise RuntimeContractError("technical retry limit is frozen at one")
        if self.sdk_max_retries or self.adapter_max_retries or self.semantic_retry != "DISABLED":
            raise RuntimeContractError("provider retry policy is not the frozen technical-only policy")

    @property
    def model_configuration(self) -> dict[str, Any]:
        result = {
            "provider": self.provider,
            "model": self.model,
            "modelVersion": self.model_version,
            "temperature": self.temperature,
            "topP": self.top_p,
            "seed": self.seed,
            "reasoning": self.reasoning,
            "timeoutSeconds": self.timeout_seconds,
            "sdkVersion": self.sdk_version,
            "apiMode": self.api_mode,
            "finalOutputTokens": self.final_output_tokens,
            "intermediateOutputTokens": self.intermediate_output_tokens,
            "maxToolOperations": self.max_tool_operations,
            "maxModelTurns": self.max_model_turns,
            "maxTechnicalRetries": self.max_technical_retries,
            "sdkMaxRetries": self.sdk_max_retries,
            "adapterMaxRetries": self.adapter_max_retries,
            "semanticRetry": self.semantic_retry,
            "finalSchema": self.final_schema,
            "toolSchema": self.tool_schema,
        }
        assert_secret_free(result)
        return result


@dataclass(frozen=True)
class EvidenceItem:
    reference: str
    content: str
    source_type: str = "DOCUMENT"
    locator_contract_version: str = "story0134-locator-1.0.0"
    provider_visible_source_identity: str = "NOT_CONFIGURED"

    @property
    def byte_length(self) -> int:
        return len(self.content.encode("utf-8"))

    @property
    def sha256(self) -> str:
        return hashlib.sha256(self.content.encode("utf-8")).hexdigest()

    def as_provider_item(self) -> dict[str, Any]:
        return {
            "reference": self.reference,
            "sourceType": self.source_type,
            "content": self.content,
            "contentEncoding": "UTF-8",
            "contentByteLength": self.byte_length,
            "contentSha256": self.sha256,
            "locatorContractVersion": self.locator_contract_version,
            "providerVisibleSourceIdentity": self.provider_visible_source_identity,
        }


@dataclass(frozen=True)
class DevlogContext:
    question_id: str
    question_version: str
    context_strategy: str
    context_digest: str
    evidence: tuple[EvidenceItem, ...]
    prompt_system: str
    prompt_user: str
    context_revision: str = "NOT_CONFIGURED"
    projection_revision: str = "NOT_CONFIGURED"
    preparation_cost: dict[str, Any] = field(default_factory=lambda: {"status": "NOT_MEASURED"})

    @property
    def evidence_bytes(self) -> int:
        return sum(item.byte_length for item in self.evidence)

    def as_input(self) -> dict[str, Any]:
        payload = {
            "condition": "DEVLOG",
            "questionId": self.question_id,
            "questionVersion": self.question_version,
            "contextStrategy": self.context_strategy,
            "contextRevision": self.context_revision,
            "projectionRevision": self.projection_revision,
            "contextDigest": self.context_digest,
            "providerVisibleEvidence": [item.as_provider_item() for item in self.evidence],
            "providerVisibleEvidenceItems": len(self.evidence),
            "providerVisibleEvidenceBytes": self.evidence_bytes,
            "prompt": {"system": self.prompt_system, "user": self.prompt_user},
        }
        assert_no_contamination(payload, condition="DEVLOG")
        assert_secret_free(payload)
        return payload


@dataclass(frozen=True)
class Assignment:
    assignment_id: str
    question_id: str
    question_version: str
    case_id: str
    condition: Literal["DEVLOG", "AGENT_DIRECT"]
    repetition: int

    @classmethod
    def from_manifest_row(cls, row: dict[str, Any]) -> "Assignment":
        if row["condition"] not in EXECUTABLE_CONDITIONS:
            raise RuntimeContractError("assignment condition is not executable")
        return cls(
            assignment_id=row["assignmentId"],
            question_id=row["questionId"],
            question_version=row["questionVersion"],
            case_id=row["caseId"],
            condition=row["condition"],
            repetition=row["repetition"],
        )

    def identity(self) -> dict[str, Any]:
        return {
            "assignmentId": self.assignment_id,
            "questionId": self.question_id,
            "questionVersion": self.question_version,
            "caseId": self.case_id,
            "condition": self.condition,
            "repetition": self.repetition,
        }


@dataclass(frozen=True)
class ProviderRequest:
    assignment: Assignment
    mode: Literal["DEVLOG", "AGENT_DIRECT"]
    turn_index: int
    input_payload: dict[str, Any]
    tool_schema: dict[str, Any] | None
    max_output_tokens: int


@dataclass(frozen=True)
class ProviderResponse:
    kind: Literal["TOOL_CALL", "FINAL"]
    payload: dict[str, Any]
    raw_response: dict[str, Any]
    usage: dict[str, Any] = field(default_factory=dict)
    finish_reason: str = "COMPLETED"


class ProviderTransport(Protocol):
    def complete(self, request: ProviderRequest) -> ProviderResponse:
        ...


class GroundingAuthority(Protocol):
    def validate(self, *, answer: dict[str, Any], evidence: list[EvidenceItem], assignment: Assignment) -> dict[str, Any]:
        ...


class RepositoryToolServer(Protocol):
    def execute(self, operation: str, arguments: dict[str, Any]) -> dict[str, Any]:
        ...


@dataclass
class ToolBudget:
    question_id: str
    max_operations: int = MAX_TOOL_OPERATIONS
    max_bytes: int = 0
    operations: int = 0
    bytes_returned: int = 0

    def attempt(self) -> int:
        if self.operations >= self.max_operations:
            raise BudgetExhausted("maximum tool operations exhausted")
        self.operations += 1
        return self.operations

    def reserve(self, result: dict[str, Any]) -> None:
        result_bytes = len(canonical(result).encode("utf-8"))
        if self.bytes_returned + result_bytes > self.max_bytes:
            raise BudgetExhausted("question-specific repository byte budget exhausted")
        self.bytes_returned += result_bytes


class InMemoryRepositoryTools:
    """Deterministic test/tool implementation with the production allowlist."""

    ALLOWED = {"read_file", "search_repository", "git_log", "git_show", "git_diff", "inspect_commit"}

    def __init__(self, *, revision: str, files: dict[str, str], commits: dict[str, dict[str, Any]] | None = None):
        self.revision = revision
        self.files = dict(sorted(files.items()))
        self.commits = commits or {}

    def execute(self, operation: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if operation not in self.ALLOWED:
            raise RuntimeContractError(f"unsupported repository operation: {operation}")
        if arguments.get("repositoryRevision", self.revision) != self.revision:
            raise RuntimeContractError("repository revision mismatch")
        if any(token in str(arguments) for token in ("..", "|", ";", "$", "`")):
            raise RuntimeContractError("unsafe repository argument")
        if operation == "read_file":
            path = arguments["path"]
            if path not in self.files:
                raise FileNotFoundError(path)
            lines = self.files[path].splitlines()
            start = int(arguments.get("startLine", 1))
            end = int(arguments.get("endLine", len(lines)))
            return {"operation": operation, "reference": path, "path": path, "startLine": start, "endLine": end, "content": "\n".join(lines[start - 1:end])}
        if operation == "search_repository":
            query = arguments["query"]
            matches = []
            for path, content in self.files.items():
                for line_number, line in enumerate(content.splitlines(), 1):
                    if query.casefold() in line.casefold():
                        matches.append({"path": path, "line": line_number, "text": line})
            return {"operation": operation, "query": query, "matches": matches[: int(arguments.get("maxMatches", 20))]}
        commit = arguments["commit"]
        if commit not in self.commits:
            raise FileNotFoundError(commit)
        record = self.commits[commit]
        if operation == "git_log":
            return {"operation": operation, "commits": record.get("log", [])}
        if operation == "git_show":
            path = arguments.get("path")
            return {"operation": operation, "commit": commit, "path": path, "content": record.get("files", {}).get(path, record.get("metadata", {}))}
        if operation == "git_diff":
            return {"operation": operation, "commit": commit, "parent": arguments.get("parent"), "diff": record.get("diff", "")}
        return {"operation": operation, "commit": commit, "metadata": record.get("metadata", {})}


def canonical_tool_result(operation: str, arguments: dict[str, Any], result: dict[str, Any], *, revision: str, duration_ms: int = 0) -> dict[str, Any]:
    safe_result = {
        "operationIndex": None,
        "operationType": operation,
        "targetOrQuery": arguments,
        "repositoryRevision": revision,
        "resultIdentity": sha256_value(result),
        "resultByteCount": len(canonical(result).encode("utf-8")),
        "resultSha256": sha256_value(result),
        "durationMs": duration_ms,
        "truncated": False,
        "errorState": None,
        "result": result,
    }
    assert_secret_free(safe_result)
    return safe_result


def validate_locator(locator: dict[str, Any]) -> None:
    kind = locator.get("kind")
    if kind == "LINE_RANGE":
        if not isinstance(locator.get("startLine"), int) or not isinstance(locator.get("endLine"), int) or locator["startLine"] > locator["endLine"]:
            raise RuntimeContractError("invalid line-range locator")
    elif kind == "SECTION":
        if not isinstance(locator.get("heading"), str) or not locator["heading"]:
            raise RuntimeContractError("invalid section locator")
    elif kind == "COMMIT_HUNK":
        if not locator.get("commit") or not locator.get("path") or not locator.get("header"):
            raise RuntimeContractError("invalid commit-hunk locator")
    else:
        raise RuntimeContractError("unsupported evidence locator")


def grounding_error(error: str | None) -> str:
    if error in ERROR_TAXONOMY:
        return error
    if error and "UNAUTHORIZED" in error:
        return "UNAUTHORIZED_REFERENCE"
    return "EVIDENCE_EXCERPT_MISMATCH"


def record_grounding_metadata(observation: dict[str, Any], grounding: dict[str, Any]) -> None:
    passed = grounding.get("status") == "PASS"
    observation["groundingValid"] = "YES" if passed else "NO"
    observation["groundingStatus"] = grounding.get("status", "FAIL")
    observation["groundingDiagnostics"] = grounding.get("diagnostic", grounding.get("error"))
    observation["canonicalEvidenceIdentity"] = [
        item.get("canonicalEvidenceIdentity")
        for resolution in grounding.get("resolutions", [])
        for item in [resolution]
        if item.get("canonicalEvidenceIdentity") is not None
    ]
    observation["resolvedContentDigest"] = [
        resolution.get("resolvedContentDigest")
        for resolution in grounding.get("resolutions", [])
        if resolution.get("resolvedContentDigest") is not None
    ]
    observation["resolvedExcerptMatchMetadata"] = [
        resolution.get("excerptMatchOffsets", [])
        for resolution in grounding.get("resolutions", [])
        if "excerptMatchOffsets" in resolution
    ]


def validate_answer(answer: dict[str, Any], assignment: Assignment) -> None:
    required = {"questionId", "questionVersion", "answerText", "relationshipResult", "abstention", "claims", "evidence", "confidence"}
    if set(answer) != required:
        raise RuntimeContractError("common answer contract fields mismatch")
    if answer["questionId"] != assignment.question_id or answer["questionVersion"] != assignment.question_version:
        raise RuntimeContractError("answer question identity mismatch")
    if not isinstance(answer["answerText"], str) or not 1 <= len(answer["answerText"]) <= 3000:
        raise RuntimeContractError("answer text length is outside the common contract")
    if answer["relationshipResult"] not in {"ESTABLISHED", "NOT_ESTABLISHED", "NOT_APPLICABLE"}:
        raise RuntimeContractError("invalid relationship result")
    if not isinstance(answer["abstention"], bool) or answer["confidence"] not in {"HIGH", "MEDIUM", "LOW"}:
        raise RuntimeContractError("invalid abstention or confidence")
    claims = answer["claims"]
    if not isinstance(claims, list) or len(claims) > 4:
        raise RuntimeContractError("claim count is outside the common contract")
    for claim in claims:
        if set(claim) != {"text", "claimType", "references"} or not isinstance(claim["text"], str) or len(claim["text"]) > 600:
            raise RuntimeContractError("invalid claim")
        if claim["claimType"] not in {"FACT", "INTERPRETATION"} or not isinstance(claim["references"], list):
            raise RuntimeContractError("invalid claim type or references")
    evidence = answer["evidence"]
    if not isinstance(evidence, list) or len(evidence) > 6:
        raise RuntimeContractError("evidence count is outside the common contract")
    for item in evidence:
        if set(item) != {"reference", "locator", "excerpt", "role"} or not isinstance(item["reference"], str):
            raise RuntimeContractError("invalid evidence assertion")
        validate_locator(item["locator"])
        if not isinstance(item["excerpt"], str) or len(item["excerpt"]) > 400 or item["role"] not in {"DIRECT", "SUPPORTING"}:
            raise RuntimeContractError("invalid evidence excerpt or role")
    assert_secret_free(answer)


def assert_no_contamination(value: Any, *, condition: str) -> None:
    serialized = canonical(value)
    for marker in _ORACLE_MARKERS:
        if marker in serialized:
            raise RuntimeContractError(f"forbidden oracle/evaluator marker: {marker}")
    if condition == "DEVLOG" and "AGENT_DIRECT" in serialized:
        raise RuntimeContractError("DEVLOG input contains AGENT_DIRECT data")
    if condition == "AGENT_DIRECT" and "DEVLOG" in serialized:
        raise RuntimeContractError("AGENT_DIRECT input contains DEVLOG data")


class ObservationStateMachine:
    _next = {
        "ASSIGNED": {"RUNNING"},
        "RUNNING": {"RAW_CAPTURED"},
        "RAW_CAPTURED": {"STRUCTURALLY_VALIDATED", "FINALIZED"},
        "STRUCTURALLY_VALIDATED": {"GROUNDING_VALIDATED", "FINALIZED"},
        "GROUNDING_VALIDATED": {"SEMANTICALLY_ELIGIBLE", "FINALIZED"},
        "SEMANTICALLY_ELIGIBLE": {"FINALIZED"},
    }

    def __init__(self) -> None:
        self.state = "ASSIGNED"
        self.history = [self.state]

    def transition(self, target: str) -> None:
        if target not in self._next.get(self.state, set()):
            raise RuntimeContractError(f"illegal observation transition {self.state}->{target}")
        self.state = target
        self.history.append(target)


class RunLedger:
    """Restart-safe assignment ledger; raw observation files remain write-once."""

    def __init__(self, *, run_id: str, path: str | Path | None = None):
        self.run_id = run_id
        self.path = Path(path) if path else None
        self.data = self._load()

    def _load(self) -> dict[str, Any]:
        if self.path is None or not self.path.exists():
            return {"runId": self.run_id, "assignments": {}, "order": []}
        loaded = json.loads(self.path.read_text(encoding="utf-8"))
        if loaded.get("runId") != self.run_id:
            raise RuntimeContractError("run ledger identity mismatch")
        return loaded

    def _persist(self) -> None:
        if self.path is None:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(self.data, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")

    def register_plan(self, assignment_ids: list[str]) -> None:
        if len(assignment_ids) != len(set(assignment_ids)):
            raise RuntimeContractError("run plan contains duplicate assignments")
        existing = self.data.get("order", [])
        if existing and existing != assignment_ids:
            raise RuntimeContractError("run plan differs on resume")
        self.data["order"] = list(assignment_ids)
        for assignment_id in assignment_ids:
            self.data["assignments"].setdefault(assignment_id, {"status": "ASSIGNED"})
        self._persist()

    def record_partial(self, assignment_id: str, state: str, trace_count: int, tool_trace: list[dict[str, Any]] | None = None) -> None:
        entry = self.data["assignments"].setdefault(assignment_id, {})
        if entry.get("status") == "FINALIZED":
            raise RuntimeContractError("cannot replace finalized assignment")
        entry.update({"status": "PARTIAL", "state": state, "traceCount": trace_count})
        if tool_trace is not None:
            assert_secret_free(tool_trace)
            entry["partialToolTrace"] = copy_json(tool_trace)
        self._persist()

    def record_finalized(self, observation: dict[str, Any]) -> None:
        assignment_id = observation["assignment"]["assignmentId"]
        entry = self.data["assignments"].setdefault(assignment_id, {})
        artifact_hash = sha256_value(observation)
        if entry.get("status") == "FINALIZED":
            if entry.get("artifactSha256") != artifact_hash:
                raise RuntimeContractError("finalized assignment artifact mismatch")
            raise FileExistsError(f"assignment already finalized: {assignment_id}")
        entry.update({"status": "FINALIZED", "observationId": observation["observationId"], "artifactSha256": artifact_hash})
        self._persist()

    def is_finalized(self, assignment_id: str) -> bool:
        return self.data["assignments"].get(assignment_id, {}).get("status") == "FINALIZED"


class AlwaysPassGrounding:
    """Offline fixture authority; production collection must inject the Core bridge."""

    def validate(self, *, answer: dict[str, Any], evidence: list[EvidenceItem], assignment: Assignment) -> dict[str, Any]:
        authorized = {item.reference for item in evidence}
        references = {item["reference"] for item in answer["evidence"]}
        if not references.issubset(authorized):
            return {"status": "FAIL", "error": "UNAUTHORIZED_REFERENCE"}
        return {"status": "PASS", "authority": "FIXTURE_ONLY", "resolvedReferences": sorted(references)}


def execution_order(seed: int, manifest: dict[str, Any] | None = None) -> dict[str, Any]:
    rows = assignment_matrix(manifest or load_manifest())
    randomizer = random.Random(seed)
    randomizer.shuffle(rows)
    return {"strategy": ORDER_STRATEGY, "seed": seed, "assignments": [row["assignmentId"] for row in rows]}


class CollectionRuntime:
    def __init__(
        self,
        transport: ProviderTransport,
        *,
        configuration: RuntimeConfiguration | None = None,
        manifest: dict[str, Any] | None = None,
        grounding: GroundingAuthority | None = None,
        devlog_contexts: dict[str, DevlogContext] | None = None,
        tool_factory: Any | None = None,
        run_id: str | None = None,
        execution_class: str = "OFFLINE_DRY_RUN",
        baseline_eligible: bool | None = None,
        artifact_writer: Callable[[dict[str, Any]], Any] | None = None,
    ):
        self.manifest = manifest or load_manifest()
        self.configuration = configuration or RuntimeConfiguration()
        self.transport = transport
        self.grounding = grounding or AlwaysPassGrounding()
        self.devlog_contexts = devlog_contexts or {}
        self.tool_factory = tool_factory
        self.run_id = run_id or str(uuid4())
        self.execution_class = execution_class
        self.baseline_eligible = baseline_eligible
        self.artifact_writer = artifact_writer
        self.provider_calls = 0
        self.logical_model_calls = 0
        self.technical_retry_calls = 0
        self._preflight()

    def _preflight(self) -> None:
        if self.manifest["repositoryRevision"] != REPOSITORY_REVISION:
            raise RuntimeContractError("Story0133 repository revision mismatch")
        load_policies()
        if MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION * 9 + MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION * 9 != MAX_PROVIDER_CALLS_FULL_BASELINE:
            raise RuntimeContractError("full-baseline provider-call envelope is inconsistent")
        assert_secret_free(self.configuration.model_configuration)

    def ordered_assignments(self, seed: int) -> dict[str, Any]:
        return execution_order(seed, self.manifest)

    def _base_observation(self, assignment: Assignment, exact_input: dict[str, Any]) -> dict[str, Any]:
        question = next(item for item in self.manifest["questions"] if item["questionId"] == assignment.question_id)
        observation = {
            "observationId": f"{self.run_id}:{assignment.assignment_id}",
            "benchmarkVersion": self.manifest["benchmarkVersion"],
            "questionId": assignment.question_id,
            "questionVersion": assignment.question_version,
            "caseId": assignment.case_id,
            "condition": assignment.condition,
            "conditionPolicyVersion": self.manifest["conditionPolicyVersions"][assignment.condition],
            "conditionPolicyCompatibilityKey": PAIRING_POLICY,
            "repetition": assignment.repetition,
            "repositoryId": self.manifest["repositoryId"],
            "repositoryRevision": self.manifest["repositoryRevision"],
            "oracleVersion": self.manifest["oracleVersion"],
            "scoringContractVersion": self.manifest["scoringContractVersion"],
            "model": self.configuration.model,
            "modelConfiguration": self.configuration.model_configuration,
            "executionStatus": "EXECUTED",
            "semanticOutcome": "NOT_EVALUATED",
            "primaryError": "NOT_EVALUATED",
            "exactInput": exact_input,
            "rawOutput": "NOT_APPLICABLE",
            "rawOutputSha256": "NOT_APPLICABLE",
            "validationResults": {},
            "runId": self.run_id,
            "artifactReference": f"{assignment.assignment_id}.json",
            "runtime": {"configuration": self.configuration.model_configuration},
            "assignment": assignment.identity(),
            "logicalModelCalls": 0,
            "technicalRetryCalls": 0,
            "toolOperations": 0,
            "toolAttempts": 0,
            "toolSuccessfulOperations": 0,
            "toolFailedOperations": 0,
            "toolNotExecutedOperations": 0,
            "finalAnswerCount": 0,
            "providerCalls": 0,
            "inputTokens": "NOT_MEASURED",
            "outputTokens": "NOT_MEASURED",
            "providerCost": "NOT_AVAILABLE",
            "latencyMs": "NOT_MEASURED",
            "visibleContextBytes": 0,
            "visibleContextItems": 0,
            "repositoryBytesInspected": 0,
            "repositoryOperations": 0,
            "stateHistory": [],
            "capturedAt": time.time(),
            "executionMode": "LIVE_PILOT" if self.execution_class == "LIVE_PILOT" else "OFFLINE_DRY_RUN",
            "questionText": question["question"],
            "structuralValid": "NOT_EVALUATED",
            "groundingValid": "NOT_EVALUATED",
            "semanticCorrect": "NOT_EVALUATED",
            "semanticEligible": False,
            "groundingStatus": "NOT_EVALUATED",
            "groundingDiagnostics": None,
            "canonicalEvidenceIdentity": [],
            "resolvedContentDigest": [],
            "resolvedExcerptMatchMetadata": [],
            "layerSpecificErrorClassification": "NOT_EVALUATED",
            "comparativeGroundingContractVersion": COMPARATIVE_GROUNDING_CONTRACT_VERSION,
            "comparativeScoringProjectionVersion": COMPARATIVE_SCORING_PROJECTION_VERSION,
        }
        if self.execution_class != "OFFLINE_DRY_RUN":
            observation["executionClass"] = self.execution_class
        if self.baseline_eligible is not None:
            observation["baselineEligible"] = self.baseline_eligible
        return observation

    def _finalize(self, observation: dict[str, Any], state: ObservationStateMachine) -> dict[str, Any]:
        observation["stateHistory"] = state.history
        if observation["rawOutput"] != "NOT_APPLICABLE":
            observation["rawOutputSha256"] = raw_output_hash(observation["rawOutput"])
        assert_secret_free(observation)
        validate_raw_observation(
            observation,
            manifest=self.manifest,
            enforce_official_baseline=self.baseline_eligible is not False,
        )
        if self.artifact_writer is not None:
            self.artifact_writer(observation)
        return observation

    def _call(self, request: ProviderRequest, *, response_seen: bool, observation: dict[str, Any], captures: list[dict[str, Any]]) -> ProviderResponse:
        max_calls = MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION if request.mode == "DEVLOG" else MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION
        if observation["providerCalls"] >= max_calls or self.provider_calls >= MAX_PROVIDER_CALLS_FULL_BASELINE:
            raise BudgetExhausted("provider-call budget exhausted")
        started_at = time.perf_counter()
        try:
            self.provider_calls += 1
            observation["providerCalls"] += 1
            response = self.transport.complete(request)
        except TransportFailure:
            if response_seen or observation["technicalRetryCalls"] >= MAX_TECHNICAL_RETRIES:
                raise
            observation["technicalRetryCalls"] += 1
            self.technical_retry_calls += 1
            self.provider_calls += 1
            observation["providerCalls"] += 1
            response = self.transport.complete(request)
        observation["latencyMs"] = (
            0 if observation["latencyMs"] == "NOT_MEASURED" else observation["latencyMs"]
        ) + round((time.perf_counter() - started_at) * 1000)
        self.logical_model_calls += 1
        observation["logicalModelCalls"] += 1
        for usage_key, observation_key in (("inputTokens", "inputTokens"), ("outputTokens", "outputTokens")):
            usage_value = response.usage.get(usage_key)
            if isinstance(usage_value, int):
                current = observation[observation_key]
                observation[observation_key] = usage_value if not isinstance(current, int) else current + usage_value
        capture = {
            "turn": request.turn_index,
            "mode": request.mode,
            "kind": response.kind,
            "responseId": response.raw_response.get("id"),
            "rawResponse": response.raw_response,
            "usage": response.usage,
            "finishReason": response.finish_reason,
        }
        assert_secret_free(capture)
        captures.append(capture)
        return response

    def run_assignment(self, row: dict[str, Any]) -> dict[str, Any]:
        assignment = Assignment.from_manifest_row(row)
        state = ObservationStateMachine()
        state.transition("RUNNING")
        if assignment.condition == "DEVLOG":
            return self._run_devlog(assignment, state)
        return self._run_agent_direct(assignment, state)

    def run_assignments(self, rows: list[dict[str, Any]], *, ledger: RunLedger | None = None) -> list[dict[str, Any]]:
        """Run supplied assignments independently, continuing after slot failures."""

        assignment_ids = [row["assignmentId"] for row in rows]
        if len(assignment_ids) != len(set(assignment_ids)):
            raise RuntimeContractError("duplicate assignment in execution plan")
        if ledger is not None:
            ledger.register_plan(assignment_ids)
        results = []
        for row in rows:
            if self.provider_calls >= MAX_PROVIDER_CALLS_FULL_BASELINE:
                break
            if ledger is not None and ledger.is_finalized(row["assignmentId"]):
                continue
            try:
                result = self.run_assignment(row)
            except SystemicInfrastructureFailure:
                break
            results.append(result)
            if ledger is not None:
                ledger.record_finalized(result)
        return results

    def _run_devlog(self, assignment: Assignment, state: ObservationStateMachine) -> dict[str, Any]:
        context = self.devlog_contexts.get(assignment.question_id)
        if context is None or context.question_version != assignment.question_version:
            raise RuntimeContractError("DEVLOG context identity is unavailable")
        if context.evidence_bytes > REPOSITORY_BYTE_BUDGETS[assignment.question_id]:
            raise RuntimeContractError("DEVLOG context exceeds the frozen question budget")
        exact_input = context.as_input()
        observation = self._base_observation(assignment, exact_input)
        captures: list[dict[str, Any]] = []
        try:
            response = self._call(ProviderRequest(assignment, "DEVLOG", 1, exact_input, None, FINAL_OUTPUT_TOKENS), response_seen=False, observation=observation, captures=captures)
            observation["rawOutput"] = {"responses": captures, "finalResponse": response.payload}
            observation["finalAnswerCount"] = 1
            observation["visibleContextBytes"] = context.evidence_bytes
            observation["visibleContextItems"] = len(context.evidence)
            if state.state == "RUNNING":
                state.transition("RAW_CAPTURED")
            validate_answer(response.payload, assignment)
            observation["validationResults"]["structural"] = "PASS"
            observation["structuralValid"] = "YES"
            state.transition("STRUCTURALLY_VALIDATED")
            grounding = self.grounding.validate(answer=response.payload, evidence=list(context.evidence), assignment=assignment)
            observation["validationResults"]["grounding"] = grounding
            record_grounding_metadata(observation, grounding)
            state.transition("GROUNDING_VALIDATED")
            if grounding.get("status") == "PASS":
                state.transition("SEMANTICALLY_ELIGIBLE")
                observation["semanticEligible"] = True
                observation["layerSpecificErrorClassification"] = "NONE"
                observation["semanticOutcome"] = "YES"
            else:
                observation["primaryError"] = grounding_error(grounding.get("error"))
                observation["layerSpecificErrorClassification"] = observation["primaryError"]
        except (TransportFailure, BudgetExhausted, RuntimeContractError) as error:
            observation["executionStatus"] = "INVALID"
            observation["primaryError"] = "INFRASTRUCTURE_FAILURE" if isinstance(error, (TransportFailure, BudgetExhausted)) else "STRUCTURAL_FAILURE"
            observation["validationResults"]["error"] = str(error)
            if observation["structuralValid"] == "NOT_EVALUATED":
                observation["structuralValid"] = "NO"
            observation["layerSpecificErrorClassification"] = observation["primaryError"]
            if captures:
                observation["rawOutput"] = {"responses": captures}
            if state.state == "RUNNING":
                state.transition("RAW_CAPTURED")
        state.transition("FINALIZED")
        return self._finalize(observation, state)

    def _run_agent_direct(self, assignment: Assignment, state: ObservationStateMachine) -> dict[str, Any]:
        if self.tool_factory is None:
            raise RuntimeContractError("AGENT_DIRECT tool server factory is unavailable")
        question = next(item for item in self.manifest["questions"] if item["questionId"] == assignment.question_id)
        input_payload = {
            "condition": "AGENT_DIRECT",
            "questionId": assignment.question_id,
            "questionVersion": assignment.question_version,
            "caseId": assignment.case_id,
            "question": question["question"],
            "repositoryId": self.manifest["repositoryId"],
            "repositoryRevision": REPOSITORY_REVISION,
            "toolSchema": TOOL_SCHEMA,
            "allowedOperations": list(ALLOWED_TOOL_OPERATIONS),
            "repositoryByteBudget": REPOSITORY_BYTE_BUDGETS[assignment.question_id],
        }
        assert_no_contamination(input_payload, condition="AGENT_DIRECT")
        observation = self._base_observation(assignment, input_payload)
        captures: list[dict[str, Any]] = []
        tool_trace: list[dict[str, Any]] = []
        budget = ToolBudget(assignment.question_id, max_bytes=REPOSITORY_BYTE_BUDGETS[assignment.question_id])
        tool_server = self.tool_factory(assignment)
        current_input = input_payload
        response_seen = False
        final_answer: dict[str, Any] | None = None
        try:
            for turn in range(1, MAX_MODEL_TURNS + 1):
                response = self._call(ProviderRequest(assignment, "AGENT_DIRECT", turn, current_input, {"schema": TOOL_SCHEMA}, FINAL_OUTPUT_TOKENS), response_seen=response_seen, observation=observation, captures=captures)
                response_seen = True
                if response.kind == "FINAL":
                    final_answer = response.payload
                    break
                if response.kind != "TOOL_CALL":
                    raise RuntimeContractError("provider response kind is invalid")
                requested_calls = response.payload.get("calls")
                if requested_calls is None:
                    requested_calls = [response.payload]
                if not isinstance(requested_calls, list) or not requested_calls:
                    raise RuntimeContractError("tool request list is structurally invalid")
                previous_results: list[dict[str, Any]] = []
                budget_exhausted = False
                not_executed_this_turn = 0
                for call_index, call in enumerate(requested_calls):
                    operation = call.get("operation") if isinstance(call, dict) else None
                    arguments = call.get("arguments", {}) if isinstance(call, dict) else {}
                    if not isinstance(operation, str) or not isinstance(arguments, dict):
                        traced = {
                            "operationIndex": None,
                            "operationType": operation,
                            "targetOrQuery": arguments,
                            "repositoryRevision": REPOSITORY_REVISION,
                            "executionStatus": "NOT_EXECUTED_INVALID_REQUEST",
                            "resultIdentity": None,
                            "resultByteCount": 0,
                            "resultSha256": None,
                            "durationMs": 0,
                            "truncated": False,
                            "errorState": "RuntimeContractError",
                            "errorMessage": "tool request is structurally invalid",
                        }
                        assert_secret_free(traced)
                        tool_trace.append(traced)
                        raise RuntimeContractError("tool request is structurally invalid")
                    if operation not in ALLOWED_TOOL_OPERATIONS or len(canonical(call).encode("utf-8")) > AGENT_INTERMEDIATE_OUTPUT_TOKENS * 4:
                        traced = {
                            "operationIndex": None,
                            "operationType": operation,
                            "targetOrQuery": arguments,
                            "repositoryRevision": REPOSITORY_REVISION,
                            "executionStatus": "NOT_EXECUTED_INVALID_REQUEST",
                            "resultIdentity": None,
                            "resultByteCount": 0,
                            "resultSha256": None,
                            "durationMs": 0,
                            "truncated": False,
                            "errorState": "RuntimeContractError",
                            "errorMessage": "tool action exceeds the intermediate action envelope or allowlist",
                        }
                        assert_secret_free(traced)
                        tool_trace.append(traced)
                        raise RuntimeContractError("tool action exceeds the intermediate action envelope")
                    try:
                        operation_index = budget.attempt()
                    except BudgetExhausted:
                        tool_trace.append({
                            "operationIndex": None,
                            "operationType": operation,
                            "targetOrQuery": arguments,
                            "repositoryRevision": REPOSITORY_REVISION,
                            "executionStatus": "NOT_EXECUTED_BUDGET_EXHAUSTED",
                            "resultIdentity": None,
                            "resultByteCount": 0,
                            "resultSha256": None,
                            "durationMs": 0,
                            "truncated": False,
                            "errorState": "BudgetExhausted",
                            "errorMessage": "maximum tool operations exhausted",
                        })
                        budget_exhausted = True
                        not_executed_this_turn = len(requested_calls) - call_index
                        break
                    try:
                        result = tool_server.execute(operation, {**arguments, "repositoryRevision": REPOSITORY_REVISION})
                        traced = canonical_tool_result(operation, arguments, result, revision=REPOSITORY_REVISION)
                        budget.reserve(result)
                        traced["operationIndex"] = operation_index
                        traced["executionStatus"] = "EXECUTED_SUCCESS" if not result.get("matches") == [] else "EXECUTED_EMPTY"
                        observation["toolSuccessfulOperations"] += 1
                        previous_results.append(traced)
                    except BudgetExhausted as error:
                        traced = {
                            "operationIndex": operation_index,
                            "operationType": operation,
                            "targetOrQuery": arguments,
                            "repositoryRevision": REPOSITORY_REVISION,
                            "executionStatus": "EXECUTED_FAILURE",
                            "resultIdentity": None,
                            "resultByteCount": 0,
                            "resultSha256": None,
                            "durationMs": 0,
                            "truncated": False,
                            "errorState": type(error).__name__,
                            "errorMessage": str(error),
                        }
                        observation["toolFailedOperations"] += 1
                        assert_secret_free(traced)
                        tool_trace.append(traced)
                        raise
                    except Exception as error:
                        traced = {
                            "operationIndex": operation_index,
                            "operationType": operation,
                            "targetOrQuery": arguments,
                            "repositoryRevision": REPOSITORY_REVISION,
                            "executionStatus": "EXECUTED_FAILURE",
                            "resultIdentity": None,
                            "resultByteCount": 0,
                            "resultSha256": None,
                            "durationMs": 0,
                            "truncated": False,
                            "errorState": type(error).__name__,
                            "errorMessage": str(error),
                        }
                        observation["toolFailedOperations"] += 1
                    assert_secret_free(traced)
                    tool_trace.append(traced)
                if budget_exhausted:
                    observation["toolNotExecutedOperations"] += not_executed_this_turn
                    raise BudgetExhausted("maximum tool operations exhausted")
                if not previous_results and tool_trace:
                    current_input = {**input_payload, "previousToolResult": tool_trace[-1]}
                elif len(previous_results) == 1:
                    current_input = {**input_payload, "previousToolResult": previous_results[0]}
                else:
                    current_input = {**input_payload, "previousToolResults": previous_results}
            if final_answer is None:
                raise BudgetExhausted("AGENT_DIRECT model-turn budget exhausted without final answer")
            observation["rawOutput"] = {"responses": captures, "toolTrace": tool_trace, "finalResponse": final_answer}
            observation["finalAnswerCount"] = 1
            observation["toolOperations"] = budget.operations
            observation["toolAttempts"] = budget.operations
            observation["repositoryBytesInspected"] = budget.bytes_returned
            observation["repositoryOperations"] = budget.operations
            state.transition("RAW_CAPTURED")
            validate_answer(final_answer, assignment)
            references = {item["reference"] for item in final_answer["evidence"]}
            seen_evidence = {
                trace["result"].get("reference"): trace["result"]
                for trace in tool_trace
                if isinstance(trace.get("result"), dict) and trace["result"].get("reference")
            }
            seen_references = set(seen_evidence)
            if not references.issubset(seen_references):
                raise RuntimeContractError("answer references were not returned by direct tools")
            observation["validationResults"]["structural"] = "PASS"
            observation["structuralValid"] = "YES"
            state.transition("STRUCTURALLY_VALIDATED")
            evidence = [EvidenceItem(
                reference=reference,
                content=str(seen_evidence[reference].get("content", "")),
                source_type="TOOL_RESULT",
                provider_visible_source_identity="AGENT_DIRECT_TOOL_RESULT",
            ) for reference in sorted(seen_references)]
            grounding = self.grounding.validate(answer=final_answer, evidence=evidence, assignment=assignment)
            observation["validationResults"]["grounding"] = grounding
            record_grounding_metadata(observation, grounding)
            state.transition("GROUNDING_VALIDATED")
            if grounding.get("status") == "PASS":
                state.transition("SEMANTICALLY_ELIGIBLE")
                observation["semanticEligible"] = True
                observation["layerSpecificErrorClassification"] = "NONE"
                observation["semanticOutcome"] = "YES"
            else:
                observation["primaryError"] = grounding_error(grounding.get("error"))
                observation["layerSpecificErrorClassification"] = observation["primaryError"]
        except (TransportFailure, BudgetExhausted, RuntimeContractError) as error:
            observation["executionStatus"] = "INVALID"
            observation["primaryError"] = "INFRASTRUCTURE_FAILURE" if isinstance(error, (TransportFailure, BudgetExhausted)) else "STRUCTURAL_FAILURE"
            observation["validationResults"]["error"] = str(error)
            if observation["structuralValid"] == "NOT_EVALUATED":
                observation["structuralValid"] = "NO"
            observation["layerSpecificErrorClassification"] = observation["primaryError"]
            observation["rawOutput"] = {"responses": captures, "toolTrace": tool_trace} if captures or tool_trace else "NOT_APPLICABLE"
            observation["toolOperations"] = budget.operations
            observation["toolAttempts"] = budget.operations
            observation["repositoryBytesInspected"] = budget.bytes_returned
            observation["repositoryOperations"] = budget.operations
            if state.state == "RUNNING":
                state.transition("RAW_CAPTURED")
        state.transition("FINALIZED")
        return self._finalize(observation, state)

    def write_observation(self, observation: dict[str, Any], destination: str | Path) -> None:
        """Write one validated observation once; existing paths are never overwritten."""

        assert_secret_free(observation)
        write_immutable_json(destination, observation)


def replay_observation(
    observation: dict[str, Any],
    *,
    manifest: dict[str, Any] | None = None,
    enforce_official_baseline: bool = True,
) -> dict[str, Any]:
    """Replay identity, hashes, parsing, and captured traces without a provider."""

    source = manifest or load_manifest()
    validate_raw_observation(
        observation,
        manifest=source,
        enforce_official_baseline=enforce_official_baseline,
    )
    raw_output = observation["rawOutput"]
    if not (isinstance(raw_output, str) and raw_output in MISSING_STATES) and observation["rawOutputSha256"] != raw_output_hash(raw_output):
        raise RuntimeContractError("raw output hash mismatch during replay")
    if isinstance(raw_output, dict):
        assignment = Assignment(
            assignment_id=observation["assignment"]["assignmentId"],
            question_id=observation["questionId"],
            question_version=observation["questionVersion"],
            case_id=observation["caseId"],
            condition=observation["condition"],
            repetition=observation["repetition"],
        )
        final_response = raw_output.get("finalResponse")
        if final_response is not None:
            validate_answer(final_response, assignment)
        for trace in raw_output.get("toolTrace", []):
            result = trace.get("result")
            if result is not None and trace.get("resultSha256") != sha256_value(result):
                raise RuntimeContractError("tool result hash mismatch during replay")
    return {
        "replayable": True,
        "providerSampling": "NOT_REPLAYABLE",
        "hiddenReasoning": "NOT_REPLAYABLE",
        "networkLatency": "NOT_REPLAYABLE",
        "capturedRawOutputVerified": True,
        "toolTraceVerified": isinstance(raw_output, str) or isinstance(raw_output.get("toolTrace", []), list),
        "providerCalls": 0,
    }
