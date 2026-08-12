# Story 0045 — Fix Understanding Refresh LLM Timeout

## Status

Draft

## Priority

High

## Objective

Investigate and fix the current `LLM_PROVIDER_ERROR: Request timed out`
failures that still make understanding refreshes unusable after the grounding
bugfixes.

## Motivation

After Stories 0041 and 0042, the latest failed understanding refreshes no
longer fail with `INVALID_LLM_OUTPUT`. They now fail with provider timeout.

This indicates the grounding layer progressed, but the refresh path is still
operationally unreliable.

## Scope

### In Scope

1. Reproduce and explain the timeout on the current refresh path.
2. Measure the likely contributing factors:
   * payload/context size
   * model call duration
   * retry or callback behavior
   * timeout budget mismatch between layers
3. Apply the minimum deterministic fix required to make refresh usable again.
4. Add regression coverage where feasible without making tests provider-coupled.

### Out of Scope

* broad AI-engine redesign
* replacing the provider
* unrelated prompt/knowledge architecture changes

## Constraints

* preserve quality gates
* avoid weakening validation to “make it pass”
* keep the fix observable and measurable

## Acceptance Criteria

* AC-1: the timeout root cause is explicitly identified.
* AC-2: the refresh path no longer fails for that reason in the local validated
  scenario.
* AC-3: the fix is covered by automated regression or a clearly documented
  operational validation path where automation is not feasible.

## Dependencies

* ideally Story 0043
* possibly Story 0044 depending on analysis path needs

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
