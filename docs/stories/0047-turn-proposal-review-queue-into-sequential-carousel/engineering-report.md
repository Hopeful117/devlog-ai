# Story 0047 — Turn Proposal Review Queue Into Sequential Carousel — Engineering Report

## Status

Completed

## Story Recap

Story 0047 refined the proposal review workspace so it behaves like a true
sequential review journey rather than a paged list with a selected detail
panel.

The repository already had the concept of a current proposal, but the overall
experience still felt queue-first and page-local.

## Problem

Before this story:

* the full page queue remained visually primary;
* current proposal selection was only partly sequential;
* after a decision, the page could only advance within the current page;
* when that page had no remaining pending proposal, the component could settle
  on already decided items rather than moving to the next pending proposal in a
  later page.

That behavior made the workflow feel technical and interrupted the intended
human review rhythm.

## Implemented Outcome

Story 0047 now treats the review workspace as a one-at-a-time sequential flow.

Implemented changes:

* [proposal-review-page.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-review-page.ts:1)
  now loads review pages sequentially until it finds pending work or a true
  completion state.
* [proposal-review-page.html](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-review-page.html:1)
  now presents a clear current-proposal workflow with secondary queue
  navigation.
* [proposal-review-page.scss](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-review-page.scss:1)
  supports the review-first presentation.
* [proposal-review-page.spec.ts](/home/ludo/Bureau/workspace/devlog-ai/frontend/src/app/features/insights/proposal-review-page.spec.ts:1)
  now covers same-page progression, cross-page progression, and completion
  behavior.

The backend projection contract remains unchanged:

* the page still consumes `proposal-review-v2`;
* no new queue endpoint was required;
* explicit decision submission still uses the same validation API behavior.

## Why This Matters

This story improves the human review workflow without weakening determinism or
traceability.

The result is better because:

* one proposal is now the obvious primary focus;
* the reviewer no longer has to manage queue progression manually across pages;
* the queue remains available as context, but it no longer dominates the
  interaction;
* the review journey now matches the intent behind a carousel-style workflow.

## What The Story Intentionally Does Not Do

This Story does **not**:

* redesign the backend review projection;
* add a dedicated “next pending proposal” API;
* redesign reviewer identity or authentication;
* add bulk review;
* add queue resume/session persistence beyond current behavior.

That keeps Story 0047 narrowly aligned with the approved plan.

## Tests And Verification

Passed:

* `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-review-page.spec.ts'`
* `npm run lint`
* `npm run format:check`
* `git diff --check`

Quality gate result:

* targeted frontend tests: **PASS**
* frontend lint: **PASS**
* frontend format verification: **PASS**
* diff formatting check: **PASS**

## Documentation Reconciliation

Documentation update: **Not required**

Reason:

* the story changes how the queue page progresses internally and visually;
* it does not alter the documented validation contract, reviewer attribution
  model, or direct proposal audit behavior.

## Architectural Outcome

Story 0047 clarifies another healthy boundary:

* the backend owns deterministic queue projection and review counts;
* the frontend owns the workflow semantics for moving the reviewer through that
  queue;
* the human still owns every individual accept/reject decision.

That is cleaner than moving carousel semantics into the server prematurely.

## Honest Limitations

Story 0047 improves the sequential review experience, but it does not make the
queue a fully persistent workflow engine.

Remaining limits:

* queue progression still depends on the existing paged projection;
* reviewer identity is still session-local;
* there is no bulk-review or resume-state model yet.

Those limitations are acceptable because they were explicitly outside scope.

## Final Outcome

Completed.

Story 0047 turns the proposal review queue into a deterministic one-at-a-time
carousel-style workflow, advances naturally across pages when needed, preserves
direct audit access, and leaves the backend review contract intentionally
unchanged.
