# Repository Analysis - Story 0166

## Repository State

- Branch: `main`
- Base: `1206d04` (merged Story 0165)
- Worktree: ADR-076 approval and this analysis artifact are the only expected
  changes.

## Story and Governance

- Story: 0166 - User API Key Credential Lifecycle.
- Epic: Simplifier la configuration des providers et modeles IA de DevLog AI.
- Governing decision: ADR-076, accepted by the human project owner on
  2026-10-30.
- Supporting decisions: ADR-073, ADR-075, ADR-006, ADR-067, ADR-070 and
  ADR-074.
- Dependencies: Stories 0163, 0164 and 0165 are delivered. Story 0166 remains
  proposed and this analysis does not authorize production implementation.

ADR-076 establishes the ownership and security boundary: the AI Engine owns
  encrypted API-key storage and resolution; Core owns non-secret association
  metadata and task-scoped bindings; the frontend never receives the key after
  intake.

DevLog context: unavailable in this session; analysis continues from the
current repository artifacts and source. Vault consultation: not performed;
repository artifacts and current source remain the authoritative sources.

## Current Implementation

### AI Engine secret boundary

The reusable credential boundary is implemented in:

- `ai-engine/app/security/credential_store.py`
- `ai-engine/app/security/credential_resolver.py`
- `ai-engine/app/services/binding_registry.py`
- `ai-engine/app/api/bindings.py`

`EncryptedCredentialStore` persists provider credentials in SQLite and encrypts
the access token, refresh token and ID token with AES-GCM. It currently models
the secret as `access_token`, identifies records by opaque handle and provider,
and has no explicit credential kind or API-key-specific field. The schema is
extended in place with SQLite `ALTER TABLE` operations.

`ProviderCredentialResolver` resolves a credential only after a task binding is
validated. `BindingRegistry` rejects expired, revoked, mismatched and unknown
bindings. These boundaries are reusable for Anthropic API keys, but the
credential model and provider resolution must distinguish an API key from an
OAuth token without introducing a second secret store.

### Existing provider execution

`ai-engine/app/providers/anthropic.py` constructs `AsyncAnthropic` with an API
key and executes bounded structured requests. The provider abstraction and
tests cover injected fake clients, retry behavior and structured responses, but
there is no user-credential intake endpoint, API-key validation operation or
credential-aware Anthropic provider factory yet.

### Existing OAuth enrollment boundary

Story 0165 added the ChatGPT-specific flow in:

- `backend/.../aiuserconfiguration/service/OAuthConnectionIntentService.java`
- `backend/.../aiuserconfiguration/controller/OAuthConnectionIntentController.java`
- `backend/.../aiuserconfiguration/entity/OAuthConnectionIntent.java`
- `ai-engine/app/api/chatgpt_auth.py`
- `ai-engine/app/services/chatgpt_oauth.py`

The current intent is explicitly OAuth/ChatGPT-shaped: it signs
`openai_chatgpt`, sends a browser to the ChatGPT OAuth endpoint, receives a
one-time completion code, and Core redeems it before persisting the opaque
association. This is a strong precedent for the API-key enrollment intent,
but the API-key path must not be forced through an OAuth callback or token
exchange.

### Core association and configuration

Core currently stores `AiCredentialAssociation` with principal, provider,
opaque handle, ChatGPT subject, runtime state and granted scopes. The database
schema originates at `V60` and was extended by `V62`; `chatgpt_subject` is
currently non-null and there is no authentication-method column.

`AiCredentialAssociationServiceImpl` verifies a candidate handle with the AI
Engine before saving it, enforces one association per principal/provider,
invalidates old bindings and queues remote revocation. `AiCredentialBinding`
remains the required task-scoped authorization before provider resolution.

`AiUserConfigurationService` already validates `API_KEY` as a catalog
authentication method and derives product runtime state from the association.
Its association state model is currently provider-neutral in intent but the
persisted association entity still contains ChatGPT-specific metadata.

## Required Story Scope

The implementation must provide the Anthropic API-key vertical slice without
changing OAuth behavior:

1. Core issues a short-lived, principal-bound, provider/method-bound enrollment
   intent for `anthropic` and `API_KEY`.
2. The AI Engine authenticates and atomically consumes the intent.
3. The frontend submits the API key only to the AI Engine over the enrollment
   flow; Core never receives the value.
4. The AI Engine validates the key with bounded provider behavior, encrypts it
   in the existing credential store and returns only an opaque handle and
   non-secret metadata.
5. Core persists or replaces the non-secret association only after secure
   storage succeeds.
6. Replacement invalidates the previous association/bindings and revokes the
   previous AI Engine handle.
7. Revocation prevents future bindings and deletes or invalidates the AI Engine
   mapping while preserving historical task records.
8. Task execution resolves the API key only through a valid Core binding.

## Required Changes Likely Needed

- Extend the AI Engine credential record/store with an explicit credential kind
  or equivalent API-key semantics, keeping all secret columns encrypted and
  redacted from errors/logs/traces.
- Add an AI Engine enrollment service and authenticated endpoint separate from
  ChatGPT OAuth callback endpoints.
- Generalize the Core enrollment-intent contract enough to represent provider,
  authentication method and one-time consumption without weakening the
  ChatGPT intent checks.
- Generalize `AiCredentialAssociation` and its migration so API-key
  associations do not require a ChatGPT subject, while preserving existing
  ChatGPT data and uniqueness semantics.
- Add Core start/complete/revoke operations for the API-key flow and reuse the
  existing service-authenticated verify/revoke boundary.
- Route Anthropic task execution through the binding-aware credential resolver,
  rather than process-level `ANTHROPIC_API_KEY`, for this user-configured path.
- Update the AI Engine configuration and operational documentation for the
  encrypted credential store and required service secrets.

## Main Risks and Open Questions

- The current store calls every secret an `access_token`; changing its model
  must preserve existing ChatGPT rows and refresh-token behavior.
- The current Core entity requires `chatgpt_subject`; nullability and migration
  must be changed without weakening identity isolation for OAuth associations.
- API-key validation can cause a provider network call during intake. It needs
  bounded timeout, no retry amplification, stable error categories and no key
  disclosure.
- The enrollment intent must be single-use under concurrent redemption and
  must not be accepted by task binding or provider execution endpoints.
- Replacement ordering must ensure the new key is durably encrypted before the
  old association is invalidated.
- A provider rejection after successful intake must map to a bounded runtime
  state and must not expose the key or silently fall back to OAuth/another
  credential.
- Existing process-level `ANTHROPIC_API_KEY` support must remain available for
  non-user-configured runtime modes without becoming an implicit fallback for
  this Story.

## Proposed Validation Surface

### AI Engine

- valid Anthropic API-key intake stores encrypted secret material and returns
  only an opaque handle;
- invalid key, provider rejection, timeout and malformed provider response map
  to stable bounded errors;
- expired, consumed, mismatched and replayed enrollment intents are rejected;
- concurrent intake/replay cannot create multiple active associations;
- API-key records cannot be read without a valid task binding;
- replacement, revocation and deletion remove or invalidate the old handle;
- no key appears in logs, exception messages, response bodies or snapshots;
- existing ChatGPT OAuth, refresh and credential-store tests remain green.

### Core

- principal isolation for start, complete, replace and revoke;
- provider and authentication-method claims are validated server-side;
- API-key association persistence never contains the key or a provider response
  containing it;
- failed verification leaves no active association;
- replacement and revocation invalidate active bindings and enqueue remote
  revocation exactly once;
- binding issuance rejects revoked, incomplete, expired or provider-mismatched
  API-key associations.

### Boundary and regression

- REST and background execution use the same Core binding decision;
- no fallback from a missing API-key association to `ANTHROPIC_API_KEY` for the
  user-configured path;
- migration tests cover existing ChatGPT associations and new API-key rows;
- regression coverage remains green for Stories 0160, 0161, 0164 and 0165;
- no live credential is committed or used in automated tests.

## Documentation Reconciliation Preview

The implementation will likely update the AI Engine credential-store
configuration guide, API-key enrollment contract and user replacement/revocation
guidance. Exact changes should be determined after the approved implementation
plan and included in the later code review.

## Gate Status

Repository Analysis approved by the human project owner on 2026-10-30.
