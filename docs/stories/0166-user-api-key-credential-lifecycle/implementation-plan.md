# Implementation Plan - Story 0166

## Scope

Implement the first user-provided API-key vertical slice for Anthropic by
reusing the existing AI Engine encrypted credential store, Core service-auth
boundary and task-scoped `AiCredentialBinding`. The implementation must not
change the ChatGPT OAuth protocol, move secrets into Core, add fallback between
credentials/providers/models, or bypass Core authorization.

## Architectural Decisions Preserved

- Core remains authoritative for principal, provider, authentication method,
  association state and task binding.
- AI Engine remains the only owner of API-key secret storage and provider secret
  resolution.
- The frontend submits the API key only through a short-lived enrollment flow
  and never receives it again after intake.
- A credential handle is opaque and never grants provider access by itself.
- Provider execution requires a valid binding scoped to principal, project,
  task and provider.
- Existing ChatGPT OAuth records and behavior remain compatible.
- Invalid, expired, consumed or mismatched enrollment intents fail closed.

## Implementation Sequence

### 1. Generalize credential metadata without creating a second store

- Add an explicit credential kind or equivalent field to `StoredCredential` and
  the encrypted SQLite table, with a backward-compatible default for existing
  ChatGPT rows.
- Keep API keys encrypted with AES-GCM and associated data bound to the opaque
  handle.
- Preserve refresh-token and ID-token handling for OAuth credentials.
- Add provider/kind validation so an Anthropic API key cannot be resolved as a
  ChatGPT credential or vice versa.
- Ensure delete, replacement and resolver paths work for both credential kinds.

### 2. Add AI Engine API-key enrollment

- Add an intent-authenticated endpoint for API-key intake, separate from the
  ChatGPT OAuth callback endpoints. Core signs the enrollment claims; the
  frontend never receives the service secret. Keep compensation
  service-authenticated.
- Validate the Core-signed enrollment claims: intent ID, principal binding,
  provider `anthropic`, method `API_KEY`, issue time, expiry and single-use
  state.
- Consume the intent atomically before or during successful storage so replay
  and concurrent redemption cannot create duplicate associations.
- Validate the submitted key with a bounded Anthropic operation and stable,
  redacted error categories.
- Encrypt the key and return only an opaque handle plus non-secret metadata.
- Delete the newly stored key if completion acknowledgement or association
  finalization fails, following the existing compensation pattern.

### 3. Generalize Core enrollment and association metadata

- Extend the enrollment intent contract to carry provider and authentication
  method while retaining the existing ChatGPT OAuth constraints.
- Add Core start/complete endpoints for the Anthropic API-key enrollment flow.
- Generalize `AiCredentialAssociation` so ChatGPT subject is optional for API
  keys and authentication method is persisted explicitly.
- Add the migration needed to preserve existing rows while allowing API-key
  associations.
- Reuse `AiCredentialAssociationServiceImpl` verification, replacement,
  binding invalidation and revocation outbox behavior.
- Keep Core DTOs and logs free of API-key values and provider responses.

### 4. Integrate provider resolution and runtime state

- Route user-configured Anthropic execution through the binding-aware resolver
  and encrypted credential store.
- Construct the Anthropic provider from the resolved API key only after binding
  validation.
- Preserve process-level `ANTHROPIC_API_KEY` support for existing non-user
  configured modes, but do not use it as fallback for a configured user
  association.
- Map missing, invalid, revoked, expired and provider-rejected credentials to
  bounded runtime states without exposing secrets.

### 5. Documentation reconciliation

- Update `ai-engine/README.md` with API-key enrollment, encryption and service
  configuration boundaries.
- Document the Core-to-AI enrollment contract and replacement/revocation
  behavior in the canonical configuration documentation if one exists.
- Record the documentation outcome in the implementation report.

## Affected Files and Modules

### AI Engine

- `app/security/credential_store.py`
- `app/security/credential_resolver.py`
- `app/services/binding_registry.py`
- `app/api/bindings.py` and new API-key enrollment route/service
- `app/providers/anthropic.py` or a focused credential-validation adapter
- `app/core/config.py`
- related schemas, tests and README documentation

### Core

- `aicredentialbinding` association entity, repository, service and DTOs
- `aiuserconfiguration` intent entity, repository, service and controller
- `AiEngineCredentialAssociationClient` or a provider-neutral enrollment client
- new Flyway migration after V65
- unit, integration and PostgreSQL migration tests

## Test Plan

### AI Engine unit tests

- encrypted API-key round trip and ciphertext non-disclosure;
- existing OAuth credential round trip and refresh regression;
- valid intake returns only handle and metadata;
- invalid key, timeout, provider rejection and malformed response;
- intent expiry, replay, provider mismatch, method mismatch and principal
  mismatch;
- concurrent/repeated intake idempotency and single-use behavior;
- replacement and revocation remove or invalidate the old handle;
- API key cannot be resolved without a valid task binding;
- exhaustive secret redaction from response, logs and exceptions.

### Core unit tests

- API-key intent signing and claim validation;
- principal isolation and one-time completion;
- association persistence never stores secret material;
- API-key association replacement and revocation;
- binding issuance rejects invalid runtime state or provider/method mismatch;
- remote revocation enqueue remains deduplicated.

### Integration and regression tests

- Flyway migration from existing ChatGPT association rows;
- PostgreSQL persistence for OAuth and API-key associations;
- end-to-end mocked Core/AI Engine enrollment contract;
- regression suites for Stories 0160, 0161, 0164 and 0165;
- no live provider credentials or external provider network calls.

## Validation Commands

```bash
./backend/mvnw -pl backend -am test -B
./backend/mvnw -pl backend -am clean verify -B
cd ai-engine && uv run pytest
cd ai-engine && python3 -m compileall -q app tests
git diff --check
```

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Existing OAuth rows become unreadable | Add schema defaults and explicit migration tests before changing reads. |
| API key leaks through shared token fields | Keep secret-bearing values internal, enforce redacted errors and add ciphertext/response assertions. |
| Enrollment replay under concurrency | Use an atomic consumed marker and database transaction/unique constraints. |
| Provider validation creates unbounded cost or latency | Use one bounded validation call, short timeout and no automatic fallback. |
| User API key bypasses Core binding | Resolve only through `ProviderCredentialResolver` after binding validation. |
| Process API key becomes silent fallback | Separate user-configured execution path and assert missing association fails closed. |

## Explicit Non-Goals

- ChatGPT OAuth redesign or migration to API-key semantics.
- Generic multi-credential management.
- Browser/local-storage persistence of API keys.
- Provider/model fallback.
- Frontend Simple/Advanced UX beyond the minimum contract needed by this
  backend/AI Engine slice.
- Changes to grounding, task trust or proposal promotion.

## Plan Status

Implementation Plan approved by the human project owner on 2026-10-30.
