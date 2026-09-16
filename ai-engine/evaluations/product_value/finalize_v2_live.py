"""Finalize and deterministically rescore a captured Story0132 V2 run."""

from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .v2_live_runner import _aggregate


def finalize(source: Path, output: Path, technical_retries: int) -> dict[str, Any]:
    raw = json.loads(source.read_text(encoding="utf-8"))
    raw_capture = raw.get("rawCapture", raw)
    aggregate = _aggregate(raw["baseline"])
    removal = dict(raw["evidenceRemoval"])
    if not removal.get("removedReferenceIds"):
        removal["status"] = "BLOCKED_NO_VALID_BASELINE_ASSERTION"
    else:
        removal["status"] = "NOT_RESCORED"
    runs = raw["baseline"]["runs"]
    failure_counts = aggregate["failureCounts"]
    core_validated = raw.get("coreValidation", {}).get("status") == "CORE_VALIDATED"
    core_errors = raw.get("coreValidation", {}).get("javaErrors", {})
    captured_evidence_failure = core_validated and bool(core_errors) and all(
        "Invalid authoritative evidence assertion" in error for error in core_errors
    )
    result = {
        "artifactVersion": (
            "story0132-v2-live-replay-pinned-evidence-correction-1.0.0"
            if core_validated else "story0132-v2-live-result-1.0.0"
        ),
        "runId": raw["runId"],
        "sourceCaptureSha256": raw.get("coreValidation", {}).get(
            "sourceCaptureSha256", hashlib.sha256(source.read_bytes()).hexdigest()
        ),
        "capturedAt": raw["capturedAt"],
        "replayedAt": datetime.now(timezone.utc).isoformat(),
        "replaySource": "EXISTING_FROZEN_LIVE_CAPTURE" if core_validated else None,
        "newProviderCalls": 0 if core_validated else None,
        "replayProviderCalls": 0 if core_validated else None,
        "story": "0132",
        "phase": "V2_FROZEN_LIVE_EVALUATION",
        "provider": raw["preflight"]["provider"],
        "model": raw["preflight"]["model"],
        "mappingHash": raw["preflight"]["mappingHash"],
        "firstGreenHash": raw["preflight"]["firstGreenHash"],
        "originalRedHash": raw["preflight"]["originalRedHash"],
        "pinnedRevision": raw["preflight"]["pinnedRevision"],
        "replayRepository": raw.get("coreValidation", {}).get("replayRepository"),
        "replayRepositoryCommit": raw.get("coreValidation", {}).get("replayRepositoryCommit"),
        "pinnedRepositoryMatch": raw.get("coreValidation", {}).get("pinnedRepositoryMatch"),
        "pythonJavaNewlineCanonicalization": raw.get("coreValidation", {}).get(
            "pythonJavaNewlineCanonicalization"
        ),
        "groundTruthResolution": raw["preflight"]["groundTruthResolution"],
        "cases": raw["preflight"]["cases"],
        "questions": raw["preflight"]["questions"],
        "repetitionsPerCase": raw["preflight"]["repetitions"],
        "expectedAssessmentSlots": raw["preflight"]["expectedSlots"],
        "observedAssessmentSlots": len(runs),
        "duplicateSlots": 0,
        "missingSlots": len(raw.get("slotValidationErrors", [])),
        "unknownSlots": 0,
        "benchmarkCompleteness": "COMPLETE" if not raw.get("slotValidationErrors") else "PARTIAL",
        "baselineProviderCalls": raw["baseline"]["providerCalls"],
        "evidenceRemovalProviderCalls": 1,
        "liveProviderCalls": raw["baseline"]["providerCalls"] + 1,
        "technicalRetries": technical_retries,
        "runs": runs,
        "aggregate": aggregate,
        "failureTaxonomy": failure_counts,
        "evidenceRemoval": removal,
        "coreValidation": raw.get("coreValidation"),
        "modelCapabilityCandidate": False,
        "primaryFailure": (
            "CAPTURED_PROVIDER_EVIDENCE_ASSERTION_FAILURE"
            if captured_evidence_failure
            else "JAVA_CORE_CONTRACT_DESERIALIZATION_FAILURE"
            if core_validated else "CONTRACT_FAILURE"
        ),
        "contributingFailures": sorted(failure_counts),
        "v2LiveClassification": (
            "V2_LIVE_CAPTURED_PROVIDER_EVIDENCE_ASSERTION_FAILURE"
            if captured_evidence_failure
            else "V2_LIVE_CORE_CONTRACT_FAILURE"
            if core_validated else "V2_LIVE_EXECUTED_ARCHITECTURE_FAILURE"
        ),
        "story0132Status": (
            "V2_LIVE_CAPTURED_PROVIDER_EVIDENCE_ASSERTION_FAILURE"
            if captured_evidence_failure
            else "V2_LIVE_CORE_CONTRACT_FAILURE"
            if core_validated else "V2_LIVE_EXECUTED_ARCHITECTURE_FAILURE"
        ),
        "resultRescoringDeterministic": True,
        "semanticScoringAuthorized": not core_validated or (
            raw.get("coreValidation", {}).get("acceptedByJavaCore") == 33
        ),
        "semanticPositiveCausalAccuracy": "NOT_ESTABLISHED" if core_validated else None,
        "semanticCase04NotEstablished": "NOT_ESTABLISHED" if core_validated else None,
        "semanticStability": "NOT_ESTABLISHED" if core_validated else None,
        "replayCoreAccepted": raw.get("coreValidation", {}).get("acceptedByJavaCore"),
        "replayCoreRejected": (
            raw.get("coreValidation", {}).get("rejectedByJavaCore", 0)
            + raw.get("coreValidation", {}).get("unparsedPythonSlots", 0)
            if core_validated else None
        ),
        "javaCoreProcessedSlots": raw.get("coreValidation", {}).get("processedByJavaCore"),
        "humanUtility": "NOT_YET_ESTABLISHED",
        "promptChanged": False,
        "retrievalChanged": False,
        "contextChanged": False,
        "ragChanged": False,
        "thresholdsChanged": False,
        "contractReconciliation": {
            "authority": "JAVA_CORE",
            "semanticType": "HIGH | MEDIUM | LOW",
            "canonicalWireRepresentation": "JSON string enum value",
            "pythonInternalRepresentation": "Confidence(level, rationale)",
            "javaExpectedRepresentation": "Confidence enum deserialized from JSON string",
            "deterministicTransformations": [
                "confidence.level_to_enum_string",
                "outputClassification.array_to_entries_wrapper",
            ],
            "outputClassification": {
                "semanticType": "finding classification reference set",
                "cardinality": "zero_or_more_entries",
                "canonicalWireRepresentation": "{entries: [...]}"
            },
            "relationType": {
                "decision": "DECISION_A",
                "genericNullable": True,
                "relationshipBearingRequired": True,
                "nonRelationshipBearingOptional": True,
                "allowedValues": [
                    "EXPLICIT",
                    "TEMPORAL_PROXIMITY",
                    "POSSIBLE_RELEVANCE",
                    "INFERRED_HYPOTHESIS",
                ],
            },
        },
        "outputClassificationReconciliationApplied": True,
        "relationTypeReconciliationApplied": True,
        "relationTypeReplayTransformation": "NONE",
        "defaultRelationTypeIntroduced": False,
        "historicalRelationTypeEnrichment": False,
        "nextCoreContractFailure": (
            raw.get("coreValidation", {}).get("javaErrors") if core_validated else None
        ),
        "relationTypeNowFirstBlocker": (
            core_validated and any(
                "relationType must not be null" in error
                for error in raw.get("coreValidation", {}).get("javaErrors", {})
            )
        ),
        "contextDigestNowFirstBlocker": (
            core_validated and bool(raw.get("coreValidation", {}).get("javaErrors"))
            and all("Context digest mismatch" in error
                    for error in raw.get("coreValidation", {}).get("javaErrors", {}))
        ),
        "providerStructurallyInvalidSlots": (
            raw.get("coreValidation", {}).get("unparsedPythonSlots") if core_validated else None
        ),
        "semanticOutputChanged": False,
        "rawCaptureChanged": False,
        "case04SpecialPrompt": False,
        "rawCapture": raw_capture,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--technical-retries", type=int, required=True)
    args = parser.parse_args()
    result = finalize(args.source, args.output, args.technical_retries)
    print(json.dumps({
        "status": "FINALIZED",
        "output": str(args.output),
        "observedAssessmentSlots": result["observedAssessmentSlots"],
        "positiveCausalAccuracy": result["aggregate"]["positiveCausalAccuracy"],
        "case04NotEstablished": result["aggregate"]["case04NotEstablished"],
        "evidenceRemoval": result["evidenceRemoval"]["status"],
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
