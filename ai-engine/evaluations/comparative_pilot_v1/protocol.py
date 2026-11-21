"""Offline contracts for Story 0223's bounded comparative pilot.

This module validates identities, treatment isolation, bounded observations,
three-treatment pairing, and execution completeness. It never creates a
provider client, accesses a repository, or performs network I/O.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import re
from typing import Any


PACKAGE = Path(__file__).resolve().parent
MANIFEST_PATH = PACKAGE / "pilot-manifest.json"
POLICY_PATHS = {
    "DEVLOG_CONTEXT": PACKAGE / "devlog-context-policy.json",
    "AGENT_DIRECT": PACKAGE / "agent-direct-policy.json",
    "STORY_CONTEXT_INVESTIGATION": PACKAGE / "story-context-investigation-policy.json",
}

TREATMENTS = tuple(POLICY_PATHS)
REPETITIONS = (1, 2, 3)
ASSIGNMENT_STATES = {"ASSIGNED", "EXECUTED", "SEMANTIC_ELIGIBLE", "INVALID", "MISSING"}
TECHNICALLY_INELIGIBLE = {
    "MISSING",
    "INVALID",
    "PROVIDER_INCOMPLETE_RESPONSE",
    "TIMEOUT",
    "IDENTITY_MISMATCH",
    "CAPTURE_INTEGRITY_FAILURE",
}
FORBIDDEN_INPUT_MARKERS = (
    "expectedClassification",
    "expectedEvidence",
    "expectedOutcome",
    "oracle",
    "STRONGLY_SUPPORTED",
    "EXPLICITLY_DOCUMENTED",
    "NOT_ESTABLISHED",
    "DIRECT_REQUIRED",
    "SUPPORTING_ALLOWED",
    "CL-01",
    "CL-09",
)
SECRET_KEY = re.compile(r"(?i)^(authorization|api[_-]?key|password|secret|token|credential|cookie|access[_-]?token)$")
SECRET_VALUE = re.compile(r"(?i)(bearer\s+\S+|sk-[a-z0-9_-]{16,})")


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_value(value: Any) -> str:
    return hashlib.sha256(canonical(value).encode("utf-8")).hexdigest()


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _questions(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item["questionId"]: item for item in manifest["questions"]}


def assert_secret_free(value: Any, *, path: str = "root") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if SECRET_KEY.match(str(key)):
                raise ValueError(f"secret-like field refused at {path}.{key}")
            assert_secret_free(child, path=f"{path}.{key}")
    elif isinstance(value, (list, tuple)):
        for index, child in enumerate(value):
            assert_secret_free(child, path=f"{path}[{index}]")
    elif isinstance(value, str) and SECRET_VALUE.search(value):
        raise ValueError(f"secret-like value refused at {path}")


def assert_no_oracle_leakage(value: Any) -> None:
    serialized = canonical(value)
    for marker in FORBIDDEN_INPUT_MARKERS:
        if marker in serialized:
            raise ValueError(f"treatment input contains forbidden oracle marker: {marker}")


def load_manifest() -> dict[str, Any]:
    manifest = _read_json(MANIFEST_PATH)
    validate_manifest(manifest)
    return manifest


def load_policies() -> dict[str, dict[str, Any]]:
    policies = {treatment: _read_json(path) for treatment, path in POLICY_PATHS.items()}
    for treatment, policy in policies.items():
        if policy.get("treatment") != treatment or not policy.get("policyVersion"):
            raise ValueError(f"invalid policy identity for {treatment}")
        assert_secret_free(policy)
    if policies["DEVLOG_CONTEXT"].get("repositoryAccess") != "NONE":
        raise ValueError("DEVLOG_CONTEXT must not access the repository")
    if policies["AGENT_DIRECT"].get("repositoryAccess") != "READ_ONLY_PINNED_REVISION":
        raise ValueError("AGENT_DIRECT must be read-only and revision-pinned")
    if policies["STORY_CONTEXT_INVESTIGATION"].get("authority") != "CORE_AUTHORIZED_ACTIONS_ONLY":
        raise ValueError("Story investigation must remain Core-authorized")
    return policies


def validate_manifest(manifest: dict[str, Any]) -> None:
    required = {
        "protocolVersion", "benchmarkId", "benchmarkVersion", "repositoryId",
        "repositoryRevision", "oracleReference", "oracleVersion", "oracleStatus",
        "scoringContractVersion", "rawObservationSchemaVersion", "questions",
        "repetitions", "treatments", "executionPolicy",
    }
    missing = sorted(required - set(manifest))
    if missing:
        raise ValueError(f"pilot manifest missing fields: {', '.join(missing)}")
    if tuple(manifest["treatments"]) != TREATMENTS:
        raise ValueError("pilot treatment order or membership is not frozen")
    if tuple(manifest["repetitions"]) != REPETITIONS:
        raise ValueError("pilot repetitions must be exactly 1, 2, and 3")
    if len(manifest["questions"]) != 8:
        raise ValueError("pilot benchmark must contain exactly Q1-Q8")
    question_ids = tuple(item.get("questionId") for item in manifest["questions"])
    if question_ids != tuple(f"Q{i}" for i in range(1, 9)):
        raise ValueError("pilot questions must be ordered Q1-Q8")
    if len(set(question_ids)) != len(question_ids):
        raise ValueError("pilot question IDs must be unique")
    for question in manifest["questions"]:
        if not question.get("question") or not question.get("questionVersion"):
            raise ValueError(f"question {question.get('questionId')} lacks frozen wording/version")
        if not question.get("oracleReference"):
            raise ValueError(f"question {question['questionId']} lacks an oracle reference")
    if manifest["executionPolicy"].get("providerCalls") != "FORBIDDEN_DURING_PREPARATION":
        raise ValueError("provider calls must remain forbidden during preparation")
    if len(assignment_matrix(manifest)) != 72:
        raise ValueError("pilot assignment matrix must contain 72 assignments")


def manifest_identity(manifest: dict[str, Any] | None = None) -> dict[str, str]:
    source = manifest or load_manifest()
    return {
        "protocolVersion": source["protocolVersion"],
        "manifestSha256": sha256_value(source),
        "repositoryRevision": source["repositoryRevision"],
        "oracleVersion": source["oracleVersion"],
    }


@dataclass(frozen=True)
class Assignment:
    assignment_id: str
    question_id: str
    question_version: str
    treatment: str
    repetition: int
    state: str = "ASSIGNED"

    def as_dict(self) -> dict[str, Any]:
        return {
            "assignmentId": self.assignment_id,
            "questionId": self.question_id,
            "questionVersion": self.question_version,
            "treatment": self.treatment,
            "repetition": self.repetition,
            "state": self.state,
        }


def assignment_matrix(manifest: dict[str, Any] | None = None) -> list[dict[str, Any]]:
    source = manifest or _read_json(MANIFEST_PATH)
    questions = _questions(source)
    return [
        Assignment(
            assignment_id=f"{question_id}:{treatment}:r{repetition}",
            question_id=question_id,
            question_version=questions[question_id]["questionVersion"],
            treatment=treatment,
            repetition=repetition,
        ).as_dict()
        for question_id in (f"Q{i}" for i in range(1, 9))
        for treatment in TREATMENTS
        for repetition in REPETITIONS
    ]


def condition_input(
    treatment: str,
    question_id: str,
    repetition: int,
    *,
    manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    source = manifest or load_manifest()
    if treatment not in TREATMENTS:
        raise ValueError(f"unknown treatment: {treatment}")
    question = _questions(source).get(question_id)
    if question is None:
        raise ValueError(f"unknown question: {question_id}")
    if repetition not in REPETITIONS:
        raise ValueError(f"invalid repetition: {repetition}")
    policy = load_policies()[treatment]
    result = {
        "benchmarkId": source["benchmarkId"],
        "benchmarkVersion": source["benchmarkVersion"],
        "questionId": question_id,
        "questionVersion": question["questionVersion"],
        "category": question["category"],
        "question": question["question"],
        "treatment": treatment,
        "policyVersion": policy["policyVersion"],
        "policyCompatibilityKey": policy["compatibilityKey"],
        "repetition": repetition,
        "repositoryId": source["repositoryId"],
        "repositoryRevision": source["repositoryRevision"],
    }
    if treatment == "DEVLOG_CONTEXT":
        result["contextStrategy"] = "FROZEN_AUTHORIZED_CONTEXT_PROJECTION"
    elif treatment == "AGENT_DIRECT":
        result["accessMode"] = "READ_ONLY_PINNED_REPOSITORY"
        result["allowedOperations"] = list(policy["allowedOperations"])
    else:
        result["actionMode"] = "CORE_AUTHORIZED_SEARCH_AND_READ"
        result["callbackContract"] = policy["callbackContract"]
    assert_no_oracle_leakage(result)
    return result


@dataclass(frozen=True)
class Observation:
    observation_id: str
    assignment_id: str
    question_id: str
    question_version: str
    treatment: str
    repetition: int
    benchmark_version: str
    repository_id: str
    repository_revision: str
    policy_version: str
    policy_compatibility_key: str
    oracle_version: str
    scoring_contract_version: str
    model: str
    model_configuration: dict[str, Any]
    execution_status: str
    semantic_eligibility: str
    primary_error: str | None
    exact_input: dict[str, Any]
    raw_output: Any
    raw_input_sha256: str
    raw_output_sha256: str
    validation_results: dict[str, Any]
    run_id: str
    artifact_reference: str
    usage: dict[str, Any] | None = None

    def as_dict(self) -> dict[str, Any]:
        value = asdict(self)
        return {
            "observationId": value["observation_id"],
            "assignmentId": value["assignment_id"],
            "questionId": value["question_id"],
            "questionVersion": value["question_version"],
            "treatment": value["treatment"],
            "repetition": value["repetition"],
            "benchmarkVersion": value["benchmark_version"],
            "repositoryId": value["repository_id"],
            "repositoryRevision": value["repository_revision"],
            "policyVersion": value["policy_version"],
            "policyCompatibilityKey": value["policy_compatibility_key"],
            "oracleVersion": value["oracle_version"],
            "scoringContractVersion": value["scoring_contract_version"],
            "model": value["model"],
            "modelConfiguration": value["model_configuration"],
            "executionStatus": value["execution_status"],
            "semanticEligibility": value["semantic_eligibility"],
            "primaryError": value["primary_error"],
            "exactInput": value["exact_input"],
            "rawOutput": value["raw_output"],
            "rawInputSha256": value["raw_input_sha256"],
            "rawOutputSha256": value["raw_output_sha256"],
            "validationResults": value["validation_results"],
            "runId": value["run_id"],
            "artifactReference": value["artifact_reference"],
            "usage": value["usage"],
        }


def validate_observation(observation: Observation | dict[str, Any], *, manifest: dict[str, Any] | None = None) -> None:
    source = manifest or load_manifest()
    value = observation.as_dict() if isinstance(observation, Observation) else observation
    required = {
        "observationId", "assignmentId", "questionId", "questionVersion", "treatment",
        "repetition", "benchmarkVersion", "repositoryId", "repositoryRevision",
        "policyVersion", "policyCompatibilityKey", "oracleVersion", "scoringContractVersion",
        "model", "modelConfiguration", "executionStatus", "semanticEligibility",
        "primaryError", "exactInput", "rawOutput", "rawInputSha256", "rawOutputSha256",
        "validationResults", "runId", "artifactReference",
    }
    missing = sorted(required - set(value))
    if missing:
        raise ValueError(f"observation incomplete: {', '.join(missing)}")
    question = _questions(source).get(value["questionId"])
    if question is None or question["questionVersion"] != value["questionVersion"]:
        raise ValueError("question identity mismatch")
    if value["treatment"] not in TREATMENTS or value["repetition"] not in REPETITIONS:
        raise ValueError("invalid treatment or repetition")
    if value["benchmarkVersion"] != source["benchmarkVersion"]:
        raise ValueError("benchmark identity mismatch")
    if value["repositoryId"] != source["repositoryId"] or value["repositoryRevision"] != source["repositoryRevision"]:
        raise ValueError("repository identity mismatch")
    if value["oracleVersion"] != source["oracleVersion"]:
        raise ValueError("oracle identity mismatch")
    if value["rawInputSha256"] != sha256_value(value["exactInput"]):
        raise ValueError("raw input digest mismatch")
    if value["rawOutputSha256"] != sha256_value(value["rawOutput"]):
        raise ValueError("raw output digest mismatch")
    if value["executionStatus"] not in {"COMPLETED", "MISSING", "INVALID", "PROVIDER_INCOMPLETE_RESPONSE", "TIMEOUT", "FAILED"}:
        raise ValueError("unknown execution status")
    if value["semanticEligibility"] not in {"SEMANTICALLY_ELIGIBLE", "NOT_EVALUATED"}:
        raise ValueError("unknown semantic eligibility")
    assert_secret_free(value)
    assert_no_oracle_leakage(value["exactInput"])


def _assignment_lookup(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {item["assignmentId"]: item for item in assignment_matrix(manifest)}


def completeness(
    observations: list[Observation | dict[str, Any]], *, manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    source = manifest or load_manifest()
    assignments = assignment_matrix(source)
    by_assignment: dict[str, Observation | dict[str, Any]] = {}
    for observation in observations:
        value = observation.as_dict() if isinstance(observation, Observation) else observation
        validate_observation(value, manifest=source)
        assignment_id = value["assignmentId"]
        if assignment_id in by_assignment:
            raise ValueError(f"duplicate observation assignment: {assignment_id}")
        if assignment_id not in _assignment_lookup(source):
            raise ValueError(f"unknown assignment: {assignment_id}")
        by_assignment[assignment_id] = value
    counts = {state: 0 for state in ASSIGNMENT_STATES}
    cells: list[dict[str, Any]] = []
    for assignment in assignments:
        value = by_assignment.get(assignment["assignmentId"])
        if value is None:
            state = "MISSING"
        elif value["executionStatus"] in TECHNICALLY_INELIGIBLE or value["semanticEligibility"] == "NOT_EVALUATED":
            state = "INVALID" if value["executionStatus"] == "INVALID" else "EXECUTED"
        elif value["executionStatus"] == "COMPLETED" and value["semanticEligibility"] == "SEMANTICALLY_ELIGIBLE":
            state = "SEMANTIC_ELIGIBLE"
        else:
            state = "EXECUTED"
        counts[state] += 1
        cells.append({**assignment, "state": state})
    executed = len(observations)
    return {
        "assignedCount": len(assignments),
        "executedCount": executed,
        "eligibleCount": counts["SEMANTIC_ELIGIBLE"],
        "invalidCount": counts["INVALID"],
        "missingCount": counts["MISSING"],
        "states": counts,
        "cells": cells,
        "executionCompleteness": executed / len(assignments),
    }


def pairing(
    observations: list[Observation | dict[str, Any]], *, manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    source = manifest or load_manifest()
    by_key: dict[tuple[str, int], set[str]] = {}
    for observation in observations:
        value = observation.as_dict() if isinstance(observation, Observation) else observation
        validate_observation(value, manifest=source)
        key = (value["questionId"], value["repetition"])
        by_key.setdefault(key, set()).add(value["treatment"])
    complete = sum(treatments == set(TREATMENTS) for treatments in by_key.values())
    incomplete = len(by_key) - complete
    return {
        "expectedPairCount": 8 * 3,
        "observedPairCount": len(by_key),
        "completePairCount": complete,
        "incompletePairCount": incomplete,
        "completeTreatmentSet": list(TREATMENTS),
    }


@dataclass(frozen=True)
class Authorization:
    authorization_id: str
    campaign_id: str
    phase: str
    approved_treatments: tuple[str, ...]
    approved_question_ids: tuple[str, ...]
    approved_repetitions: tuple[int, ...]
    repository_revision: str
    max_provider_calls: int
    stop_conditions: tuple[str, ...]

    def validate_for(self, *, treatment: str, question_id: str, repetition: int, provider_calls: int) -> None:
        if self.phase not in {"TECHNICAL_VALIDATION", "QUALITY_PILOT"}:
            raise ValueError("live authorization phase is not executable")
        if treatment not in self.approved_treatments or question_id not in self.approved_question_ids:
            raise ValueError("assignment is outside live authorization scope")
        if repetition not in self.approved_repetitions:
            raise ValueError("repetition is outside live authorization scope")
        if provider_calls > self.max_provider_calls:
            raise ValueError("provider call limit exceeded")


def build_report(
    observations: list[Observation | dict[str, Any]], *, manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    source = manifest or load_manifest()
    completion = completeness(observations, manifest=source)
    pairs = pairing(observations, manifest=source)
    oracle_reviewed = source["oracleStatus"] == "HUMAN_APPROVED"
    return {
        "protocolVersion": source["protocolVersion"],
        "manifestIdentity": manifest_identity(source),
        "oracleReviewed": oracle_reviewed,
        "qualityMetricsAvailable": oracle_reviewed,
        "completeness": {key: value for key, value in completion.items() if key != "cells"},
        "pairing": pairs,
        "technicalFailures": sum(
            value["executionStatus"] in TECHNICALLY_INELIGIBLE
            for value in (item.as_dict() if isinstance(item, Observation) else item for item in observations)
        ),
        "qualityConclusion": "NOT_AVAILABLE" if not oracle_reviewed else "HUMAN_REVIEW_REQUIRED",
    }
