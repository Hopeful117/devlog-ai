# Story 0044 — Fix Analysis Context Diagnostics Null Safety

## Status

Draft

## Priority

High

## Objective

Fix the diagnostics `/context` failure that currently makes the refresh flow
partially unusable even after grounding fixes, by making context-snapshot
exposure null-safe and contract-consistent.

## Motivation

The backend currently throws a `NullPointerException` in
`AnalysisDiagnosticsServiceImpl.getContext(...)` when `/api/v1/analyses/{id}/context`
attempts to expose a persisted context snapshot containing null values.

This is not the same bug as the grounding failures addressed by Stories 0041
and 0042. It is a diagnostics/runtime-observability bug affecting the same
refresh user journey.

## Scope

### In Scope

1. Reproduce and explain the diagnostics `/context` failure.
2. Make `contextSnapshot` exposure null-safe.
3. Preserve a stable diagnostics contract for the UI and debugging workflows.
4. Add regression coverage for the null-containing snapshot case.

### Out of Scope

* LLM timeout remediation
* grounding-contract redesign
* broad diagnostics redesign

## Constraints

* preserve deterministic diagnostics output
* do not silently drop meaningful context structure unless the contract
  explicitly allows it
* keep the change narrow and reviewable

## Acceptance Criteria

* AC-1: `/api/v1/analyses/{id}/context` no longer fails on persisted snapshots
  containing null values.
* AC-2: regression tests cover the failing case.
* AC-3: diagnostics contract changes, if any, are explicitly documented.

## Dependencies

* ideally Story 0043

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
