# Story 0062 — Cross-Surface Pattern Detection Agent

## Status

Draft

## Priority

Medium

## Objective

Implement cross-surface pattern detection for the Context Maintenance Agent
so correlated staleness and degradation signals across multiple context
surfaces can be identified and surfaced as a coherent maintenance assessment.

## Motivation

ADR-054 identifies that multiple weak signals across surfaces may together
imply degraded project understanding that no single deterministic rule
captures.

For example:

* stale project understanding combined with missing projection refresh
  and stale human context inputs may indicate broader context degradation;
* correlated duplicate debt across knowledge surfaces may indicate a
  systemic quality issue.

The deterministic layer evaluates each surface independently. The agent
can synthesize cross-surface patterns into a single coherent assessment.

## Scope

### In Scope

1. Implement cross-surface pattern detection that evaluates maintenance
   findings across multiple context surfaces.
2. Detect correlated staleness patterns across:
   * project understanding;
   * project projections;
   * internal human context.
3. Detect correlated duplicate debt patterns across knowledge surfaces.
4. Produce agent assessments for detected patterns with:
   * pattern classification;
   * affected surfaces;
   * confidence level;
   * rationale;
   * prioritized remediation recommendation.
5. Reuse the agent assessment model from Story 0060.

### Out Of Scope

* cross-project maintenance reasoning;
* automatic remediation of detected patterns;
* priority ranking across all project findings;
* AI-driven trend analysis or forecasting;
* UI changes for pattern-specific visualization.

## Constraints

* pattern detection must be grounded in existing deterministic findings;
* the agent must not independently scan for patterns outside maintenance
  findings;
* cross-surface assessments must be traceable to the underlying findings;
* the agent must prefer silence over weak or spurious pattern detection;
* patterns must be explainable in terms of the signals that produced them.

## Acceptance Criteria

* AC-1: DevLog can detect correlated staleness patterns across at least
  two context surfaces.
* AC-2: DevLog can detect correlated duplicate debt patterns across
  knowledge surfaces.
* AC-3: each pattern assessment references the underlying findings that
  contributed to it.
* AC-4: each assessment includes a pattern classification, confidence
  level, and rationale.
* AC-5: pattern assessments are retrievable through the project-scoped
  assessment API.
* AC-6: tests cover multi-surface correlation, single-surface exclusion,
  and weak-signal suppression.
* AC-7: documentation explains the cross-surface pattern detection
  domain and its scope boundaries.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* ADR-054 — Context Maintenance Agent
* Story 0054 — Detect Stale Understanding And Projection Refresh Gaps
* Story 0055 — Detect Trusted Knowledge Duplicate Debt Through Maintenance
  Findings
* Story 0057 — Context Maintenance For Internal Human Context Inputs
* Story 0060 — Define Maintenance Agent Assessment Model

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
