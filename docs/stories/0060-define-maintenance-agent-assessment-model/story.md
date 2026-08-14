# Story 0060 — Define Maintenance Agent Assessment Model

## Status

Draft

## Priority

High

## Objective

Define the domain model and persistence layer for AI-assisted maintenance
assessments so the Context Maintenance Agent can produce structured,
reviewable, and traceable interpretations of ambiguous maintenance findings.

## Motivation

ADR-054 introduces a Context Maintenance Agent that uses AI assistance to
evaluate ambiguous maintenance situations.

Before the agent can produce assessments, DevLog needs a coherent domain
model answering:

* what an agent assessment contains;
* how it relates to existing maintenance findings;
* how it is persisted and retrieved;
* how it is surfaced to humans and future consumers.

Without this foundation, agent reasoning would remain ad hoc and impossible
to audit, review, or extend.

## Scope

### In Scope

1. Define a persistent model for maintenance agent assessments.
2. Each assessment must reference a maintenance finding or finding cluster.
3. Each assessment must include:
   * interpreted severity or semantic classification;
   * confidence indicator;
   * rationale;
   * recommended action;
   * supporting signals or evidence references.
4. Define repository and service layer for assessment persistence.
5. Define DTOs for assessment creation and retrieval.
6. Preserve clear separation between:
   * deterministic maintenance findings;
   * AI-assisted agent assessments;
   * human remediation actions.

### Out Of Scope

* AI inference logic for generating assessments;
* duplicate ambiguity resolution reasoning;
* cross-surface pattern detection;
* UI integration;
* confidence threshold filtering.

## Constraints

* the assessment model must not replace or mutate maintenance findings;
* assessments are advisory artifacts, not lifecycle transitions;
* the model must support traceability to the finding and the reasoning
  that produced it;
* the first slice must remain intentionally narrow;
* the model must be extensible for future reasoning domains without
  schema changes.

## Acceptance Criteria

* AC-1: DevLog defines a first-class agent-assessment model persisted
  inside the Core.
* AC-2: each assessment references exactly one maintenance finding.
* AC-3: each assessment records confidence, rationale, recommended action,
  and supporting signals.
* AC-4: assessments are retrievable through a project-scoped API.
* AC-5: the assessment model is explicitly separated from the finding
  model and the remediation action model.
* AC-6: tests cover assessment persistence, retrieval, and relationship
  to findings.
* AC-7: documentation explains the assessment model boundaries and its
  relationship to ADR-054.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* ADR-054 — Context Maintenance Agent
* Story 0052 — Define Context Health Signals And Maintenance Findings
* Story 0056 — Human-Reviewed Remediation Workflow For Maintenance Findings

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
