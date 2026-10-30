# Repository Analysis - Story 0165

## Repository State

- Branch: `feat/story-0165-oauth-provider-configuration`
- Base: `main` at `a5017fd` (merged Story 0164)
- Worktree: clean before this analysis artifact.

## Story and Governance

- Story: 0165 - OAuth Provider Configuration Integration.
- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Governing decisions: ADR-072, ADR-073 and ADR-075.
- Supporting decisions: ADR-006, ADR-067, ADR-070, ADR-074 and ADR-076.
- Dependency status: Stories 0160, 0161, 0163 and 0164 are implemented and
  delivered; Stories 0165 remains proposed and implementation is not yet
  authorized.

ADR-072, ADR-073 and ADR-075 remain `Proposed - requires human architecture
approval`. This analysis records repository evidence and does not authorize
production implementation.

DevLog context: unavailable in this session; analysis continues from current
repository artifacts and source. Vault consultation: not performed; the
repository Story, ADRs and implementation are the authoritative sources for
this provider-specific work.

## Current Implementation

### AI Engine OAuth boundary

The existing Python implementation is in:

- `ai-engine/app/api/chatgpt_auth.py`
- `ai-engine/app/services/chatgpt_oauth.py`
- `ai-engine/app/security/credential_store.py`
- `ai-engine/app/providers/chatgpt.py`

It already provides:

- browser session cookie management;
- authorization start and callback endpoints;
- state, PKCE S256 and nonce generation;
- short-lived, single-use OAuth transaction consumption;
- token and ID-token exchange through the AI Engine;
- issuer, audience and nonce validation for ID tokens;
- encrypted credential storage through `EncryptedCredentialStore`;
- granted-scope checking for `resource.invoke` and
  `chatgpt.tokens.use.direct`;
- access-token refresh and fail-closed invalidation;
- bounded provider errors and streamed Responses API completion checks;
- disconnect that deletes the local credential and clears the browser session.

Existing tests cover PKCE transaction creation, dynamic client ID handling and
disabled authorization, but do not cover a successful callback, Core association
creation, revocation propagation or the complete product configuration state.

### Core association and binding boundary

Core currently contains:

- `AiCredentialAssociation` and its principal/provider repository;
- `AiCredentialAssociationServiceImpl`, which saves an opaque handle and asks
  the AI Engine to verify it;
- `AiCredentialAssociationController`, which exposes ChatGPT-specific POST and
  DELETE operations;
- `AiCredentialBinding` and task-scoped binding services from Story 0161;
- Story 0164's `AiUserConfiguration` and derived configuration state API.

The current association controller derives the principal from the JWT, but its
request includes `chatgptSubject` and `credentialHandle`, and its response
exposes the principal ID and ChatGPT subject. The controller currently uses the
provider identity `chatgpt`, while the product catalog and Story 0164 use
`openai_chatgpt`. This mismatch is documented and must be resolved explicitly
within the Story; it must not be normalized silently or via fallback.

### Existing product configuration

Story 0164 persists only non-secret choices in Core and derives state without
claiming credential-dependent readiness. The OAuth Story must connect a verified
AI Engine credential association to that product configuration while keeping
tokens, refresh tokens, authorization codes and ID tokens inside the AI Engine.

## Required Story Scope

The implementation must cover the existing OAuth lifecycle rather than create a
new OAuth protocol:

1. connect or reconnect a principal's `openai_chatgpt` configuration;
2. distinguish identity authorization from ChatGPT plan-usage authorization;
3. project missing scopes, expired tokens and refresh failure to explicit
   product runtime states;
4. update or create the Core association only after the AI Engine has securely
   completed the callback and returned a non-secret handle/metadata;
5. revoke or disconnect so future Core association/binding use fails closed;
6. preserve the selected model and explicit selection origin;
7. keep REST, MCP and background execution behind the existing Core association
   and task-binding authority.

## Main Risks and Open Questions

- The current browser session cookie is not visibly bound to a Core
  `AuthenticatedPrincipal`; the connection flow needs an authenticated,
  non-forgeable handoff without putting OAuth tokens in Core.
- The AI Engine callback currently returns only a session status. The Core
  association lifecycle needs a secure way to receive or trigger the opaque
  credential association and its non-secret subject metadata.
- Disconnect currently removes the AI Engine credential but does not visibly
  notify or revoke the Core association. This can leave stale Core state unless
  the boundary is extended.
- The current association save happens before AI Engine verification. Failure
  semantics must be checked so a failed verification cannot leave an apparently
  active association.
- The `chatgpt` versus `openai_chatgpt` identity mismatch affects catalog
  validation, association lookup, provider resolution and binding scope.
- ADR-072 requires actual granted scopes to be used, explicit cancellation and
  reauthorization semantics, secure redirect/session handling, and complete
  redaction. Existing code covers some of these but lacks end-to-end proof.
- ADR-072/073 are still proposed. Any change to the Core/AI ownership or binding
  protocol requires an explicit architecture decision rather than an inferred
  implementation choice.

## Proposed Validation Surface

### AI Engine

- successful callback with mocked token endpoint and ID-token/JWKS validation;
- state, PKCE, nonce, redirect, client ID, issuer, audience and scope checks;
- cancellation, missing scope, expired transaction, replay and mismatch;
- credential storage, refresh, disconnect and redaction;
- provider behavior for completed, failed, incomplete and interrupted streams.

### Core

- authenticated principal isolation for connect, reconnect and revoke;
- association/provider identity mapping without accepting a client-selected owner;
- failed AI Engine verification does not leave active association state;
- product state transitions for identity-only, reauthorization, revoked and
  usable configurations;
- selected model and explicit selection origin remain unchanged;
- task binding rejects revoked, expired or provider-mismatched associations.

### Boundary and regression

- no OAuth secret in Core entities, DTOs, callbacks, logs, traces or prompts;
- no fallback to API key or another principal/provider;
- regression coverage for Stories 0160, 0161 and 0164;
- no live provider credential or network call in tests.

## Documentation Reconciliation Preview

The implementation will likely affect the OAuth connection guide and the
configuration/runtime-state documentation. Exact documentation changes should
be determined after the approved implementation plan, not invented during this
analysis.

## Gate Status

Repository Analysis approved by the human project owner on 2026-10-30.
