# Implementation Plan - Story 0170

## Status

Implementation Plan approved by the human project owner on 2026-10-31 after
approval of the Story 0170 Repository Analysis. Implementation is authorized.

## Objective

Extend the existing Simple AI configuration flow with a bounded Advanced mode,
secure diagnostics and explicit connection lifecycle actions, while preserving
the shared Core configuration model, Core authorization boundary, and separate
OAuth/API-key secret lifecycles.

## Scope

### In scope

- Add Advanced configuration controls on top of the existing configuration
  contract, without creating a second persistence model.
- Add an explicit, catalog-owned allowlist for supported parameters with backend
  validation and provider/model applicability.
- Preserve explicit model selection and provide an explicit return-to-recommended
  action.
- Show configured unavailable models and require an explicit replacement action.
- Add explicit connection revocation/disconnect actions for ChatGPT and
  Anthropic, with state refresh and bounded errors.
- Expose bounded, pseudonymized diagnostics for configuration/test/replacement/
  revocation outcomes without secrets or raw provider payloads.
- Add user and operator documentation for Simple versus Advanced, secret-store
  ownership, lifecycle, revocation, and ADR-072/ADR-073 status reconciliation.
- Add frontend, Core, AI Engine, documentation, and regression tests required by
  the Story.

### Out of scope

- Multi-credential selection or project-level configuration.
- Arbitrary provider parameters, dynamic model discovery, or automatic fallback.
- New observability infrastructure.
- Secret storage in Core, PostgreSQL, browser storage, or frontend analytics.
- Changing the architectural status of ADR-072 or ADR-073.
- Reworking the Story 0169 task-binding protocol.

## Design

### 1. Shared configuration contract

Keep `AiUserConfiguration` as the only persisted configuration representation.
Simple and Advanced both call the existing Core `GET`/`PUT` configuration
endpoints. The frontend mode is presentation and intent, not an authorization
boundary.

Extend the catalog contract with parameter descriptors only if the existing
catalog model can carry them without provider-specific UI coupling. A descriptor
must identify a stable parameter name, primitive type, allowed range/options,
default, and supported provider/model scope. Catalog metadata must contain no
credentials or authorization claims.

### 2. Bounded Advanced parameter contract

The current provider implementations do not accept arbitrary request parameters.
The proposed V1 allowlist is one shared, bounded parameter:

- `maxOutputTokens`: integer, bounded to the repository-approved safe range,
  applied only where the provider adapter already supports an equivalent output
  token limit.

`temperature`, `topP`, provider-native extensions, and arbitrary JSON keys remain
rejected because the current provider abstraction does not support them
consistently. The exact numeric range must be approved with this plan before
implementation. Core validates type, range, provider/model applicability, and
normalizes the persisted map; AI Engine consumes only the Core-authorized
normalized values.

### 3. Model and recommendation behavior

- Keep unavailable configured models visible with an explicit unavailable state.
- Do not silently replace an unavailable model.
- Add an explicit `Use recommended model` action that changes the model and
  selection origin only after the user invokes it.
- Keep explicit model origin as `EXPLICIT` after ordinary saves and tests.
- Require a supported available model before a configuration can become `READY`.

### 4. Connection lifecycle and audit

- Reuse existing provider-specific association revocation endpoints.
- Add frontend actions for disconnect/revoke and refresh configuration state from
  Core after completion.
- Reuse existing Core association, binding, outbox, and AI Engine revocation
  paths; do not add a second credential store.
- Add a bounded audit projection or event record only if repository evidence
  confirms no existing suitable audit contract. Persist categorized outcome,
  principal-scoped identity, provider, model, and timestamps, but never keys,
  tokens, handles where not necessary, signed enrollment payloads, prompts, or
  raw provider responses.
- Redact diagnostics at the Core/AI Engine boundary and test logs, traces,
  snapshots, callbacks, and HTTP error bodies.

### 5. Frontend Advanced mode

- Keep the current `/settings/ai` route and Simple path.
- Add a clearly labeled Advanced mode toggle or section with keyboard-accessible
  controls.
- Render labels and parameter help from the catalog contract, not internal
  provider identifiers.
- Add confirmation for model replacement and return-to-recommended actions.
- Render bounded diagnostics and actionable remediation for unavailable model,
  credential invalid, provider unavailable, reauthorization, expiry, and
  revocation states.
- Ensure secrets and opaque enrollment/binding data never enter persistent
  browser state, URLs, rendered diagnostics, or telemetry payloads.

### 6. Documentation reconciliation

Update only established canonical documentation locations:

- `frontend/README.md`: Simple/Advanced user behavior and safe lifecycle
  actions.
- `docs/architecture.md`: configuration, parameter validation, diagnostics,
  audit, and revocation boundaries.
- `docs/decisions/ADR-072.md` and `docs/decisions/ADR-073.md`: clarify the
  implementation-versus-architectural-approval reconciliation without changing
  status.
- `docs/epics/simplify-ai-provider-model-configuration/epic.md` and related
  Story references only if link or status inconsistencies require correction.

Add a lifecycle diagram in the canonical architecture documentation rather than
creating an ad-hoc standalone design document.

## Expected Files and Modules

### Core

- `aicatalog` catalog models/service/controller for parameter descriptors.
- `aiuserconfiguration` entity/service/controller/tests for normalized parameter
  validation and runtime-state behavior.
- Existing association/revocation services and tests for explicit lifecycle
  actions and audit-safe outcomes.
- Existing API response contracts where diagnostics require a bounded extension.

### Frontend

- `frontend/src/app/features/ai-configuration/ai-configuration.models.ts`
- `ai-configuration.service.ts`
- `simple-ai-configuration-page.ts/html/scss`
- corresponding component/service tests and browser coverage.

### Documentation

- `frontend/README.md`
- `docs/architecture.md`
- ADR-072/ADR-073 reconciliation sections if needed
- this Story's implementation report.

## Tests

### Core

- supported parameter accepted and normalized;
- invalid type, range, unknown key, and wrong provider/model rejected;
- explicit model origin preserved;
- unavailable model leaves configuration non-`READY`;
- explicit recommendation reset changes state only by user action;
- revocation invalidates future association/binding use;
- audit/diagnostic projections contain categories and pseudonymous identifiers
  only.

### Frontend

- Advanced mode uses the same service contract as Simple;
- parameter validation and bounded error rendering;
- explicit replacement confirmation and recommendation reset;
- unavailable model remediation;
- revoke/disconnect and state refresh;
- desktop/mobile layout and keyboard accessibility;
- no secrets or opaque credentials in DOM, URL, storage, or telemetry-facing
  state.

### Regression and integration

- Story 0168 Simple configuration tests remain green;
- Story 0169 task-binding tests remain green;
- OAuth/API-key lifecycle and revocation tests remain green;
- AI Engine rejects unauthorized task bindings and secret-bearing diagnostics;
- documentation links and status wording are consistent;
- repository frontend, backend, AI Engine, E2E, and formatting checks pass.

## Validation Commands

```bash
cd frontend && npm run lint
cd frontend && npm run format:check
cd frontend && npx ng test --coverage --watch=false
cd frontend && npm run build
cd frontend && npm run e2e
cd ai-engine && uv run pytest -q
./backend/mvnw -pl backend -am test -B
git diff --check
```

## Decisions Required Before Implementation

1. Approve the bounded `maxOutputTokens` parameter proposal and its exact safe
   numeric range, or explicitly choose a different finite allowlist.
2. Approve reuse of existing revocation/audit boundaries, with a new audit
   projection only if no suitable existing contract is found.
3. Preserve ADR-072/ADR-073 status as an explicit human architectural decision;
   implementation must only document the current reconciliation.

## Approval Gate

The human project owner explicitly approved this current Implementation Plan,
including the bounded `maxOutputTokens` proposal and reuse of existing
revocation/audit boundaries, on 2026-10-31.
