# Repository Analysis - Story 0164

## Repository State

- Branch: `feat/story-0164-user-ai-configuration-state`
- Base: `main` at `f4c624f`
- Worktree: clean before this analysis artifact.

## Governance

- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Story: 0164 - User AI Configuration and State.
- Governing ADR: ADR-075 - User AI Configuration and Runtime State Ownership.
- Supporting ADRs: ADR-006, ADR-067, ADR-070, ADR-073, ADR-074 and ADR-076.
- Dependency: Story 0163 provides the Core-owned provider/model catalog.

At the time this analysis was written, ADR-075 remained `Proposed - requires
human architecture approval` and Story 0164 remained `PROPOSED - design
materialized; implementation not authorized`. This analysis was a pre-
implementation snapshot and did not authorize production implementation.
The human project owner subsequently approved the implementation plan; the
current implementation and validation status are recorded in
`implementation-report.md`.

## Relevant Current Implementation

### Principal ownership

Core already defines the provider-neutral `AuthenticatedPrincipal` in
`backend/src/main/java/com/hopeful117/devlogai/authorization/AuthenticatedPrincipal.java`.
`RequestAuthenticatedPrincipalResolver` derives the principal from validated
Spring Security authentication and rejects anonymous or unauthenticated
requests. Configuration endpoints should use this boundary and must not accept
a principal identifier from request payloads or path parameters.

The closest controller pattern is
`aicredentialbinding/controller/AiCredentialBindingController.java`; the
existing `AiCredentialAssociationController` is also principal-scoped but
currently uses a ChatGPT-specific JWT adapter.

### Existing credential association boundary

`AiCredentialAssociation` is persisted by principal and provider, with a unique
`(principal_id, provider)` constraint. Its service reads and upserts through
`findByPrincipalIdAndProvider`, using the authenticated principal supplied by
Core. The association contains only an opaque credential handle in Core, not
secret material.

This is supporting runtime evidence, not the configuration entity. Story 0164
must not copy credential handles, tokens, API keys or OAuth subjects into the
user configuration model.

The current implementation has a provider identity gap: the catalog uses
`openai_chatgpt`, while `AiCredentialAssociationController` hardcodes
`chatgpt`. Story 0164 must not silently normalize or migrate these identifiers
without an explicit decision. The mismatch must be resolved or isolated before
the configuration state can reliably determine credential readiness.

### Provider/model catalog

Story 0163 introduced `AiProviderCatalogService` with provider/model resolution,
catalog versioning and declarative model availability. It is a suitable source
for validating provider and selected-model references, but catalog metadata is
not authorization and does not prove that a user has usable credentials.

The configuration implementation must preserve an explicit model selection when
the catalog recommendation changes. A selected model that remains known but is
`UNAVAILABLE` must remain persisted while the derived state becomes
`MODEL_UNAVAILABLE`.

### Persistence and migrations

Core uses JPA entities with UUID identifiers, explicit table/column names and
Flyway migrations under
`backend/src/main/resources/db/migration/`. `AiCredentialAssociation` is the
closest persisted principal-owned model and uses timestamps plus a unique
principal/provider constraint.

No user AI configuration or runtime-state table currently exists. Story 0164
will require a new migration and repository unless the approved plan explicitly
chooses another existing persistence boundary. The initial scope is one global
configuration per authenticated principal, not a project override.

### API and error conventions

The repository exposes typed request/response records and uses
`GlobalExceptionHandler` for validation, authentication, not-found and conflict
responses. `ApiErrorCode` has generic `UNAUTHENTICATED`, `ENTITY_NOT_FOUND`,
`RESOURCE_CONFLICT` and validation codes, but no configuration-specific state
codes.

The new capability should use the existing error conventions unless a concrete
acceptance criterion requires a new public error code. Error responses must not
reveal credential existence, secret values or provider runtime details.

## Required Configuration Shape

The internal configuration must represent the non-secret product intent defined
by ADR-075:

```text
UserAiConfiguration
  principalId              // ownership, never request-supplied
  provider
  authenticationMethod
  selectedModel
  modelSelectionOrigin    // RECOMMENDED or EXPLICIT
  allowedParameters       // constrained, non-secret product parameters
```

Runtime state must remain separate from persisted intent and must represent at
least:

```text
NOT_CONFIGURED
CONFIGURATION_INCOMPLETE
READY
REAUTH_REQUIRED
PROVIDER_UNAVAILABLE
MODEL_UNAVAILABLE
CREDENTIAL_INVALID
REVOKED
```

The exact state calculation boundary is not present in the repository. The
implementation plan must define whether Story 0164 calculates state from
catalog plus association snapshots, exposes a deterministic check service, or
introduces a separate synchronization adapter. It must not turn `READY` into a
permanent authorization grant.

## Scope Boundary

Story 0164 includes:

- one persisted non-secret configuration per authenticated principal;
- creation, update and deletion semantics;
- provider, authentication method and selected-model validation against the
  catalog;
- explicit versus recommended model origin;
- constrained allowed parameters;
- derived or synchronized product state;
- authenticated read/write API behavior;
- tests for isolation, state transitions and secret absence.

Story 0164 excludes:

- API key or OAuth token storage;
- credential intake or provider OAuth flow;
- project-level overrides;
- task credential bindings;
- multi-credential selection;
- automatic provider/model fallback;
- frontend components;
- dynamic provider discovery.

## Risks and Required Decisions

1. **Allowed parameters are underspecified.** The Story names permitted
   parameters but does not define names, types, bounds or a whitelist. The plan
   must choose a minimal typed contract or explicitly constrain V1 to an empty
   parameter set. Arbitrary JSON would weaken the product boundary.
2. **Runtime state ownership is underspecified.** `READY` requires credential
   association and current usability checks, but no existing service combines
   catalog, association and provider checks. The plan must define a deterministic
   Core boundary without storing secrets or silently changing preferences.
3. **Provider identifier mismatch exists.** `openai_chatgpt` is catalogued while
   the existing association controller uses `chatgpt`. This is a contract
   conflict that must be resolved before implementing readiness for that
   provider.
4. **Association model is provider-specific.** `AiCredentialAssociation`
   requires `chatgptSubject`, so it cannot yet be treated as a generic
   association for all providers without a separate approved change. Story 0164
   must not broaden that entity implicitly.
5. **Deletion and concurrency semantics are absent.** The Story requires
   creation, modification and suppression, but does not specify whether delete
   means no configuration, a tombstone, or a state transition, nor how concurrent
   updates are detected. The plan must define this explicitly.
6. **Public state leakage is possible.** `CREDENTIAL_INVALID`, `REVOKED` and
   `REAUTH_REQUIRED` may disclose provider/account details if errors are too
   granular. Responses should expose product state only and keep provider
   diagnostic details internal.

## Validation Plan

The implementation plan should cover:

- migration and repository persistence for one configuration per principal;
- create, update, delete and read behavior;
- authentication-derived ownership and cross-principal isolation;
- provider, authentication method, model and parameter validation;
- recommended model changes without mutating explicit selections;
- explicit unavailable model producing `MODEL_UNAVAILABLE`, not `READY`;
- state distinction between no configuration and unauthorized access;
- response and persistence inspection proving no secret fields;
- API error behavior for malformed, unknown and unauthorized requests;
- regression of the Story 0163 catalog and existing credential-association
  contracts.

Expected validation will include focused unit/controller tests and a PostgreSQL
integration test for the new migration and unique principal constraint. No live
provider call or real credential is required.

## Documentation Assessment

Story 0164 changes a persisted Core model and public API behavior. The
implementation will require reconciliation of the Story 0164 report and any
canonical API/architecture documentation that currently describes user AI
configuration ownership. It must not update frontend or operational
documentation speculatively before those surfaces exist.

No roadmap update is expected because `docs/roadmap.md` has no Epic registration
convention.

## DevLog and Vault Context

The DevLog Story Agent capability was unavailable in the current tool context;
this analysis uses the current repository and approved project artifacts.

The Obsidian vault was not consulted. The Story is governed by repository-owned
ADR and Story artifacts and the relevant implementation evidence was available
locally. No vault action is proposed from this analysis.

## Gate Status

Repository Analysis complete.

Human approval of this exact Repository Analysis is required before producing
the Implementation Plan. No production source file has been modified.
