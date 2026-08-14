# Story 0053 — Expose Context Maintenance Findings In API And Cockpit

## Status

Draft

## Priority

High

## Objective

Expose context-maintenance findings through a bounded API and a first user-facing
cockpit surface so maintenance becomes visible, reviewable, and operational.

## Motivation

ADR-053 explicitly rejects invisible background maintenance behavior.

If DevLog detects stale or degraded context but keeps those findings hidden
inside internal services, the user still cannot act on them and future agent
workflows still cannot rely on a stable operational maintenance surface.

After Story 0052 defines the maintenance-finding model, DevLog needs a first
vertical read path that makes those findings visible in both:

* machine-consumable APIs;
* human-facing cockpit UX.

This Story focuses on visibility rather than remediation.

## Scope

### In Scope

1. Add a read API for project-scoped maintenance findings.
2. Expose bounded finding details such as:
   * context surface;
   * issue type;
   * severity or priority;
   * current status;
   * suggested action;
   * timestamps.
3. Integrate the first maintenance view into the project cockpit.
4. Present graceful empty states when no findings exist.
5. Distinguish clearly between:
   * informational findings,
   * reviewable findings,
   * blocked findings awaiting human action.
6. Preserve an explainable UI rather than a vague health score with no details.

### Out Of Scope

* actual remediation actions
* background scheduling policy
* broad cockpit redesign unrelated to maintenance visibility
* automatic mutation of project memory

## Constraints

* the first UI slice must remain simple and high-signal
* empty and low-data states must be handled gracefully
* the API should be stable enough for later remediation workflows
* the feature must not imply that findings are authoritative project knowledge

## Acceptance Criteria

* AC-1: project-scoped maintenance findings are retrievable through a bounded
  API.
* AC-2: the cockpit exposes a first user-facing maintenance surface showing
  current findings.
* AC-3: the UI clearly communicates the difference between no findings,
  informational findings, and findings that require review.
* AC-4: empty states and sparse states are rendered gracefully.
* AC-5: tests cover API serialization and the first cockpit rendering paths.
* AC-6: documentation records the initial API and UX boundaries.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* Story 0052 — Define Context Health Signals And Maintenance Findings

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
