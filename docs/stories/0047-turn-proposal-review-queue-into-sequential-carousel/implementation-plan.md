# Story 0047 — Turn Proposal Review Queue Into Sequential Carousel — Implementation Plan

## Status

Planned

## Planning Goal

Apply the smallest safe frontend refinement that turns the proposal review page
into a true one-at-a-time sequential review flow, while preserving the existing
backend projection contract and explicit human per-proposal decisions.

## Key Decision

Keep the current backend review projection unchanged and implement the carousel
behavior entirely at the Angular page/workflow layer.

Do **not** start with:

* a new backend “next proposal” endpoint,
* a proposal review contract redesign,
* or a reviewer identity redesign.

The repository already provides enough queue metadata and page information to
drive deterministic sequential review behavior from the frontend.

## Why This Approach

The approved Repository Analysis established four important facts:

1. the review page already has the concept of a current proposal;
2. the remaining gap is mainly in frontend workflow semantics, not backend
   data availability;
3. auto-advance is currently page-local and can fall back to already decided
   items;
4. the existing paged projection already exposes counts, items, and pagination
   metadata sufficient for deterministic navigation.

That means the highest-value implementation is to convert the page from
“selected item within a queue” into “current proposal within a sequential
journey” without expanding the contract surface.

## In-Scope Implementation Steps

### Step 1 — Refactor `ProposalReviewPage` around sequential current-item progression

Primary target:

* `frontend/src/app/features/insights/proposal-review-page.ts`

Planned change:

* make “current proposal” the primary state machine concept;
* keep selecting a pending proposal first whenever one exists;
* after a successful decision, advance deterministically to the next pending
  proposal;
* when the current page is exhausted but `page.hasNext` is true and global
  pending count remains non-zero, advance across pages until the next pending
  proposal is found;
* avoid falling back to a decided item while pending work still exists
  elsewhere.

Design preference:

* add small private helpers for “find current pending”, “advance to next page”,
  and “resolve completion state” rather than embedding all logic inline in the
  observable pipeline.

### Step 2 — Demote the full queue list and make the current proposal the obvious focus

Primary targets:

* `frontend/src/app/features/insights/proposal-review-page.html`
* `frontend/src/app/features/insights/proposal-review-page.scss`

Planned change:

* keep one current proposal as the clear primary review surface;
* reduce the visual prominence of the full queue button list so it becomes
  supportive navigation rather than the main workflow;
* preserve direct audit access to the underlying proposal;
* keep explicit empty/completion messaging when no pending proposal remains.

UX constraint:

* the page should still expose progress and manual navigation affordances, but
  the human should feel guided through one proposal at a time rather than asked
  to manage a technical list.

### Step 3 — Align reviewer session behavior with the post-0046 model

Primary target:

* `frontend/src/app/features/insights/proposal-review-page.ts`

Possible UI touchpoint:

* `frontend/src/app/features/insights/proposal-review-page.html`

Planned change:

* keep the existing reviewer-session compatibility intact;
* ensure the sequential flow does not regress reviewer handling while current
  proposals change;
* if needed, mildly reduce reviewer-control prominence so the carousel story
  stays focused on proposal progression rather than on identity setup.

Scope note:

* this step is about preserving compatibility with the existing session-local
  reviewer model, not redesigning it.

### Step 4 — Add regression coverage for sequential and cross-page advancement

Primary target:

* `frontend/src/app/features/insights/proposal-review-page.spec.ts`

Planned changes:

* add tests proving one current proposal is primary;
* verify automatic progression after acceptance;
* verify automatic progression after rejection;
* verify cross-page advancement when the current page no longer contains a
  pending proposal but later pages do;
* verify explicit completion behavior when no pending proposals remain;
* preserve relevant conflict-refresh and pagination tests where still valid.

## Explicit Out-Of-Scope Choices

This Story will **not**:

* redesign the backend proposal review response
* add a new dedicated backend carousel endpoint
* redesign reviewer identity or authentication
* introduce bulk review
* introduce long-term queue persistence or resume state beyond current behavior

Those belong to separate, broader stories if needed.

## Files Likely To Change

Expected:

* `frontend/src/app/features/insights/proposal-review-page.ts`
* `frontend/src/app/features/insights/proposal-review-page.html`
* `frontend/src/app/features/insights/proposal-review-page.scss`
* `frontend/src/app/features/insights/proposal-review-page.spec.ts`

Possible:

* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

only if they currently describe queue behavior in a way that becomes
materially inaccurate after the carousel implementation.

## Validation Plan

At minimum:

* targeted frontend tests for `proposal-review-page`
* project frontend unit test execution
* `git diff --check`

Behavioral validation:

* open `/analyses/:id/proposal-review`
* verify one proposal is visually primary
* accept or reject the current proposal
* verify the page advances automatically to the next pending proposal
* verify explicit completion state when no pending proposal remains

## Risks

### Risk 1 — Cross-page auto-advance becomes stateful and brittle

Mitigation:

* keep advancement logic in small deterministic helpers;
* cover page-boundary scenarios directly in specs.

### Risk 2 — The page still feels list-first despite logic improvements

Mitigation:

* change both the state model and the visual emphasis;
* demote the full queue list rather than only changing selection defaults.

### Risk 3 — Completion handling becomes page-local instead of global

Mitigation:

* rely on global `counts.pending` plus page metadata;
* never declare completion solely because the current page has no pending item.

## Planned Outcome

After this Story:

* the proposal review workspace will behave like a true one-at-a-time review
  carousel;
* after a successful accept or reject, the UI will advance to the next pending
  proposal automatically, including across page boundaries when necessary;
* explicit completion behavior will remain visible when no pending proposal
  remains;
* direct audit access and deterministic per-proposal review semantics will stay
  intact.
