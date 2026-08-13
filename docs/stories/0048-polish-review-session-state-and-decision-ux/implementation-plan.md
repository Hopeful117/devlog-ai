# Story 0048 — Polish Review Session State And Decision UX — Implementation Plan

## Status

Planned

## Implementation Strategy

Keep Story 0048 frontend-first.

The backend validation contract, reviewer attribution model, and paged review
projection already appear sufficient.

The implementation should therefore focus on:

* clearer review progress framing;
* more explicit decision feedback;
* more human-friendly reviewer-session continuity;
* stronger empty/completion/resume states;
* visual polish of the affected review pages;
* targeted regression coverage for the refined behaviors.

No backend change should be introduced unless a concrete implementation blocker
proves that the current projection is insufficient.

## Step 1 — Refine the queue review state model around continuity and feedback

Target:

* `frontend/src/app/features/insights/proposal-review-page.ts`

Goals:

* express whether the reviewer session is newly created, resumed, or reset;
* make post-decision feedback explicit enough for the template to render
  unambiguous status copy;
* keep progression deterministic and easy to test;
* avoid introducing sprawling imperative UI state.

Implementation direction:

* reuse the existing `ProposalReviewerSessionService` as the source of truth for
  session-local continuity;
* add small, explicit view-model signals for:
  * resumed reviewer session presence,
  * action success/error/pending feedback,
  * completion/empty-state framing;
* preserve the current sequential page-loading logic unless a real defect is
  discovered.

Constraints:

* do not redesign reviewer attribution semantics;
* do not add a backend-backed review session concept;
* do not weaken explicit human confirmation.

## Step 2 — Polish the queue review template and reviewer UX copy

Target:

* `frontend/src/app/features/insights/proposal-review-page.html`

Goals:

* present queue progress as a more intentional review journey;
* reduce technical emphasis on the raw UUID field;
* make reviewer-session continuity legible to the human reviewer;
* improve explicit feedback after accept/reject actions;
* improve empty, completion, and resume states.

Implementation direction:

* revise copy so the reviewer identity is framed as a local review session
  rather than as a raw persistence identifier;
* keep reset/change affordances available, but demote them visually;
* strengthen the progress panel with clearer active-state and completion-state
  messaging;
* surface decision feedback in a calm, explicit way that does not rely only on
  transient pending/error messages;
* preserve direct audit access and the secondary queue section.

Constraints:

* do not imply authentication or multi-user guarantees that do not exist;
* do not turn this Story into a workflow-domain redesign.

## Step 3 — Visually polish the queue review page

Target:

* `frontend/src/app/features/insights/proposal-review-page.scss`

Goals:

* move the page from “functional MVP” styling to a more coherent, intentional
  review surface;
* improve hierarchy between progress, current proposal, and secondary queue;
* give decision areas, status areas, and completion states clearer visual
  semantics;
* keep the page responsive and maintainable.

Implementation direction:

* introduce a more deliberate layout rhythm, spacing, surface treatment, and
  visual grouping;
* strengthen the current-proposal focus without hiding the surrounding queue;
* style feedback, confirmation, and completion areas as purposeful states
  rather than generic blocks;
* keep mobile behavior straightforward and robust.

Constraints:

* preserve the existing design language of the application where it already
  exists;
* avoid decorative complexity that makes the UI harder to scan or harder to
  test.

## Step 4 — Harmonize the directly related proposal detail review page

Targets:

* `frontend/src/app/features/insights/proposal-detail-page.html`
* `frontend/src/app/features/insights/proposal-detail-page.scss`
* optionally `proposal-detail-page.ts` if minor state-copy adjustments are
  needed

Goals:

* align the direct proposal review surface with the improved queue review UX;
* reduce inconsistency between the two review entry points;
* keep the detail page’s stronger reviewer-session baseline from 0046 while
  bringing its visual treatment and feedback semantics into the same polish
  family as 0048.

Implementation direction:

* harmonize reviewer-session messaging and decision-area affordances with the
  queue page;
* polish evidence, metadata, and decision panels so the detail page feels like
  the same family of review surfaces;
* keep status/result sections explicit and easy to scan.

Constraints:

* do not redesign the underlying detail-page information architecture beyond
  what is necessary for consistency and polish;
* do not change direct proposal decision semantics.

## Step 5 — Expand focused regression coverage

Targets:

* `frontend/src/app/features/insights/proposal-review-page.spec.ts`
* any directly relevant detail-page spec if behavior/copy/session handling is
  refined there

Goals:

* lock in the new UX behavior without making tests style-fragile;
* prove continuity, session-state, and feedback semantics explicitly;
* keep tests centered on meaningful interaction outcomes.

Coverage to add or refine:

* resumed session messaging or state when reviewer identity already exists;
* clearer decision feedback after explicit review actions;
* completion/empty-state messaging refinements;
* any changed reviewer reset/change behavior;
* any directly related detail-page refinements introduced for consistency.

Testing rule:

* assert user-visible behavior and deterministic state transitions;
* avoid assertions that depend on incidental CSS details unless the style
  carries real semantic state.

## Step 6 — Run targeted frontend verification

Validation targets:

* review-page specs
* any touched detail-page specs
* frontend lint
* format verification
* diff formatting check

Expected commands:

* `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-review-page.spec.ts'`
* if needed:
  * `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-detail-page.spec.ts'`
* `npm run lint`
* `npm run format:check`
* `git diff --check`

If the template or styling changes require broader frontend verification, expand
validation only as needed.

## Documentation Expectation

Documentation may need a small update if the manual MVP review flow text still
describes the review pages in visibly outdated terms.

Likely candidates:

* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

This should remain proportional:

update docs only if the new UX changes what the repository currently tells
humans to expect.
