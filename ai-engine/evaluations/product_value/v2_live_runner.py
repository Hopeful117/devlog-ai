"""Frozen Story0132 V2 live runner.

This module is evaluation-only. It uses the production Python generation service
and applies the same immutable snapshot binding rules as the Core V2 resolver to
make every live assertion independently inspectable in the evaluation capture.
"""

from __future__ import annotations

import argparse
import asyncio
import copy
import hashlib
import json
import re
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from uuid import uuid4

from app.core.config import Settings
from app.models.ai_task import AiTaskType
from app.models.proposal import AiTaskResultStatus, ProposalType
from app.prompts.story_context_analysis import StoryContextAnalysisPromptBuilder
from app.providers.openai import OpenAiLlmProvider
from app.schemas.ai_task import IntentDefinition, PromptRequest
from app.schemas.story_context_analysis import StoryContextAnalysisResult
from app.services.story_context_analysis_generation_service import (
    StoryContextAnalysisGenerationService,
)

from .causal_evaluation import (
    causal_diagnostics,
    evaluate_thresholds,
    remove_causal_evidence_for_question,
)
from .causal_mapping import (
    CASE04_QUESTION_ID,
    FrozenCausalQuestion,
    build_frozen_causal_questions,
    expected_assessment_slots,
    validate_assessment_slots,
)
from .loader import apply_frozen_oracle, load_benchmark
from .repository_ground_truth import build_ground_truth_contexts, resolve_repository


MAPPING_SHA256 = "67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0"
FIRST_GREEN_SHA256 = "011aed5f5c75264f2fea7272c745d8bc9fff45ee8c58b5c74ce8494190b345e1"
ORIGINAL_RED_SHA256 = "bf148c3ed4b5e697478d7dc3325dd7c39a0e8b3ec604fec85e4c1545145f4128"
PINNED_REVISION = "18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149"
PINNED_REPOSITORY_PATH = Path("/home/ludo/Bureau/workspace/trading-os")
REPETITIONS = 3


class CaptureCallback:
    def __init__(self) -> None:
        self.result: Any = None

    async def send_result(self, _correlation_id: Any, result: Any) -> None:
        self.result = result


def _sha256(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _canonical(value: object) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def _resolve_locator(content: str, locator: dict[str, Any]) -> str:
    # Match Java String.split("\\R", -1): retain the trailing empty line so
    # section resolution hashes the same raw bytes as Core.
    lines = re.split(r"\r\n|\r|\n", content)
    kind = locator.get("kind")
    if kind == "LINE_RANGE":
        start = locator.get("startLine")
        end = locator.get("endLine")
        if not isinstance(start, int) or not isinstance(end, int) or start < 1 or end < start:
            raise ValueError("LOCATOR_OUT_OF_BOUNDS")
        if end > len(lines):
            raise ValueError("LOCATOR_OUT_OF_BOUNDS")
        return "\n".join(lines[start - 1:end])
    if kind == "SECTION":
        heading = locator.get("heading")
        if not isinstance(heading, str) or not heading:
            raise ValueError("LOCATOR_RESOLUTION_FAILURE")
        start = None
        level = None
        for index, line in enumerate(lines):
            stripped = line.rstrip()
            if stripped.startswith("#"):
                hashes, _, title = stripped.partition(" ")
                if hashes and set(hashes) == {"#"} and title == heading:
                    start = index
                    level = len(hashes)
                    break
        if start is None or level is None:
            raise ValueError("LOCATOR_RESOLUTION_FAILURE")
        end = len(lines)
        for index in range(start + 1, len(lines)):
            stripped = lines[index].rstrip()
            if stripped.startswith("#"):
                hashes, _, _ = stripped.partition(" ")
                if hashes and set(hashes) == {"#"} and len(hashes) <= level:
                    end = index
                    break
        return "\n".join(lines[start:end])
    raise ValueError("UNSUPPORTED_LOCATOR")


def _bind_assessment(
    result: dict[str, Any], evidence_by_reference: dict[str, dict[str, Any]],
    authorized: set[str],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    assessment = result.get("causalAssessment")
    if not isinstance(assessment, dict):
        raise ValueError("CARDINALITY_FAILURE")
    assertions = assessment.get("evidenceAssertions")
    if not isinstance(assertions, list):
        raise ValueError("EVIDENCE_ASSERTION_VALIDITY_FAILURE")
    seen_assertions: set[tuple[str, str]] = set()
    seen_digests: set[str] = set()
    bound: list[dict[str, Any]] = []
    diagnostics: list[dict[str, Any]] = []
    for assertion in assertions:
        if not isinstance(assertion, dict):
            raise ValueError("EVIDENCE_ASSERTION_VALIDITY_FAILURE")
        evidence_reference = assertion.get("evidenceReference")
        reference = evidence_reference.get("reference") if isinstance(evidence_reference, dict) else None
        if not isinstance(reference, str) or reference not in authorized:
            raise ValueError("REFERENCE_AUTHORIZATION_FAILURE")
        evidence = evidence_by_reference.get(reference)
        if evidence is None:
            raise ValueError("UNKNOWN_REFERENCE")
        content = evidence.get("content", {})
        if not isinstance(content, dict) or content.get("status") != "COMPLETE":
            raise ValueError("LOCATOR_RESOLUTION_FAILURE")
        text = content.get("text")
        if not isinstance(text, str):
            raise ValueError("LOCATOR_RESOLUTION_FAILURE")
        locator = assertion.get("locator")
        if not isinstance(locator, dict):
            raise ValueError("UNSUPPORTED_LOCATOR")
        resolved = _resolve_locator(text, locator)
        key = (reference, _canonical(locator))
        if key in seen_assertions:
            raise ValueError("DUPLICATE_ASSERTION")
        digest = _sha256(resolved)
        if digest in seen_digests:
            raise ValueError("DUPLICATE_ASSERTION")
        if assertion.get("resolvedContentDigest") not in (None, digest):
            raise ValueError("DIGEST_MISMATCH")
        if assertion.get("excerpt") not in (None, resolved):
            raise ValueError("FABRICATED_EXCERPT_MISMATCH")
        seen_assertions.add(key)
        seen_digests.add(digest)
        normalized = copy.deepcopy(assertion)
        normalized["resolvedContent"] = resolved
        normalized["resolvedContentDigest"] = digest
        bound.append(normalized)
        diagnostics.append({
            "reference": reference,
            "locator": locator,
            "resolvedContent": resolved,
            "resolvedContentDigest": digest,
            "modelExcerptTrusted": False,
            "assertionRole": assertion.get("assertionRole"),
        })
    normalized_result = copy.deepcopy(result)
    normalized_result["causalAssessment"]["evidenceAssertions"] = bound
    return normalized_result, diagnostics


def _intent() -> IntentDefinition:
    return IntentDefinition(
        id="engineering-story-context-analysis",
        version="v1",
        objective="Produce a structured, grounded analysis of an Engineering Story context for Discuss/Plan preparation.",
        outputProposalType=ProposalType.NONE,
        executionMode="GENERIC",
        supportedInsightTypes=[],
        constraints=[
            "Use only the provided EngineeringContext.",
            "Never invent project characteristics or present analysis as validated knowledge.",
            "Ground all factual claims with evidence references from the context.",
            "Preserve uncertainty; do not fabricate content to populate sections.",
        ],
        outputSchema=StoryContextAnalysisResult.model_json_schema(),
        promptTemplate="story-context-analysis-prompt-v1",
        contextProfiles=["engineering-story-v1", "project-state-v1", "history-v1"],
    )


def _selected_knowledge(context: dict[str, Any], revision: str) -> dict[str, Any]:
    evidence = []
    for item in context["evidence"]:
        evidence.append({
            "reference": item["reference"],
            "provenance": {"originatingFile": item["reference"]},
            "content": {
                "text": item.get("content", ""),
                "status": "COMPLETE",
                "revision": revision,
            },
        })
    repository_context = {
        "contextVersion": "repository-context-engine-v1",
        "profile": "GROUND_TRUTH_CONTEXT",
        "evidence": evidence,
        "usedTokens": 0,
        "contextDigest": _sha256(_canonical(evidence)),
    }
    selection_digest = _sha256(_canonical(repository_context))
    return {
        "project": {"id": "trading-os", "name": "trading-os", "slug": "trading-os"},
        "analysis": {"id": "story0132-v2-live"},
        "projectProfile": {"id": "story0132-v2-live", "profileVersion": "v1"},
        "selectedFacts": [],
        "selectedObservations": [],
        "diagnostics": {"collectionComplete": True, "truncated": False, "warningCount": 0, "errorCount": 0},
        "selectedInsights": [],
        "existingArchitectureKnowledge": [],
        "selectionMetadata": {"selectionVersion": "story0132-v2-frozen-ground-truth"},
        "selectionDigest": selection_digest,
        "repositoryContext": repository_context,
        "engineeringStories": [],
    }


def _submission(
    selected_knowledge: dict[str, Any], question: FrozenCausalQuestion,
) -> PromptRequest:
    intent = _intent()
    grounding = {
        "allowedEvidenceReferences": [item["reference"] for item in selected_knowledge["repositoryContext"]["evidence"]],
        "causalAnswerRequired": True,
        "causalContractVersion": "V2",
        "causalQuestion": {
            "source": question.source,
            "target": question.target,
            "relationAsked": question.relation_asked,
            "answerRequired": question.answer_required,
        },
    }
    return PromptRequest(
        requestId=uuid4(),
        correlationId=uuid4(),
        analysisId=uuid4(),
        aiTaskId=uuid4(),
        taskType=AiTaskType.STORY_CONTEXT_ANALYSIS,
        intent=intent,
        userGuidance=None,
        selectedKnowledge=selected_knowledge,
        expectedOutputContract=intent.output_schema,
        groundingContract=grounding,
        metadata={"story": "0132", "evaluation": "v2-frozen-live"},
    )


async def _generate(
    provider: OpenAiLlmProvider,
    submission: PromptRequest,
) -> tuple[dict[str, Any] | None, list[dict[str, Any]], str | None]:
    callback = CaptureCallback()
    service = StoryContextAnalysisGenerationService(
        provider=provider,
        prompt_builder=StoryContextAnalysisPromptBuilder(),
        callback_client=callback,
    )
    await service.process(submission, uuid4())
    if callback.result is None:
        return None, [], "RUNNER_CALLBACK_FAILURE"
    traces = [trace.model_dump(mode="json", by_alias=True) for trace in callback.result.interaction_traces]
    if callback.result.status is not AiTaskResultStatus.COMPLETED or callback.result.analysis_result is None:
        error = callback.result.error.message if callback.result.error else "INVALID_LLM_OUTPUT"
        return None, traces, error
    return callback.result.analysis_result.model_dump(mode="json", by_alias=True), traces, None


def _case_result(
    case: dict[str, Any], question: FrozenCausalQuestion, repetition: int,
    result: dict[str, Any] | None, traces: list[dict[str, Any]], error: str | None,
    diagnostics: list[dict[str, Any]], failure: str | None,
) -> dict[str, Any]:
    assessment = result.get("causalAssessment") if result else None
    actual_question = assessment.get("question") if isinstance(assessment, dict) else None
    actual_classification = assessment.get("classification") if isinstance(assessment, dict) else None
    roles = [
        assertion.get("assertionRole")
        for assertion in (assessment.get("evidenceAssertions", []) if isinstance(assessment, dict) else [])
        if isinstance(assertion, dict)
    ]
    return {
        "caseId": question.case_id,
        "questionId": question.question_id,
        "claimId": question.claim_id,
        "repetition": repetition,
        "expectedClassification": question.expected_classification,
        "actualClassification": actual_classification,
        "question": actual_question,
        "normalizedResult": result,
        "assertionCount": len(assessment.get("evidenceAssertions", [])) if isinstance(assessment, dict) else 0,
        "validAssertionCount": len(diagnostics),
        "causalReasoningAccuracy": 1.0 if actual_classification == question.expected_classification else 0.0 if actual_classification else None,
        "groundingValid": failure is None,
        "causalOverclaimRate": 1.0 if question.expected_classification == "NOT_ESTABLISHED" and actual_classification != "NOT_ESTABLISHED" else 0.0 if question.expected_classification == "NOT_ESTABLISHED" else None,
        "abstentionAccuracy": 1.0 if question.expected_classification == "NOT_ESTABLISHED" and actual_classification == "NOT_ESTABLISHED" else 0.0 if question.expected_classification == "NOT_ESTABLISHED" else None,
        "roleAdmissibilityRate": 1.0 if roles else 0.0,
        "causalClaimSignature": [(question.question_id, actual_classification, tuple(roles))],
        "evidenceAssertions": diagnostics,
        "evidenceAssertionValidity": failure is None,
        "failureCategory": failure,
        "validationError": error,
        "interactionTraces": traces,
    }


def _core_failure_category(error: str) -> str:
    if "Invalid authoritative evidence assertion" in error:
        return "CAPTURED_PROVIDER_EVIDENCE_ASSERTION_FAILURE"
    if "Generated evidence excerpt" in error:
        return "FABRICATED_EXCERPT_MISMATCH"
    if "Line locator" in error:
        return "LOCATOR_OUT_OF_BOUNDS"
    if "Section heading" in error or "section" in error.lower() and "present" in error.lower():
        return "LOCATOR_RESOLUTION_FAILURE"
    if "Reference is not authorized" in error or "unauthorized" in error.lower():
        return "REFERENCE_AUTHORIZATION_FAILURE"
    return "CONTRACT_FAILURE"


def _core_diagnostics(result: dict[str, Any]) -> list[dict[str, Any]]:
    assessment = result.get("causalAssessment", {})
    diagnostics = []
    for assertion in assessment.get("evidenceAssertions", []):
        diagnostics.append({
            "reference": assertion["evidenceReference"]["reference"],
            "locator": assertion["locator"],
            "resolvedContent": assertion.get("resolvedContent"),
            "resolvedContentDigest": assertion.get("resolvedContentDigest"),
            "modelExcerptTrusted": False,
            "assertionRole": assertion.get("assertionRole"),
        })
    return diagnostics


def _validate_through_core(
    runs: list[dict[str, Any]], contexts: dict[str, Any], benchmark: dict[str, Any],
) -> dict[str, Any]:
    def historical_task_context_digest(run: dict[str, Any]) -> str:
        traces = run.get("interactionTraces") or []
        digests = {
            trace.get("selectedKnowledgeFingerprint")
            for trace in traces
            if isinstance(trace, dict) and trace.get("selectedKnowledgeFingerprint")
        }
        if len(digests) != 1:
            raise ValueError(
                "Historical task context digest must be present and unambiguous"
            )
        return next(iter(digests))

    context_by_case = {item["caseId"]: item["context"] for item in contexts["cases"]}
    questions = build_frozen_causal_questions(benchmark)
    selected_by_case = {
        case_id: _selected_knowledge(context_by_case[case_id], benchmark["repositoryRevision"])
        for case_id in context_by_case
    }
    items = []
    provenance_corrections = 0
    for index, run in enumerate(runs):
        if run["normalizedResult"] is None:
            continue
        question = next(item for item in questions[run["caseId"]]
                        if item.question_id == run["questionId"])
        selected = selected_by_case[run["caseId"]]
        historical_digest = historical_task_context_digest(run)
        result = copy.deepcopy(run["normalizedResult"])
        callback_digest = result.get("provenance", {}).get("contextDigest")
        if callback_digest != historical_digest:
            result["provenance"]["contextDigest"] = historical_digest
            provenance_corrections += 1
            run["replayProvenanceCorrection"] = {
                "type": "CALLBACK_PROVENANCE_CONTEXT_DIGEST_TO_HISTORICAL_TASK_IDENTITY",
                "originalCallbackDigest": callback_digest,
                "historicalTaskDigest": historical_digest,
                "semanticOutputChanged": False,
            }
        items.append({
            "index": index,
            "result": result,
            "selectedKnowledge": selected,
            # The frozen trace is the historical task-boundary identity. The
            # reconstructed selected knowledge remains only for evidence binding.
            "contextDigest": historical_digest,
            "groundingContract": {
                "allowedEvidenceReferences": [
                    item["reference"] for item in selected["repositoryContext"]["evidence"]
                ],
                "causalAnswerRequired": True,
                "causalContractVersion": "V2",
                "causalQuestion": {
                    "source": question.source,
                    "target": question.target,
                    "relationAsked": question.relation_asked,
                    "answerRequired": question.answer_required,
                },
            },
        })
    with tempfile.TemporaryDirectory(prefix="story0132-core-bridge-") as directory:
        input_path = Path(directory) / "input.json"
        output_path = Path(directory) / "output.json"
        input_path.write_text(json.dumps({"items": items}), encoding="utf-8")
        root = Path(__file__).resolve().parents[3]
        subprocess.run([
            str(root / "backend" / "mvnw"), "-q", "-pl", "backend", "-am", "test",
            "-Dtest=CoreV2EvaluationBridgeTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            f"-Dstory0132.bridge.input={input_path}",
            f"-Dstory0132.bridge.output={output_path}",
        ], cwd=root, check=True, timeout=180)
        bridge_items = json.loads(output_path.read_text(encoding="utf-8"))["items"]
    by_index = {item["index"]: item for item in bridge_items}
    errors: dict[str, int] = {}
    transformations: dict[str, int] = {}
    for index, run in enumerate(runs):
        if run["normalizedResult"] is None:
            continue
        bridged = by_index[index]
        transformation = bridged.get("deterministicTransformation")
        if transformation:
            transformations[transformation] = transformations.get(transformation, 0) + 1
        if bridged["ok"]:
            run["normalizedResult"] = bridged["result"]
            assessment = bridged["result"].get("causalAssessment", {})
            actual_classification = assessment.get("classification")
            roles = [item.get("assertionRole") for item in assessment.get("evidenceAssertions", [])]
            run["actualClassification"] = actual_classification
            run["question"] = assessment.get("question")
            run["causalReasoningAccuracy"] = (
                1.0 if actual_classification == run["expectedClassification"]
                else 0.0 if actual_classification else None
            )
            run["causalOverclaimRate"] = (
                1.0 if run["expectedClassification"] == "NOT_ESTABLISHED"
                and actual_classification != "NOT_ESTABLISHED"
                else 0.0 if run["expectedClassification"] == "NOT_ESTABLISHED" else None
            )
            run["abstentionAccuracy"] = (
                1.0 if run["expectedClassification"] == "NOT_ESTABLISHED"
                and actual_classification == "NOT_ESTABLISHED"
                else 0.0 if run["expectedClassification"] == "NOT_ESTABLISHED" else None
            )
            run["roleAdmissibilityRate"] = 1.0 if roles else 0.0
            run["causalClaimSignature"] = [(run["questionId"], actual_classification, tuple(roles))]
            run["evidenceAssertions"] = _core_diagnostics(bridged["result"])
            run["validAssertionCount"] = len(run["evidenceAssertions"])
            run["assertionCount"] = len(bridged["result"].get("causalAssessment", {}).get("evidenceAssertions", []))
            run["failureCategory"] = None
            run["groundingValid"] = True
        else:
            run["failureCategory"] = _core_failure_category(bridged.get("error", ""))
            run["coreValidationError"] = bridged.get("error")
            error = bridged.get("error", "UNKNOWN_CORE_VALIDATION_FAILURE")
            errors[error] = errors.get(error, 0) + 1
            run["groundingValid"] = False
            run["actualClassification"] = None
            run["question"] = None
            run["causalReasoningAccuracy"] = None
            run["causalOverclaimRate"] = None
            run["abstentionAccuracy"] = None
            run["roleAdmissibilityRate"] = 0.0
            run["causalClaimSignature"] = [(run["questionId"], None, tuple())]
            run["evidenceAssertions"] = []
            run["validAssertionCount"] = 0
    return {
        "processedByJavaCore": len(bridge_items),
        "acceptedByJavaCore": sum(item.get("ok") is True for item in bridge_items),
        "rejectedByJavaCore": sum(item.get("ok") is not True for item in bridge_items),
        "deterministicTransformations": transformations,
        "javaErrors": errors,
        "historicalTaskContextDigestSource": "interactionTrace.selectedKnowledgeFingerprint",
        "replayContextStrategy": "historical_task_identity_plus_reconstructed_evidence_snapshot",
        "replayProvenanceCorrection": (
            "callback_provenance_context_digest_to_historical_task_identity"
            if provenance_corrections else "none"
        ),
        "replayProvenanceCorrections": provenance_corrections,
    }


def _aggregate(baseline: dict[str, Any]) -> dict[str, Any]:
    runs = baseline["runs"]
    positive = [run for run in runs if run["caseId"] != "CASE-04"]
    case04 = [run for run in runs if run["caseId"] == "CASE-04"]
    assertion_count = sum(run["assertionCount"] for run in runs)
    valid_assertion_count = sum(run["validAssertionCount"] for run in runs)
    diagnostics = causal_diagnostics(runs)
    thresholds = evaluate_thresholds(
        runs,
        stability=diagnostics["causalClaimStability"],
        expected_positive_total=30,
    )
    failures = {}
    for run in runs:
        category = run.get("failureCategory")
        if category:
            failures[category] = failures.get(category, 0) + 1
    return {
        "positiveCausalCorrect": sum(run.get("causalReasoningAccuracy") == 1.0 for run in positive),
        "positiveCausalTotal": len(positive),
        "positiveCausalAccuracy": thresholds["positiveCausalAccuracy"],
        "case04NotEstablished": thresholds["case04NotEstablishedRuns"],
        "case04Total": len(case04),
        "grounding": thresholds["groundingPass"],
        "unsupportedInference": thresholds["unsupportedInferencePass"],
        "causalOverclaimRate": diagnostics["causalOverclaimRate"],
        "abstentionAccuracy": diagnostics["abstentionAccuracy"],
        "roleAdmissibilityRate": diagnostics["roleAdmissibilityRate"],
        "causalClaimStability": diagnostics["causalClaimStability"],
        "evidenceAssertionValidityRate": (
            valid_assertion_count / assertion_count if assertion_count else 0.0
        ),
        "assertionTraceabilityRate": (
            valid_assertion_count / assertion_count if assertion_count else 0.0
        ),
        "referenceAuthorization": not any(
            category in failures for category in {"REFERENCE_AUTHORIZATION_FAILURE", "UNKNOWN_REFERENCE"}
        ),
        "locatorResolution": not any(
            category in failures for category in {"LOCATOR_OUT_OF_BOUNDS", "LOCATOR_RESOLUTION_FAILURE", "UNSUPPORTED_LOCATOR"}
        ),
        "failureCounts": failures,
        "thresholds": thresholds,
    }


async def run_live(
    benchmark: dict[str, Any], contexts: dict[str, Any], settings: Settings,
) -> dict[str, Any]:
    provider = OpenAiLlmProvider(
        api_key=settings.llm_api_key or "",
        model=settings.llm_model,
        timeout_seconds=settings.llm_timeout_seconds,
        max_output_tokens=settings.llm_max_output_tokens,
        max_retries=settings.llm_max_retries,
    )
    questions = build_frozen_causal_questions(benchmark)
    context_by_case = {item["caseId"]: item["context"] for item in contexts["cases"]}
    runs: list[dict[str, Any]] = []
    provider_calls = 0
    technical_retries = 0
    for case in benchmark["cases"]:
        case_id = case["caseId"]
        base_context = context_by_case[case_id]
        selected = _selected_knowledge(base_context, benchmark["repositoryRevision"])
        evidence_by_reference = {
            item["reference"]: item for item in selected["repositoryContext"]["evidence"]
        }
        authorized = set(evidence_by_reference)
        for repetition in range(1, REPETITIONS + 1):
            for question in questions[case_id]:
                submission = _submission(selected, question)
                provider_calls += 1
                result, traces, error = await _generate(provider, submission)
                normalized = result
                diagnostics: list[dict[str, Any]] = []
                failure = None
                if normalized is not None:
                    pass
                else:
                    failure = "CONTRACT_FAILURE"
                runs.append(_case_result(case, question, repetition, normalized, traces, error, diagnostics, failure))
    _validate_through_core(runs, contexts, benchmark)
    return {
        "runs": runs,
        "providerCalls": provider_calls,
        "technicalRetries": technical_retries,
        "expectedSlots": sum(expected_assessment_slots(benchmark, REPETITIONS).values()),
    }


async def run_evidence_removal(
    benchmark: dict[str, Any], contexts: dict[str, Any], settings: Settings,
    baseline: dict[str, Any],
) -> dict[str, Any]:
    case = next(item for item in benchmark["cases"] if item["caseId"] == "CASE-01")
    question = next(item for item in build_frozen_causal_questions(benchmark)["CASE-01"] if item.claim_id == "CL-01")
    baseline_run = next(item for item in baseline["runs"] if item["questionId"] == question.question_id and item["repetition"] == 1)
    removed = {item["reference"] for item in baseline_run["evidenceAssertions"]}
    context = next(item["context"] for item in contexts["cases"] if item["caseId"] == "CASE-01")
    variant = remove_causal_evidence_for_question(
        {"evidence": [{"reference": item["reference"]} for item in context["evidence"]]},
        case_id="CASE-01", question_id=question.question_id, references=removed,
    )
    reduced_context = {
        "condition": context["condition"],
        "contextVersion": context["contextVersion"],
        "evidence": [item for item in context["evidence"] if item["reference"] not in removed],
    }
    selected = _selected_knowledge(reduced_context, benchmark["repositoryRevision"])
    provider = OpenAiLlmProvider(
        api_key=settings.llm_api_key or "", model=settings.llm_model,
        timeout_seconds=settings.llm_timeout_seconds,
        max_output_tokens=settings.llm_max_output_tokens,
        max_retries=settings.llm_max_retries,
    )
    result, traces, error = await _generate(provider, _submission(selected, question))
    return {
        "caseId": "CASE-01",
        "questionId": question.question_id,
        "removedReferenceIds": sorted(removed),
        "baselineContextHash": _sha256(_canonical(context)),
        "removedEvidenceContextHash": _sha256(_canonical(reduced_context)),
        "variantMetadata": {"caseId": variant["caseId"], "questionId": variant["questionId"]},
        "actualClassification": result.get("causalAssessment", {}).get("classification") if result else None,
        "result": result,
        "error": error,
        "interactionTraces": [trace for trace in traces],
    }


def _preflight(args: argparse.Namespace, settings: Settings) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    if args.repository.resolve() != PINNED_REPOSITORY_PATH:
        raise RuntimeError(
            f"PINNED_REPOSITORY_PATH_MISMATCH:{args.repository.resolve()}"
        )
    mapping_path = args.mapping
    mapping_hash = hashlib.sha256(mapping_path.read_bytes()).hexdigest()
    if mapping_hash != MAPPING_SHA256:
        raise RuntimeError(f"FROZEN_MAPPING_HASH_MISMATCH:{mapping_hash}")
    if hashlib.sha256(args.first_green.read_bytes()).hexdigest() != FIRST_GREEN_SHA256:
        raise RuntimeError("FIRST_GREEN_HASH_MISMATCH")
    if hashlib.sha256(args.original_red.read_bytes()).hexdigest() != ORIGINAL_RED_SHA256:
        raise RuntimeError("ORIGINAL_RED_LIVE_HASH_MISMATCH")
    benchmark = apply_frozen_oracle(load_benchmark(args.benchmark), json.loads(args.oracle.read_text()))
    if benchmark["repositoryRevision"] != PINNED_REVISION:
        raise RuntimeError("PINNED_REVISION_MISMATCH")
    manifest = resolve_repository(benchmark, args.repository)
    if manifest["status"] != "VALID" or manifest["resolvedArtifactCount"] != 19:
        raise RuntimeError("GROUND_TRUTH_RESOLUTION_FAILED")
    contexts = build_ground_truth_contexts(benchmark, args.repository)
    questions = build_frozen_causal_questions(benchmark)
    if sum(len(items) for items in questions.values()) != 11:
        raise RuntimeError("QUESTION_COUNT_MISMATCH")
    if settings.llm_provider != "openai" or settings.llm_model != "gpt-4.1-mini" or not settings.llm_api_key:
        raise RuntimeError("FROZEN_PROVIDER_CONFIGURATION_MISMATCH")
    if sum(expected_assessment_slots(benchmark, REPETITIONS).values()) != 33:
        raise RuntimeError("ASSESSMENT_SLOT_COUNT_MISMATCH")
    return benchmark, contexts, {
        "mappingHash": mapping_hash,
        "firstGreenHash": FIRST_GREEN_SHA256,
        "originalRedHash": ORIGINAL_RED_SHA256,
        "pinnedRevision": PINNED_REVISION,
        "groundTruthResolution": "19/19",
        "provider": settings.llm_provider,
        "model": settings.llm_model,
        "cases": 4,
        "questions": 11,
        "repetitions": REPETITIONS,
        "expectedSlots": 33,
    }


async def _run(args: argparse.Namespace) -> int:
    settings = Settings.from_environment()
    benchmark, contexts, preflight = _preflight(args, settings)
    if args.preflight_only:
        print(json.dumps({"status": "PRE_LIVE_PREFLIGHT_PASS", **preflight}, sort_keys=True))
        return 0
    run_id = f"story0132-v2-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}"
    baseline = await run_live(benchmark, contexts, settings)
    records = [
        {"caseId": run["caseId"], "questionId": run["questionId"], "repetition": run["repetition"]}
        for run in baseline["runs"]
    ]
    slot_errors = validate_assessment_slots(benchmark, records, REPETITIONS)
    aggregate = _aggregate(baseline)
    evidence_removal = await run_evidence_removal(benchmark, contexts, settings, baseline)
    artifact = {
        "artifactVersion": "story0132-v2-live-1.0.0",
        "runId": run_id,
        "capturedAt": datetime.now(timezone.utc).isoformat(),
        "story": "0132",
        "phase": "V2_FROZEN_LIVE_EVALUATION",
        "preflight": preflight,
        "baseline": baseline,
        "aggregate": aggregate,
        "slotValidationErrors": slot_errors,
        "evidenceRemoval": evidence_removal,
        "humanUtility": "NOT_YET_ESTABLISHED",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({
        "runId": run_id,
        "baselineCalls": baseline["providerCalls"],
        "expectedSlots": baseline["expectedSlots"],
        "observedSlots": len(baseline["runs"]),
        "slotErrors": slot_errors,
        "output": str(args.output),
    }, sort_keys=True))
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--benchmark", type=Path, required=True)
    parser.add_argument("--oracle", type=Path, required=True)
    parser.add_argument("--repository", type=Path, required=True)
    parser.add_argument("--mapping", type=Path, required=True)
    parser.add_argument("--first-green", type=Path, required=True)
    parser.add_argument("--original-red", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--preflight-only", action="store_true")
    args = parser.parse_args()
    return asyncio.run(_run(args))


if __name__ == "__main__":
    raise SystemExit(main())
