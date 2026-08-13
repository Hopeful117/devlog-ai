# Story 0047 — Turn Proposal Review Queue Into Sequential Carousel — Repository Analysis

## Status

Completed

## Scope Of This Analysis

This Repository Analysis focuses on the existing proposal review queue page and
its current interaction model.

The goal is to determine:

* how close the current page already is to a sequential review experience;
* where the remaining non-carousel behavior still leaks through;
* what the smallest safe implementation path is for a true one-at-a-time
  proposal review flow.

## Story Context

Story 0047 follows Story 0046.

That dependency is meaningful:

* Story 0046 removed manual reviewer UUID entry from the direct proposal detail
  page;
* Story 0047 can now stay focused on the queue interaction model rather than
  mixing reviewer identity redesign into carousel work.

This story should therefore be treated as a workflow-UX correction for the
proposal review queue, not as a broader validation-contract or identity story.

## DevLog Context Outcome

DevLog lifecycle registration succeeded for this Story:

* DevLog story id: `0ac616c3-14e6-4c72-babc-4c895cec5d13`

DevLog engineering-story context retrieval also succeeded.

Useful signals from that context:

* current repository revision resolved to `9decfd792a7164d37c36a2b4c90098096017ce55`
* the compact repository context path is healthy after increasing timeout
  budget beyond the adapter default
* the returned evidence for this story remained generic, which reinforces that
  direct targeted repository inspection is still the authoritative source for
  this UI-focused analysis

## Vault Context Outcome

Vault was not consulted.

Reason:

* this story is narrowly about the current repository-local proposal review UX;
* the decisive evidence is in current Angular component logic, tests, and the
  existing backend review projection contract.

## Current Behavior

### 1. The review page already has a “current item”, but not a real carousel

The current queue page is implemented in:

* `frontend/src/app/features/insights/proposal-review-page.ts`
* `frontend/src/app/features/insights/proposal-review-page.html`

Important existing behavior:

* the page fetches a bounded paged review projection from
  `/api/v1/analyses/{analysisId}/proposal-review`
* it tracks a `currentId`
* it renders one primary proposal detail panel via `current(vm.data.items)`
* it selects the first `PROPOSED` item on refresh when possible

This means the page is **not** a raw grid anymore.

However, it is still not a true sequential carousel because:

* the full queue of items is always visible as a button list
* the experience still feels like selecting from a technical list
* “current proposal” is a rendering detail layered on top of a queue page, not
  the primary workflow abstraction

### 2. Auto-advance is partial and page-local

After accept or reject:

* `currentId` is reset to `null`
* the page refreshes the current review page
* the component then picks:
  * the previous current item if still present
  * otherwise the first `PROPOSED` item on the current page
  * otherwise `data.items[0]`

This behavior is close, but incomplete.

Two important gaps remain:

* the “next relevant proposal” is only chosen from the **current page**
* when the current page no longer contains a pending proposal, the component
  can fall back to a decided item instead of advancing to the next page with
  remaining pending work

That means AC-2 is only partially satisfied by the current implementation.

### 3. Completion behavior is explicit, but not fully aligned with page-local navigation

The page already displays:

* counts for total / pending / accepted / rejected
* a `Review complete` panel when `counts.pending === 0`

This is good and should be preserved.

However, because paging and current-item selection are separate concerns, the
component can still render queue-navigation mechanics that are more technical
than the intended one-at-a-time review flow.

### 4. The current queue projection contract is already sufficient

The backend projection contract:

* `backend/src/main/java/com/hopeful117/devlogai/proposal/review/ProposalReviewResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/proposal/review/ProposalReviewService.java`

already provides:

* bounded page metadata
* per-status counts
* ordered items
* decision/resulting artifact information

This is important because it means Story 0047 likely does **not** need a
backend contract redesign to achieve the carousel UX.

The current backend seems sufficient for:

* determining whether more pages exist
* determining whether pending items remain globally
* navigating page by page until the next pending item is found

### 5. Existing tests already capture parts of the intended behavior

Current regression coverage in:

* `frontend/src/app/features/insights/proposal-review-page.spec.ts`

already proves:

* queue progress rendering
* decision submission
* conflict refresh behavior
* pagination behavior
* active-item selection helpers

But it does **not** yet prove the true story outcome:

* one-proposal-first workflow semantics
* automatic advancement to the next pending proposal across page boundaries
* explicit no-pending state in the sequential flow

## Architectural Interpretation

The repository does not need a new queue API to deliver this story.

The real issue is in the frontend workflow model:

* the page still treats the review experience as “a paged list with a selected
  detail”

when the story wants:

* “a current proposal in a sequential review journey”

That distinction matters.

The best implementation direction is to keep the existing projection and shift
the Angular page so that:

* one proposal is the obvious primary focus
* queue navigation becomes secondary metadata or lightweight progression
* advancing after a decision is deterministic, including across page
  boundaries when necessary

## Candidate Implementation Directions

### Option A — Frontend-only carousel over the existing paged projection

Approach:

* keep the backend projection unchanged
* refactor `ProposalReviewPage` to behave like a sequential carousel
* after a decision, search the current page first, then move forward across
  pages until the next pending proposal is found
* reduce the visual prominence of the full queue list while preserving direct
  audit access

Benefits:

* smallest change surface
* preserves the current deterministic projection contract
* fits the story scope cleanly
* keeps regression scope focused on Angular behavior

Risks:

* page-transition logic must stay deterministic and easy to test
* careless implementation could increase imperative state complexity

Assessment:

* best fit for this story.

### Option B — Backend returns a dedicated “next pending proposal” contract

Approach:

* add a dedicated endpoint or response shape for “current” and “next”
  proposals

Benefits:

* could simplify frontend state transitions

Risks:

* larger backend contract change than the current story appears to need
* more moving parts and more review surface
* unnecessary if the existing paged projection is already sufficient

Assessment:

* not recommended unless frontend-only implementation proves too awkward.

### Option C — Keep the queue list and only tweak current-item defaults

Approach:

* preserve the full visible list as the main navigation
* only improve which item gets selected after refresh/decision

Benefits:

* very low change volume

Risks:

* would not really deliver the “true sequential carousel” objective
* would leave the page cognitively list-first rather than review-first

Assessment:

* insufficient for the story intent.

## Recommended Direction

Implement Option A.

More concretely:

1. Treat the review page as a sequential workflow with one primary current
   proposal.
2. Preserve direct audit access through the detail-page link.
3. Keep global progress visible, but demote the full queue list from primary UI
   focus.
4. On successful accept/reject, advance to the next pending proposal
   deterministically.
5. When the current page has no remaining pending proposal but more pages
   exist, advance across pages automatically until:
   * a pending proposal is found, or
   * the queue is exhausted.

## Likely Affected Areas

### Frontend

Primary:

* `frontend/src/app/features/insights/proposal-review-page.ts`
* `frontend/src/app/features/insights/proposal-review-page.html`
* `frontend/src/app/features/insights/proposal-review-page.scss`

Tests:

* `frontend/src/app/features/insights/proposal-review-page.spec.ts`

### Backend

Likely no contract change required.

The existing review projection should remain reusable as-is unless an
unexpected gap appears during implementation.

### Documentation

Potential canonical documentation touchpoints:

* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

only if they currently describe the queue behavior in a way that becomes
materially inaccurate after the carousel change.

## Risks And Constraints

### Preserve explicit per-proposal human decisions

The UI must never auto-decide while auto-advancing.

Only navigation may be automatic; each proposal decision must remain explicit.

### Preserve direct audit access

The “Open direct audit page” affordance is important and should remain intact.

The carousel flow should focus the review, not trap the reviewer inside it.

### Avoid page-local false completion

The implementation must not behave as though review is complete simply because
the current page has no pending item while later pages still do.

### Keep the state machine testable

The more the page behaves like a carousel, the more important it becomes to
keep page transitions deterministic and spec-driven rather than ad hoc.

## Test Impact

At minimum, regression coverage should prove:

* the page presents one current proposal as the primary focus
* after acceptance, the next pending proposal becomes current automatically
* after rejection, the next pending proposal becomes current automatically
* when the current page is exhausted but more pending proposals exist later,
  the page advances across pagination
* when no pending proposals remain anywhere, the completion state is explicit

Existing pagination and conflict behavior tests should be preserved where still
relevant.

## Repository Analysis Verdict

Story 0047 is primarily a frontend workflow refinement.

The existing backend review projection is already strong enough. The real work
is to turn the current “selected item within a queue page” into a deterministic
one-at-a-time review journey that advances naturally after each decision,
including across paged queue boundaries.

## Approval Recommendation

Repository Analysis ready for human review.

Recommendation:

* Approve this Repository Analysis and continue to Implementation Planning.
