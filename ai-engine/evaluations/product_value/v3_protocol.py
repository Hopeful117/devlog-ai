"""Evaluation-only V3 manifest, artifact, gate, and replay primitives.

This module deliberately contains no provider execution. It freezes inputs and
validates captured results so a later smoke runner can be fail-fast and replayable.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Any

from .causal_mapping import build_frozen_causal_questions
from .loader import apply_frozen_oracle, load_benchmark


V3_MANIFEST_VERSION = "story0132-v3-candidate-1.0.0"
V3_MAPPING_STATUS = "MECHANICALLY_MIGRATED"
V3_MAPPING_HUMAN_APPROVED = "YES"
V3_MAPPING_HASH = "67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0"
V3_SOURCE_MAPPING = "docs/stories/0132-evidence-grounded-causal-interpretation/v2-frozen-causal-question-mapping.md"
V3_TECHNICAL_RETRY_MAX = 1
V3_SEMANTIC_RETRY = "DISABLED"
V3_SMOKE_PROVIDER_CALLS_EXPECTED = 3
V3_SMOKE_PROVIDER_CALLS_MAXIMUM = 6
V3_PROVIDER_CONFIGURATION_HUMAN_APPROVED = "YES"
V3_EXECUTION_CONFIGURATION_REVISION = "story0132-v3-execution-config-3.0.0"
V3_EXECUTION_CONFIGURATION_DIGEST = "b77d3d093e86ffc6f0296a63483218f3bca9c340db1410a85d3248e7c6f8cefe"
V3_PROVIDER_SCHEMA_DIGEST = "f1cd0a1c97e752d319ba7be63198060705bdc4a740abab08ddfe183840342093"
V3_RAW_CAPTURE_BOUNDARY = "SDK_RESPONSE_BEFORE_PARSE"
V3_PROVIDER_CONFIGURATION = {
    "provider": "openai",
    "model": "gpt-4.1-mini",
    "maxOutputTokens": 3000,
    "openaiSdkMaxRetries": 0,
    "providerAdapterMaxRetries": 0,
    "technicalRetryMax": 1,
    "maximumTotalAttemptsPerSlot": 2,
    "semanticRetry": "DISABLED",
    "timeoutSeconds": 90,
    "temperature": "PROVIDER_DEFAULT",
    "topP": "PROVIDER_DEFAULT",
    "seed": "NOT_CONFIGURED",
    "tools": "NOT_CONFIGURED",
    "reasoning": "NOT_CONFIGURED",
    "structuredOutput": "responses.create(...) -> capture SDK Response -> parse_response(..., text_format=...)",
    "providerSchemaDigest": V3_PROVIDER_SCHEMA_DIGEST,
    "rawCaptureBoundary": V3_RAW_CAPTURE_BOUNDARY,
}

_V3_ENVIRONMENT_VALUES = {
    "LLM_PROVIDER": ("provider", str),
    "LLM_MODEL": ("model", str),
    "LLM_MAX_OUTPUT_TOKENS": ("maxOutputTokens", int),
    "LLM_MAX_RETRIES": ("providerAdapterMaxRetries", int),
    "LLM_TIMEOUT_SECONDS": ("timeoutSeconds", float),
}

GATE_ORDER = (
    "GATE_1_TRANSPORT_COMPLETENESS",
    "GATE_2_STRUCTURAL_SCHEMA_VALIDITY",
    "GATE_3_CONTEXT_IDENTITY_VALIDITY",
    "GATE_4_REFERENCE_AUTHORIZATION",
    "GATE_5_EVIDENCE_ASSERTION_VALIDITY",
    "GATE_6_BUSINESS_CAUSAL_CONTRACT_VALIDITY",
    "GATE_7_SEMANTIC_SCORING_ELIGIBILITY",
)

GATE_AUTHORITY = {
    GATE_ORDER[0]: "evaluation provider adapter",
    GATE_ORDER[1]: "frozen Python schema contract",
    GATE_ORDER[2]: "Java Core task identity and evaluation artifact",
    GATE_ORDER[3]: "Java Core reference mapping and grounding contract",
    GATE_ORDER[4]: "Java Core TaskSnapshotEvidenceResolver",
    GATE_ORDER[5]: "Java Core authoritative causal validator",
    GATE_ORDER[6]: "evaluation scorer after all prior gates",
}

VALIDITY_STATES = {
    "VALID",
    "INVALID_STRUCTURAL_OUTPUT",
    "INVALID_CONTRACT",
    "INVALID_EVIDENCE",
    "INVALID_EXECUTION",
    "INCOMPLETE",
    "NOT_EVALUATED",
}

_SECRET_VALUE = re.compile(
    r"(?i)(bearer\s+\S+|sk-[a-z0-9_-]{16,}|api[_-]?key\s*[:=]\s*[\"']?[a-z0-9_+/=-]{16,})"
)


def _is_secret_key(key: object) -> bool:
    normalized = re.sub(r"[^a-z0-9]", "", str(key).casefold())
    return normalized in {
        "authorization", "apikey", "password", "secret", "token", "credential",
        "accesstoken", "refreshtoken", "bearertoken", "clientsecret",
    }


def canonical(value: object) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_text(value: str) -> str:
    return sha256_bytes(value.encode("utf-8"))


def _case_question(benchmark: dict[str, Any], case_id: str, question_id: str) -> dict[str, Any]:
    questions = build_frozen_causal_questions(benchmark)
    for question in questions.get(case_id, ()):
        if question.question_id == question_id:
            return {
                "caseId": question.case_id,
                "questionId": question.question_id,
                "claimId": question.claim_id,
                "source": question.source,
                "target": question.target,
                "relationAsked": question.relation_asked,
                "answerRequired": question.answer_required,
                "expectedClassification": question.expected_classification,
                "expectedEvidence": list(question.expected_evidence),
                "mappingAuthority": question.mapping_authority,
            }
    raise ValueError(f"unknown frozen question: {case_id}/{question_id}")


def build_candidate_manifest(
    benchmark_path: str | Path,
    oracle_path: str | Path,
    *,
    mapping_path: str | Path,
) -> dict[str, Any]:
    """Build the three-slot candidate manifest without approving new semantics."""

    benchmark = apply_frozen_oracle(
        load_benchmark(benchmark_path),
        json.loads(Path(oracle_path).read_text(encoding="utf-8")),
    )
    candidates = (
        ("CASE-01", "CASE-01::CL-01", "positive causal relation"),
        ("CASE-04", "CASE-04::CASE-04", "NOT_ESTABLISHED abstention control"),
        ("CASE-03", "CASE-03::CL-09", "evidence-heavy positive relation"),
    )
    slots = []
    for case_id, question_id, reason in candidates:
        question = _case_question(benchmark, case_id, question_id)
        slots.append({
            **question,
            "repetition": 1,
            "smokeCandidate": True,
            "selectionReason": reason,
        })
    mapping_hash = sha256_bytes(Path(mapping_path).read_bytes())
    if mapping_hash != V3_MAPPING_HASH:
        raise ValueError(f"V2 mapping hash mismatch: {mapping_hash}")
    expected_denominators = {
        "positiveCausalAccuracy": 2,
        "case04Abstention": 1,
        "causalStability": 0,
        "causalOverclaimRate": 1,
        "evidenceValidity": 3,
        "referenceAuthorization": 3,
        "roleAdmissibility": 2,
    }
    manifest = {
        "manifestVersion": V3_MANIFEST_VERSION,
        "benchmarkVersion": f"{benchmark['suiteId']}:{benchmark['suiteVersion']}",
        "benchmarkSuiteId": benchmark["suiteId"],
        "benchmarkSuiteVersion": benchmark["suiteVersion"],
        "repositoryId": benchmark["repositoryId"],
        "repositoryRevision": benchmark["repositoryRevision"],
        "groundTruthAuthority": "HUMAN_APPROVED_ORACLE_CARRIED_FORWARD_UNCHANGED",
        "sourceBenchmark": str(Path(benchmark_path)),
        "sourceOracle": str(Path(oracle_path)),
        "sourceMapping": str(Path(mapping_path)),
        "mappingVersion": "v2-frozen-causal-question-mapping",
        "mappingHash": mapping_hash,
        "mappingStatus": V3_MAPPING_STATUS,
        "mappingHumanApproval": V3_MAPPING_HUMAN_APPROVED,
        "mappingSemanticChanges": [],
        "repetitions": 1,
        "technicalRetryMax": V3_TECHNICAL_RETRY_MAX,
        "semanticRetry": V3_SEMANTIC_RETRY,
        "expectedProviderCalls": V3_SMOKE_PROVIDER_CALLS_EXPECTED,
        "maximumProviderCalls": V3_SMOKE_PROVIDER_CALLS_MAXIMUM,
        "smokeSlotCount": len(slots),
        "smokeCandidates": slots,
        "expectedDenominators": expected_denominators,
        "semanticThresholds": {
            "positiveCausalAccuracyMin": 0.80,
            "case04NotEstablishedRuns": 1,
            "stabilityMin": 0.90,
            "unsupportedInferenceRateMax": 0.0,
        },
        "schema": {
            "identifier": "StoryContextAnalysisResult",
            "version": "story-context-analysis-prompt-v1",
            "digestSource": "existing scenario schema digest",
        },
        "providerConfigurationApproval": V3_PROVIDER_CONFIGURATION_HUMAN_APPROVED,
        "executionConfigurationRevision": V3_EXECUTION_CONFIGURATION_REVISION,
        "executionConfigurationDigest": V3_EXECUTION_CONFIGURATION_DIGEST,
        "providerSchemaDigest": V3_PROVIDER_SCHEMA_DIGEST,
        "rawCaptureBoundary": V3_RAW_CAPTURE_BOUNDARY,
        "providerConfiguration": dict(V3_PROVIDER_CONFIGURATION),
        "status": "CANDIDATE_REQUIRES_HUMAN_APPROVAL",
    }
    validate_manifest(manifest)
    return manifest


def validate_manifest(manifest: dict[str, Any]) -> None:
    required = (
        "manifestVersion", "benchmarkVersion", "repositoryId", "repositoryRevision",
        "mappingHash", "mappingStatus", "mappingHumanApproval", "smokeCandidates",
        "expectedDenominators", "technicalRetryMax", "semanticRetry",
        "providerConfigurationApproval", "providerConfiguration",
        "executionConfigurationRevision", "executionConfigurationDigest",
        "providerSchemaDigest", "rawCaptureBoundary",
    )
    missing = [field for field in required if field not in manifest]
    if missing:
        raise ValueError(f"V3 manifest missing fields: {', '.join(missing)}")
    if manifest["mappingStatus"] not in {"MECHANICALLY_MIGRATED", "HUMAN_APPROVED"}:
        raise ValueError("invalid V3 mapping status")
    if manifest["mappingHumanApproval"] not in {"PENDING", "YES"}:
        raise ValueError("invalid V3 mapping approval status")
    if manifest["technicalRetryMax"] != 1 or manifest["semanticRetry"] != "DISABLED":
        raise ValueError("V3 retry policy is not the approved pre-smoke policy")
    candidates = manifest["smokeCandidates"]
    if not isinstance(candidates, list) or len(candidates) != 3:
        raise ValueError("V3 smoke manifest must contain exactly three candidates")
    identities = {(item.get("caseId"), item.get("questionId"), item.get("repetition")) for item in candidates}
    if len(identities) != len(candidates) or any(item.get("repetition") != 1 for item in candidates):
        raise ValueError("V3 smoke candidate identities must be unique one-repetition slots")
    required_cases = {"CASE-01", "CASE-03", "CASE-04"}
    if {item.get("caseId") for item in candidates} != required_cases:
        raise ValueError("V3 smoke candidates must cover CASE-01, CASE-03, and CASE-04")
    if manifest["expectedProviderCalls"] != 3 or manifest["maximumProviderCalls"] != 6:
        raise ValueError("V3 smoke call envelope must be 3 expected and 6 maximum")
    if manifest["mappingHash"] != V3_MAPPING_HASH:
        raise ValueError("V3 mapping hash is not the carried-forward approved mapping hash")
    if manifest["providerConfigurationApproval"] != V3_PROVIDER_CONFIGURATION_HUMAN_APPROVED:
        raise ValueError("V3 provider configuration is not human approved")
    if manifest["providerConfiguration"] != V3_PROVIDER_CONFIGURATION:
        raise ValueError("V3 provider configuration is not the frozen execution authority")
    if manifest["executionConfigurationRevision"] != V3_EXECUTION_CONFIGURATION_REVISION:
        raise ValueError("V3 execution configuration revision is not frozen")
    if manifest["executionConfigurationDigest"] != V3_EXECUTION_CONFIGURATION_DIGEST:
        raise ValueError("V3 execution configuration digest is not frozen")
    if manifest["providerSchemaDigest"] != V3_PROVIDER_SCHEMA_DIGEST:
        raise ValueError("V3 provider schema digest is not frozen")
    if manifest["rawCaptureBoundary"] != V3_RAW_CAPTURE_BOUNDARY:
        raise ValueError("V3 raw capture boundary is not frozen")


def resolve_v3_provider_configuration(
    manifest: dict[str, Any], *, environment: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Return manifest-owned provider settings and reject conflicting environment values."""

    validate_manifest(manifest)
    values = os.environ if environment is None else environment
    for environment_name, (configuration_name, parser) in _V3_ENVIRONMENT_VALUES.items():
        if environment_name not in values:
            continue
        raw_value = values[environment_name]
        try:
            actual = parser(raw_value)
        except (TypeError, ValueError) as exc:
            raise ValueError(
                f"invalid V3 environment override: {environment_name}={raw_value!r}"
            ) from exc
        expected = V3_PROVIDER_CONFIGURATION[configuration_name]
        if actual != expected:
            raise ValueError(
                f"V3 environment conflict for {environment_name}: "
                f"manifest={expected!r}, environment={raw_value!r}"
            )
    return dict(V3_PROVIDER_CONFIGURATION)


def build_v3_openai_provider(
    manifest: dict[str, Any], *, api_key: str,
    environment: dict[str, str] | None = None,
):
    """Construct the V3 provider from frozen settings, never application defaults."""

    configuration = resolve_v3_provider_configuration(manifest, environment=environment)
    from app.providers.openai import OpenAiLlmProvider

    return OpenAiLlmProvider(
        api_key=api_key,
        model=configuration["model"],
        timeout_seconds=configuration["timeoutSeconds"],
        max_output_tokens=configuration["maxOutputTokens"],
        max_retries=configuration["providerAdapterMaxRetries"],
    )


def safe_metadata(value: Any) -> Any:
    """Allowlist-style recursive metadata sanitization; never persist secrets."""

    if isinstance(value, dict):
        result = {}
        for key, nested in value.items():
            result[str(key)] = "[REDACTED]" if _is_secret_key(key) else safe_metadata(nested)
        return result
    if isinstance(value, list):
        return [safe_metadata(item) for item in value]
    if isinstance(value, str):
        return "[REDACTED]" if _SECRET_VALUE.search(value) else value
    return value


def assert_safe_exact_text(value: str, *, field: str) -> None:
    if _SECRET_VALUE.search(value):
        raise ValueError(f"secret-like content in exact persisted field: {field}")


def assert_safe_value(value: Any, *, field: str) -> None:
    if isinstance(value, dict):
        for key, nested in value.items():
            if _is_secret_key(key):
                raise ValueError(f"secret-bearing key in exact persisted field: {field}.{key}")
            assert_safe_value(nested, field=f"{field}.{key}")
    elif isinstance(value, list):
        for index, nested in enumerate(value):
            assert_safe_value(nested, field=f"{field}[{index}]")
    elif isinstance(value, str):
        assert_safe_exact_text(value, field=field)


def capture_evidence_item(item: dict[str, Any], *, ordering: int) -> dict[str, Any]:
    content = item.get("content")
    if not isinstance(content, dict) or content.get("status") != "COMPLETE":
        raise ValueError("provider-visible evidence must have COMPLETE content")
    text = content.get("text")
    if not isinstance(text, str):
        raise ValueError("provider-visible evidence content.text must be a string")
    assert_safe_exact_text(text, field="providerVisibleEvidence.content.text")
    raw = text.encode("utf-8")
    return {
        "reference": item.get("reference"),
        "sourceType": item.get("artifactType", item.get("sourceType", "UNKNOWN")),
        "repositoryIdentity": item.get("repositoryIdentity"),
        "repositoryRevision": item.get("repositoryRevision"),
        "pathOrResource": item.get("pathOrResource", item.get("reference")),
        "ordering": ordering,
        "contentEncoding": "UTF-8",
        "content": text,
        "contentByteLength": len(raw),
        "contentSha256": sha256_bytes(raw),
        "locatorContractVersion": "story0132-java-locator-v1",
    }


def capture_prompt_representation(
    *, system_message: str, user_message: str, schema: Any, rendering_version: str,
) -> dict[str, Any]:
    assert_safe_exact_text(system_message, field="prompt.systemMessage")
    assert_safe_exact_text(user_message, field="prompt.userMessage")
    representation = {
        "renderingVersion": rendering_version,
        "systemMessage": system_message,
        "userMessage": user_message,
        "schema": schema,
    }
    return {"representation": representation, "promptDigest": sha256_text(canonical(representation))}


def capture_raw_response(raw_response: str | dict[str, Any] | list[Any]) -> dict[str, Any]:
    if isinstance(raw_response, str):
        assert_safe_value(raw_response, field="rawProviderResponse")
        raw_bytes = raw_response.encode("utf-8")
        stored: Any = raw_response
    else:
        assert_safe_value(raw_response, field="rawProviderResponse")
        stored = json.loads(json.dumps(raw_response, ensure_ascii=False))
        raw_bytes = canonical(stored).encode("utf-8")
    return {"rawProviderResponse": stored, "rawResponseSha256": sha256_bytes(raw_bytes)}


def capture_attempt(
    *, attempt: int, status: str, provider: str, model: str,
    response_present: bool, finish_reason: str | None = None,
    token_usage: dict[str, Any] | None = None, error: str | None = None,
    retry_of: int | None = None,
) -> dict[str, Any]:
    if attempt < 1:
        raise ValueError("attempt must be positive")
    return {
        "attempt": attempt,
        "status": status,
        "provider": provider,
        "model": model,
        "responsePresent": response_present,
        "finishReason": finish_reason if finish_reason is not None else "NOT_REPORTED",
        "tokenUsage": safe_metadata(token_usage or {}),
        "error": error,
        "retryOf": retry_of,
        "retryType": "TECHNICAL_RETRY" if retry_of is not None else None,
    }


def build_provider_visible_evidence(selected_knowledge: dict[str, Any]) -> list[dict[str, Any]]:
    evidence = selected_knowledge.get("repositoryContext", {}).get("evidence", [])
    if not isinstance(evidence, list):
        raise ValueError("SelectedKnowledge repositoryContext.evidence must be a list")
    return [capture_evidence_item(item, ordering=index) for index, item in enumerate(evidence)]


def build_slot_artifact(
    manifest: dict[str, Any], *, evaluation_run_id: str, candidate: dict[str, Any],
    provider: str, model: str, provider_configuration: dict[str, Any],
    repository_identity: str, ai_task_id: str, correlation_id: str,
    context_digest: str, selected_knowledge_snapshot: dict[str, Any],
    provider_projection: dict[str, Any], prompt_representation: dict[str, Any],
    schema: dict[str, Any], raw_response: str | dict[str, Any] | list[Any] | None,
    parsed_response: dict[str, Any] | None, attempts: list[dict[str, Any]],
    gates: dict[str, Any], core_validation: dict[str, Any],
    evidence_validation: dict[str, Any],
) -> dict[str, Any]:
    """Create a complete immutable slot record from already captured inputs."""

    validate_manifest(manifest)
    if candidate not in manifest["smokeCandidates"]:
        raise ValueError("slot candidate is not present in the frozen manifest")
    evidence = build_provider_visible_evidence(selected_knowledge_snapshot)
    assert_safe_value(selected_knowledge_snapshot, field="selectedKnowledgeSnapshot")
    assert_safe_value(provider_projection, field="providerProjection")
    assert_safe_value(parsed_response, field="parsedResponse")
    raw_capture = capture_raw_response(raw_response if raw_response is not None else {})
    prompt_digest = prompt_representation.get("promptDigest")
    if not isinstance(prompt_digest, str):
        raise ValueError("prompt representation must include promptDigest")
    slot = {
        "evaluationRunId": evaluation_run_id,
        "benchmarkVersion": manifest["benchmarkVersion"],
        "caseId": candidate["caseId"],
        "questionId": candidate["questionId"],
        "repetition": candidate["repetition"],
        "provider": provider,
        "model": model,
        "providerConfiguration": safe_metadata(provider_configuration),
        "providerSchemaDigest": manifest["providerSchemaDigest"],
        "rawCaptureBoundary": manifest["rawCaptureBoundary"],
        "executionConfigurationRevision": manifest["executionConfigurationRevision"],
        "executionConfigurationDigest": manifest["executionConfigurationDigest"],
        "repositoryIdentity": repository_identity,
        "repositoryRevision": manifest["repositoryRevision"],
        "aiTaskId": ai_task_id,
        "correlationId": correlation_id,
        "contextDigest": context_digest,
        "selectedKnowledgeSnapshot": json.loads(json.dumps(selected_knowledge_snapshot)),
        "selectedKnowledgeDigest": sha256_text(canonical(selected_knowledge_snapshot)),
        "providerProjection": json.loads(json.dumps(provider_projection)),
        "providerVisibleEvidence": evidence,
        "promptRepresentation": prompt_representation["representation"],
        "promptDigest": prompt_digest,
        "schema": schema,
        "mappingHash": manifest["mappingHash"],
        "attempts": attempts,
        **raw_capture,
        "parsedResponse": parsed_response,
        "gates": gates,
        "coreValidation": core_validation,
        "evidenceValidation": evidence_validation,
        "semanticScoringEligible": gates["semanticScoringEligible"],
        "finalSlotStatus": gates["finalSlotStatus"],
    }
    validate_slot_completeness(slot)
    validate_evidence_bytes(slot)
    return slot


def finalize_run_artifact(
    manifest: dict[str, Any], *, evaluation_run_id: str, slots: list[dict[str, Any]],
) -> dict[str, Any]:
    validate_manifest(manifest)
    identities = {
        (slot.get("caseId"), slot.get("questionId"), slot.get("repetition"))
        for slot in slots
    }
    if len(identities) != len(slots):
        raise ValueError("duplicate V3 slot identity")
    for slot in slots:
        validate_slot_completeness(slot)
    validity = benchmark_validity(slots, manifest["smokeSlotCount"])
    artifact = {
        "artifactVersion": "story0132-v3-run-1.0.0",
        "evaluationRunId": evaluation_run_id,
        "benchmarkManifest": manifest,
        "slots": slots,
        "benchmarkValidity": validity,
        "immutable": True,
    }
    artifact["artifactSha256"] = sha256_text(canonical(artifact))
    return artifact


def write_immutable_artifact(path: str | Path, artifact: dict[str, Any]) -> None:
    destination = Path(path)
    if destination.exists():
        raise FileExistsError(f"refusing to overwrite finalized V3 artifact: {destination}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(artifact, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")


def technical_retry_allowed(attempt: dict[str, Any]) -> bool:
    return not attempt.get("responsePresent") and attempt.get("status") in {
        "TIMEOUT", "NETWORK_FAILURE", "PROVIDER_UNAVAILABLE", "RATE_LIMITED",
    }


def gate_result(gate: str, status: str, reason: str | None = None) -> dict[str, Any]:
    if gate not in GATE_ORDER:
        raise ValueError(f"unknown V3 gate: {gate}")
    if status not in {"PASS", "FAIL", "NOT_EVALUATED"}:
        raise ValueError(f"invalid V3 gate status: {status}")
    return {"gate": gate, "status": status, "authority": GATE_AUTHORITY[gate], "reason": reason}


def compose_gates(checks: dict[str, dict[str, Any]]) -> dict[str, Any]:
    results = []
    first_failure = None
    for gate in GATE_ORDER:
        if first_failure is not None:
            result = gate_result(gate, "NOT_EVALUATED", f"blocked by {first_failure}")
        else:
            result = checks.get(gate, gate_result(gate, "FAIL", "gate result missing"))
            if result["status"] != "PASS":
                first_failure = result["gate"]
        results.append(result)
    eligible = first_failure is None
    return {
        "gates": results,
        "firstFailure": first_failure,
        "semanticScoringEligible": eligible,
        "finalSlotStatus": "VALID" if eligible else _gate_failure_state(first_failure),
    }


def _gate_failure_state(gate: str | None) -> str:
    if gate == GATE_ORDER[0]:
        return "INVALID_EXECUTION"
    if gate == GATE_ORDER[1]:
        return "INVALID_STRUCTURAL_OUTPUT"
    if gate == GATE_ORDER[4]:
        return "INVALID_EVIDENCE"
    if gate is None:
        return "INCOMPLETE"
    return "INVALID_CONTRACT"


def metric_result(
    *, value: float | None, eligible: bool, observed_denominator: int,
    expected_denominator: int, benchmark_valid: bool, invalid_reason: str | None = None,
) -> dict[str, Any]:
    if not eligible:
        value = None
    return {
        "value": value,
        "eligible": eligible,
        "observedDenominator": observed_denominator,
        "expectedDenominator": expected_denominator,
        "benchmarkValid": benchmark_valid,
        "invalidReason": invalid_reason,
    }


def benchmark_validity(slot_artifacts: list[dict[str, Any]], expected_slots: int) -> dict[str, Any]:
    if len(slot_artifacts) != expected_slots:
        return {"status": "INCOMPLETE", "valid": False, "reason": "frozen slot denominator is incomplete"}
    invalid = [slot.get("finalSlotStatus") for slot in slot_artifacts if slot.get("finalSlotStatus") != "VALID"]
    if invalid:
        return {"status": invalid[0], "valid": False, "reason": "one or more frozen slots are invalid"}
    return {"status": "VALID", "valid": True, "reason": None}


def smoke_should_stop(slot_artifact: dict[str, Any]) -> bool:
    """Stop smoke execution for infrastructure/validity failure, not semantic error."""

    return slot_artifact.get("finalSlotStatus") != "VALID"


def validate_slot_completeness(slot: dict[str, Any]) -> None:
    required = (
        "evaluationRunId", "benchmarkVersion", "caseId", "questionId", "repetition",
        "provider", "model", "repositoryIdentity", "repositoryRevision", "aiTaskId",
        "contextDigest", "selectedKnowledgeSnapshot", "providerProjection",
        "providerVisibleEvidence", "promptRepresentation", "promptDigest", "schema",
        "mappingHash", "attempts", "rawProviderResponse", "rawResponseSha256",
        "parsedResponse", "gates", "semanticScoringEligible", "finalSlotStatus",
    )
    missing = [field for field in required if field not in slot]
    if missing:
        raise ValueError(f"incomplete V3 slot artifact: {', '.join(missing)}")
    if slot["finalSlotStatus"] == "VALID" and not slot["semanticScoringEligible"]:
        raise ValueError("valid V3 slot must be semantically eligible")
    if slot["mappingHash"] != V3_MAPPING_HASH:
        raise ValueError("slot mapping hash differs from frozen manifest")


def validate_evidence_bytes(slot: dict[str, Any]) -> None:
    for item in slot.get("providerVisibleEvidence", []):
        content = item.get("content")
        if not isinstance(content, str):
            raise ValueError("evidence content is not textual")
        actual = content.encode("utf-8")
        if item.get("contentByteLength") != len(actual) or item.get("contentSha256") != sha256_bytes(actual):
            raise ValueError(f"provider-visible evidence bytes changed: {item.get('reference')}")


def replay_slot_through_core(slot: dict[str, Any], repository_root: str | Path) -> dict[str, Any]:
    """Replay a frozen slot using the existing real Java Core evaluation bridge."""

    validate_slot_completeness(slot)
    validate_evidence_bytes(slot)
    if slot.get("semanticScoringEligible") is not True:
        raise ValueError("invalid slot is not eligible for Core semantic replay")
    result = slot["parsedResponse"]
    selected = slot["selectedKnowledgeSnapshot"]
    evidence = selected.get("repositoryContext", {}).get("evidence", [])
    grounding = slot.get("groundingContract") or {
        "allowedEvidenceReferences": [item.get("reference") for item in evidence],
        "causalAnswerRequired": True,
        "causalContractVersion": "V2",
        "causalQuestion": result.get("causalAssessment", {}).get("question"),
    }
    root = Path(repository_root).resolve()
    with tempfile.TemporaryDirectory(prefix="v3-offline-replay-") as directory:
        input_path = Path(directory) / "input.json"
        output_path = Path(directory) / "output.json"
        input_path.write_text(json.dumps({"items": [{
            "index": 0,
            "result": result,
            "selectedKnowledge": selected,
            "groundingContract": grounding,
            "contextDigest": slot["contextDigest"],
        }]}, ensure_ascii=False), encoding="utf-8")
        subprocess.run([
            str(root / "backend" / "mvnw"), "-o", "-q", "-pl", "backend", "-am", "test",
            "-Dtest=CoreV2EvaluationBridgeTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            f"-Dstory0132.bridge.input={input_path}",
            f"-Dstory0132.bridge.output={output_path}",
        ], cwd=root, check=True, timeout=180)
        return json.loads(output_path.read_text(encoding="utf-8"))["items"][0]


def compare_replay_outcome(slot: dict[str, Any], replay: dict[str, Any]) -> dict[str, Any]:
    """Compare stable live/replay boundaries without requiring wording equality."""

    expected_core_ok = slot.get("coreValidation", {}).get("status") == "PASS"
    actual_core_ok = replay.get("ok") is True
    expected_digest = slot.get("contextDigest")
    replay_digest = (replay.get("result") or {}).get("provenance", {}).get("contextDigest")
    equal = expected_core_ok == actual_core_ok and (
        not actual_core_ok or replay_digest == expected_digest
    )
    return {
        "equal": equal,
        "slotIdentity": [slot.get("caseId"), slot.get("questionId"), slot.get("repetition")],
        "expectedCoreAccepted": expected_core_ok,
        "replayCoreAccepted": actual_core_ok,
        "expectedContextDigest": expected_digest,
        "replayContextDigest": replay_digest,
        "comparisonBoundary": "slot identity, Core acceptance, and context digest",
    }
