import json
from pathlib import Path

import pytest

from evaluations.product_value.v3_protocol import (
    GATE_ORDER,
    V3_MAPPING_HASH,
    build_candidate_manifest,
    build_slot_artifact,
    build_v3_openai_provider,
    benchmark_validity,
    capture_attempt,
    capture_evidence_item,
    capture_prompt_representation,
    capture_raw_response,
    compare_replay_outcome,
    compose_gates,
    finalize_run_artifact,
    metric_result,
    replay_slot_through_core,
    resolve_v3_provider_configuration,
    safe_metadata,
    smoke_should_stop,
    sha256_bytes,
    technical_retry_allowed,
    validate_evidence_bytes,
    validate_slot_completeness,
    validate_manifest,
    write_immutable_artifact,
)


ROOT = Path(__file__).resolve().parents[2]
BENCHMARK = ROOT / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"
ORACLE = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
MAPPING = ROOT / "docs/stories/0132-evidence-grounded-causal-interpretation/v2-frozen-causal-question-mapping.md"
MANIFEST = ROOT / "ai-engine/evaluations/product_value/v3/benchmark-manifest.json"


def candidate_manifest() -> dict:
    return build_candidate_manifest(BENCHMARK, ORACLE, mapping_path=MAPPING)


def _selected_knowledge() -> dict:
    return {
        "repositoryContext": {
            "evidence": [{
                "reference": "src/Example.java",
                "artifactType": "SOURCE_FILE",
                "content": {"status": "COMPLETE", "revision": "revision", "text": "# Heading\nbody\n"},
            }],
        },
    }


def _parsed_response() -> dict:
    return {
        "objectiveUnderstanding": {"summary": "bounded"},
        "architectureFindings": [], "decisionFindings": [], "evidenceFindings": [],
        "historicalContext": [], "constraintFindings": [], "impactedComponentFindings": [],
        "uncertainties": [], "missingInformation": [], "implementationQuestions": [],
        "confidence": "HIGH",
        "provenance": {
            "contextDigest": "a" * 64,
            "promptVersion": "story-context-analysis-prompt-v1",
            "provider": "fixture", "modelIdentifier": "fixture",
            "promptContentDigest": "b" * 64, "intentId": "engineering-story-context-analysis",
            "intentVersion": "v1", "guidanceKeys": [], "executionMetadata": {},
        },
        "outputClassification": {"entries": []},
        "causalClaims": [],
        "causalAssessment": {
            "question": {
                "source": "ADR-042", "target": "Story-0039",
                "relationAsked": "CAUSAL", "answerRequired": True,
            },
            "classification": "NOT_ESTABLISHED",
            "evidenceAssertions": [{
                "evidenceReference": {
                    "reference": "src/Example.java", "resource": "src/Example.java",
                    "role": "NON_CAUSAL_CONTEXT",
                },
                "locator": {"kind": "SECTION", "heading": "Heading"},
                "assertionRole": "NON_CAUSAL_CONTEXT",
            }],
            "explanation": "The evidence is contextual and does not establish causality.",
        },
    }


def _slot() -> dict:
    manifest = candidate_manifest()
    candidate = manifest["smokeCandidates"][0]
    prompt = capture_prompt_representation(
        system_message="system", user_message="user", schema={"type": "object"},
        rendering_version="v3-test",
    )
    gates = compose_gates({gate: {"gate": gate, "status": "PASS", "authority": "test", "reason": None} for gate in GATE_ORDER})
    return build_slot_artifact(
        manifest,
        evaluation_run_id="run-1",
        candidate=candidate,
        provider="fixture",
        model="fixture",
        provider_configuration={"temperature": 0},
        repository_identity="repository",
        ai_task_id="task-1",
        correlation_id="correlation-1",
        context_digest="a" * 64,
        selected_knowledge_snapshot=_selected_knowledge(),
        provider_projection={"repositoryContext": "captured"},
        prompt_representation=prompt,
        schema={"identifier": "StoryContextAnalysisResult", "version": "v1", "digest": "c" * 64},
        raw_response={"fixture": True},
        parsed_response=_parsed_response(),
        attempts=[capture_attempt(attempt=1, status="SUCCEEDED", provider="fixture", model="fixture", response_present=True)],
        gates=gates,
        core_validation={"status": "PASS", "authority": "Java Core"},
        evidence_validation={"status": "PASS", "authority": "Java Core"},
    )


def test_candidate_manifest_matches_v2_mapping_and_remains_pending():
    manifest = candidate_manifest()
    static = json.loads(MANIFEST.read_text(encoding="utf-8"))

    validate_manifest(manifest)
    assert manifest["mappingHash"] == V3_MAPPING_HASH
    assert manifest["mappingSemanticChanges"] == []
    assert manifest["mappingHumanApproval"] == "YES"
    assert [(item["caseId"], item["questionId"]) for item in manifest["smokeCandidates"]] == [
        (item["caseId"], item["questionId"]) for item in static["smokeCandidates"]
    ]


def test_v3_provider_configuration_is_manifest_authority():
    manifest = candidate_manifest()

    configuration = resolve_v3_provider_configuration(
        manifest,
        environment={
            "LLM_PROVIDER": "openai",
            "LLM_MODEL": "gpt-4.1-mini",
            "LLM_MAX_OUTPUT_TOKENS": "3000",
            "LLM_MAX_RETRIES": "0",
            "LLM_TIMEOUT_SECONDS": "90",
        },
    )

    assert configuration["maxOutputTokens"] == 3000
    assert configuration["openaiSdkMaxRetries"] == 0
    assert configuration["providerAdapterMaxRetries"] == 0
    assert configuration["technicalRetryMax"] == 1
    assert configuration["maximumTotalAttemptsPerSlot"] == 2
    assert configuration["temperature"] == "PROVIDER_DEFAULT"
    assert configuration["topP"] == "PROVIDER_DEFAULT"
    assert configuration["seed"] == "NOT_CONFIGURED"
    assert configuration["tools"] == "NOT_CONFIGURED"
    assert configuration["reasoning"] == "NOT_CONFIGURED"


@pytest.mark.parametrize(
    ("environment_name", "environment_value"),
    [
        ("LLM_PROVIDER", "mock"),
        ("LLM_MODEL", "different-model"),
        ("LLM_MAX_OUTPUT_TOKENS", "4000"),
        ("LLM_MAX_RETRIES", "1"),
        ("LLM_TIMEOUT_SECONDS", "30"),
    ],
)
def test_conflicting_v3_environment_override_fails_closed(
    environment_name: str, environment_value: str,
):
    with pytest.raises(ValueError, match="V3 environment conflict"):
        resolve_v3_provider_configuration(
            candidate_manifest(),
            environment={environment_name: environment_value},
        )


def test_v3_provider_construction_uses_frozen_retry_and_generation_configuration():
    provider = build_v3_openai_provider(
        candidate_manifest(),
        api_key="test-key",
        environment={"LLM_MAX_RETRIES": "0", "LLM_MAX_OUTPUT_TOKENS": "3000"},
    )

    assert provider.model_identifier == "gpt-4.1-mini"
    assert provider._max_output_tokens == 3000
    assert provider._max_retries == 0
    assert provider._client.max_retries == 0


def test_evidence_capture_preserves_utf8_terminal_newline_bytes():
    item = capture_evidence_item({
        "reference": "file.md",
        "artifactType": "DOCUMENT",
        "content": {"status": "COMPLETE", "text": "é\n"},
    }, ordering=0)

    assert item["content"] == "é\n"
    assert item["contentByteLength"] == len("é\n".encode("utf-8"))
    assert item["contentSha256"] == sha256_bytes("é\n".encode("utf-8"))


def test_secret_preflight_redacts_metadata_and_rejects_exact_context():
    assert safe_metadata({"api_key": "secret", "provider": "fixture"}) == {
        "api_key": "[REDACTED]", "provider": "fixture"
    }
    with pytest.raises(ValueError, match="secret-like"):
        capture_evidence_item({
            "reference": "file.md",
            "content": {"status": "COMPLETE", "text": "Authorization: Bearer fake"},
        }, ordering=0)


def test_raw_response_is_exactly_preserved_and_secret_bearing_capture_fails():
    response = {"output": {"classification": "NOT_ESTABLISHED"}, "tokenUsage": {"total": 3}}
    captured = capture_raw_response(response)

    assert captured["rawProviderResponse"] == response
    with pytest.raises(ValueError, match="secret-bearing key"):
        capture_raw_response({"api_key": "fake"})


def test_retry_policy_rejects_retry_after_any_provider_response():
    technical = capture_attempt(
        attempt=1, status="TIMEOUT", provider="fixture", model="fixture", response_present=False,
    )
    response_failure = capture_attempt(
        attempt=1, status="SCHEMA_FAILURE", provider="fixture", model="fixture", response_present=True,
    )

    assert technical_retry_allowed(technical)
    response_retry = capture_attempt(
        attempt=2, status="SUCCEEDED", provider="fixture", model="fixture", response_present=True, retry_of=1,
    )

    assert not technical_retry_allowed(response_failure)
    assert response_retry["retryType"] == "TECHNICAL_RETRY"


def test_gates_fail_fast_and_block_later_gates():
    composed = compose_gates({
        GATE_ORDER[0]: {"gate": GATE_ORDER[0], "status": "PASS", "authority": "test", "reason": None},
        GATE_ORDER[1]: {"gate": GATE_ORDER[1], "status": "FAIL", "authority": "test", "reason": "truncated"},
    })

    assert composed["firstFailure"] == GATE_ORDER[1]
    assert composed["semanticScoringEligible"] is False
    assert all(item["status"] == "NOT_EVALUATED" for item in composed["gates"][2:])
    assert composed["finalSlotStatus"] == "INVALID_STRUCTURAL_OUTPUT"


def test_smoke_circuit_breaker_stops_invalid_slots_but_not_valid_semantic_errors():
    assert smoke_should_stop({"finalSlotStatus": "INVALID_EVIDENCE"}) is True
    assert smoke_should_stop({"finalSlotStatus": "VALID", "semanticWrong": True}) is False


def test_invalid_slots_never_become_semantic_zeroes():
    metric = metric_result(
        value=0.0, eligible=False, observed_denominator=0,
        expected_denominator=2, benchmark_valid=False, invalid_reason="structural failure",
    )

    assert metric["value"] is None
    assert metric["eligible"] is False
    assert metric["benchmarkValid"] is False


def test_slot_artifact_preserves_selected_context_prompt_raw_response_and_attempt():
    slot = _slot()

    assert slot["selectedKnowledgeSnapshot"] == _selected_knowledge()
    assert slot["selectedKnowledgeDigest"]
    assert slot["promptRepresentation"] == {
        "renderingVersion": "v3-test",
        "systemMessage": "system",
        "userMessage": "user",
        "schema": {"type": "object"},
    }
    assert slot["rawProviderResponse"] == {"fixture": True}
    assert slot["attempts"][0]["responsePresent"] is True
    validate_slot_completeness(slot)


def test_tampered_evidence_and_incomplete_slots_fail_closed():
    slot = _slot()
    slot["providerVisibleEvidence"][0]["content"] = "changed"
    with pytest.raises(ValueError, match="bytes changed"):
        validate_evidence_bytes(slot)

    incomplete = dict(_slot())
    del incomplete["promptDigest"]
    with pytest.raises(ValueError, match="incomplete"):
        validate_slot_completeness(incomplete)


def test_benchmark_validity_invalidates_incomplete_or_invalid_denominator():
    slot = _slot()
    assert benchmark_validity([slot], 3) == {
        "status": "INCOMPLETE", "valid": False,
        "reason": "frozen slot denominator is incomplete",
    }
    invalid = dict(slot)
    invalid["finalSlotStatus"] = "INVALID_EVIDENCE"
    assert benchmark_validity([slot, invalid, slot], 3)["valid"] is False


def test_completed_slot_artifact_is_immutable_and_replayable_through_java_core(tmp_path):
    slot = _slot()
    artifact = finalize_run_artifact(candidate_manifest(), evaluation_run_id="run-1", slots=[slot])
    output = tmp_path / "run.json"
    write_immutable_artifact(output, artifact)
    with pytest.raises(FileExistsError):
        write_immutable_artifact(output, artifact)

    replay = replay_slot_through_core(slot, ROOT)
    assert replay["ok"] is True
    assert compare_replay_outcome(slot, replay)["equal"] is True
