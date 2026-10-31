# Repository Analysis - Story 0168

## Workflow Status

Repository Analysis complete. Implementation is not authorized by the Story.
This analysis must be explicitly approved before an implementation plan is
created.

## Repository State

- Branch: `main`.
- Story 0167 is merged in commit `22816d56df0dfea820f4cf5dfe23725f2630579b`.
- Worktree was clean before this analysis.
- The DevLog Story Agent MCP capability was not available in the current tool
  set; analysis uses the canonical Story, Epic, accepted ADRs, current source,
  and tests.
- The Obsidian vault was searched for relevant AI configuration and UX notes;
  no relevant note was found.

## Governing Context

- Story 0168: Simple AI Configuration UX, first frontend implementation for
  the configuration Epic.
- Epic: implementation order is 0163, 0164, 0165, 0166, 0167, 0168, 0169,
  then 0170.
- ADR-074: Core-owned provider/model catalog and advisory recommendations.
- ADR-075: Core-owned non-secret user configuration and explicit runtime state.
- ADR-076: AI Engine-owned API-key secret lifecycle and one-time intake intent.
- Stories 0163-0167 are implemented or merged and provide the required backend
  capabilities.

## Current Implementation

### Core APIs already available

- `GET /api/v1/ai/catalog` exposes the curated provider/model catalog.
- `GET`, `PUT`, and `DELETE /api/v1/ai/configuration` expose the authenticated
  user's non-secret configuration and derived state.
- `POST /api/v1/ai/configuration/oauth/chatgpt/start` starts ChatGPT OAuth.
- `POST /api/v1/ai/configuration/oauth/chatgpt/complete` completes ChatGPT
  OAuth using a short-lived intent.
- `POST /api/v1/ai/configuration/api-key/anthropic/start` starts API-key
  enrollment.
- `POST /api/v1/ai/configuration/api-key/anthropic/complete` completes the
  enrollment using the opaque credential handle returned by the AI Engine.
- `POST /api/v1/ai/configuration/test` runs the bounded configuration test.

The Core configuration model contains provider, authentication method, selected
model, selection origin, allowed parameters, test status, and derived state.
It does not expose secrets or permit arbitrary credential selection.

### Frontend

- Angular 22 application with standalone components, lazy routes, RxJS, and
  existing HTTP service/test patterns.
- Existing navigation has a placeholder Settings link, but no AI configuration
  route, service, model, or components were found.
- Existing global SCSS provides dark-theme tokens, accessible focus styling,
  responsive layout primitives, badges, panels, and error messages.
- Existing frontend tests use Vitest and Angular HTTP testing providers.

## Required Product Boundary

The frontend should be a thin intent adapter over the Core contracts:

- display provider names, authentication methods, recommendations, and states
  from the catalog/configuration APIs;
- initiate OAuth without handling provider credentials;
- submit an API key only to the governed AI Engine intake endpoint returned by
  Core, and never retain or re-display it;
- persist only non-secret configuration choices through Core;
- invoke the bounded configuration test and render stable, actionable error
  categories;
- require explicit user confirmation before replacing a selected model;
- never select an arbitrary credential, infer authorization, or implement
  provider fallback in the browser.

## Scope Risks and Open Questions

1. The current API-key `start` response exposes an `intakeEndpoint` and signed
   enrollment metadata. The frontend implementation must use the existing
   governed intake contract without introducing a second credential transport.
2. The OAuth completion contract currently accepts a `completionCode`; the
   UX needs a concrete browser return/callback flow that does not place tokens
   in frontend state or URLs beyond the existing governed code contract.
3. The Story requires desktop/mobile and accessibility coverage, but the
   repository has no existing AI configuration UX to reuse. The implementation
   plan must define the route, component boundary, error mapping, and browser
   test surface before code is written.
4. The Story requires Simple and Advanced to converge on the same model. Story
   0168 must not introduce Advanced-only parameters or a competing configuration
   representation.
5. The existing backend `allowedParameters` validation currently accepts only
   an empty map, which is consistent with the Simple scope and must not be
   broadened under this Story.

## Proposed Validation Scope

- Frontend service tests for catalog, configuration, OAuth, API-key enrollment,
  and bounded test requests.
- Component tests for first launch, OAuth, API-key, cancellation, invalid key,
  model unavailable, reauthorization, explicit model replacement, and error
  actions.
- Accessibility checks for labels, keyboard focus, status announcements, and
  actionable error text.
- Responsive browser coverage at desktop and mobile viewports.
- Regression checks that no secret is stored in frontend state, URL, browser
  storage, rendered DOM, or error message.
- Backend contract/integration verification where the frontend contract reveals
  an ambiguity; no backend authority should move into the frontend.

## Gate

This Repository Analysis is ready for human approval. After explicit approval,
the next permitted stage is the Implementation Plan. No implementation files
should be modified until both the analysis and the subsequent implementation
plan have been explicitly approved.
