# Story 0047 — Turn Proposal Review Queue Into Sequential Carousel

## Status

Draft

## Priority

Medium

## Objective

Transform the existing proposal review queue into a true sequential review
experience that presents one proposal at a time and naturally advances after a
decision.

## Motivation

The current review page already exposes a proposal-review queue, but the
interaction still feels like a technical list rather than a focused human
review workflow. A sequential carousel-style flow would make review faster and
less cognitively noisy.

## Scope

### In Scope

1. Review the current proposal review page and queue projection.
2. Design a one-at-a-time proposal review experience.
3. Advance to the next relevant proposal after accept/reject.
4. Preserve direct audit access to the underlying proposal.
5. Add regression coverage for the sequential review behavior.

### Out of Scope

* reviewer identity contract redesign
* authentication redesign
* bulk actions
* queue persistence/session recovery beyond what already exists

## Constraints

* preserve explicit per-proposal human decisions
* do not remove direct access to detailed proposal audit information
* keep the workflow deterministic and testable

## Acceptance Criteria

* AC-1: the review workspace presents a single current proposal as the primary
  focus.
* AC-2: after a successful accept or reject, the UI advances to the next
  pending proposal when one exists.
* AC-3: completion and empty-state behavior are explicit when no pending
  proposals remain.
* AC-4: automated tests cover the sequential review behavior.

## Dependencies

* ideally Story 0046

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
