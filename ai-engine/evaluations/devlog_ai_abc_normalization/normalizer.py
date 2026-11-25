"""Deterministic, evaluation-only adapters for the DevLog AI A/B/C benchmark.

The adapters intentionally do less than a semantic parser. They copy explicit
structured fields, preserve original references, and report information that
cannot be extracted reliably instead of inferring it.
"""

from __future__ import annotations

import copy
import hashlib
import json
from dataclasses import dataclass
from typing import Any, Iterable

from evaluations.acceptability import classify_acceptability


NORMALIZATION_CONTRACT_VERSION = "devlog-abc-claim-evidence-normalization-v1"

_TECHNICAL_STATUSES = {
    "COMPLETED",
    "PROVIDER_FAILURE",
    "PROVIDER_TIMEOUT",
    "PROVIDER_INCOMPLETE_RESPONSE",
    "RUNTIME_FAILURE",
    "NOT_EVALUATED",
}
_SEMANTIC_ELIGIBILITY = {
    "SEMANTICALLY_ELIGIBLE",
    "NOT_EVALUATED",
    "INELIGIBLE",
}


class NormalizationError(ValueError):
    """Raised when a supplied RAW identity is internally inconsistent."""


@dataclass(frozen=True)
class RawEnvelope:
    """The immutable identity envelope required by a normalization adapter."""

    observation_id: str
    case_id: str
    treatment: str
    repository_revision: str
    payload: dict[str, Any]
    raw_sha256: str | None = None

    @classmethod
    def from_mapping(cls, value: dict[str, Any]) -> "RawEnvelope":
        payload = value.get("rawOutput", value.get("payload", value))
        if not isinstance(payload, dict):
            raise NormalizationError("RAW payload must be an object")
        envelope = cls(
            observation_id=_required_string(value, "observationId"),
            case_id=_required_string(value, "caseId"),
            treatment=_required_string(value, "treatment"),
            repository_revision=_required_string(value, "repositoryRevision"),
            payload=copy.deepcopy(payload),
            raw_sha256=value.get("rawSha256"),
        )
        actual = sha256_json(envelope.payload)
        if envelope.raw_sha256 is not None and envelope.raw_sha256 != actual:
            raise NormalizationError("RAW hash does not match RAW payload")
        return envelope


def normalize_story_context(raw: dict[str, Any]) -> dict[str, Any]:
    """Normalize A or C Story Context output without semantic interpretation."""

    envelope = RawEnvelope.from_mapping(raw)
    container = _final_payload(envelope.payload)
    payload = _story_analysis(container)
    normalized = _base_answer(envelope, "A_DEVLOG_CURRENT" if envelope.treatment == "A" else envelope.treatment)
    normalized["answerText"] = (
        _optional_text(payload, "answer")
        or _optional_text(payload, "answerText")
        or _story_summary(payload)
    )
    if normalized["answerText"] is None:
        normalized["normalizationWarnings"].append("ANSWER_TEXT_NOT_AVAILABLE")

    claims = _story_claims(payload)
    normalized["claims"] = claims
    if any(not claim["evidenceReferences"] for claim in claims):
        normalized["normalizationWarnings"].append("CLAIM_WITHOUT_CITATION")
    normalized["evidenceReferences"] = _evidence_references(
        _story_reference_records(payload), envelope.repository_revision, envelope.payload
    )
    normalized["limitations"] = _story_limitations(payload)
    normalized["abstention"] = _story_abstention(payload, normalized["normalizationWarnings"])
    normalized["confidence"] = _explicit_confidence(payload, normalized["normalizationWarnings"])
    normalized["acceptability"] = classify_acceptability(
        _story_acceptability_observation(envelope.payload, _with_container_freshness(payload, container), normalized)
    )
    _finish(normalized, envelope, container)
    return normalized


def normalize_direct_investigator(raw: dict[str, Any]) -> dict[str, Any]:
    """Normalize the V4 common answer emitted by B."""

    envelope = RawEnvelope.from_mapping(raw)
    payload = _final_payload(envelope.payload)
    normalized = _base_answer(envelope, "B_DIRECT_INVESTIGATOR")
    normalized["answerText"] = _optional_text(payload, "answerText")
    if normalized["answerText"] is None:
        normalized["normalizationWarnings"].append("ANSWER_TEXT_NOT_AVAILABLE")

    normalized["claims"] = _direct_claims(payload, normalized["normalizationWarnings"])
    if any(not claim["evidenceReferences"] for claim in normalized["claims"]):
        normalized["normalizationWarnings"].append("CLAIM_WITHOUT_CITATION")
    normalized["evidenceReferences"] = _evidence_references(
        _direct_reference_records(envelope.payload), envelope.repository_revision, envelope.payload
    )
    normalized["limitations"] = _direct_limitations(payload)
    normalized["abstention"] = _explicit_abstention(payload, normalized["normalizationWarnings"])
    normalized["confidence"] = _explicit_confidence(payload, normalized["normalizationWarnings"])
    normalized["acceptability"] = classify_acceptability(
        _story_acceptability_observation(envelope.payload, payload, normalized)
    )
    _finish(normalized, envelope, payload)
    return normalized


def render_evaluation_display(answer: dict[str, Any]) -> str | None:
    """Render only copied normalized text for human inspection.

    This is deliberately not an answer synthesizer. It keeps explicit answer
    text when present; otherwise it joins exact extracted claim/limitation text
    with newlines and adds no connective prose.
    """

    if isinstance(answer.get("answerText"), str) and answer["answerText"].strip():
        return answer["answerText"]
    lines = [
        claim["statement"]
        for claim in answer.get("claims", [])
        if isinstance(claim, dict) and isinstance(claim.get("statement"), str)
    ]
    lines.extend(
        limitation
        for limitation in answer.get("limitations", [])
        if isinstance(limitation, str) and limitation.strip()
    )
    return "\n".join(lines) if lines else None


def _base_answer(envelope: RawEnvelope, treatment: str) -> dict[str, Any]:
    warnings: list[str] = []
    raw_hash = envelope.raw_sha256 or sha256_json(envelope.payload)
    if envelope.raw_sha256 is None:
        warnings.append("RAW_HASH_NOT_SUPPLIED_COMPUTED_FOR_PROVENANCE")
    return {
        "normalizationContract": NORMALIZATION_CONTRACT_VERSION,
        "observationId": envelope.observation_id,
        "caseId": envelope.case_id,
        "treatment": treatment,
        "repositoryRevision": envelope.repository_revision,
        "technicalStatus": _technical_status(envelope.payload, warnings),
        "semanticEligibility": _semantic_eligibility(envelope.payload, warnings),
        "answerText": None,
        "claims": [],
        "evidenceReferences": [],
        "limitations": [],
        "abstention": None,
        "confidence": None,
        "normalizationWarnings": warnings,
        "rawArtifact": {
            "observationId": envelope.observation_id,
            "rawSha256": raw_hash,
            "rawHashSource": "SUPPLIED" if envelope.raw_sha256 else "COMPUTED_FROM_RAW_PAYLOAD",
        },
    }


def _finish(answer: dict[str, Any], envelope: RawEnvelope, payload: dict[str, Any]) -> None:
    if answer["technicalStatus"] == "COMPLETED" and not answer["claims"] and answer["answerText"] is None:
        answer["normalizationWarnings"].append("NO_EXPLICIT_ASSERTION_EXTRACTED")
    answer["rawArtifact"]["rawPayloadPreserved"] = True
    answer["rawArtifact"]["rawPayloadSha256"] = sha256_json(envelope.payload)
    answer["rawArtifact"]["rawStatusObserved"] = payload.get("executionStatus", payload.get("status"))
    answer["derivedSha256"] = sha256_json({key: value for key, value in answer.items() if key != "derivedSha256"})


def _technical_status(payload: dict[str, Any], warnings: list[str]) -> str:
    value = payload.get("executionStatus", payload.get("technicalStatus", payload.get("status")))
    if value in _TECHNICAL_STATUSES:
        return value
    if value in {"PROVIDER_INCOMPLETE", "INCOMPLETE", "NO_FINAL_ANSWER"}:
        return "PROVIDER_INCOMPLETE_RESPONSE"
    if value is None:
        warnings.append("TECHNICAL_STATUS_NOT_AVAILABLE")
    else:
        warnings.append("TECHNICAL_STATUS_UNRECOGNIZED")
    return "NOT_EVALUATED"


def _semantic_eligibility(payload: dict[str, Any], warnings: list[str]) -> str:
    value = payload.get("semanticEligibility")
    if value in _SEMANTIC_ELIGIBILITY:
        return value
    if value is None:
        warnings.append("SEMANTIC_ELIGIBILITY_NOT_EVALUATED")
    else:
        warnings.append("SEMANTIC_ELIGIBILITY_UNRECOGNIZED")
    return "NOT_EVALUATED"


def _final_payload(payload: dict[str, Any]) -> dict[str, Any]:
    for key in ("finalResponse", "result", "payload"):
        candidate = payload.get(key)
        if isinstance(candidate, dict):
            return candidate
    return payload


_NOT_ESTABLISHED_MARKER = "NOT_ESTABLISHED"


def _story_analysis(container: dict[str, Any]) -> dict[str, Any]:
    """Return the Story Context analysis body.

    The Core MCP execution wraps the persisted result as
    ``finalResponse.analysis.analysis`` next to ``contextFreshness`` and
    ``provenance``; replay fixtures may carry the body directly. Descend only
    through ``analysis`` objects until the analysis contract is reached.
    """

    current = container
    for _ in range(2):
        if "objectiveUnderstanding" in current or "outputClassification" in current:
            return current
        nested = current.get("analysis")
        if not isinstance(nested, dict):
            return current
        current = nested
    return current


def _story_summary(payload: dict[str, Any]) -> str | None:
    understanding = payload.get("objectiveUnderstanding")
    if not isinstance(understanding, dict):
        return None
    return _optional_text(understanding, "summary")


def _story_abstention(payload: dict[str, Any], warnings: list[str]) -> bool | None:
    """Copy explicit abstention, else the protocol's NOT_ESTABLISHED summary marker."""

    if isinstance(payload.get("abstention"), bool):
        return payload["abstention"]
    summary = _story_summary(payload)
    if summary is None:
        warnings.append("ABSTENTION_NOT_EXPLICIT")
        return None
    warnings.append("ABSTENTION_FROM_SUMMARY_MARKER")
    return summary.lstrip().startswith(_NOT_ESTABLISHED_MARKER)


def _with_container_freshness(payload: dict[str, Any], container: dict[str, Any]) -> dict[str, Any]:
    if "freshness" in payload or payload is container:
        return payload
    freshness = container.get("freshness")
    if not isinstance(freshness, dict):
        wrapper = container.get("analysis")
        freshness = wrapper.get("contextFreshness") if isinstance(wrapper, dict) else None
    if not isinstance(freshness, dict):
        return payload
    return {**payload, "freshness": freshness}


def _story_claims(payload: dict[str, Any]) -> list[dict[str, Any]]:
    claims: list[dict[str, Any]] = []
    classifications = {
        item.get("findingReference"): item.get("classification")
        for item in payload.get("outputClassification", {}).get("entries", [])
        if isinstance(item, dict) and item.get("findingReference")
    }

    def add(statement: Any, claim_type: str, source_path: str, references: Iterable[str]) -> None:
        if not isinstance(statement, str) or not statement.strip():
            return
        refs = _unique_strings(references)
        claims.append({
            "claimId": f"claim-{len(claims) + 1:04d}",
            "statement": statement,
            "claimType": claim_type,
            "evidenceReferences": refs,
            "uncertainty": None,
            "sourcePath": source_path,
        })

    for index, item in enumerate(payload.get("causalClaims", [])):
        if isinstance(item, dict):
            add(item.get("explanation"), "INTERPRETATION", f"causalClaims[{index}].explanation", _refs(item))
    assessment = payload.get("causalAssessment")
    if isinstance(assessment, dict):
        add(
            assessment.get("explanation"),
            "INTERPRETATION",
            "causalAssessment.explanation",
            (
                item.get("evidenceReference", {}).get("reference")
                for item in assessment.get("evidenceAssertions", [])
                if isinstance(item, dict) and isinstance(item.get("evidenceReference"), dict)
            ),
        )

    for section, fields in (
        ("architectureFindings", ("description",)),
        ("decisionFindings", ("context", "choice", "rationale")),
        ("evidenceFindings", ("description",)),
        ("historicalContext", ("description",)),
        ("constraintFindings", ("description",)),
        ("impactedComponentFindings", ("impactDescription",)),
    ):
        for index, item in enumerate(payload.get(section, [])):
            if not isinstance(item, dict):
                continue
            ref = f"{section}[{index}]"
            claim_type = _classification_to_claim_type(classifications.get(ref))
            references = _refs_from_grounding(item.get("grounding"))
            for field in fields:
                add(item.get(field), claim_type, f"{ref}.{field}", references)
    return claims


def _direct_claims(payload: dict[str, Any], warnings: list[str]) -> list[dict[str, Any]]:
    raw_claims = payload.get("claims")
    if not isinstance(raw_claims, list):
        warnings.append("CLAIMS_NOT_AVAILABLE")
        return []
    claims: list[dict[str, Any]] = []
    for index, item in enumerate(raw_claims):
        if not isinstance(item, dict) or not isinstance(item.get("text"), str):
            warnings.append(f"CLAIM_{index + 1}_NOT_STRUCTURED")
            continue
        claims.append({
            "claimId": f"claim-{index + 1:04d}",
            "statement": item["text"],
            "claimType": item.get("claimType", "UNCLASSIFIED"),
            "evidenceReferences": _unique_strings(item.get("references", [])),
            "uncertainty": None,
            "sourcePath": f"claims[{index}]",
        })
    return claims


def _story_reference_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for claim in payload.get("causalClaims", []):
        if isinstance(claim, dict):
            records.extend(_refs_as_records(claim.get("evidenceReferences")))
    records.extend(_refs_as_records(payload.get("evidenceReferences")))
    records.extend(_refs_from_all_grounding(payload))
    records.extend(_refs_from_assertions(payload.get("causalAssessment")))
    return records


def _direct_reference_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    candidates = [payload]
    final = payload.get("finalResponse")
    if isinstance(final, dict):
        candidates.append(final)
    for candidate in candidates:
        for claim in candidate.get("claims", []):
            if isinstance(claim, dict):
                records.extend({"reference": ref} for ref in claim.get("references", []) if isinstance(ref, str))
        evidence = candidate.get("evidence")
        if isinstance(evidence, list):
            records.extend(item for item in evidence if isinstance(item, dict) and isinstance(item.get("reference"), str))
    return records


def _evidence_references(records: list[dict[str, Any]], revision: str, raw_payload: dict[str, Any]) -> list[dict[str, Any]]:
    ledger = {
        item.get("reference")
        for item in _raw_evidence_records(raw_payload)
        if isinstance(item.get("reference"), str)
    }
    result: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in records:
        reference = item.get("reference")
        if not isinstance(reference, str) or not reference.strip():
            continue
        if reference in seen:
            existing = next(item for item in result if item["sourceIdentity"] == reference)
            if existing["locator"] is None and item.get("locator") is not None:
                existing["locator"] = copy.deepcopy(item["locator"])
            if existing["revisionSource"] == "ENVELOPE" and item.get("revision"):
                existing["revision"] = item["revision"]
                existing["revisionSource"] = "SOURCE_RECORD"
                existing["citationStatus"] = "REVISION_MISMATCH" if item["revision"] != revision else existing["citationStatus"]
            continue
        seen.add(reference)
        source_revision = item.get("revision")
        citation_status = "RESOLVED_IN_RAW_LEDGER" if reference in ledger else "CITED_NOT_RESOLVED"
        if source_revision and source_revision != revision:
            citation_status = "REVISION_MISMATCH"
        result.append({
            "sourceIdentity": reference,
            "revision": source_revision or revision,
            "revisionSource": "SOURCE_RECORD" if source_revision else "ENVELOPE",
            "locator": copy.deepcopy(item.get("locator")),
            "citationStatus": citation_status,
        })
    return result


def _raw_evidence_records(payload: dict[str, Any]) -> list[dict[str, Any]]:
    candidates = [payload]
    final = payload.get("finalResponse")
    if isinstance(final, dict):
        candidates.append(final)
    for candidate in candidates:
        for key in ("evidence", "evidenceLedger", "evidenceReferences"):
            value = candidate.get(key)
            if isinstance(value, list):
                return [item if isinstance(item, dict) else {"reference": item} for item in value]
    return []


def _story_limitations(payload: dict[str, Any]) -> list[str]:
    limitations: list[str] = []
    for key in ("uncertainties", "missingInformation"):
        for item in payload.get(key, []):
            if isinstance(item, dict) and isinstance(item.get("description"), str):
                limitations.append(item["description"])
    return _unique_strings(limitations)


def _story_acceptability_observation(
    source_payload: dict[str, Any],
    payload: dict[str, Any],
    normalized: dict[str, Any],
) -> dict[str, Any]:
    metadata = source_payload.get("acceptability")
    if not isinstance(metadata, dict):
        metadata = payload.get("acceptability")
    metadata = metadata if isinstance(metadata, dict) else {}

    def value(key: str, default: Any = None) -> Any:
        if key in metadata:
            return metadata[key]
        if key in payload:
            return payload[key]
        return source_payload.get(key, default)

    def has_value(key: str) -> bool:
        return key in metadata or key in payload or key in source_payload

    freshness = value("freshness")
    if isinstance(freshness, dict):
        freshness = freshness.get("state")
    freshness = freshness or "UNKNOWN"
    return {
        "technicalStatus": normalized["technicalStatus"],
        "semanticEligibility": normalized["semanticEligibility"],
        "groundingStatus": value("groundingStatus"),
        "freshness": freshness,
        "temporalScope": value("temporalScope", "CURRENT"),
        "revisionPinned": value("revisionPinned"),
        "expectedEvidenceCount": value("expectedEvidenceCount", 0),
        "coveredEvidenceCount": value("coveredEvidenceCount", 0),
        "contradictionCount": value("contradictionCount", 0),
        "truncated": value("truncated", False),
        "principalConclusionCovered": value("principalConclusionCovered", True),
        "nextStepPresent": value("nextStepPresent"),
        "abstention": normalized["abstention"],
        "unauthorizedEvidenceCount": value(
            "unauthorizedEvidenceCount",
            sum(
                reference.get("citationStatus") == "CITED_NOT_RESOLVED"
                for reference in normalized["evidenceReferences"]
            ),
        ),
        "revisionMismatchCount": sum(
            reference.get("citationStatus") == "REVISION_MISMATCH"
            for reference in normalized["evidenceReferences"]
        ),
        "metadataComplete": all(
            has_value(key)
            for key in (
                "groundingStatus",
                "freshness",
                "expectedEvidenceCount",
                "coveredEvidenceCount",
                "contradictionCount",
                "truncated",
                "principalConclusionCovered",
                "nextStepPresent",
                "revisionPinned",
            )
        ) and normalized["abstention"] is not None,
        "limitations": normalized["limitations"],
    }


def _direct_limitations(payload: dict[str, Any]) -> list[str]:
    value = payload.get("limitations", [])
    return _unique_strings(value) if isinstance(value, list) else []


def _explicit_abstention(payload: dict[str, Any], warnings: list[str]) -> bool | None:
    value = payload.get("abstention")
    if isinstance(value, bool):
        return value
    warnings.append("ABSTENTION_NOT_EXPLICIT")
    return None


def _explicit_confidence(payload: dict[str, Any], warnings: list[str]) -> str | None:
    value = payload.get("confidence")
    if isinstance(value, dict):
        value = value.get("level")
    if value in {"HIGH", "MEDIUM", "LOW"}:
        return value
    if value is not None:
        warnings.append("CONFIDENCE_INVALID")
    else:
        warnings.append("CONFIDENCE_NOT_EXPLICIT")
    return None


def _refs(value: dict[str, Any]) -> list[str]:
    return _unique_strings(item.get("reference") for item in value.get("evidenceReferences", []) if isinstance(item, dict))


def _refs_from_grounding(value: Any) -> list[str]:
    if not isinstance(value, dict):
        return []
    return _refs(value)


def _refs_from_all_grounding(payload: dict[str, Any]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for key in ("architectureFindings", "decisionFindings", "evidenceFindings", "historicalContext", "constraintFindings", "impactedComponentFindings"):
        for item in payload.get(key, []):
            if isinstance(item, dict):
                grounding = item.get("grounding")
                if isinstance(grounding, dict):
                    records.extend(_refs_as_records(grounding.get("evidenceReferences")))
    return records


def _refs_from_assertions(value: Any) -> list[str] | list[dict[str, Any]]:
    if not isinstance(value, dict):
        return []
    assertions = value.get("evidenceAssertions", [])
    records: list[dict[str, Any]] = []
    for item in assertions:
        if not isinstance(item, dict):
            continue
        ref = item.get("evidenceReference")
        if isinstance(ref, dict) and isinstance(ref.get("reference"), str):
            records.append({"reference": ref["reference"], "locator": item.get("locator")})
    return records


def _refs_as_records(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, dict) and isinstance(item.get("reference"), str)]


def _classification_to_claim_type(value: Any) -> str:
    return {
        "FACTUAL_EXTRACTION": "FACT",
        "AI_INTERPRETATION": "INTERPRETATION",
        "RECOMMENDATION": "RECOMMENDATION",
    }.get(value, "UNCLASSIFIED")


def _optional_text(value: dict[str, Any], key: str) -> str | None:
    candidate = value.get(key)
    return candidate if isinstance(candidate, str) and candidate.strip() else None


def _unique_strings(values: Iterable[Any]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for value in values:
        if isinstance(value, str) and value not in seen:
            seen.add(value)
            result.append(value)
    return result


def _required_string(value: dict[str, Any], key: str) -> str:
    result = value.get(key)
    if not isinstance(result, str) or not result:
        raise NormalizationError(f"RAW envelope field is missing: {key}")
    return result


def sha256_json(value: Any) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()
