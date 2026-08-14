# Story 0056 — Human-Reviewed Remediation Workflow For Maintenance Findings

## Status

Draft

## Priority

Medium

## Objective

Introduce a safe human-reviewed remediation workflow for maintenance findings so
users can act on degraded context without weakening DevLog’s trust boundaries.

## Motivation

Maintenance findings are only useful if they can eventually lead to remediation.

However, ADR-053 makes a critical distinction:

* detection may be system-owned;
* ambiguous or destructive remediation must remain human-controlled.

After findings become visible, DevLog needs a bounded workflow that lets users
review and resolve them with traceability rather than leaving maintenance as a
static warning surface.

## Scope

### In Scope

1. Define a reviewable remediation workflow for maintenance findings.
2. Support explicit user actions such as:
   * acknowledge or dismiss with rationale;
   * mark resolved when an external fix was applied;
   * launch a bounded remediation path when supported.
3. Record enough audit trail to explain who resolved or dismissed a finding and
   why.
4. Support at least one maintenance-finding family end-to-end.
5. Preserve separate handling for informational findings versus findings that
   imply project-memory changes.

### Out Of Scope

* fully autonomous remediation
* broad task-management features unrelated to maintenance
* deep merge UI for every possible duplicate case
* new validation semantics for trusted knowledge itself

## Constraints

* remediation must remain explicit and reviewable
* dismissing a finding must not silently mutate the underlying project memory
* any operation with destructive knowledge consequences must preserve existing
  validation and audit boundaries

## Acceptance Criteria

* AC-1: DevLog provides an explicit workflow for reviewing and resolving at
  least one maintenance-finding family.
* AC-2: findings can be acknowledged, dismissed, or resolved through explicit
  tracked actions.
* AC-3: the system preserves an audit trail for remediation decisions.
* AC-4: destructive or ambiguous changes remain blocked behind explicit human
  control.
* AC-5: tests cover status transitions and auditability of the workflow.
* AC-6: documentation explains the remediation boundary and what is still out
  of scope.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* Story 0052 — Define Context Health Signals And Maintenance Findings
* Story 0053 — Expose Context Maintenance Findings In API And Cockpit
* Story 0055 — Detect Trusted Knowledge Duplicate Debt Through Maintenance Findings

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
