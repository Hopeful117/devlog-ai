# Story 0041 — Fix Selected Knowledge Grounding Consistency

## Status

Draft

## Priority

High

## Objective

Fix the `SelectedKnowledge` grounding inconsistency that causes project
understanding / insight-generation refreshes to fail with:

`INVALID_LLM_OUTPUT: supportingFactIds contains references absent from AnalysisContext`

The bugfix must ensure the knowledge sent to the AI Engine remains internally
consistent so the model cannot legitimately copy fact identifiers that the
grounding validator later rejects.

## Motivation

The current refresh path can fail even when the model follows the prompt
correctly.

The selected observations sent to the AI Engine may still reference
`supportingFactIds` for facts that were omitted from `selectedFacts` by the
knowledge-selection budget and ranking logic.

This creates a deterministic contract mismatch:

* the model sees the fact identifiers inside the selected observation payload;
* the validator only authorizes identifiers present in `selectedFacts`;
* the result is rejected as invalid LLM output.

This is a grounding-contract bug in DevLog, not merely a prompt-quality issue.

## Scope

### In Scope

1. Reproduce and explain the `supportingFactIds` inconsistency in the current
   selected-knowledge pipeline.
2. Fix `SelectedKnowledge` construction so selected observations and selected
   facts remain grounding-consistent.
3. Add regression coverage proving the selected snapshot no longer exposes
   dangling fact references.
4. Validate the fix on the impacted insight-generation / project-understanding
   path as far as the local environment allows.

### Out of Scope

* redesigning the overall grounding contract
* broad prompt rewrites
* weakening the AI output validator
* relaxing the `supportingFactIds` subset rule
* unrelated repository-context ranking changes

## Constraints

* preserve strict grounding validation
* keep the fix deterministic
* prefer correcting the selected-knowledge snapshot over loosening downstream
  validators
* avoid broad selection-policy changes unless they are directly required to
  restore grounding consistency

## Acceptance Criteria

* AC-1: selected observations cannot expose `supportingFactIds` for facts that
  are absent from `selectedFacts`.
* AC-2: the selected-knowledge snapshot remains deterministic and bounded.
* AC-3: regression tests cover the previously failing dangling-reference case.
* AC-4: the insight-generation path no longer fails for this reason when the
  model copies visible fact identifiers from selected observations.
* AC-5: documentation clearly records the root cause and any remaining
  limitations.

## Dependencies

* ADR-013
* ADR-033
* Story 0035
* Story 0037

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
