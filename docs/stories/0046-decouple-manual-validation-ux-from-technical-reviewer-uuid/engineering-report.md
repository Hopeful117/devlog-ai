# Story 0046 — Decouple Manual Validation UX From Technical Reviewer UUID — Engineering Report

## Status

Completed

## Story Recap

Story 0046 repaired a UX leak in the proposal-validation flow.

The system already needed a reviewer identifier for immutable decision
attribution, but the direct proposal detail page exposed that persistence
concern directly to the human reviewer by requiring manual UUID entry.

## Problem

On `/proposals/:id`, a reviewer had to:

* generate or type a valid UUID;
* satisfy the `validatedBy` form field;
* then proceed with acceptance or rejection.

That behavior created avoidable friction and made the MVP feel more technical
than it needed to be, even though the real business rule was only:

* keep an explicit human decision;
* keep deterministic reviewer attribution.

## Implemented Outcome

Story 0046 now reuses the existing session-local reviewer identity model on the
direct proposal detail page.

Implemented changes:

* [proposal-detail-page.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-detail-page.ts:1)
  now injects `ProposalReviewerSessionService` and ensures a reviewer ID exists
  automatically.
* [proposal-detail-page.html](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-detail-page.html:1)
  no longer asks the human to type a UUID before deciding.
* [proposal-detail-page.spec.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-detail-page.spec.ts:1)
  now covers automatic reviewer creation, reuse, reset behavior, and unchanged
  request semantics.

The backend validation contract remains unchanged:

* Core still receives `validatedBy`;
* Core still records immutable reviewer attribution;
* explicit decision confirmation is still required.

## Why This Matters

This Story improves the proposal-validation experience without weakening the
human-in-the-loop safety model.

The result is better because:

* reviewers can focus on the content of the proposal rather than on a
  persistence identifier;
* auditability is preserved;
* the direct proposal page now follows the same session-local reviewer concept
  already used elsewhere in the frontend.

## What The Story Intentionally Does Not Do

This Story does **not**:

* redesign the backend `Validation` schema;
* remove `validatedBy` from the API;
* introduce authentication or authorization;
* redesign the queue review workflow;
* add bulk review.

That keeps Story 0046 narrowly aligned with the approved plan.

## Tests And Verification

Passed:

* `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-detail-page.spec.ts' --include='src/app/features/insights/proposal-reviewer-session.service.spec.ts'`
* `git diff --check`

Quality gate result:

* targeted frontend tests: **PASS**
* diff formatting check: **PASS**

## Documentation Reconciliation

Updated:

* [frontend/README.md](/home/ludo/Bureau/workspace/devlog-ai/frontend/README.md:145)
* [manual-mvp-test.md](/home/ludo/Bureau/workspace/devlog-ai/frontend/docs/manual-mvp-test.md:1)

Reason:

* the repository previously documented manual reviewer UUID entry as part of
  the MVP flow;
* the implemented UX now uses an automatic session-local reviewer identity on
  the direct proposal detail page.

## Architectural Outcome

Story 0046 clarifies a healthy boundary:

* the backend owns validation persistence and auditability;
* the frontend owns how a local MVP reviewer identity is sourced for the
  interaction;
* the human still owns the actual decision.

That is a cleaner separation than exposing backend-oriented UUID handling
directly in the review form.

## Honest Limitations

Story 0046 improves the direct proposal detail page, but it does not make the
overall reviewer identity model production-ready.

Remaining limits:

* reviewer identity is still session-local rather than authenticated;
* the queue review page still exposes reviewer controls differently;
* there is still no broader auth model behind decision attribution.

Those constraints are acceptable for this Story because they were explicitly
outside scope.

## Final Outcome

Completed.

Story 0046 removes manual reviewer-UUID friction from the direct proposal
decision flow while preserving explicit human validation, deterministic
attribution, and the existing Core contract.
