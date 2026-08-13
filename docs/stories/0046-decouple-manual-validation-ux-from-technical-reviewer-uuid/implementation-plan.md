# Story 0046 — Decouple Manual Validation UX From Technical Reviewer UUID — Implementation Plan

## Status

Planned

## Planning Goal

Apply the smallest safe UX fix that removes manual reviewer-UUID entry from the
direct proposal decision flow, while preserving the current backend validation
contract and immutable audit trail.

## Key Decision

Keep the backend reviewer-attribution contract unchanged and fix the problem at
the frontend session-identity layer.

Do **not** start with:

* a backend contract redesign,
* server-side auto-fabrication of reviewer identities,
* or an authentication overhaul.

The existing repository already has a session-local reviewer identity pattern.
The cleanest implementation is to reuse that pattern on the direct proposal
detail page.

## Why This Approach

The approved Repository Analysis established four important facts:

1. the backend requires `validatedBy`, but only as a stable reviewer
   identifier;
2. the detail page currently exposes that persistence-oriented UUID directly in
   the main user flow;
3. the review queue page already uses `ProposalReviewerSessionService` to
   create and reuse a session-local reviewer identity;
4. this story is intentionally scoped as a UX correction, not an identity
   redesign.

That means the highest-value plan is to align the direct audit page with the
existing session reviewer model instead of expanding the backend scope.

## In-Scope Implementation Steps

### Step 1 — Reuse session-local reviewer identity on the proposal detail page

Primary target:

* `frontend/src/app/features/insights/proposal-detail-page.ts`

Planned change:

* inject `ProposalReviewerSessionService`;
* initialize the detail-page form from the stored reviewer identity when one
  exists;
* auto-generate and persist a session-local reviewer UUID when the page is used
  without an existing reviewer identity;
* continue sending a valid backend-compatible `validatedBy` with both accept
  and reject requests.

Design preference:

* centralize reviewer-identity generation/reuse through the existing service
  instead of duplicating UUID handling inside the component.

### Step 2 — Remove manual UUID entry from the main detail-page UX

Primary target:

* `frontend/src/app/features/insights/proposal-detail-page.html`

Planned change:

* remove the visible requirement to type a reviewer UUID before deciding;
* replace it with clear session-local reviewer messaging appropriate to the
  unauthenticated MVP;
* keep explicit confirmation for accept/reject decisions unchanged;
* preserve optional comment and severity behavior.

UX constraint:

* the UI must still communicate that decisions are attributed to a local
  session reviewer identity, even if the raw UUID is no longer the main input
  surface.

### Step 3 — Align detail-page regression coverage with the new flow

Primary target:

* `frontend/src/app/features/insights/proposal-detail-page.spec.ts`

Planned changes:

* replace tests that depend on manual reviewer entry with tests proving the
  page can decide without manual UUID typing;
* verify that a valid reviewer identifier is created or reused automatically;
* verify accept and reject requests still send `validatedBy`;
* preserve conflict-refresh regression coverage.

Possible supporting test target:

* `frontend/src/app/features/insights/proposal-reviewer-session.service.spec.ts`

only if a small service behavior refinement is needed.

### Step 4 — Reconcile canonical frontend documentation

Primary targets:

* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

Planned change:

* remove instructions that tell reviewers to manually prepare or type a UUID
  for the direct proposal detail flow;
* document that the unauthenticated MVP now uses a session-local reviewer
  identity behind the scenes;
* keep auditability language accurate by stating that Core still records the
  reviewer identifier used for the decision.

## Explicit Out-Of-Scope Choices

This Story will **not**:

* redesign the backend `Validation` schema
* make `validatedBy` optional in the API
* add authentication or authorization
* redesign the review queue carousel
* introduce bulk review

Those belong to later or broader Stories.

## Files Likely To Change

Expected:

* `frontend/src/app/features/insights/proposal-detail-page.ts`
* `frontend/src/app/features/insights/proposal-detail-page.html`
* `frontend/src/app/features/insights/proposal-detail-page.spec.ts`
* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

Possible:

* `frontend/src/app/features/insights/proposal-reviewer-session.service.ts`
* `frontend/src/app/features/insights/proposal-reviewer-session.service.spec.ts`

if a small reuse-oriented adjustment is required.

## Validation Plan

At minimum:

* targeted frontend tests for the proposal detail page and reviewer session
  service
* project frontend test execution covering the modified insight review surface
* `git diff --check`

Behavioral validation:

* open a proposed item on `/proposals/:id`
* verify a human can accept or reject without manually entering a UUID
* verify the resulting decision still shows immutable reviewer attribution

## Risks

### Risk 1 — Silent reviewer identity generation feels opaque

Mitigation:

* surface concise explanatory copy that a session-local reviewer identity is
  being used in this MVP;
* keep final immutable decision attribution visible after submission.

### Risk 2 — Divergent reviewer behavior between detail page and queue page

Mitigation:

* reuse `ProposalReviewerSessionService` rather than inventing a second detail
  page flow.

### Risk 3 — Accidental contract drift in decision submission

Mitigation:

* keep request payload assertions in frontend tests;
* avoid backend API or persistence changes in this Story.

## Planned Outcome

After this Story:

* a reviewer will be able to accept or reject a proposal from the detail page
  without manually generating or typing a UUID;
* Core will continue recording a deterministic reviewer identifier for audit
  purposes;
* the direct proposal page and the review queue will follow the same
  session-local reviewer identity model;
* repository documentation will match the corrected UX.
