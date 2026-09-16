"""Authorized, sequential V3 live smoke runner.

This module is evaluation-only. It uses the frozen manifest as the execution
authority, performs no corrective semantic retry, and checkpoints each
finalized slot before proceeding to the next slot.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
from pathlib import Path
from typing import Any
from uuid import uuid4

from openai import APIConnectionError, APIStatusError, APITimeoutError
from pydantic import ValidationError
from openai.lib._parsing._responses import type_to_text_format_param

from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.openai import OpenAiLlmProvider
from app.schemas.story_context_analysis import StoryContextAnalysisResult

from .causal_mapping import FrozenCausalQuestion, build_frozen_causal_questions
from .loader import apply_frozen_oracle, load_benchmark
from .repository_ground_truth import build_ground_truth_contexts, resolve_repository
from .v2_live_runner import _selected_knowledge, _submission
from .v3_protocol import (
    GATE_ORDER,
    V3_MAPPING_HASH,
    build_slot_artifact,
    build_v3_openai_provider,
    canonical,
    capture_attempt,
    capture_prompt_representation,
    compare_replay_outcome,
    compose_gates,
    finalize_run_artifact,
    replay_slot_through_core,
    resolve_v3_provider_configuration,
    safe_metadata,
    sha256_text,
    smoke_should_stop,
    validate_manifest,
    write_immutable_artifact,
)
from .v3_structured_output import (
    ProviderParseFailure,
    ProviderStoryContextAnalysisResult,
    capture_then_parse_openai_response,
    provider_result_to_internal,
    provider_schema,
)


ROOT = Path(__file__).resolve().parents[3]
TRADING_OS = Path("/home/ludo/Bureau/workspace/trading-os")
MANIFEST_PATH = ROOT / "ai-engine/evaluations/product_value/v3/benchmark-manifest.json"
BENCHMARK_PATH = ROOT / "docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json"
ORACLE_PATH = ROOT / "docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/oracle-freeze-v1.json"
MAPPING_PATH = ROOT / "docs/stories/0132-evidence-grounded-causal-interpretation/v2-frozen-causal-question-mapping.md"


def _load_manifest() -> dict[str, Any]:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    validate_manifest(manifest)
    if manifest["mappingHash"] != V3_MAPPING_HASH:
        raise ValueError("V3 mapping hash mismatch")
    if manifest["mappingHumanApproval"] != "YES":
        raise ValueError("V3 mapping approval is not frozen")
    return manifest


def _question(questions: dict[str, tuple[FrozenCausalQuestion, ...]], candidate: dict[str, Any]) -> FrozenCausalQuestion:
    for item in questions[candidate["caseId"]]:
        if item.question_id == candidate["questionId"]:
            return item
    raise ValueError(f"unknown smoke question: {candidate['questionId']}")


def _exception_status(error: Exception) -> str:
    if isinstance(error, APITimeoutError):
        return "TIMEOUT"
    if isinstance(error, APIConnectionError):
        return "NETWORK_FAILURE"
    if isinstance(error, APIStatusError):
        if error.status_code == 429:
            return "RATE_LIMITED"
        if error.status_code >= 500:
            return "PROVIDER_UNAVAILABLE"
    return "PROVIDER_EXECUTION_FAILURE"


def _response_from_structural_error(error: Exception) -> str | None:
    if not isinstance(error, ValidationError):
        return None
    for detail in error.errors():
        value = detail.get("input")
        if isinstance(value, str):
            return value
    return None


def _reference_authorization(result: dict[str, Any], authorized: set[str]) -> tuple[bool, str | None]:
    assessment = result.get("causalAssessment")
    if not isinstance(assessment, dict):
        return False, "causal assessment missing"
    assertions = assessment.get("evidenceAssertions")
    if not isinstance(assertions, list):
        return False, "evidence assertions missing"
    for assertion in assertions:
        reference = assertion.get("evidenceReference") if isinstance(assertion, dict) else None
        value = reference.get("reference") if isinstance(reference, dict) else None
        if not isinstance(value, str) or value not in authorized:
            return False, f"unauthorized evidence reference: {value!r}"
    return True, None


def _provider_projection(submission: Any) -> dict[str, Any]:
    return submission.model_dump(mode="json", by_alias=True)


def _schema(prompt: Any) -> dict[str, Any]:
    schema = prompt.expected_output_schema
    provider_json_schema = provider_schema()
    return {
        "identifier": "StoryContextAnalysisResult",
        "version": "story-context-analysis-prompt-v1",
        "digest": sha256_text(canonical(schema)),
        "jsonSchema": schema,
        "providerSchemaDigest": sha256_text(canonical(provider_json_schema)),
        "providerJsonSchema": provider_json_schema,
    }


def _summary(
    slots: list[dict[str, Any]], candidates: list[dict[str, Any]],
    provider_calls: int, technical_retries: int,
) -> dict[str, Any]:
    expected_by_question = {
        candidate["questionId"]: candidate["expectedClassification"]
        for candidate in candidates
    }
    return {
        "expectedProviderCalls": 3,
        "actualProviderCalls": provider_calls,
        "maximumProviderCalls": 6,
        "technicalRetries": technical_retries,
        "slotCount": len(slots),
        "infrastructureResult": "PASS" if len(slots) == 3 and all(
            slot.get("offlineReplay", {}).get("equal") is True for slot in slots
        ) else "FAIL",
        "modelOutputResult": (
            "NOT_EVALUABLE" if not slots or not any(slot["semanticScoringEligible"] for slot in slots)
            else "PASS" if all(
                slot["parsedResponse"]["causalAssessment"]["classification"]
                == expected_by_question[slot["questionId"]]
                for slot in slots if slot["semanticScoringEligible"]
            ) else "PARTIAL"
        ),
    }


def _write_checkpoint(path: Path, run_id: str, slot: dict[str, Any]) -> None:
    checkpoint: dict[str, Any] = {
        "artifactVersion": "story0132-v3-slot-checkpoint-1.0.0",
        "evaluationRunId": run_id,
        "slot": slot,
        "immutable": True,
    }
    checkpoint["artifactSha256"] = sha256_text(canonical(checkpoint))
    write_immutable_artifact(path, checkpoint)


async def _execute_slot(
    *, manifest: dict[str, Any], candidate: dict[str, Any], question: FrozenCausalQuestion,
    contexts: dict[str, Any], provider: OpenAiLlmProvider, run_id: str,
    provider_configuration: dict[str, Any], provider_calls: list[int],
    technical_retries: list[int], repository_root: Path,
) -> dict[str, Any]:
    context = next(item["context"] for item in contexts["cases"] if item["caseId"] == candidate["caseId"])
    selected = _selected_knowledge(context, manifest["repositoryRevision"])
    submission = _submission(selected, question)
    prompt = StoryContextAnalysisPromptBuilder().build(submission)
    prompt_representation = capture_prompt_representation(
        system_message=prompt.system_message,
        user_message=prompt.user_message,
        schema=prompt.expected_output_schema,
        rendering_version=StoryContextAnalysisPromptBuilder.BUILDER_VERSION,
    )
    schema = _schema(prompt)
    projection = _provider_projection(submission)
    context_digest = selected["selectionDigest"]
    evidence_refs = {item["reference"] for item in selected["repositoryContext"]["evidence"]}
    attempts: list[dict[str, Any]] = []
    parsed: dict[str, Any] | None = None
    raw_response: str | dict[str, Any] | None = None
    provider_capture: dict[str, Any] | None = None
    error: Exception | None = None
    attempt_number = 1

    while True:
        provider_calls[0] += 1
        try:
            response = await provider._client.responses.create(
                model=provider.model_identifier,
                input=[
                    {"role": "system", "content": prompt.system_message},
                    {"role": "user", "content": prompt.user_message},
                ],
                text={"format": type_to_text_format_param(ProviderStoryContextAnalysisResult)},
                max_output_tokens=provider._max_output_tokens,
            )
            captured, parsed_response = capture_then_parse_openai_response(
                response, ProviderStoryContextAnalysisResult,
            )
            provider_capture = {
                "providerTextOutput": captured.provider_text_output,
                "providerMetadata": captured.provider_metadata,
                "rawResponseSha256": captured.raw_response_capture["rawResponseSha256"],
            }
            internal = provider_result_to_internal(parsed_response.output_parsed)
            parsed = internal.model_dump(mode="json", by_alias=True)
            raw_response = captured.raw_provider_response
            attempts.append(capture_attempt(
                attempt=attempt_number,
                status="SUCCEEDED",
                provider=provider.provider_name,
                model=provider.model_identifier,
                response_present=True,
                token_usage={
                    **(captured.provider_metadata.get("usage") or {}),
                },
                retry_of=attempt_number - 1 if attempt_number > 1 else None,
            ))
            break
        except Exception as caught:  # provider failures are classified before any retry
            error = caught
            if isinstance(caught, ProviderParseFailure):
                provider_capture = {
                    "providerTextOutput": caught.capture.provider_text_output,
                    "providerMetadata": caught.capture.provider_metadata,
                    "rawResponseSha256": caught.capture.raw_response_capture["rawResponseSha256"],
                }
                raw_response = caught.capture.raw_provider_response
                status = "STRUCTURAL_FAILURE"
                response_present = True
            else:
                status = _exception_status(caught)
                response_present = False
            attempts.append(capture_attempt(
                attempt=attempt_number,
                status=status,
                provider=provider.provider_name,
                model=provider.model_identifier,
                response_present=response_present,
                error=safe_metadata(str(caught)),
                retry_of=attempt_number - 1 if attempt_number > 1 else None,
            ))
            if isinstance(caught, ProviderParseFailure):
                break
            if status not in {"TIMEOUT", "NETWORK_FAILURE", "PROVIDER_UNAVAILABLE", "RATE_LIMITED"}:
                break
            if attempt_number >= manifest["providerConfiguration"]["maximumTotalAttemptsPerSlot"]:
                break
            technical_retries[0] += 1
            attempt_number += 1

    transport_ok = parsed is not None or raw_response is not None
    structural_ok = parsed is not None
    context_ok = bool(parsed and parsed.get("provenance", {}).get("contextDigest") == context_digest)
    reference_ok, reference_reason = _reference_authorization(parsed, evidence_refs) if parsed else (False, "no response")
    checks: dict[str, dict[str, Any]] = {
        GATE_ORDER[0]: {"gate": GATE_ORDER[0], "status": "PASS" if transport_ok else "FAIL", "authority": "evaluation provider adapter", "reason": None if transport_ok else str(error)},
        GATE_ORDER[1]: {"gate": GATE_ORDER[1], "status": "PASS" if structural_ok else "FAIL", "authority": "frozen Python schema contract", "reason": None if structural_ok else "no response-bearing structured output"},
        GATE_ORDER[2]: {"gate": GATE_ORDER[2], "status": "PASS" if context_ok else "FAIL", "authority": "Java Core task identity and evaluation artifact", "reason": None if context_ok else "provider context digest differs from frozen SelectedKnowledge"},
        GATE_ORDER[3]: {"gate": GATE_ORDER[3], "status": "PASS" if reference_ok else "FAIL", "authority": "Java Core reference mapping and grounding contract", "reason": reference_reason},
    }
    for gate in GATE_ORDER[4:]:
        checks[gate] = {"gate": gate, "status": "NOT_EVALUATED", "authority": "evaluation", "reason": "pending Core validation"}

    core_validation: dict[str, Any] = {"status": "NOT_EVALUATED", "authority": "Java Core authoritative causal validator"}
    evidence_validation: dict[str, Any] = {"status": "NOT_EVALUATED", "authority": "Java Core TaskSnapshotEvidenceResolver"}
    offline_replay: dict[str, Any] = {"equal": False, "status": "NOT_EVALUATED"}

    common = {
        "manifest": manifest,
        "evaluation_run_id": run_id,
        "candidate": candidate,
        "provider": provider.provider_name,
        "model": provider.model_identifier,
        "provider_configuration": provider_configuration,
        "repository_identity": manifest["repositoryId"],
        "ai_task_id": str(submission.ai_task_id),
        "correlation_id": str(submission.correlation_id),
        "context_digest": context_digest,
        "selected_knowledge_snapshot": selected,
        "provider_projection": projection,
        "prompt_representation": prompt_representation,
        "schema": schema,
        "raw_response": raw_response if raw_response is not None else {},
        "parsed_response": parsed,
        "attempts": attempts,
    }

    if all(checks[gate]["status"] == "PASS" for gate in GATE_ORDER[:4]):
        for gate in GATE_ORDER[4:]:
            checks[gate] = {"gate": gate, "status": "PASS", "authority": "Java Core authoritative validation", "reason": None}
        provisional = build_slot_artifact(
            **common,
            gates=compose_gates(checks),
            core_validation={"status": "PASS", "authority": "Java Core authoritative causal validator"},
            evidence_validation={"status": "PASS", "authority": "Java Core TaskSnapshotEvidenceResolver"},
        )
        core_result = replay_slot_through_core(provisional, repository_root)
        if core_result.get("ok") is True:
            core_validation = {"status": "PASS", "authority": "Java Core authoritative causal validator", "result": core_result}
            evidence_validation = {"status": "PASS", "authority": "Java Core TaskSnapshotEvidenceResolver"}
        else:
            reason = core_result.get("error", "Core validation rejected response")
            core_validation = {"status": "FAIL", "authority": "Java Core authoritative causal validator", "error": reason}
            evidence_validation = {"status": "FAIL", "authority": "Java Core TaskSnapshotEvidenceResolver", "error": reason}
            for gate in GATE_ORDER[4:]:
                checks[gate] = {"gate": gate, "status": "FAIL", "authority": "Java Core authoritative validation", "reason": reason}

    gates = compose_gates(checks)
    slot = build_slot_artifact(
        **common,
        gates=gates,
        core_validation=core_validation,
        evidence_validation=evidence_validation,
    )
    for item in slot["providerVisibleEvidence"]:
        item["repositoryIdentity"] = manifest["repositoryId"]
        item["repositoryRevision"] = manifest["repositoryRevision"]
    if provider_capture is not None:
        slot["providerCapture"] = provider_capture

    if gates["semanticScoringEligible"]:
        replay_result = replay_slot_through_core(slot, repository_root)
        offline_replay = compare_replay_outcome(slot, replay_result)
        offline_replay["status"] = "PASS" if offline_replay["equal"] else "FAIL"
        slot["offlineReplay"] = offline_replay
        if not offline_replay["equal"]:
            raise RuntimeError(f"offline replay mismatch for {candidate['questionId']}")
    else:
        slot["offlineReplay"] = offline_replay
    return slot


async def run_smoke(*, output_directory: Path, repository_root: Path = TRADING_OS) -> dict[str, Any]:
    manifest = _load_manifest()
    provider_configuration = resolve_v3_provider_configuration(manifest)
    canonical_schema = provider_schema()
    if sha256_text(canonical(canonical_schema)) != manifest["providerSchemaDigest"]:
        raise RuntimeError("provider schema digest mismatch")
    confidence_schema = canonical_schema.get("properties", {}).get("confidence")
    if confidence_schema != {"enum": ["HIGH", "MEDIUM", "LOW"], "title": "Confidence", "type": "string"}:
        raise RuntimeError("provider confidence schema is not the canonical string enum")
    api_key = os.environ.get("LLM_API_KEY")
    if not api_key:
        raise RuntimeError("LLM_API_KEY is required for the authorized live smoke")
    if repository_root.resolve() != TRADING_OS.resolve():
        raise RuntimeError(f"pinned repository path mismatch: {repository_root.resolve()}")

    benchmark = apply_frozen_oracle(
        load_benchmark(BENCHMARK_PATH), json.loads(ORACLE_PATH.read_text(encoding="utf-8")),
    )
    if benchmark["repositoryRevision"] != manifest["repositoryRevision"]:
        raise RuntimeError("benchmark and manifest revision mismatch")
    resolution = resolve_repository(benchmark, repository_root)
    if resolution["status"] != "VALID" or resolution["resolvedArtifactCount"] != 19:
        raise RuntimeError("pinned ground-truth repository resolution failed")
    contexts = build_ground_truth_contexts(benchmark, repository_root)
    questions = build_frozen_causal_questions(benchmark)
    expected = [("CASE-01", "CASE-01::CL-01"), ("CASE-04", "CASE-04::CASE-04"), ("CASE-03", "CASE-03::CL-09")]
    actual = [(item["caseId"], item["questionId"]) for item in manifest["smokeCandidates"]]
    if actual != expected:
        raise RuntimeError(f"smoke candidate mismatch: {actual}")

    provider = build_v3_openai_provider(manifest, api_key=api_key)
    run_id = str(uuid4())
    output_directory.mkdir(parents=True, exist_ok=True)
    slots: list[dict[str, Any]] = []
    provider_calls = [0]
    technical_retries = [0]
    for index, candidate in enumerate(manifest["smokeCandidates"], start=1):
        slot = await _execute_slot(
            manifest=manifest,
            candidate=candidate,
            question=_question(questions, candidate),
            contexts=contexts,
            provider=provider,
            run_id=run_id,
            provider_configuration=provider_configuration,
            provider_calls=provider_calls,
            technical_retries=technical_retries,
            repository_root=repository_root,
        )
        slots.append(slot)
        _write_checkpoint(output_directory / f"{run_id}.slot-{index:02d}.json", run_id, slot)
        if smoke_should_stop(slot):
            break

    summary = _summary(slots, manifest["smokeCandidates"], provider_calls[0], technical_retries[0])
    artifact = finalize_run_artifact(manifest, evaluation_run_id=run_id, slots=slots)
    artifact["smokeSummary"] = summary
    artifact.pop("artifactSha256", None)
    artifact["artifactSha256"] = sha256_text(canonical(artifact))
    artifact_name = f"{run_id}.json" if len(slots) == 3 else f"{run_id}.partial.json"
    artifact_path = output_directory / artifact_name
    write_immutable_artifact(artifact_path, artifact)
    return {"artifactPath": str(artifact_path), "artifact": artifact}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=ROOT / "ai-engine/evaluations/product_value/v3/smoke",
    )
    args = parser.parse_args()
    result = asyncio.run(run_smoke(output_directory=args.output_directory))
    print(json.dumps({
        "status": "V3_SMOKE_COMPLETE",
        "artifactPath": result["artifactPath"],
        "artifactSha256": result["artifact"]["artifactSha256"],
        "providerCalls": result["artifact"]["smokeSummary"]["actualProviderCalls"],
        "technicalRetries": result["artifact"]["smokeSummary"]["technicalRetries"],
        "slotCount": result["artifact"]["smokeSummary"]["slotCount"],
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
