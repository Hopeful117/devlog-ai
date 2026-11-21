"""Fail-closed proof gate for Story 0223 epic closure.

The gate consumes already-reviewed pilot aggregates. It does not score raw
answers and it never invents thresholds or human-utility evidence.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping


NON_INFERIORITY_METRICS = (
    "groundedCorrectnessRate",
    "evidenceSupportRate",
    "specificityRate",
    "abstentionCorrectnessRate",
)


@dataclass(frozen=True)
class ProofThresholds:
    """Human-approved thresholds required before a quality verdict is possible."""

    minimum_execution_completeness: float
    minimum_human_utility: float
    non_inferiority_margin: float
    minimum_advantage_delta: float
    advantage_metric: str

    def validate(self) -> None:
        values = (
            self.minimum_execution_completeness,
            self.minimum_human_utility,
            self.non_inferiority_margin,
            self.minimum_advantage_delta,
        )
        if any(not 0 <= value <= 1 for value in values):
            raise ValueError("proof thresholds must be between 0 and 1")
        if self.advantage_metric not in NON_INFERIORITY_METRICS:
            raise ValueError("advantage metric is not an approved quality metric")


def evaluate_proof_gate(
    report: Mapping[str, Any],
    treatment_metrics: Mapping[str, Mapping[str, float]],
    human_utility: Mapping[str, float],
    thresholds: ProofThresholds | None,
    *,
    target_treatments: tuple[str, ...] = (
        "DEVLOG_CONTEXT",
        "STORY_CONTEXT_INVESTIGATION",
    ),
    baseline_treatment: str = "AGENT_DIRECT",
) -> dict[str, Any]:
    """Return ``PROVEN`` only when every closure condition is evidenced."""

    reasons: list[str] = []
    if thresholds is None:
        reasons.append("approved proof thresholds are missing")
    else:
        thresholds.validate()

    if report.get("oracleReviewed") is not True:
        reasons.append("human-reviewed oracles are missing")
    if report.get("qualityMetricsAvailable") is not True:
        reasons.append("quality metrics are not available")

    completeness = report.get("completeness", {})
    if completeness.get("missingCount") != 0:
        reasons.append("campaign contains missing assignments")
    if thresholds is not None:
        execution_completeness = completeness.get("executionCompleteness")
        if execution_completeness is None or execution_completeness < thresholds.minimum_execution_completeness:
            reasons.append("campaign execution is below the approved threshold")
    if report.get("pairing", {}).get("incompletePairCount") != 0:
        reasons.append("campaign contains incomplete treatment pairs")

    required_treatments = (baseline_treatment, *target_treatments)
    for treatment in required_treatments:
        metrics = treatment_metrics.get(treatment)
        if metrics is None:
            reasons.append(f"quality metrics are missing for {treatment}")
            continue
        for metric in NON_INFERIORITY_METRICS:
            value = metrics.get(metric)
            if value is None or not 0 <= value <= 1:
                reasons.append(f"invalid {metric} for {treatment}")

    for treatment in target_treatments:
        value = human_utility.get(treatment)
        if value is None or not 0 <= value <= 1:
            reasons.append(f"human utility evidence is missing for {treatment}")

    if not reasons and thresholds is not None:
        baseline = treatment_metrics[baseline_treatment]
        has_advantage = False
        for treatment in target_treatments:
            metrics = treatment_metrics[treatment]
            for metric in NON_INFERIORITY_METRICS:
                if metrics[metric] < baseline[metric] - thresholds.non_inferiority_margin:
                    reasons.append(f"{treatment} is inferior to baseline on {metric}")
            if metrics[thresholds.advantage_metric] - baseline[thresholds.advantage_metric] >= thresholds.minimum_advantage_delta:
                has_advantage = True
            if human_utility[treatment] < thresholds.minimum_human_utility:
                reasons.append(f"human utility is below threshold for {treatment}")
        if not has_advantage:
            reasons.append("no approved target treatment demonstrates the required advantage")

    return {
        "verdict": "PROVEN" if not reasons else "NOT_PROVEN",
        "closureAllowed": not reasons,
        "reasons": reasons,
        "baselineTreatment": baseline_treatment,
        "targetTreatments": list(target_treatments),
    }
