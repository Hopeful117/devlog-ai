# Story 0042 — Fix Analysis Context Grounding Closure

## Status

Draft

## Priority

High

## Objective

Fix the `AnalysisContext` grounding inconsistency that still causes project
understanding / insight-generation refreshes to fail with:

`INVALID_LLM_OUTPUT: supportingFactIds contains references absent from AnalysisContext`

The bugfix must ensure the base `AnalysisContext` itself is internally
coherent before any selected-knowledge ranking or budgeting occurs.

## Motivation

Story 0041 fixed the selected-knowledge layer by enforcing observation-to-fact
closure inside `SelectedKnowledge`.

However, real failed analyses after Story 0041 still persist AI tasks with:

* `selectionVersion = knowledge-selection-v4`
* many observation `supportingFactIds` absent from `selectedFacts`
* `INVALID_LLM_OUTPUT` failures in the AI Engine

This proves a second upstream defect remains.

The current repository evidence indicates that `AnalysisContextServiceImpl`
loads:

* facts through a hard `MAX_FACTS` page limit;
* observations independently through a separate `MAX_OBSERVATIONS` page limit.

An observation can therefore expose support fact IDs that are absent from the
base `AnalysisContext.facts` set before selection even starts.

This is a source-context bug, not a selected-snapshot bug.

## Scope

### In Scope

1. Reproduce and explain the remaining grounding failure after Story 0041.
2. Fix `AnalysisContext` construction so observations cannot reference support
   fact IDs absent from `AnalysisContext.facts`.
3. Preserve deterministic ordering and bounded context behavior.
4. Add regression coverage for the base-context closure invariant.
5. Validate the fix on the impacted architecture-review / project-understanding
   path as far as the local environment allows.

### Out of Scope

* redesigning the overall grounding contract
* weakening AI validation
* reverting or replacing Story 0041
* unrelated repository-context ranking changes
* broad prompt rewrites

## Constraints

* preserve strict grounding validation
* keep the fix deterministic
* preserve a bounded `AnalysisContext`
* prefer truthful context closure over silently trimming support metadata
* maintain compatibility with Story 0041 selected-knowledge closure

## Acceptance Criteria

* AC-1: `AnalysisContext.observations[].supportingFactIds` cannot reference
  fact IDs absent from `AnalysisContext.facts`.
* AC-2: the base context remains deterministic and bounded.
* AC-3: regression tests cover the previously failing source-context dangling
  reference case.
* AC-4: a post-Story-0041 architecture-review refresh no longer fails for this
  reason when using a rebuilt local service.
* AC-5: documentation records the distinction between the Story 0041 fix
  layer and the remaining Story 0042 root cause.

## Dependencies

* ADR-013
* ADR-033
* Story 0035
* Story 0041

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
