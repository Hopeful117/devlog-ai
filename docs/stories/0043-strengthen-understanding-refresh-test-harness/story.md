# Story 0043 — Strengthen Understanding Refresh Test Harness

## Status

Draft

## Priority

High

## Objective

Strengthen the DevLog test harness around project-understanding refreshes so
cross-layer bugs are detected before they escape into the running system.

This Story addresses the testing blind spot revealed by Stories 0041 and 0042:
the system can pass component-level tests while still failing in the real
refresh path because critical invariants are not asserted across the full
pipeline.

## Motivation

Recent bugfix work exposed three distinct issues:

* selected-knowledge grounding inconsistency;
* source `AnalysisContext` grounding inconsistency;
* diagnostics `/context` runtime failure.

The deeper lesson is that our current tests are strong on individual layers but
too weak on cross-layer refresh behavior and runtime-facing invariants.

We need a dedicated test slice that protects:

* base `AnalysisContext` coherence;
* selected-knowledge coherence;
* diagnostics endpoint safety;
* refresh-path stability;
* AI-task result compatibility with the snapshots actually exposed by the Core.

## Scope

### In Scope

1. Define the minimum invariant set that must hold during understanding
   refreshes.
2. Add targeted backend and/or integration tests for those invariants.
3. Cover the diagnostics endpoints that the UI actually consumes during a
   refresh.
4. Add at least one refresh-oriented scenario that crosses the main runtime
   seams instead of testing only isolated components.
5. Use the known failed cases from Stories 0041 and 0042 as regression drivers.

### Out of Scope

* fixing the diagnostics `/context` bug itself
* fixing the LLM timeout itself
* redesigning the AI prompt contract
* end-to-end browser automation
* full synthetic environment orchestration beyond what the repository needs for
  stable regression coverage

## Constraints

* preserve deterministic tests
* avoid brittle tests coupled to provider timing
* prefer invariant-focused coverage over large opaque scenarios
* keep quality gates unchanged

## Acceptance Criteria

* AC-1: there is explicit automated coverage for refresh-path grounding
  invariants across source context and selected knowledge.
* AC-2: diagnostics endpoints used during refreshes are covered by tests that
  would catch the class of `/context` runtime failure.
* AC-3: at least one refresh-oriented scenario exercises multiple layers
  together, not only isolated services.
* AC-4: the known bug shapes from Stories 0041 and 0042 are represented as
  regression cases.
* AC-5: the Story documents the remaining test limitations honestly.

## Dependencies

* Story 0041
* Story 0042
* ADR-013
* ADR-033

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
