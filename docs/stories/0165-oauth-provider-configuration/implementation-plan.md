# Implementation Plan - Story 0165

## Scope

Integrate the existing Sign in with ChatGPT OAuth lifecycle with the Core-owned
configuration and credential-association model. Reuse the current OAuth
protocol implementation and encrypted AI Engine credential store. Do not add a
new OAuth protocol, move tokens into Core, change task grounding, or introduce
fallback behavior.

Implementation plan approved by the human project owner on 2026-10-30.
ADR-072, ADR-073 and ADR-075 are approved architectural constraints.

## Affected Modules

### AI Engine

- Extend the existing ChatGPT OAuth service/API to accept a short-lived,
  authenticated Core connection intent and to bind the browser transaction to
  that intent.
- After successful callback, retain tokens and credential handle in the AI
  Engine and return only non-secret association metadata through the existing
  authenticated Core-to-AI boundary.
- Add explicit disconnect/revocation notification so Core cannot retain an
  apparently usable association after the AI Engine credential is removed.
- Preserve existing PKCE, state, nonce, issuer/audience validation, granted
  scope validation, encrypted storage, refresh and bounded error behavior.

### Java Core

- Add a principal-scoped OAuth connection capability around the existing
  `AiCredentialAssociation` and Story 0164 configuration service.
- Derive the principal from the authenticated request; never accept principal
  identity from the browser payload or callback.
- Issue short-lived, single-use connection intent data and finalize the
  association only after AI Engine verification succeeds.
- Map the internal provider identity explicitly between the product catalog
  `openai_chatgpt` and the existing ChatGPT credential/provider boundary. Do not
  change existing `chatgpt` behavior without a tested compatibility mapping.
- Update runtime state projection for identity-only, missing-scope,
  reauthorization, revoked and usable association conditions without mutating
  the persisted selected model or selection origin.

### Documentation

- Update the canonical OAuth connection/configuration documentation only after
  the implemented API and lifecycle are known.
- Record the final documentation outcome in the Implementation Report.

## Ordered Implementation Steps

1. Define the Core-to-AI connection-intent contract using the existing
   authenticated service boundary and opaque identifiers only. Include
   principal binding, provider, redirect/session correlation, expiration and
   one-time consumption metadata. Exclude OAuth codes, access tokens, refresh
   tokens and ID tokens.
2. Add Core persistence/service behavior for pending connection intent and
   principal-scoped finalization. Ensure failed AI Engine verification leaves no
   active association and that replacement is atomic with respect to the
   association lifecycle.
3. Add AI Engine request validation and transaction binding. A browser callback
   must consume exactly one transaction and cannot be replayed, transferred to
   another principal or completed without the Core-issued intent.
4. Update the successful callback path to validate the actual granted scopes,
   store the encrypted credential, and call the Core finalization boundary with
   only the opaque handle and non-secret subject/client metadata.
5. Update disconnect/revocation propagation. Delete or invalidate the AI
   Engine credential first, then make future Core association/binding use fail
   closed. Preserve bounded, non-secret status/error categories.
6. Integrate Story 0164 state derivation with the association lifecycle:
   identity-only and missing required scopes map to incomplete/reauthorization
   states; revoked or invalid credentials do not claim `READY`; the selected
   model and explicit origin remain unchanged.
7. Reconcile the `openai_chatgpt` product identifier with the internal
   `chatgpt` credential/provider identifier through one explicit, tested mapping
   at the boundary. No alternate provider or credential fallback is allowed.
8. Update the affected OAuth/configuration documentation and complete the
   Implementation Report before review.

## Expected API Changes

- Add or adapt Core-authenticated connect, callback/finalization and disconnect
  operations for the product configuration flow.
- Add an internal authenticated Core-to-AI callback/finalization contract if
  the existing binding client cannot carry the connection intent safely.
- Keep public responses limited to product state and non-secret metadata.
- Return stable bounded errors for unauthenticated, expired, replayed,
  cancelled, scope-incomplete, revoked and provider-unavailable cases.
- Do not expose principal IDs, credential handles, OAuth subjects or token
  material in user-facing responses unless an existing contract explicitly
  requires a non-secret audit identifier.

## Persistence and Transaction Rules

- Core may persist only non-secret intent and association metadata.
- AI Engine remains the sole owner of OAuth access/refresh/ID tokens and
  encrypted credential storage.
- Pending intents are short-lived and single-use; consumed, expired or mismatched
  intents fail closed.
- Association finalization must not leave a usable Core row when AI Engine
  verification fails.
- Disconnect/revocation must invalidate future bindings without rewriting
  historical task snapshots.
- No new migration is assumed until the exact pending-intent and association
  schema is confirmed during implementation; if required, add one forward-only
  Flyway migration with rollback implications documented.

## Test Plan

### AI Engine tests

- successful callback with mocked token endpoint and deterministic ID-token/JWKS
  validation;
- state, session, redirect URI, PKCE verifier, nonce, issuer, audience and
  client-ID mismatch rejection;
- cancellation, missing usage scopes, expired transaction and replay;
- encrypted credential creation, replacement, refresh failure and disconnect;
- no secret-bearing logs, response bodies or callback payloads;
- provider stream success only after `response.completed`, with failed,
  incomplete and interrupted streams rejected.

### Core tests

- authenticated principal isolation for start, finalize, reconnect and revoke;
- pending intent expiration, replay, provider mismatch and principal mismatch;
- AI Engine verification failure leaves no active association;
- explicit `openai_chatgpt` to internal `chatgpt` mapping;
- runtime state for identity-only, missing scope, usable credential,
  reauthorization and revoked association;
- selected model and explicit selection origin remain unchanged;
- credential binding rejects revoked, expired and provider-mismatched
  associations.

### Integration and regression tests

- mocked Core-to-AI signed exchange with no live provider credentials;
- complete browser-to-callback-to-association flow using test doubles;
- disconnect blocks subsequent association/binding use;
- regression tests for Stories 0160, 0161 and 0164;
- PostgreSQL/Testcontainers coverage if Core persistence or migrations change;
- Python test suite and focused backend tests before broader repository checks.

## Validation Commands

```bash
cd ai-engine && python -m pytest -q
./backend/mvnw -pl backend -am test -B
./backend/mvnw -pl backend -am clean verify -B
git diff --check
```

Focused tests must run first for the changed AI Engine OAuth and Core
association/configuration modules. No live OAuth credentials or provider calls
are permitted in validation.

## Risks and Controls

| Risk | Control |
|---|---|
| Browser session can be transferred between principals | Bind the transaction to a Core-issued, authenticated, single-use intent and verify it at finalization. |
| Core stores or receives OAuth secrets | Keep token exchange/storage inside AI Engine and restrict callback payloads to opaque/non-secret metadata. |
| Failed verification leaves active association | Verify before activation or compensate transactionally; add an integration test. |
| `chatgpt`/`openai_chatgpt` mismatch creates split state | Centralize and test an explicit boundary mapping. |
| Identity is mistaken for plan usage | Persist and project granted scopes separately; require usage scopes for usable state. |
| Disconnect leaves stale bindings | Propagate revocation and fail closed before future provider use. |
| Proposed ADR is silently treated as accepted | Do not change ADR status or widen the binding/service-auth architecture without explicit human architecture approval. |

## Out of Scope

- new OAuth protocol or identity provider;
- ChatGPT conversation access;
- Anthropic API-key lifecycle;
- configuration test Story 0167;
- Simple/Advanced frontend UX;
- task integration beyond preserving and validating existing binding boundaries;
- provider fallback, model fallback or secret storage in Core.

## Plan Status

Implementation Plan approved by the human project owner on 2026-10-30.
