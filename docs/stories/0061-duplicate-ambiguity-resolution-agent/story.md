# Story 0061 — Duplicate Ambiguity Resolution Agent

## Status

Done

## Priority

Medium

## Objective

Implement the first AI-assisted reasoning domain for the Context Maintenance
Agent by evaluating ambiguous duplicate and overlap findings to distinguish
genuine duplication from legitimate enrichment.

## Motivation

ADR-053 and Story 0055 produce maintenance findings for trusted-knowledge
duplicate debt, including semantic duplicate and overlap review categories.

The deterministic layer classifies these findings, but the distinction between
true duplication and legitimate enrichment is often semantically ambiguous and
benefits from AI-assisted interpretation.

This Story delivers the first scoped reasoning domain for the Context
Maintenance Agent defined in ADR-054.

## Scope

### In Scope

1. Implement AI-assisted evaluation for `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
   findings.
2. Implement AI-assisted evaluation for `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`
   findings.
3. For each ambiguous finding, produce an agent assessment with:
   * semantic classification (likely duplicate, likely enrichment, uncertain);
   * confidence level;
   * rationale comparing the candidate records;
   * recommended action (RESOLVE, DISMISS, ESCALATE).
4. Reuse the agent assessment model from Story 0060.
5. Preserve human authority for all resolution decisions.

### Out Of Scope

* automatic duplicate resolution without human review;
* AI-driven trusted knowledge mutation;
* cross-surface pattern detection;
* priority ranking across findings;
* exact duplicate handling (deterministic, not ambiguous).

## Constraints

* the agent must not directly modify trusted knowledge;
* the agent must not auto-resolve duplicate findings;
* assessments must be bounded to the semantic-overlap problem domain;
* the AI inference must be scoped to comparing candidate records and
  their metadata;
* the agent must prefer silence over low-confidence assessments.

## Acceptance Criteria

* AC-1: DevLog can produce an agent assessment for semantic-duplicate
  findings when the deterministic layer identifies ambiguous overlap.
* AC-2: each assessment classifies the overlap as likely duplicate,
  likely enrichment, or uncertain.
* AC-3: each assessment includes a confidence level and rationale.
* AC-4: each assessment recommends RESOLVE, DISMISS, or ESCALATE.
* AC-5: no trusted knowledge is mutated by the agent.
* AC-6: tests cover duplicate classification, enrichment classification,
  uncertain cases, and low-confidence suppression.
* AC-7: documentation explains the duplicate ambiguity resolution domain
  and its limitations.

## Dependencies

* ADR-051 — Trusted Knowledge Duplicate Policy
* ADR-053 — Internal Context Maintenance Capability
* ADR-054 — Context Maintenance Agent
* Story 0055 — Detect Trusted Knowledge Duplicate Debt Through Maintenance
  Findings
* Story 0060 — Define Maintenance Agent Assessment Model

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
