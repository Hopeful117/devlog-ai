# Story 0047 — Turn Proposal Review Queue Into Sequential Carousel — Implementation Report

## Status

Implemented

## Summary

Implemented a frontend-only workflow refinement so the proposal review page now
behaves like a true one-at-a-time sequential review experience rather than a
technical paged list with a selected detail panel.

The fix preserves the existing backend review contract:

* the page still consumes the same paged `proposal-review-v2` projection;
* explicit per-proposal human decisions remain unchanged;
* direct audit access to the underlying proposal remains available.

## Changes

### 1. Refactored the review page around sequential page loading

Updated:

* `frontend/src/app/features/insights/proposal-review-page.ts`

Previous behavior:

* the page fetched one page at a time and selected a current item locally;
* after a decision, it could only choose the next pending proposal from the
  current page;
* when the current page had no remaining pending proposal, it could fall back
  to a decided item instead of progressing to later pages.

New behavior:

* the page now loads review pages sequentially until it finds a page that
  contains a pending proposal, unless global pending count is already zero;
* the current proposal is resolved from:
  * the existing current pending item when still valid,
  * otherwise the first pending item on the page,
  * otherwise the first item when no pending proposal remains;
* after a successful decision, refreshing the page now advances naturally to
  the next pending proposal, including across page boundaries when necessary.

Outcome:

* the review workspace now follows sequential review semantics rather than
  page-local selection semantics.

### 2. Demoted the full queue list and made the current proposal primary

Updated:

* `frontend/src/app/features/insights/proposal-review-page.html`
* `frontend/src/app/features/insights/proposal-review-page.scss`

Changes:

* added a progress panel that frames the page as a sequential review workflow;
* promoted the current proposal as the obvious primary focus;
* moved the current page queue into a secondary collapsible section;
* removed the old primary pagination controls from the main workflow surface;
* preserved the direct audit link for the current proposal.

Outcome:

* the page now feels review-first rather than list-first while still keeping
  supporting navigation available.

### 3. Expanded regression coverage for sequential and cross-page advancement

Updated:

* `frontend/src/app/features/insights/proposal-review-page.spec.ts`

Changes:

* now verifies:
  * sequential current-proposal framing,
  * same-page automatic advancement after a decision,
  * cross-page automatic advancement when later pending proposals exist,
  * explicit completion behavior when no pending proposal remains,
  * unchanged conflict refresh behavior,
  * unchanged reviewer-session compatibility.

Outcome:

* the carousel behavior is now covered as a first-class workflow rather than
  inferred indirectly from queue selection behavior.

## Behavioral Outcome

### Now fixed

* the review workspace presents one proposal as the primary focus
* after accept or reject, the UI advances to the next pending proposal on the
  same page when available
* when the current page is exhausted but later pages still contain pending
  proposals, the workspace advances across pages automatically

### Preserved

* explicit human confirmation for each proposal decision
* direct access to the underlying proposal audit page
* existing backend projection compatibility
* reviewer-session behavior already present in the queue workflow
* explicit completion messaging when no pending work remains

## Contract Outcome

Backend/API contract change: **None**

Clarification:

* Story 0047 changes frontend navigation semantics over the existing projection;
* it does not change the response shape returned by
  `/api/v1/analyses/{id}/proposal-review`.

## Documentation Outcome

Documentation update: **Not required**

Reason:

* the canonical documentation currently emphasizes human validation semantics,
  reviewer identity, and proposal audit behavior more than queue navigation
  mechanics;
* the implemented change stays within that documented behavior and does not
  alter the underlying validation contract.

## Vault Outcome

Vault consulted during Repository Analysis: **No**

Vault outcome: **no vault action**

Rationale:

* this story is a repository-local UX/workflow refinement without a new
  cross-project engineering pattern that needs curation;
* the repository code and story artifacts remain the canonical record.

## Validation

Performed:

* targeted frontend unit tests through the repository’s Angular test runner
* frontend lint
* frontend format verification
* repository diff formatting check

Results:

* `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-review-page.spec.ts'`: pass
* `npm run lint`: pass
* `npm run format:check`: pass
* `git diff --check`: pass

## Remaining Limitations

* the queue still relies on the existing paged backend projection rather than a
  dedicated server-side “next pending” contract
* reviewer identity handling remains session-local in this workflow
* this Story does not introduce bulk review or review-session recovery
