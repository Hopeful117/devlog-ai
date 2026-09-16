"""Offline contracts for Story0133 comparative baseline infrastructure.

This module defines assignments, condition boundaries, immutable raw observation
validation, pairing compatibility, historical exclusion, and completeness. It
does not call providers, read repositories, or perform statistical analysis.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
PACKAGE = Path(__file__).resolve().parent
MANIFEST_PATH = PACKAGE / "baseline-manifest.json"
POLICY_PATHS = {
    "DEVLOG": PACKAGE / "devlog-policy.json",
    "AGENT_DIRECT": PACKAGE / "agent-direct-policy.json",
    "HUMAN_DIRECT": PACKAGE / "human-direct-policy.json",
}

CONDITIONS = ("DEVLOG", "AGENT_DIRECT", "HUMAN_DIRECT")
EXECUTABLE_CONDITIONS = ("DEVLOG", "AGENT_DIRECT")
QUESTION_IDS = ("CASE-01-COMPARATIVE", "CASE-03", "CASE-04")
REPETITIONS = (1, 2, 3)
MISSING_STATES = {
    "NOT_APPLICABLE", "NOT_AVAILABLE", "NOT_MEASURED", "INVALID", "NOT_EVALUATED",
}
ASSIGNMENT_STATES = {"ASSIGNED", "EXECUTED", "SEMANTIC_ELIGIBLE", "INVALID", "MISSING"}
ERROR_TAXONOMY = {
    "CORRECT_GROUNDED", "CORRECT_POOR_GROUNDING", "WRONG_RELATIONSHIP",
    "OVERCLAIM", "FALSE_ABSTENTION", "FAILED_TO_ABSTAIN", "FABRICATED_EVIDENCE",
    "EVIDENCE_EXCERPT_MISMATCH", "UNAUTHORIZED_REFERENCE", "STRUCTURAL_FAILURE",
    "INFRASTRUCTURE_FAILURE", "INVALID_COMPARISON", "NOT_EVALUATED",
}
FORBIDDEN_INPUT_MARKERS = (
    "expectedClassification", "expectedEvidence", "expectedOutcome", "oracle",
    "STRONGLY_SUPPORTED", "EXPLICITLY_DOCUMENTED", "NOT_ESTABLISHED",
    "DIRECT_REQUIRED", "SUPPORTING_ALLOWED", "CL-01", "CL-09",
)
PAIR_FIELDS = (
    "benchmarkVersion", "questionId", "questionVersion", "repositoryId",
    "repositoryRevision", "model", "modelConfiguration",
    "conditionPolicyCompatibilityKey", "repetition", "oracleVersion",
    "scoringContractVersion",
)


def canonical(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def sha256_value(value: Any) -> str:
    return sha256_text(canonical(value))


def _read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def _question_map(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    return {question["questionId"]: question for question in manifest["questions"]}


def assignment_matrix(manifest: dict[str, Any] | None = None) -> list[dict[str, Any]]:
    source = manifest or _read_json(MANIFEST_PATH)
    questions = _question_map(source)
    return [
        {
            "assignmentId": f"{question_id}:{condition}:r{repetition}",
            "questionId": question_id,
            "questionVersion": questions[question_id]["questionVersion"],
            "caseId": questions[question_id]["caseId"],
            "condition": condition,
            "repetition": repetition,
        }
        for question_id in QUESTION_IDS
        for condition in EXECUTABLE_CONDITIONS
        for repetition in REPETITIONS
    ]


def validate_manifest(manifest: dict[str, Any]) -> None:
    required = {
        "manifestVersion", "benchmarkVersion", "repositoryId", "repositoryRevision",
        "oracleReference", "oracleVersion", "oracleStatus", "mappingHash",
        "scoringContractVersion", "rawObservationSchemaVersion", "questions",
        "repetitionCount", "executableConditions", "conditionPolicyVersions",
    }
    missing = sorted(required - set(manifest))
    if missing:
        raise ValueError(f"baseline manifest missing fields: {', '.join(missing)}")
    if manifest["repetitionCount"] != 3 or tuple(manifest["executableConditions"]) != EXECUTABLE_CONDITIONS:
        raise ValueError("baseline repetition or executable condition policy is not frozen")
    if manifest["oracleStatus"] != "HUMAN_APPROVED":
        raise ValueError("baseline oracle must be human approved")
    if len(manifest["questions"]) != 3 or tuple(q["questionId"] for q in manifest["questions"]) != QUESTION_IDS:
        raise ValueError("baseline question set must be exactly CASE-01-COMPARATIVE, CASE-03, CASE-04")
    question_map = _question_map(manifest)
    case01 = question_map["CASE-01-COMPARATIVE"]
    if (case01["questionVersion"], case01["category"], case01["sourceIdentity"], case01["targetIdentity"]) != (
        "1.0.0", "CAUSAL_RELATIONSHIP", "ADR-042",
        "Story-0039: Persisted Account Identity and Explicit PAPER Provisioning",
    ):
        raise ValueError("CASE-01 comparative identity is not frozen")
    if case01["adaptation"]["mappingClaimId"] != "CL-01" or case01["adaptation"]["humanApproval"] != "YES":
        raise ValueError("CASE-01 adaptation compatibility is not frozen")
    if len(case01["authorizedEvidence"]) != 14:
        raise ValueError("CASE-01 authorized evidence count must be 14")
    if len(case01["providerVisibleEvidence"]) != 4:
        raise ValueError("CASE-01 provider-visible evidence count must be 4")
    if len(case01["expectedGroundingEvidence"]) != 2:
        raise ValueError("CASE-01 expected grounding evidence count must be 2")
    if not set(case01["expectedGroundingEvidence"]).issubset(case01["providerVisibleEvidence"]):
        raise ValueError("CASE-01 expected grounding must be provider-visible")
    if not set(case01["providerVisibleEvidence"]).issubset(case01["authorizedEvidence"]):
        raise ValueError("CASE-01 provider-visible evidence must be authorized")
    if not set(case01["supportingAllowedEvidence"]).issubset(case01["providerVisibleEvidence"]):
        raise ValueError("CASE-01 supporting evidence must be provider-visible")
    for question in manifest["questions"]:
        if not question.get("question") or not question.get("questionVersion"):
            raise ValueError("every baseline question needs a versioned wording")
        if len(set(question["authorizedEvidence"])) != len(question["authorizedEvidence"]):
            raise ValueError(f"duplicate authorized evidence in {question['questionId']}")
        if not set(question.get("providerVisibleEvidence", [])).issubset(question["authorizedEvidence"]):
            raise ValueError(f"provider-visible evidence is not authorized in {question['questionId']}")
    if len(assignment_matrix(manifest)) != 18:
        raise ValueError("baseline assignment matrix must contain 18 assignments")


def load_manifest() -> dict[str, Any]:
    manifest = _read_json(MANIFEST_PATH)
    validate_manifest(manifest)
    return manifest


def load_policies() -> dict[str, dict[str, Any]]:
    policies = {condition: _read_json(path) for condition, path in POLICY_PATHS.items()}
    for condition, policy in policies.items():
        if policy.get("condition") != condition or not policy.get("policyVersion"):
            raise ValueError(f"invalid {condition} policy identity")
    if policies["DEVLOG"].get("repositoryAccess") != "NONE":
        raise ValueError("DEVLOG policy must have no repository access")
    if policies["AGENT_DIRECT"].get("repositoryAccess") != "READ_ONLY_PINNED_REVISION":
        raise ValueError("AGENT_DIRECT policy must be read-only and pinned")
    if policies["HUMAN_DIRECT"].get("mode") != "DESIGNED_NOT_EXECUTABLE":
        raise ValueError("HUMAN_DIRECT must remain non-executable")
    return policies


def policy_identities() -> dict[str, dict[str, str]]:
    return {
        condition: {
            "policyVersion": policy["policyVersion"],
            "policySha256": sha256_value(policy),
        }
        for condition, policy in load_policies().items()
    }


def manifest_identity(manifest: dict[str, Any] | None = None) -> dict[str, str]:
    source = manifest or load_manifest()
    return {
        "manifestVersion": source["manifestVersion"],
        "manifestSha256": sha256_value(source),
        "repositoryRevision": source["repositoryRevision"],
        "oracleVersion": source["oracleVersion"],
        "mappingHash": source["mappingHash"],
    }


def condition_input(
    condition: str, question_id: str, repetition: int, *, manifest: dict[str, Any] | None = None,
) -> dict[str, Any]:
    source = manifest or load_manifest()
    if condition not in EXECUTABLE_CONDITIONS:
        raise ValueError(f"condition is not executable: {condition}")
    question = _question_map(source).get(question_id)
    if question is None:
        raise ValueError(f"unknown baseline question: {question_id}")
    if repetition not in REPETITIONS:
        raise ValueError(f"invalid repetition: {repetition}")
    result: dict[str, Any] = {
        "benchmarkVersion": source["benchmarkVersion"],
        "questionId": question["questionId"],
        "questionVersion": question["questionVersion"],
        "caseId": question["caseId"],
        "category": question["category"],
        "question": question["question"],
        "sourceIdentity": question["sourceIdentity"],
        "targetIdentity": question["targetIdentity"],
        "condition": condition,
        "conditionPolicyVersion": source["conditionPolicyVersions"][condition],
        "conditionPolicyCompatibilityKey": "story0133-ai-pairing-policy-1.0.0",
        "repetition": repetition,
        "repositoryId": source["repositoryId"],
        "repositoryRevision": source["repositoryRevision"],
    }
    if condition == "DEVLOG":
        result["contextStrategy"] = "FROZEN_DEVLOG_EVALUATION_CONTEXT_PROJECTION"
        result["providerVisibleEvidence"] = list(question.get("providerVisibleEvidence", []))
    else:
        result["accessMode"] = "READ_ONLY_PINNED_REPOSITORY"
        result["allowedOperations"] = list(load_policies()["AGENT_DIRECT"]["allowed"])
    assert_no_oracle_leakage(result)
    return result


def assert_no_oracle_leakage(value: Any) -> None:
    serialized = canonical(value)
    for marker in FORBIDDEN_INPUT_MARKERS:
        if marker in serialized:
            raise ValueError(f"condition input contains forbidden oracle marker: {marker}")


def raw_output_hash(raw_output: Any) -> str:
    return sha256_value(raw_output)


def validate_raw_observation(observation: dict[str, Any], *, manifest: dict[str, Any] | None = None) -> None:
    source = manifest or load_manifest()
    assert_official_baseline_eligible(observation)
    required = {
        "observationId", "benchmarkVersion", "questionId", "questionVersion", "caseId",
        "condition", "conditionPolicyVersion", "conditionPolicyCompatibilityKey", "repetition",
        "repositoryId", "repositoryRevision", "oracleVersion", "scoringContractVersion",
        "model", "modelConfiguration",
        "executionStatus", "semanticOutcome", "primaryError", "exactInput", "rawOutput",
        "rawOutputSha256", "validationResults", "runId", "artifactReference",
    }
    missing = sorted(required - set(observation))
    if missing:
        raise ValueError(f"raw observation incomplete: {', '.join(missing)}")
    question = _question_map(source).get(observation["questionId"])
    if question is None or question["questionVersion"] != observation["questionVersion"]:
        raise ValueError("raw observation question identity mismatch")
    if observation["caseId"] != question["caseId"]:
        raise ValueError("raw observation case identity mismatch")
    if observation["condition"] not in CONDITIONS:
        raise ValueError("invalid condition")
    if observation["repetition"] not in REPETITIONS:
        raise ValueError("invalid repetition")
    if observation["benchmarkVersion"] != source["benchmarkVersion"]:
        raise ValueError("benchmark identity mismatch")
    if observation["repositoryId"] != source["repositoryId"] or observation["repositoryRevision"] != source["repositoryRevision"]:
        raise ValueError("repository identity mismatch")
    if observation["oracleVersion"] != source["oracleVersion"]:
        raise ValueError("oracle identity mismatch")
    if observation["scoringContractVersion"] != source["scoringContractVersion"]:
        raise ValueError("scoring identity mismatch")
    if observation["conditionPolicyVersion"] != source["conditionPolicyVersions"][observation["condition"]]:
        raise ValueError("condition policy identity mismatch")
    if observation["conditionPolicyCompatibilityKey"] != "story0133-ai-pairing-policy-1.0.0":
        raise ValueError("condition policy compatibility mismatch")
    if observation["executionStatus"] not in {"EXECUTED", "INVALID"}:
        raise ValueError("invalid execution status")
    if observation["semanticOutcome"] not in {"YES", "NO", "NOT_EVALUATED"}:
        raise ValueError("invalid semantic outcome")
    if observation["primaryError"] not in ERROR_TAXONOMY:
        raise ValueError("invalid primary error")
    if observation["primaryError"] == "INFRASTRUCTURE_FAILURE" and observation["semanticOutcome"] != "NOT_EVALUATED":
        raise ValueError("infrastructure failure must leave semantic outcome NOT_EVALUATED")
    if observation["executionStatus"] == "INVALID" and observation["semanticOutcome"] != "NOT_EVALUATED":
        raise ValueError("invalid observation must leave semantic outcome NOT_EVALUATED")
    if isinstance(observation["rawOutput"], str) and observation["rawOutput"] in MISSING_STATES:
        if observation["rawOutputSha256"] != "NOT_APPLICABLE":
            raise ValueError("missing raw output must have NOT_APPLICABLE hash")
    elif observation["rawOutputSha256"] != raw_output_hash(observation["rawOutput"]):
        raise ValueError("raw output hash mismatch")
    assert_no_oracle_leakage(observation["exactInput"])


def pairing_key(observation: dict[str, Any]) -> tuple[Any, ...]:
    return tuple(observation[field] if field != "modelConfiguration" else canonical(observation[field]) for field in PAIR_FIELDS)


def compare_pair(left: dict[str, Any], right: dict[str, Any]) -> dict[str, Any]:
    try:
        validate_raw_observation(left)
        validate_raw_observation(right)
    except ValueError as error:
        return {"status": "INVALID_COMPARISON", "reasons": [str(error)]}
    if {left["condition"], right["condition"]} != {"DEVLOG", "AGENT_DIRECT"}:
        return {"status": "INVALID_COMPARISON", "reasons": ["conditions must be DEVLOG and AGENT_DIRECT"]}
    mismatches = [field for field in PAIR_FIELDS if (
        canonical(left[field]) if field == "modelConfiguration" else left[field]
    ) != (
        canonical(right[field]) if field == "modelConfiguration" else right[field]
    )]
    if mismatches:
        return {"status": "INVALID_COMPARISON", "reasons": [f"incompatible {field}" for field in mismatches]}
    if left["executionStatus"] != "EXECUTED" or right["executionStatus"] != "EXECUTED":
        return {"status": "INCOMPLETE", "reasons": ["both observations must be executed"]}
    return {"status": "COMPATIBLE", "pairingKey": pairing_key(left), "reasons": []}


def completeness(observations: list[dict[str, Any]], *, manifest: dict[str, Any] | None = None) -> dict[str, Any]:
    source = manifest or load_manifest()
    indexed: dict[str, dict[str, Any]] = {}
    for observation in observations:
        validate_raw_observation(observation, manifest=source)
        assignment_id = f"{observation['questionId']}:{observation['condition']}:r{observation['repetition']}"
        if assignment_id in indexed:
            raise ValueError(f"duplicate observation assignment: {assignment_id}")
        indexed[assignment_id] = observation
    rows = []
    for assignment in assignment_matrix(source):
        observation = indexed.get(assignment["assignmentId"])
        if observation is None:
            status = "MISSING"
        elif observation["executionStatus"] == "INVALID":
            status = "INVALID"
        elif observation["semanticOutcome"] == "YES":
            status = "SEMANTIC_ELIGIBLE"
        else:
            status = "EXECUTED"
        rows.append({**assignment, "status": status})
    pairs = []
    for question in QUESTION_IDS:
        for repetition in REPETITIONS:
            left = indexed.get(f"{question}:DEVLOG:r{repetition}")
            right = indexed.get(f"{question}:AGENT_DIRECT:r{repetition}")
            if left is None or right is None:
                pairs.append({"questionId": question, "repetition": repetition, "status": "INCOMPLETE"})
            else:
                result = compare_pair(left, right)
                pairs.append({"questionId": question, "repetition": repetition, **result})
    assigned = len(rows)
    eligible = sum(row["status"] == "SEMANTIC_ELIGIBLE" for row in rows)
    return {
        "assignmentStatus": rows,
        "pairs": pairs,
        "assignedObservationCount": assigned,
        "executedObservationCount": sum(row["status"] in {"EXECUTED", "SEMANTIC_ELIGIBLE"} for row in rows),
        "semanticEligibleCount": eligible,
        "invalidObservationCount": sum(row["status"] == "INVALID" for row in rows),
        "missingObservationCount": sum(row["status"] == "MISSING" for row in rows),
        "pairedCompleteCount": sum(pair["status"] == "COMPATIBLE" for pair in pairs),
        "pairedIncompleteCount": sum(pair["status"] != "COMPATIBLE" for pair in pairs),
        "executionCompleteness": eligible / assigned if assigned else None,
    }


def primary_outcome_snapshot(derived_observations: list[dict[str, Any]], completeness_view: dict[str, Any]) -> dict[str, Any]:
    required = {
        "assignedObservationCount", "executedObservationCount", "semanticEligibleCount",
        "invalidObservationCount", "missingObservationCount", "executionCompleteness",
    }
    if not required.issubset(completeness_view):
        raise ValueError("primary outcome requires execution completeness fields")
    eligible = [item for item in derived_observations if item.get("semanticEligible") is True]
    correct = [item for item in eligible if item.get("semanticCorrect") is True and item.get("groundingValid") is True and item.get("structuralValid") is True]
    return {
        **{key: completeness_view[key] for key in sorted(required)},
        "correctGroundedCount": len(correct),
        "correctGroundedAnswerRate": len(correct) / len(eligible) if eligible else None,
    }


def classify_historical_case01(artifact_reference: str) -> dict[str, Any]:
    if not artifact_reference:
        raise ValueError("historical artifact reference is required")
    return {"classification": "HISTORICAL_PRE_BASELINE", "baselineEligible": False, "artifactReference": artifact_reference}


def assert_official_baseline_eligible(observation: dict[str, Any]) -> None:
    if observation.get("historicalClassification") == "HISTORICAL_PRE_BASELINE":
        raise ValueError("historical observation cannot enter official baseline")
    if observation.get("executionClass") == "LIVE_PILOT" or observation.get("baselineEligible") is False:
        raise ValueError("live pilot observation cannot enter official baseline")


def write_immutable_json(path: str | Path, value: Any) -> None:
    destination = Path(path)
    if destination.exists():
        raise FileExistsError(f"refusing to overwrite immutable artifact: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")


def build_raw_artifact(observation: dict[str, Any]) -> dict[str, Any]:
    validate_raw_observation(observation)
    artifact = {
        "artifactVersion": "story0133-raw-observation-artifact-1.0.0",
        "observation": observation,
        "immutable": True,
    }
    artifact["artifactSha256"] = sha256_value(artifact)
    return artifact


def validate_raw_artifact(artifact: dict[str, Any]) -> None:
    if artifact.get("immutable") is not True or "observation" not in artifact:
        raise ValueError("raw artifact is incomplete")
    expected = artifact.get("artifactSha256")
    unsigned = {key: value for key, value in artifact.items() if key != "artifactSha256"}
    if expected != sha256_value(unsigned):
        raise ValueError("raw artifact hash mismatch")
    validate_raw_observation(artifact["observation"])
