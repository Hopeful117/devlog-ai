# Story 0048 — Polish Review Session State And Decision UX

## Status

Draft

## Priority

Medium

## Objective

Improve the proposal review session UX around progress, feedback, continuity,
and decision ergonomics after the core reviewer identity and sequential review
flows are in place.

## Motivation

Once manual UUID friction is removed and the review queue becomes sequential,
the next UX gains come from polishing state transitions and session continuity:
remaining count, decision feedback, resume behavior, empty states, and related
review affordances.

## Scope

### In Scope

1. Improve review progress visibility.
2. Improve feedback after accept/reject decisions.
3. Improve continuity when resuming a review queue.
4. Improve empty states and review completion states.
5. Add focused regression coverage for the refined UX behaviors.

### Out of Scope

* new backend workflow domain model unless analysis proves it necessary
* authentication redesign
* bulk decision workflow
* unrelated proposal or insight browsing redesign

## Constraints

* preserve explicit human decision boundaries
* prefer incremental UX improvements over architectural overreach
* keep the resulting behavior deterministic enough for automated tests

## Acceptance Criteria

* AC-1: the review workspace communicates queue progress clearly.
* AC-2: decision feedback is explicit and reduces ambiguity after accept/reject.
* AC-3: the review experience resumes predictably after reload/navigation.
* AC-4: automated tests cover the key session-state and UX refinements.

## Dependencies

* ideally Story 0047

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
