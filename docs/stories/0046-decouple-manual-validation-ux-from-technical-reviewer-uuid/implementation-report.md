# Story 0046 — Decouple Manual Validation UX From Technical Reviewer UUID — Implementation Report

## Status

Implemented

## Summary

Implemented a narrow frontend UX correction so the direct proposal detail page
no longer requires a reviewer to manually generate or type a UUID before
accepting or rejecting a proposal.

The fix preserves the existing backend validation contract:

* `validatedBy` is still sent on every decision request;
* Core still records immutable reviewer attribution;
* explicit human confirmation remains required for acceptance and rejection.

## Changes

### 1. Reused session-local reviewer identity on the proposal detail page

Updated:

* `frontend/src/app/features/insights/proposal-detail-page.ts`

Previous behavior:

* the page owned reviewer UUID generation itself;
* the reviewer had to manually populate the `validatedBy` field before the
  decision flow became usable.

New behavior:

* the page now injects `ProposalReviewerSessionService`;
* it reuses an existing session reviewer identity when available;
* it auto-generates a deterministic local reviewer UUID when no session
  identity exists yet;
* accept and reject requests still send a valid `validatedBy` value to Core.

Outcome:

* reviewer attribution remains deterministic for the unauthenticated MVP;
* manual UUID handling is removed from the main decision path.

### 2. Removed manual UUID entry from the direct decision UX

Updated:

* `frontend/src/app/features/insights/proposal-detail-page.html`

Changes:

* removed the visible reviewer UUID text input;
* removed the old “generate local test reviewer UUID” interaction;
* replaced it with concise session-identity messaging;
* added an optional “reset local reviewer session” action;
* kept explicit accept/reject confirmation unchanged.

Outcome:

* the human still makes an explicit irreversible decision;
* the technical reviewer UUID is no longer a prerequisite to interact with the
  page.

### 3. Updated regression coverage around the new flow

Updated:

* `frontend/src/app/features/insights/proposal-detail-page.spec.ts`

Changes:

* replaced manual-entry assumptions with automatic reviewer-session behavior;
* now verifies that:
  * a valid reviewer UUID is created automatically;
  * the same session reviewer identity is reused across renders;
  * resetting the reviewer session generates a fresh valid UUID;
  * accept and reject requests still include `validatedBy`;
  * conflict refresh behavior remains unchanged.

Outcome:

* the repaired UX is now protected by direct component tests rather than by
  manual assumptions.

## Behavioral Outcome

### Now fixed

* a reviewer can accept a proposal on `/proposals/:id` without manually typing
  a UUID
* a reviewer can reject a proposal on `/proposals/:id` without manually typing
  a UUID
* the page reuses a stable session-local reviewer identity until explicitly
  reset

### Preserved

* explicit human confirmation for final decisions
* immutable decision attribution in the backend
* `validatedBy` request compatibility with the existing validation API
* severity selection on acceptance
* conflict reload behavior after `409`

## Contract Outcome

Backend/API contract change: **None**

Clarification:

* Story 0046 changes how the frontend obtains the reviewer identifier;
* it does not change how Core expects or stores that identifier.

## Documentation Outcome

Updated:

* `frontend/README.md`
* `frontend/docs/manual-mvp-test.md`

Documentation update reason:

* the repository previously documented manual reviewer UUID entry as part of
  the MVP flow;
* that is no longer true for the direct proposal detail page.

## Vault Outcome

Vault consulted during Repository Analysis: **No**

Vault outcome: **no vault action**

Rationale:

* the story is a repository-local UX correction with no new cross-project
  engineering pattern worth curating separately;
* the existing implementation and repository documentation remain the relevant
  canonical record.

## Validation

Performed:

* targeted frontend unit tests through the repository’s Angular test runner
* repository diff formatting check

Results:

* `npm exec ng test -- --watch=false --include='src/app/features/insights/proposal-detail-page.spec.ts' --include='src/app/features/insights/proposal-reviewer-session.service.spec.ts'`: pass
* `git diff --check`: pass

## Remaining Limitations

* reviewer identity is still session-local rather than authenticated
* the review queue page still has its own visible reviewer controls
* this Story does not redesign bulk review or queue navigation
