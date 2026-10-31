# Repository Analysis - Story 0167

## Workflow Status

Repository Analysis complete. The Story and implementation plan were approved
by the human project owner on 2026-10-31. ADR-074 is accepted. The original
analysis below is retained as historical repository state captured before
those approvals.

## Repository State

- Branch: `feat/story-0167-ai-configuration-test`
- Story 0166 is merged in commit `98531cb`.
- Worktree was clean before this analysis.
- DevLog MCP context was not available in the current tool set; analysis uses
  the canonical repository Story, ADRs, current source, and tests.

## Governing Context

- Story 0167: bounded test for an OAuth or API-key configuration.
- ADR-074: Core-owned provider/model catalog boundary; accepted.
- ADR-075: Core-owned non-secret configuration and derived runtime state;
  accepted.
- Story 0165: ChatGPT OAuth configuration; delivered.
- Story 0166: Anthropic API-key credential lifecycle; delivered.

## Current Implementation

### Core

- `AiUserConfigurationService` persists non-secret provider, authentication
  method, selected model, selection origin, and allowed parameters.
- `AiUserConfigurationService.state(...)` already derives `READY`,
  `REAUTH_REQUIRED`, `MODEL_UNAVAILABLE`, `REVOKED`, and incomplete states.
- `AiProviderCatalogService` resolves provider and model metadata.
- `AiCredentialAssociationService` verifies credential associations through the
  AI Engine and records the resulting runtime state.
- Existing authenticated configuration endpoints expose configuration and
  runtime state, but no bounded configuration-test endpoint exists.

### AI Engine

- Provider implementations expose structured generation through the shared
  `LlmProvider` protocol and retain the configured model identifier.
- Credential resolution is binding-scoped through `CredentialResolver`.
- No dedicated configuration-test capability or provider/model availability
  error contract exists.
- Existing task-generation paths are repository-grounded and must not be reused
  for this test because Story 0167 explicitly forbids repository context.

### Frontend

- The current frontend has no discovered configuration-test workflow or
  provider configuration feature implementation.
- Story 0167 should expose a Core contract first; the Simple UX belongs to
  Story 0168.

## Required Design Boundary

The test must be a bounded capability, not a normal repository-grounded AI
task. Core must remain responsible for the authenticated principal, selected
provider/model, association state, and authorization boundary. AI Engine may
perform the provider/model probe using an authorized, short-lived capability,
but must not select credentials from arbitrary metadata or receive repository
context.

The result needs stable categories at minimum for:

- credential invalid or unavailable;
- provider unavailable;
- model unavailable;
- bounded timeout;
- success.

The result must contain no secret, prompt repository content, provider response
body, or unnecessary persisted test content. A successful test may update or
synchronize runtime state to `READY`, but must not create a task binding or
authorize future task execution.

## Risks and Open Questions

1. The probe uses provider metadata/model endpoints rather than generation, so
   it does not incur prompt-generation cost or receive repository context.
2. The existing AI Engine provider abstraction is generation-oriented; adding a
   probe method requires a deliberate contract rather than reusing a prompt
   path that could accidentally include repository context.
3. The last bounded test status is persisted to prevent a failed test from
   leaving the derived configuration state falsely `READY`.
4. Story 0167 requires both OAuth and API-key paths, so Core/AI Engine tests
   need provider-independent test doubles and must not require live credentials.

## Proposed Validation Scope

- Core contract and principal isolation tests.
- OAuth valid, missing, invalid, revoked, and expired association cases.
- Anthropic API-key valid, missing, invalid, revoked, and expired association
  cases.
- Provider unavailable, model unavailable, and timeout mapping.
- Assert selected model is used without fallback.
- Assert no repository context, secret, provider response body, or test content
  is persisted or returned.
- Assert a successful test does not create a task binding or permanent
  authorization.
- Regression tests for Stories 0165 and 0166.

## Gate

Historical analysis gate satisfied by explicit human approval of the Story,
implementation plan, and ADR-074 on 2026-10-31. Implementation remains subject
to focused validation and human review; no commit or push is authorized by this
document.
