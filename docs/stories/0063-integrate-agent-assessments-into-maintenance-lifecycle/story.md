# Story 0063 — Integrate Agent Assessments Into Maintenance Lifecycle

## Status

Draft

## Priority

High

## Objective

Integrate agent assessments into the existing maintenance-finding lifecycle
and API surface so users can view AI-assisted interpretations alongside
deterministic findings and make informed remediation decisions.

## Motivation

ADR-054 specifies that agent assessments must integrate with the existing
workflow rather than introducing a parallel surface.

After the assessment model (Story 0060) and the first reasoning domains
(Stories 0061, 0062) exist, DevLog needs to wire assessments into the
API and UI so humans can actually consume them.

This Story connects the agent layer to the existing maintenance lifecycle
without creating a separate UI surface.

## Scope

### In Scope

1. Extend the maintenance-finding API response to include attached agent
   assessments.
2. Extend the frontend to display agent assessments alongside findings:
   * confidence indicator;
   * semantic classification;
   * rationale;
   * recommended action.
3. Allow users to view assessment details without leaving the maintenance
   section.
4. Preserve the existing remediation workflow without modification.
5. Ensure agent assessments are included in the finding audit history.

### Out Of Scope

* new remediation actions driven by agent assessments;
* separate UI surface for assessments;
* assessment-driven automatic workflow transitions;
* assessment editing or manual override;
* assessment-specific notifications.

## Constraints

* assessments must appear as additional context, not as workflow drivers;
* the existing remediation workflow must not be altered;
* assessments must not be editable by users;
* the UI must clearly distinguish deterministic findings from agent
  assessments;
* the integration must not degrade existing maintenance section performance.

## Acceptance Criteria

* AC-1: the maintenance-finding API response includes attached agent
  assessments when present.
* AC-2: the frontend displays agent assessments within the maintenance
  section for each finding.
* AC-3: users can view confidence, classification, rationale, and
  recommended action for each assessment.
* AC-4: the UI visually distinguishes deterministic findings from agent
  assessments.
* AC-5: the existing remediation workflow remains unchanged.
* AC-6: tests cover API response shape, frontend rendering, and
  assessment display for findings with and without assessments.
* AC-7: documentation explains how assessments integrate into the
  maintenance lifecycle.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* ADR-054 — Context Maintenance Agent
* Story 0053 — Expose Context Maintenance Findings In API And Cockpit
* Story 0056 — Human-Reviewed Remediation Workflow For Maintenance Findings
* Story 0060 — Define Maintenance Agent Assessment Model

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
