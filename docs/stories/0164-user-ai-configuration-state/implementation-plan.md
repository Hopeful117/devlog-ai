# Implementation Plan - Story 0164

## Approved Repository Analysis

`repository-analysis.md` is approved by the human project owner.

## Scope

Implement the Core-owned, global-per-principal non-secret AI configuration and
its product-level state. Keep credential material, provider OAuth/API-key
intake, task bindings, project overrides, frontend work and fallback behavior
outside this Story.

## Required Domain Model

Add a dedicated `aiuserconfiguration` Core package containing:

- an immutable request/response model for provider, authentication method,
  selected model, selection origin and allowed parameters;
- `AiModelSelectionOrigin` with `RECOMMENDED` and `EXPLICIT`;
- `AiUserConfigurationState` with the ADR-075 state names;
- a persisted JPA entity owned by `principalId`, with one row per principal;
- repository methods scoped by authenticated `principalId` only;
- a service that validates catalog references and never accepts a principal ID
  from a client request.

The persistence model must contain no credential handle, token, API key, OAuth
subject, authorization code or provider runtime payload.

## Allowed Parameters

V1 will persist `allowedParameters` as a JSON object using the repository's
existing PostgreSQL JSONB/JPA pattern. The initial whitelist is empty until a
provider-specific parameter is explicitly catalogued. Non-empty or unknown
parameters must be rejected rather than stored as arbitrary JSON.

This keeps the field forward-compatible without creating an unbounded provider
configuration contract.

## Persistence

Add the next Flyway migration after `V60`:

- table `ai_user_configurations`;
- UUID primary key;
- unique `principal_id` constraint;
- provider, authentication method, selected model and selection origin columns;
- JSONB `allowed_parameters` column;
- created and updated timestamps;
- indexes only where required by principal ownership or state reads.

No secret-bearing column is permitted. Add a PostgreSQL integration test for the
migration, unique principal constraint and persisted JSON shape.

## State Derivation

Add a separate state service. It must derive state without mutating the stored
configuration:

1. no configuration -> `NOT_CONFIGURED`;
2. incomplete or invalid non-secret configuration ->
   `CONFIGURATION_INCOMPLETE`;
3. catalog-known model with `UNAVAILABLE` availability ->
   `MODEL_UNAVAILABLE`;
4. a supported no-auth provider with a valid available model and no missing
   checks -> `READY`;
5. credential-dependent states must be returned through an explicit
   credential-association status boundary and must not be inferred from a
   secret or from frontend input.

The implementation must not claim `READY` for OAuth/API-key providers unless
the existing Core association boundary can provide a verified usable state.
If that boundary is not available in this Story, the state must remain a
non-ready configuration state and the limitation must be recorded.

Runtime state is a projection; changing the catalog recommendation or runtime
availability must never rewrite `selectedModel` or `modelSelectionOrigin`.

## API Boundary

Follow the existing authenticated Core controller pattern:

- `GET /api/v1/ai/configuration` returns the authenticated principal's
  configuration and derived state;
- `PUT /api/v1/ai/configuration` creates or replaces that principal's
  non-secret configuration after validation;
- `DELETE /api/v1/ai/configuration` removes the configuration and returns
  `204 No Content`;
- no route accepts `principalId`.

Responses must expose product state and non-secret choices only. They must not
serialize persistence entities directly if doing so could expose future fields.
Use explicit DTOs and test response fields for secret absence.

## Provider Identity Compatibility

The configuration contract must use the canonical catalog identifier
`openai_chatgpt`. Existing `chatgpt` association behavior must be isolated
behind an explicit adapter or left non-ready until the canonical identity is
resolved. No silent database migration or broad credential-association rewrite
is included in this Story.

This is a blocking design decision for the implementation if the state service
must consume the existing ChatGPT association. Human approval must confirm one
of:

1. treat `openai_chatgpt` as the canonical identifier and add an explicit
   compatibility adapter for the legacy association identifier; or
2. defer credential-dependent readiness to Stories 0165/0166 and implement only
   the configuration persistence and catalog/model state in Story 0164.

## Tests

Add focused tests for:

- create, update, read and delete for one authenticated principal;
- no configuration versus unauthorized/unauthenticated access;
- cross-principal isolation and absence of request-supplied ownership;
- provider, authentication method and model validation;
- empty/unknown `allowedParameters` rejection;
- recommended model changes without changing an explicit selection;
- explicit unavailable model returning `MODEL_UNAVAILABLE` and preserving the
  selected identifier;
- no-auth provider readiness;
- response and persistence absence of secret fields;
- REST validation and error responses;
- migration and unique principal persistence behavior.

No live provider, OAuth flow or real credential is required.

## Documentation Reconciliation

Update the Story implementation report with the documentation outcome. Update
canonical API or architecture documentation only if the repository contains a
document describing the new public configuration contract. Do not modify
frontend or deployment documentation until those surfaces are implemented.

The implementation report must include the required vault outcome. No vault
write is authorized by this plan.

## Validation Commands

Focused validation:

```bash
./backend/mvnw -pl backend -am -Dtest=<configuration test classes> \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
```

Broader backend validation:

```bash
./backend/mvnw -pl backend -am test -B
```

The migration integration test requires the repository's PostgreSQL/Testcontainers
environment. No provider credentials or network calls are permitted.

## Risks and Blockers

- ADR-075 is still `Proposed`; this plan does not accept it architecturally.
- The `openai_chatgpt` versus `chatgpt` identity mismatch requires an explicit
  implementation decision before credential-dependent `READY` behavior.
- Story 0163 does not currently provide a curated Anthropic model, so the
  Anthropic vertical slice cannot be claimed from this Story without a separate
  catalog decision.
- Existing credential associations do not expose a durable generic status for
  `REAUTH_REQUIRED`, `CREDENTIAL_INVALID` or `REVOKED`; those states must not be
  fabricated from association presence alone.

## Gate Status

Implementation Plan approved by the human project owner. Story 0164
implementation is complete on the dedicated feature branch; see the
implementation report for the implemented scope and validation evidence.
