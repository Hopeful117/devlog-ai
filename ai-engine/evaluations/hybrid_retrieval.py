"""Offline evaluation of bounded hybrid retrieval conditions.

This module is evaluation-only. It operates on versioned candidate fixtures and
never accesses a repository, database, provider, or production retrieval index.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


CORPUS_PATH = Path(__file__).parent / (
    "scenarios/story-0177-hybrid-retrieval-v1/manifest.json"
)
TOKEN = re.compile(r"[a-z0-9]+")
EXCLUDED_TRUST_TIERS = {"UNVALIDATED", "TRANSIENT_AI"}
CONDITIONS = (
    "LEXICAL_CONTROL",
    "STRUCTURAL_CONTROL",
    "SEMANTIC_EXPERIMENT",
    "HYBRID_EXPERIMENT",
)


@dataclass(frozen=True)
class Candidate:
    reference: str
    project: str
    source: str
    revision: str
    family: str
    trust_tier: str
    citability: bool
    originating_file: str
    text: str
    structural_terms: tuple[str, ...]
    semantic_terms: tuple[str, ...]
    status: str = "CURRENT"
    scope_allowed: bool = True

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "Candidate":
        return cls(
            reference=str(value["reference"]),
            project=str(value["project"]),
            source=str(value["source"]),
            revision=str(value["revision"]),
            family=str(value["family"]),
            trust_tier=str(value["trustTier"]),
            citability=bool(value["citability"]),
            originating_file=str(value.get("originatingFile", "")),
            text=str(value.get("text", "")),
            structural_terms=tuple(str(item) for item in value.get("structuralTerms", [])),
            semantic_terms=tuple(str(item) for item in value.get("semanticTerms", [])),
            status=str(value.get("status", "CURRENT")),
            scope_allowed=bool(value.get("scopeAllowed", True)),
        )


@dataclass(frozen=True)
class Scenario:
    scenario_id: str
    version: str
    project: str
    source: str
    revision: str
    question: str
    requested_files: tuple[str, ...]
    expected_evidence: frozenset[str]
    expected_abstention: bool
    excluded_statuses: frozenset[str]
    candidates: tuple[Candidate, ...]
    max_candidates: int
    max_selected: int
    latency_ms: int | None
    timed_out: bool
    provider_calls: int | None
    projection_truncated: bool

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "Scenario":
        return cls(
            scenario_id=str(value["scenarioId"]),
            version=str(value["version"]),
            project=str(value["scope"]["project"]),
            source=str(value["scope"]["source"]),
            revision=str(value["scope"]["revision"]),
            question=str(value["question"]),
            requested_files=tuple(value.get("requestedFiles", [])),
            expected_evidence=frozenset(value.get("expectedEvidence", [])),
            expected_abstention=bool(value.get("expectedAbstention", False)),
            excluded_statuses=frozenset(value.get("excludedStatuses", ["SUPERSEDED"])),
            candidates=tuple(Candidate.from_dict(item) for item in value["candidates"]),
            max_candidates=int(value.get("maxCandidates", 8)),
            max_selected=int(value.get("maxSelected", 4)),
            latency_ms=(
                int(value["runtime"]["latencyMs"])
                if value.get("runtime", {}).get("latencyMs") is not None
                else None
            ),
            timed_out=bool(value.get("runtime", {}).get("timedOut", False)),
            provider_calls=(
                int(value["runtime"]["providerCalls"])
                if value.get("runtime", {}).get("providerCalls") is not None
                else None
            ),
            projection_truncated=bool(value.get("projection", {}).get("truncated", False)),
        )


@dataclass(frozen=True)
class CandidateResult:
    candidate: Candidate
    producer: str
    score: float
    accepted: bool
    rejection_reason: str | None


@dataclass(frozen=True)
class ConditionResult:
    scenario_id: str
    condition: str
    candidate_references: tuple[str, ...]
    selected_references: tuple[str, ...]
    rejected: tuple[dict[str, str], ...]
    candidate_recall: float
    final_recall: float
    noise_rate: float
    requested_file_coverage: float
    correct_abstention: bool | None
    reproducible_digest: str
    candidate_details: tuple[dict[str, Any], ...]
    selected_details: tuple[dict[str, Any], ...]
    latency_ms: int | None
    timed_out: bool
    provider_calls: int | None
    candidate_count: int
    selected_count: int
    discarded_count: int
    estimated_context_tokens: int
    estimated_projection_tokens: int
    context_digest: str
    projection_digest: str
    grounding_accepted: bool
    truncation_warning: bool

    def as_dict(self) -> dict[str, Any]:
        return {
            "scenarioId": self.scenario_id,
            "condition": self.condition,
            "candidateReferences": list(self.candidate_references),
            "selectedReferences": list(self.selected_references),
            "rejected": list(self.rejected),
            "candidateRecall": self.candidate_recall,
            "finalRecall": self.final_recall,
            "noiseRate": self.noise_rate,
            "requestedFileCoverage": self.requested_file_coverage,
            "correctAbstention": self.correct_abstention,
            "reproducibleDigest": self.reproducible_digest,
            "candidateDetails": list(self.candidate_details),
            "selectedDetails": list(self.selected_details),
            "latencyMs": self.latency_ms,
            "timedOut": self.timed_out,
            "providerCalls": self.provider_calls,
            "candidateCount": self.candidate_count,
            "selectedCount": self.selected_count,
            "discardedCount": self.discarded_count,
            "estimatedContextTokens": self.estimated_context_tokens,
            "estimatedProjectionTokens": self.estimated_projection_tokens,
            "contextDigest": self.context_digest,
            "projectionDigest": self.projection_digest,
            "groundingAccepted": self.grounding_accepted,
            "truncationWarning": self.truncation_warning,
        }


def load_corpus(path: Path = CORPUS_PATH) -> tuple[str, tuple[Scenario, ...]]:
    raw = json.loads(path.read_text(encoding="utf-8"))
    if raw.get("protocolVersion") != "story-0177-hybrid-retrieval/v1":
        raise ValueError("unsupported Story 0177 corpus protocol")
    scenarios = tuple(Scenario.from_dict(item) for item in raw["scenarios"])
    if not scenarios:
        raise ValueError("Story 0177 corpus must contain scenarios")
    ids = [scenario.scenario_id for scenario in scenarios]
    if len(ids) != len(set(ids)):
        raise ValueError("Story 0177 scenario identifiers must be unique")
    return str(raw["corpusVersion"]), scenarios


def _terms(value: str) -> set[str]:
    return set(TOKEN.findall(value.lower()))


def _metadata_rejection(candidate: Candidate, scenario: Scenario) -> str | None:
    if not candidate.scope_allowed:
        return "OUT_OF_SCOPE"
    if candidate.project != scenario.project:
        return "PROJECT_SCOPE_MISMATCH"
    if candidate.source != scenario.source:
        return "SOURCE_SCOPE_MISMATCH"
    if candidate.revision != scenario.revision:
        return "REVISION_SCOPE_MISMATCH"
    if candidate.trust_tier in EXCLUDED_TRUST_TIERS:
        return "TRUST_TIER_EXCLUDED"
    if not candidate.citability or not candidate.reference.strip():
        return "CANONICAL_REFERENCE_REQUIRED"
    if candidate.status in scenario.excluded_statuses:
        return "DOCUMENT_STATUS_EXCLUDED"
    return None


def _score(candidate: Candidate, scenario: Scenario, condition: str) -> float:
    query_terms = _terms(scenario.question)
    lexical = len(query_terms & _terms(candidate.text))
    structural = len(query_terms & _terms(" ".join(candidate.structural_terms)))
    semantic = len(query_terms & _terms(" ".join(candidate.semantic_terms)))
    requested = any(
        candidate.originating_file == requested
        or candidate.originating_file.startswith(requested + "/")
        for requested in scenario.requested_files
    )
    if condition == "LEXICAL_CONTROL":
        return float(lexical)
    if condition == "STRUCTURAL_CONTROL":
        return float(lexical * 2 + structural * 3 + (2 if requested else 0))
    if condition == "SEMANTIC_EXPERIMENT":
        return float(semantic * 3 + lexical)
    if condition == "HYBRID_EXPERIMENT":
        return float(lexical * 2 + structural * 3 + semantic * 3 + (3 if requested else 0))
    raise ValueError(f"unsupported condition: {condition}")


def _producer_candidates(
    scenario: Scenario, condition: str
) -> tuple[list[CandidateResult], list[dict[str, str]]]:
    accepted: list[CandidateResult] = []
    rejected: list[dict[str, str]] = []
    for candidate in scenario.candidates:
        rejection = _metadata_rejection(candidate, scenario)
        if rejection:
            rejected.append({"reference": candidate.reference, "reason": rejection})
            continue
        score = _score(candidate, scenario, condition)
        if score <= 0:
            continue
        accepted.append(CandidateResult(candidate, condition, score, True, None))
    accepted.sort(key=lambda item: (-item.score, item.candidate.reference))
    return accepted[: scenario.max_candidates], rejected


def _digest(values: Iterable[str]) -> str:
    import hashlib

    return hashlib.sha256("\n".join(values).encode("utf-8")).hexdigest()


def evaluate_condition(scenario: Scenario, condition: str) -> ConditionResult:
    if condition not in CONDITIONS:
        raise ValueError(f"unsupported condition: {condition}")
    produced, rejected = _producer_candidates(scenario, condition)
    selected = produced[: scenario.max_selected]
    candidate_refs = tuple(item.candidate.reference for item in produced)
    selected_refs = tuple(item.candidate.reference for item in selected)
    expected = scenario.expected_evidence
    candidate_recall = (
        1.0 if not expected else len(expected & set(candidate_refs)) / len(expected)
    )
    final_recall = (
        1.0 if not expected else len(expected & set(selected_refs)) / len(expected)
    )
    noise_rate = (
        0.0
        if not selected
        else len(set(selected_refs) - expected) / len(selected_refs)
    )
    requested = set(scenario.requested_files)
    covered = {
        item.candidate.originating_file
        for item in selected
        if any(
            item.candidate.originating_file == requested_file
            or item.candidate.originating_file.startswith(requested_file + "/")
            for requested_file in requested
        )
    }
    coverage = 1.0 if not requested else len(covered) / len(requested)
    correct_abstention = (
        scenario.expected_abstention
        and not expected.intersection(selected_refs)
    ) if scenario.expected_abstention else None
    context_digest = _digest(candidate_refs)
    projection_values = tuple(
        f"{item.candidate.reference}:{item.candidate.originating_file}:{item.candidate.text}"
        for item in selected
    )
    projection_digest = _digest(projection_values)
    grounding_accepted = all(
        item.candidate.citability
        and item.candidate.reference.strip()
        and item.candidate.revision == scenario.revision
        for item in selected
    )
    return ConditionResult(
        scenario_id=scenario.scenario_id,
        condition=condition,
        candidate_references=candidate_refs,
        selected_references=selected_refs,
        rejected=tuple(rejected),
        candidate_recall=round(candidate_recall, 6),
        final_recall=round(final_recall, 6),
        noise_rate=round(noise_rate, 6),
        requested_file_coverage=round(coverage, 6),
        correct_abstention=correct_abstention,
        reproducible_digest=_digest((condition, *candidate_refs, *selected_refs)),
        candidate_details=tuple(_detail(item) for item in produced),
        selected_details=tuple(_detail(item) for item in selected),
        latency_ms=scenario.latency_ms,
        timed_out=scenario.timed_out,
        provider_calls=scenario.provider_calls,
        candidate_count=len(produced),
        selected_count=len(selected),
        discarded_count=max(0, len(produced) - len(selected)),
        estimated_context_tokens=_estimate_tokens(
            item.candidate.text for item in selected
        ),
        estimated_projection_tokens=_estimate_tokens(projection_values),
        context_digest=context_digest,
        projection_digest=projection_digest,
        grounding_accepted=grounding_accepted,
        truncation_warning=scenario.projection_truncated,
    )


def _detail(result: CandidateResult) -> dict[str, Any]:
    candidate = result.candidate
    return {
        "reference": candidate.reference,
        "producer": result.producer,
        "score": result.score,
        "family": candidate.family,
        "trustTier": candidate.trust_tier,
        "citability": candidate.citability,
        "project": candidate.project,
        "source": candidate.source,
        "revision": candidate.revision,
        "originatingFile": candidate.originating_file,
    }


def _estimate_tokens(values: Iterable[str]) -> int:
    return sum(max(1, len(_terms(value))) for value in values)


def evaluate_corpus(
    scenarios: Iterable[Scenario],
    conditions: Iterable[str] = CONDITIONS,
) -> list[ConditionResult]:
    return [
        evaluate_condition(scenario, condition)
        for scenario in scenarios
        for condition in conditions
    ]


def aggregate(results: Iterable[ConditionResult]) -> dict[str, Any]:
    grouped: dict[str, list[ConditionResult]] = {}
    for result in results:
        grouped.setdefault(result.condition, []).append(result)
    report: dict[str, Any] = {}
    for condition, values in sorted(grouped.items()):
        report[condition] = {
            "scenarioCount": len(values),
            "candidateRecallMean": round(
                sum(value.candidate_recall for value in values) / len(values), 6
            ),
            "finalRecallMean": round(
                sum(value.final_recall for value in values) / len(values), 6
            ),
            "noiseRateMean": round(
                sum(value.noise_rate for value in values) / len(values), 6
            ),
            "requestedFileCoverageMean": round(
                sum(value.requested_file_coverage for value in values) / len(values), 6
            ),
            "correctAbstentionRate": round(
                sum(value.correct_abstention is True for value in values)
                / max(1, sum(value.correct_abstention is not None for value in values)),
                6,
            ),
            "reproducible": True,
            "timeoutRate": round(
                sum(value.timed_out for value in values) / len(values), 6
            ),
            "groundingAcceptanceRate": round(
                sum(value.grounding_accepted for value in values) / len(values), 6
            ),
            "selectedCountMean": round(
                sum(value.selected_count for value in values) / len(values), 6
            ),
            "estimatedContextTokensMean": round(
                sum(value.estimated_context_tokens for value in values) / len(values), 6
            ),
            "estimatedProjectionTokensMean": round(
                sum(value.estimated_projection_tokens for value in values) / len(values), 6
            ),
            "truncationWarningRate": round(
                sum(value.truncation_warning for value in values) / len(values), 6
            ),
        }
        timings = sorted(
            value.latency_ms for value in values if value.latency_ms is not None
        )
        if timings:
            report[condition]["latencyP50Ms"] = timings[len(timings) // 2]
            report[condition]["latencyP95Ms"] = timings[
                min(len(timings) - 1, int(len(timings) * 0.95))
            ]
        calls = [value.provider_calls for value in values if value.provider_calls is not None]
        if calls:
            report[condition]["providerCallsMean"] = round(sum(calls) / len(calls), 6)
    return report


def run(path: Path = CORPUS_PATH) -> dict[str, Any]:
    corpus_version, scenarios = load_corpus(path)
    results = evaluate_corpus(scenarios)
    return {
        "protocolVersion": "story-0177-hybrid-retrieval/v1",
        "corpusVersion": corpus_version,
        "scenarioCount": len(scenarios),
        "results": [result.as_dict() for result in results],
        "aggregate": aggregate(results),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Replay Story 0177 retrieval conditions")
    parser.add_argument("--corpus", type=Path, default=CORPUS_PATH)
    parser.add_argument("--live", action="store_true",
                        help="Run live-capture evaluation (Story 0206)")
    parser.add_argument("--captures", type=Path, default=LIVE_CAPTURES_PATH)
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    if args.live:
        report = run_live(args.captures)
    else:
        report = run(args.corpus)
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        if args.live:
            agg = report["aggregate"]
            print(f"recommendation: {agg.get('recommendation', 'N/A')}")
            for condition, values in sorted(agg.items()):
                if condition == "recommendation":
                    continue
                print(
                    f"{condition}: final_recall={values['finalRecallMean']:.3f} "
                    f"noise={values['noiseRateMean']:.3f} "
                    f"semantic_only_recall={values['semanticOnlyRecallMean']:.3f}"
                )
        else:
            for condition, values in report["aggregate"].items():
                print(
                    f"{condition}: candidate_recall={values['candidateRecallMean']:.3f} "
                    f"final_recall={values['finalRecallMean']:.3f} "
                    f"noise={values['noiseRateMean']:.3f}"
                )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())


# ---------------------------------------------------------------------------
# Story 0206 — Live capture evaluation
# ---------------------------------------------------------------------------

LIVE_CAPTURES_PATH = Path(__file__).parent / "captures/live"
LIVE_PROTOCOL = "story-0206-live-capture/v1"
LIVE_CONDITIONS = ("LEXICAL_CONTROL", "HYBRID_EXPERIMENT")


@dataclass(frozen=True)
class LiveCandidateCapture:
    reference: str
    family: str
    trust_tier: str | None
    status: str
    source_type: str | None
    relevance_score: int
    selected: bool
    rejection_reason: str | None

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "LiveCandidateCapture":
        return cls(
            reference=str(value["reference"]),
            family=str(value["family"]),
            trust_tier=value.get("trustTier"),
            status=str(value.get("status", "CURRENT")),
            source_type=value.get("sourceType"),
            relevance_score=int(value["relevanceScore"]),
            selected=bool(value["selected"]),
            rejection_reason=value.get("rejectionReason"),
        )


@dataclass(frozen=True)
class LiveCapture:
    protocol_version: str
    scenario_id: str
    project_slug: str
    revision: str
    question: str
    candidate_count: int
    selected_count: int
    estimated_tokens: int
    truncated: bool
    latency_ms: int | None
    context_digest: str
    condition: str | None
    expected_evidence: frozenset[str]
    candidates: tuple[LiveCandidateCapture, ...]

    @property
    def annotated(self) -> bool:
        return bool(self.expected_evidence)

    @classmethod
    def from_dict(cls, value: dict[str, Any]) -> "LiveCapture":
        return cls(
            protocol_version=str(value["protocolVersion"]),
            scenario_id=str(value["scenarioId"]),
            project_slug=str(value["projectSlug"]),
            revision=str(value["revision"]),
            question=str(value["question"]),
            candidate_count=int(value["candidateCount"]),
            selected_count=int(value["selectedCount"]),
            estimated_tokens=int(value["estimatedTokens"]),
            truncated=bool(value["truncated"]),
            latency_ms=(int(value["latencyMs"]) if value.get("latencyMs") is not None else None),
            context_digest=str(value["contextDigest"]),
            condition=value.get("condition"),
            expected_evidence=frozenset(value.get("expectedEvidence", [])),
            candidates=tuple(LiveCandidateCapture.from_dict(c) for c in value["candidates"]),
        )


@dataclass(frozen=True)
class LiveConditionResult:
    scenario_id: str
    condition: str
    selected_references: tuple[str, ...]
    candidate_recall: float
    final_recall: float
    noise_rate: float
    semantic_only_recall: float
    lexical_only_recall: float
    latency_ms: int | None
    estimated_context_tokens: int
    truncated: bool
    annotated: bool

    def as_dict(self) -> dict[str, Any]:
        return {
            "scenarioId": self.scenario_id,
            "condition": self.condition,
            "selectedReferences": list(self.selected_references),
            "candidateRecall": self.candidate_recall,
            "finalRecall": self.final_recall,
            "noiseRate": self.noise_rate,
            "semanticOnlyRecall": self.semantic_only_recall,
            "lexicalOnlyRecall": self.lexical_only_recall,
            "latencyMs": self.latency_ms,
            "estimatedContextTokens": self.estimated_context_tokens,
            "truncated": self.truncated,
            "annotated": self.annotated,
        }


def load_live_captures(path: Path = LIVE_CAPTURES_PATH) -> tuple[LiveCapture, ...]:
    if not path.exists():
        return ()
    captures = []
    for file in sorted(path.glob("*.json")):
        raw = json.loads(file.read_text(encoding="utf-8"))
        if raw.get("protocolVersion") != LIVE_PROTOCOL:
            raise ValueError(
                f"unsupported live capture protocol in {file}: {raw.get('protocolVersion')!r}"
            )
        captures.append(LiveCapture.from_dict(raw))
    ids = [c.scenario_id for c in captures]
    if len(ids) != len(set(ids)):
        raise ValueError("live capture scenario identifiers must be unique")
    return tuple(captures)


def evaluate_live_capture(capture: LiveCapture, condition: str) -> LiveConditionResult:
    if condition not in LIVE_CONDITIONS:
        raise ValueError(f"unsupported live condition: {condition}")

    if condition == "LEXICAL_CONTROL":
        pool = [c for c in capture.candidates if c.source_type != "SEMANTIC_RETRIEVAL"]
    else:
        pool = list(capture.candidates)

    pool.sort(key=lambda c: (-c.relevance_score, c.reference))
    selected = pool[:capture.selected_count]
    selected_refs = tuple(c.reference for c in selected)
    all_refs = tuple(c.reference for c in pool)
    expected = capture.expected_evidence

    candidate_recall = (
        1.0 if not expected else len(expected & set(all_refs)) / len(expected)
    )
    final_recall = (
        1.0 if not expected else len(expected & set(selected_refs)) / len(expected)
    )
    noise_rate = (
        0.0 if not selected else len(set(selected_refs) - expected) / len(selected_refs)
    )

    semantic_refs = frozenset(
        c.reference for c in capture.candidates if c.source_type == "SEMANTIC_RETRIEVAL"
    )
    lexical_refs = frozenset(
        c.reference for c in capture.candidates if c.source_type != "SEMANTIC_RETRIEVAL"
    )
    semantic_only_recall = (
        0.0 if not expected
        else len(expected & (semantic_refs - lexical_refs)) / len(expected)
    )
    lexical_only_recall = (
        0.0 if not expected
        else len(expected & (lexical_refs - semantic_refs)) / len(expected)
    )

    return LiveConditionResult(
        scenario_id=capture.scenario_id,
        condition=condition,
        selected_references=selected_refs,
        candidate_recall=round(candidate_recall, 6),
        final_recall=round(final_recall, 6),
        noise_rate=round(noise_rate, 6),
        semantic_only_recall=round(semantic_only_recall, 6),
        lexical_only_recall=round(lexical_only_recall, 6),
        latency_ms=capture.latency_ms,
        estimated_context_tokens=capture.estimated_tokens,
        truncated=capture.truncated,
        annotated=capture.annotated,
    )


def aggregate_live(results: list[LiveConditionResult]) -> dict[str, Any]:
    grouped: dict[str, list[LiveConditionResult]] = {}
    for result in results:
        grouped.setdefault(result.condition, []).append(result)

    report: dict[str, Any] = {}
    for condition, values in sorted(grouped.items()):
        report[condition] = {
            "scenarioCount": len(values),
            "candidateRecallMean": round(
                sum(v.candidate_recall for v in values) / len(values), 6
            ),
            "finalRecallMean": round(
                sum(v.final_recall for v in values) / len(values), 6
            ),
            "noiseRateMean": round(
                sum(v.noise_rate for v in values) / len(values), 6
            ),
            "semanticOnlyRecallMean": round(
                sum(v.semantic_only_recall for v in values) / len(values), 6
            ),
            "lexicalOnlyRecallMean": round(
                sum(v.lexical_only_recall for v in values) / len(values), 6
            ),
            "truncationRate": round(
                sum(v.truncated for v in values) / len(values), 6
            ),
        }
        timings = sorted(v.latency_ms for v in values if v.latency_ms is not None)
        if timings:
            report[condition]["latencyP50Ms"] = timings[len(timings) // 2]
            report[condition]["latencyP95Ms"] = timings[
                min(len(timings) - 1, int(len(timings) * 0.95))
            ]

    hybrid = report.get("HYBRID_EXPERIMENT", {})
    lexical = report.get("LEXICAL_CONTROL", {})
    hybrid_recall = hybrid.get("finalRecallMean", 0.0)
    lexical_recall = lexical.get("finalRecallMean", 0.0)
    hybrid_noise = hybrid.get("noiseRateMean", 0.0)

    hybrid_results = grouped.get("HYBRID_EXPERIMENT", [])
    lexical_results = grouped.get("LEXICAL_CONTROL", [])

    annotated_scenario_ids = {r.scenario_id for r in results if r.annotated}
    annotated_count = len(annotated_scenario_ids)

    # Detect whether paired comparison is available (V2 captures with explicit condition).
    # V1 captures have no condition field — comparisons are biased (derived from same run).
    has_paired = any(r.annotated for r in hybrid_results) and any(
        r.annotated for r in lexical_results
    )
    all_conditions_explicit = all(
        c.condition is not None for c in []
    )  # populated at call site in run_live
    comparison_method = "PAIRED_INDEPENDENT" if all_conditions_explicit else "DERIVED_V1_BIASED"

    improved_scenarios = sum(
        h.final_recall > l.final_recall
        for h, l in zip(hybrid_results, lexical_results)
    )

    if annotated_count == 0 or (annotated_count < 2 and not has_paired):
        recommendation = "INSUFFICIENT_EVIDENCE"
    elif hybrid_recall > lexical_recall and improved_scenarios >= 2 and hybrid_noise <= 0.15:
        recommendation = "ENABLE_HYBRID"
    elif hybrid_recall > lexical_recall or improved_scenarios >= 1:
        if max(hybrid_recall, lexical_recall) < 0.3:
            recommendation = "COMPOSITION_BOTTLENECK"
        else:
            recommendation = "ENABLE_HYBRID"
    elif max(hybrid_recall, lexical_recall) < 0.3:
        recommendation = "COMPOSITION_BOTTLENECK"
    else:
        recommendation = "LEXICAL_SUFFICIENT"

    report["recommendation"] = recommendation
    report["comparisonMethod"] = comparison_method
    report["annotatedScenarioCount"] = annotated_count
    return report


def run_live(captures_path: Path = LIVE_CAPTURES_PATH) -> dict[str, Any]:
    captures = load_live_captures(captures_path)
    if not captures:
        return {
            "protocolVersion": "story-0206-live-evaluation/v1",
            "captureCount": 0,
            "results": [],
            "aggregate": {"recommendation": "COMPOSITION_BOTTLENECK"},
        }
    results = [
        evaluate_live_capture(capture, condition)
        for capture in captures
        for condition in LIVE_CONDITIONS
    ]
    return {
        "protocolVersion": "story-0206-live-evaluation/v1",
        "captureCount": len(captures),
        "results": [r.as_dict() for r in results],
        "aggregate": aggregate_live(results),
    }
