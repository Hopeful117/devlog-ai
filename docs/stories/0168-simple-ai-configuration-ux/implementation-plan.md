# Implementation Plan - Story 0168

## Status

Implementation Plan complete. It requires explicit human approval before any
production implementation begins.

## Objective

Add the first user-facing Simple AI Configuration flow for ChatGPT OAuth and
Anthropic API key. The flow must consume the existing Core contracts, converge
on the persisted non-secret configuration model, and expose clear product
states without moving authorization or secret ownership into Angular.

## Design

### Frontend Contract Layer

1. Add typed models for the catalog, configuration response, configuration
   states, OAuth start response, API-key enrollment response, and bounded test
   result categories.
2. Add an Angular service that calls the existing Core endpoints:
   - catalog read;
   - configuration read/save/delete;
   - OAuth start/complete;
   - API-key enrollment start/complete;
   - configuration test.
3. Keep provider identifiers inside the service/catalog mapping. Templates must
   render user-facing catalog labels rather than internal provider names.
4. Keep all request state ephemeral. Do not write API keys, opaque handles,
   OAuth completion codes, enrollment signatures, or provider payloads to
   local storage, session storage, persistent state, or analytics.

### Simple Configuration Experience

1. Add a settings/configuration route accessible from the existing application
   shell Settings navigation.
2. Add a standalone configuration page with an initial provider choice between
   the catalog-supported ChatGPT OAuth and Anthropic API-key paths.
3. Render the shared state machine explicitly:
   `NOT_CONFIGURED`, `CONFIGURATION_INCOMPLETE`, `REAUTH_REQUIRED`, `READY`,
   `MODEL_UNAVAILABLE`, `CREDENTIAL_INVALID`, `PROVIDER_UNAVAILABLE`, and
   bounded test/transport errors.
4. For ChatGPT OAuth, start the Core intent and navigate to the returned
   authorization URL. OAuth completion is finalized server-to-server by the AI
   Engine calling a new HMAC-authenticated Core callback. The browser receives
   only a non-sensitive success/failure status and never handles the completion
   code, access token, or credential handle.
5. For Anthropic API key, start the Core enrollment intent, submit the key only
   to the returned governed AI Engine intake endpoint with the returned signed
   fields, discard the key immediately after the request, then send only the
   opaque completion handle back to Core. Never display or retain the key after
   submission.
6. Save the selected model through Core. A recommendation may preselect the
   catalog recommendation, but changing an existing explicit model requires a
   user action and confirmation.
7. Run the bounded configuration test only through Core and display its stable
   result. A successful test must render `READY` only for the selected model.
8. Provide actionable, secret-free errors and retry/reconnect actions. Do not
   expose raw exception text or provider response bodies.
9. Preserve the existing Simple scope: no arbitrary credential picker,
   fallback, Advanced parameters, environment-variable editing, or secret
   display.

### Backend/API Boundary

- Reuse the existing Core endpoints and response contracts from Stories
  0163-0167; add only the bounded internal OAuth completion callback required
  to make the approved Simple flow usable.
- The callback is service-authenticated and accepts only the intent identity,
  validated subject, provider, opaque credential handle, and bounded OAuth
  metadata required for association. Core resolves the principal from its
  persisted intent and remains the association authority.
- The callback must be idempotent, single-use, expiry-checked, provider-bound,
  and fail closed on principal, intent, or provider mismatch. If association
  fails after AI Engine storage, the existing compensation/revocation path must
  be used.
- The AI Engine callback redirects the browser only after server-to-server
  finalization, with a non-sensitive status and no completion code or handle.
- If browser access to the returned AI Engine intake endpoint is not supported
  by the deployment's CORS/runtime contract, stop and request an explicit
  transport decision; do not proxy secrets through an ad-hoc frontend or Core
  endpoint.

## Files and Modules

### Frontend

- Add a feature-local configuration service and typed models.
- Add standalone Simple configuration page/component and stylesheet.
- Add route and Settings navigation entry.
- Add unit tests for service request contracts, state rendering, secret
  non-retention, OAuth/API-key flows, model replacement, and error mapping.
- Add/update browser smoke coverage for desktop/mobile responsive behavior and
  keyboard-accessible controls.

### Documentation

- Add or update the canonical user-facing Simple configuration guide only if
  the repository contains the established canonical location.
- Document the action-oriented error and reauthorization behavior without
  documenting internal provider credentials or secret transport details.
- Record the documentation outcome and vault outcome in the implementation
  report. No curated Obsidian vault write is authorized.

## Security and Governance Constraints

- Core remains the authority for principal, provider, model, association,
  runtime state, and authorization.
- AI Engine remains the owner of OAuth/API-key secret storage and resolution.
- The frontend never receives an API key after intake and never stores secrets
  or credential handles persistently.
- OAuth and API-key flows remain separate lifecycles while converging on the
  same `ProviderConfiguration` model.
- OAuth completion codes remain inside the AI Engine/Core service boundary and
  never enter browser state or URL parameters.
- No fallback between providers, credentials, or models.
- A model recommendation never silently replaces explicit user intent.
- Errors, telemetry, URL state, DOM state, and browser storage must exclude
  secrets, access tokens, raw provider payloads, and signed enrollment data
  after completion.

## Tests

### Frontend Unit

- catalog and configuration request/response mapping;
- first-launch provider selection;
- OAuth start and completion state transitions;
- API-key enrollment submission and immediate secret discard;
- cancellation and retry behavior;
- invalid key, reauthorization, unavailable provider, unavailable model, and
  timeout/error mapping;
- explicit model replacement confirmation;
- no fallback when the selected model is unavailable;
- no credential picker or persistent secret state.

### Frontend Browser

- ChatGPT OAuth happy path at desktop and mobile widths;
- Anthropic API-key happy path at desktop and mobile widths;
- cancellation, reauthorization, invalid key, and model-unavailable paths;
- keyboard navigation, labels, focus visibility, and status announcements;
- rendered DOM, URL, and browser storage do not retain the API key.

### Regression

- Existing frontend lint, formatting, unit tests, strict production build, and
  E2E smoke tests.
- Existing backend configuration contract tests where changed integration
  reveals a contract defect.
- `git diff --check` and complete diff review before delivery.

## Validation Commands

```bash
cd frontend && npm run lint
cd frontend && npm run format:check
cd frontend && npx ng test --coverage --watch=false
cd frontend && npm run build
cd frontend && npm run e2e
git diff --check
```

## Resolved Implementation Decision

The OAuth browser completion gap is resolved by a service-to-service callback.
The AI Engine completes the provider OAuth exchange, then calls Core through a
new HMAC-authenticated endpoint. Core validates and consumes the persisted
connection intent, creates the credential association, and returns a bounded
acknowledgement. Only then does the AI Engine redirect the browser to the
frontend with a non-sensitive status. The existing Core public completion
endpoint must be retained only for compatibility if current callers require it;
the new Simple UX must not use it.

The API-key browser transport is resolved by a separate public AI Engine base
URL configured in Core (`AI_ENGINE_PUBLIC_BASE_URL`) and a strict AI Engine
CORS allowlist (`AI_ENGINE_CORS_ORIGINS`). The public URL is used only for the
one-time signed enrollment endpoint; Core-to-AI service calls continue using the
private `AI_ENGINE_BASE_URL`. The API key remains browser-to-AI Engine only and
is never proxied through Core.

## Gate

This plan version includes the approved server-to-server OAuth completion
decision and is ready for explicit human approval. Implementation must not
begin until the human approves this current plan version.
