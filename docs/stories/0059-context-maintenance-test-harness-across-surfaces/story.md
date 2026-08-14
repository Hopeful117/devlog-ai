# Story 0059 — Context Maintenance Test Harness Across Surfaces

## Status

Draft

## Priority

High

## Objective

Build a dedicated test harness for context maintenance so cross-surface
maintenance regressions are caught before they escape into running DevLog
instances.

## Motivation

Recent work on understanding refreshes and AI-facing context showed that
component-level tests can still miss important cross-layer failures.

ADR-053 introduces another cross-cutting capability spanning:

* project understanding;
* projections;
* trusted knowledge;
* internal human context;
* maintenance findings and remediation flows.

Without a dedicated test strategy, DevLog is likely to repeat the same failure
pattern:

* individually correct components;
* broken operational behavior across boundaries;
* noisy or missing maintenance signals discovered only in live usage.

This Story hardens the maintenance capability as a system, not just as isolated
units.

## Scope

### In Scope

1. Define the minimum invariant set for context-maintenance behavior across
   surfaces.
2. Add backend and/or integration tests covering maintenance findings
   generation.
3. Cover at least one multi-surface scenario end-to-end.
4. Cover no-finding paths and false-positive resistance where relevant.
5. Cover remediation or automation boundaries where those paths exist.

### Out Of Scope

* browser E2E for every cockpit detail
* provider-timing-sensitive AI tests
* full synthetic project orchestration beyond stable repository needs
* replacing existing quality gates outside the maintenance scope

## Constraints

* tests should emphasize invariants over brittle snapshots
* prefer deterministic inputs and repeatable scenarios
* the harness must exercise cross-surface behavior, not only isolated services
* the Story should document residual test limitations honestly

## Acceptance Criteria

* AC-1: DevLog defines an explicit context-maintenance invariant set for
  automated verification.
* AC-2: at least one multi-surface scenario exercises maintenance behavior
  across more than one context surface.
* AC-3: tests cover both finding-generation paths and no-finding
  non-regression behavior.
* AC-4: tests protect trust-boundary rules such as no silent trusted-knowledge
  mutation.
* AC-5: documentation records the remaining test blind spots and why they
  remain out of scope.

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* Story 0043 — Strengthen Understanding Refresh Test Harness
* Story 0052 — Define Context Health Signals And Maintenance Findings

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
