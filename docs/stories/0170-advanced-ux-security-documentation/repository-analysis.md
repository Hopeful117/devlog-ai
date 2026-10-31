# Repository Analysis - Story 0170

## Workflow Status

Repository Analysis complete. Implementation is not authorized by the Story.
This analysis must be explicitly approved before an implementation plan is
created.

## Repository State

- Branch: `main`.
- Story 0169 is merged in commit `edcd2cace8a55e24eef2a6769a9ecd16d6fe2b6c`.
- Worktree was clean before this analysis.
- The DevLog Story Agent MCP capability was not available in the current tool
  set; analysis uses the canonical Story, Epic, accepted ADRs, current source,
  and tests.
- The Obsidian vault was searched for relevant AI configuration, UX, and
  security notes; no relevant note was found.

## Governing Context

- Story 0170 is the final implementation Story of the AI provider/model
  configuration Epic.
- The Epic explicitly orders 0170 after Stories 0163-0169 and does not itself
  authorize implementation.
- ADR-074 requires a Core-owned provider/model catalog and advisory
  recommendations with no automatic model fallback.
- ADR-075 requires Simple and Advanced modes to converge on the same
  non-secret `ProviderConfiguration` model and keeps runtime state separate
  from user intent.
- ADR-076 keeps API-key secret storage in the AI Engine, requires explicit
  replacement/revocation, and forbids arbitrary parameters or fallback.
- ADR-072 and ADR-073 define OAuth and task-binding security boundaries. Their
  documents contain a material status inconsistency: the header says
  `ACCEPTED`, while the reconciliation sections preserve `Proposed` and state
  that implementation does not imply architectural approval. Story 0170 must
  document and preserve this distinction rather than silently changing either
  ADR status.

## Current Implementation

### Frontend

- Angular 22 has a standalone Simple configuration page at `/settings/ai`.
- `AiConfigurationService` already exposes catalog, configuration save/read,
  OAuth start, API-key enrollment, configuration test, and Core deletion calls.
- `SimpleAiConfigurationPage` supports provider selection, catalog-driven model
  selection, recommendation versus explicit origin, OAuth connection, API-key
  intake, bounded testing, and runtime-state messages.
- The current UI always sends `allowedParameters: {}` and therefore has no
  Advanced parameter editor or parameter validation experience.
- Unavailable catalog models are filtered from the select, so the current UI
  does not show an unavailable configured model with an explicit remediation
  action.
- There is no explicit model replacement confirmation, return-to-recommended
  action, connection revocation control, advanced diagnostics panel, or
  configuration audit view in the feature.
- Existing frontend tests cover the Simple service/component contract and the
  mobile API-key intake E2E path. No Story 0170 Advanced/security/documentation
  tests exist yet.

### Core

- `AiUserConfigurationService` validates provider, authentication method,
  selected model, selection origin, and currently rejects all non-empty
  `allowedParameters`.
- Runtime state already distinguishes `READY`, `MODEL_UNAVAILABLE`,
  `CREDENTIAL_INVALID`, `PROVIDER_UNAVAILABLE`, `REAUTH_REQUIRED`, and
  `REVOKED`.
- Explicit selections are persisted and are not silently replaced by the
  catalog recommendation.
- `AiCredentialAssociationController` exposes provider-specific revocation for
  ChatGPT and Anthropic. Binding revocation and AI Engine credential revocation
  are already coordinated through Core services and the revocation outbox.
- Configuration, test, OAuth, API-key enrollment, and association endpoints are
  authenticated through the existing principal boundary.
- No dedicated audit projection for configuration changes, tests, replacement,
  or revocation was found in the current AI configuration module.

### AI Engine and Documentation

- The AI Engine owns encrypted credential storage and provider resolution; Core
  owns non-secret configuration and task authorization.
- Provider errors and configuration-test results already use bounded categories,
  but Story 0170 requires confirming that advanced diagnostics remain
  pseudonymized and secret-free across logs, traces, errors, and responses.
- `frontend/README.md` documents the Simple flow and operator environment
  values, but no separate Simple/Advanced user guide or operator lifecycle
  guide exists.
- No canonical lifecycle diagram for configuration, enrollment, testing,
  replacement, revocation, and task binding was found.

## Required Product Boundary

Story 0170 should extend the existing feature without creating a second
configuration or authorization model:

- Advanced and Simple must call the same Core configuration contract.
- Core must validate the exact supported parameter set and preserve explicit
  model intent.
- The browser may express user intent and render bounded diagnostics, but may
  not select credentials, authorize tasks, receive secrets, or implement
  fallback.
- Unavailable configured models must remain visible as an invalid runtime state
  until the user explicitly selects a supported replacement or returns to the
  recommendation.
- Replacement, testing, connection, and revocation actions require safe,
  categorized audit evidence without secret values.
- Documentation must distinguish current implementation reality from the
  unresolved human architectural status of ADR-072/ADR-073.

## Scope Risks and Open Questions

1. The exact Advanced parameter allowlist is not defined in the current Story,
   catalog contract, or ADRs. It must be explicitly decided before planning;
   arbitrary provider parameters are prohibited.
2. The current Core service rejects every non-empty parameter map. Story 0170
   therefore needs a bounded catalog-owned parameter contract, including types,
   ranges, defaults, and provider/model applicability, before implementation.
3. The current frontend hides unavailable models. The replacement UX must expose
   the invalid configured choice without implying automatic fallback.
4. The desired audit storage/projection and retention boundary is not yet
   identified. Existing task and credential audit records must be reused where
   applicable rather than creating an unrelated observability platform.
5. Revocation endpoints exist in Core, but the frontend service/page does not
   currently expose an explicit disconnect/revoke action or refresh state after
   revocation.
6. ADR-072/ADR-073 status reconciliation needs documentation changes only unless
   a separate human architectural decision changes their status. Story 0170
   must not perform that status transition implicitly.

## Proposed Validation Scope

- Core unit and integration tests for supported parameters, invalid values,
  provider/model applicability, explicit selection preservation, unavailable
  model state, replacement, recommendation reset, revocation, and audit-safe
  diagnostics.
- Frontend service/component tests for Advanced mode, parameter validation,
  explicit replacement confirmation, unavailable-model remediation, return to
  recommendation, disconnect/revoke, and secret-free error rendering.
- Browser coverage at desktop and mobile widths for Simple and Advanced modes,
  keyboard accessibility, status announcements, and absence of secrets in DOM,
  URL, storage, and telemetry-facing state.
- Backend/AI Engine regression tests proving Stories 0168 and 0169 vertical
  slices remain unchanged and task-scoped bindings remain required.
- Documentation link and consistency checks for user/operator guides, the Epic,
  Stories, ADR-072, and ADR-073.
- Repository-standard frontend lint, format, unit, build, and E2E checks;
  backend and AI Engine suites; `git diff --check`.

## Gate

This Repository Analysis is ready for human approval. After explicit approval,
the next permitted stage is the Implementation Plan. No implementation files
should be modified until both the analysis and the subsequent implementation
plan have been explicitly approved.
