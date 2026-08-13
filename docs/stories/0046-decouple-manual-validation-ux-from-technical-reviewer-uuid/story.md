# Story 0046 — Decouple Manual Validation UX From Technical Reviewer UUID

## Status

Draft

## Priority

High

## Objective

Remove the current requirement for a human reviewer to manually generate and
paste a technical UUID when accepting or rejecting an Insight proposal.

## Motivation

The current review flow leaks a persistence-oriented technical identifier into
the end-user experience. Reviewers must manually generate a UUID before they
can validate a proposal, which creates avoidable friction and makes the human
validation workflow feel artificial.

This story should improve the UX without weakening traceability or introducing
implicit acceptance.

## Scope

### In Scope

1. Inspect the current validation contract and reviewer identity handling.
2. Remove the need for manual UUID entry from the user-facing validation flow.
3. Preserve the existing audit trail and validation ownership semantics.
4. Keep the change compatible with the existing proposal review workflow.
5. Add regression coverage for the updated validation interaction.

### Out of Scope

* full redesign of the proposal review workspace
* bulk validation
* authentication/authorization redesign
* proposal review carousel redesign

## Constraints

* preserve explicit human validation
* preserve proposal and validation traceability
* do not silently weaken decision attribution
* avoid coupling the UX fix to a larger frontend redesign

## Acceptance Criteria

* AC-1: a reviewer can accept or reject a proposal without manually generating
  or typing a UUID.
* AC-2: the backend still records a valid reviewer identity according to the
  decided contract.
* AC-3: proposal validation remains auditable and deterministic.
* AC-4: automated tests cover the updated validation flow.

## Dependencies

* none required

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
