# Story 0160 - Repository Analysis

## Repository State

- Branch: `main`
- HEAD: `659ec50`
- Worktree: modified before this analysis with unrelated AI provider and
  evaluation changes; those files are outside Story 0160 and must not be
  included accidentally.
- DevLog context check: unavailable for this request because the configured
  provider returned an upstream `429`; repository evidence and current OpenAI
  documentation were used instead.

## Current Implementation

1. `ai-engine/app/core/config.py` supports process-wide provider selection via
   `LLM_PROVIDER`, model and API-key environment variables. It has no OAuth,
   browser session, user identity, credential reference or secret-store
   abstraction.
2. `ai-engine/app/core/dependencies.py` creates providers from cached settings.
   The current provider is therefore process-scoped rather than user-scoped.
3. `ai-engine/app/providers/openai.py` uses `AsyncOpenAI` with a static API key
   and the non-streaming `responses.parse` path. It does not support user OAuth
   tokens, `store=false`, `stream=true` or streamed completion events.
4. `ai-engine/app/providers/base.py` passes a `Prompt` containing prompt and
   traceability data, but no credential reference. Adding a raw token here would
   violate the proposed security boundary.
5. `ai-engine/app/services/task_processing_service.py` receives Core task
   submissions and dispatches them to provider services. The current task
   contract must be inspected before deciding how an authenticated user is
   associated with a task. The provider must receive an opaque credential
   reference or a narrowly scoped runtime credential resolver, never a token in
   the task payload.
6. `ai-engine/app/main.py` exposes health, task and deliverable routes. There is
   no authentication router, browser session middleware or callback route.
7. `ai-engine/app/core/logging.py` includes exception text in JSON logs. OAuth
   handlers and provider errors must use sanitized exception mapping and must
   not rely on the current generic exception formatter for token-bearing
   failures.
8. `ai-engine/pyproject.toml` already depends on `fastapi`, `httpx` and the
   OpenAI SDK, but has no explicitly selected OAuth client, secure session
   store, encryption library or production secret-manager adapter.
9. `.env.example` and `docker-compose.yml` expose process-level API-key
   configuration only. They do not define callback URLs, OAuth client
   registration, token storage or encryption-key configuration.

## Security Findings

### Blocking design decisions

- **Credential storage is unresolved.** Refresh tokens cannot be stored in
  plaintext files, environment variables, ordinary logs or `AiTask` snapshots.
  Production requires a deployment secret manager or encrypted persistence with
  explicit key management. Local development needs an explicit non-production
  policy.
- **User-to-task association is unresolved.** The current Core-to-AI task
  contract does not visibly carry a user principal or provider credential
  reference. The implementation must define how Core authorizes a user task
  without forwarding the OAuth token to Core.
- **Browser session security is unresolved.** The callback needs a server-side,
  single-use transaction bound to an HttpOnly/Secure/SameSite session cookie or
  an equivalent protected session mechanism. A process-local dictionary is not
  sufficient for a multi-instance deployment.
- **Deployment topology is unresolved.** The callback URL, TLS termination,
  trusted proxy behavior and public AI Engine/frontend boundary must be known
  before live OAuth can be enabled.

### Required fail-closed controls

- cryptographically random `state`, PKCE verifier and OIDC `nonce`;
- one-time transaction consumption and short expiration;
- exact redirect URI binding;
- validation of callback errors before code exchange;
- validation of issuer, token response, nonce, granted scopes and resource;
- access-token refresh with bounded retry and session invalidation on failure;
- no token values in logs, exception strings, correlation metadata, prompts,
  task submissions, callbacks, MCP responses or persisted AI snapshots;
- explicit disconnect/revocation behavior;
- no automatic fallback from a missing user grant to `LLM_API_KEY`;
- tests that assert redaction and that unauthorized/malformed callbacks do not
  reach the token endpoint.

## OpenAI Protocol Constraints Verified

The current OpenAI documentation states that the integration separates identity
from ChatGPT-plan usage. The initial authorization must request the documented
identity and plan-usage permissions, including `offline_access`,
`resource.invoke` and `chatgpt.tokens.use.direct`, but the implementation must
trust only the scopes returned by OpenAI.

For plan-authorized inference:

- use `https://api.openai.com/v1/responses`;
- send the OAuth access token as a bearer credential;
- set `store=false` on every request;
- set `stream=true` on every request;
- treat only `response.completed` as success;
- surface `response.failed`, `response.incomplete` and interrupted streams as
  failures;
- never call ChatGPT private `backend-api` endpoints.

## Architectural Impact

The first implementation cannot be a small replacement of `LLM_API_KEY`.
It requires four explicit boundaries:

1. **OAuth transaction boundary:** browser start/callback, state/PKCE/nonce and
   single-use transaction lifecycle.
2. **Credential boundary:** opaque credential identity, secure access/refresh
   token storage, refresh and disconnect.
3. **Provider boundary:** resolve a credential internally, perform the streamed
   Responses request and return the existing `ProviderGenerationResult`.
4. **Task authorization boundary:** associate an already authorized task with a
   credential reference without expanding Core context or exposing the token.

The first three can be implemented inside the AI Engine. The fourth requires
reconciliation with the existing Core task contract and ADR-070 principal
semantics. It must not be guessed in code.

## Proposed First Slice

The safest implementation sequence is:

1. Add a provider-neutral credential interface and redacted error model.
2. Add an OAuth transaction service with a replaceable session/credential
   store, initially backed only by test doubles and an explicitly local-only
   development implementation.
3. Add strict callback and scope validation tests before enabling live calls.
4. Add a streamed OpenAI Responses client behind an injected transport and
   verify `store=false`, `stream=true`, bearer handling and completion semantics.
5. Integrate an opaque credential reference into the task path only after the
   Core association and authorization contract is approved.
6. Enable live configuration only after redirect URI, TLS, secret store and
   operational redaction are validated by a human.

No live OAuth request or real credential is required for the first code slice.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Token leakage through generic exception logging | Sanitize provider/OAuth exceptions and add log-redaction tests before wiring routes. |
| Cross-user credential use | Opaque credential ownership checks and task binding in one Core-authorized path. |
| CSRF or authorization-code injection | Server-generated state, PKCE, nonce, exact redirect binding and one-time transaction consumption. |
| Replay of callback or refresh request | Delete transaction before processing and bind refresh state to the local credential owner. |
| Accidental API-key fallback | Explicit provider mode and fail-closed missing-grant behavior. |
| Multi-instance session inconsistency | Require shared protected session/transaction storage before production. |
| Provider request stores user data | Assert `store=false` and `stream=true` in request contract tests. |
| Scope overgranting | Request minimum documented scopes and verify granted scopes before activation. |
| Core boundary contamination | Keep token storage and provider credentials in AI Engine; send only opaque metadata/authorized task context. |

## Required Decisions Before Implementation Plan Approval

1. Select the production credential store and encryption/key-rotation model.
2. Select the browser-session and OAuth-transaction store for local and
   multi-instance deployments.
3. Define the Core-to-AI task association for the authenticated principal or
   opaque credential reference.
4. Confirm whether the first user-facing flow is hosted by the AI Engine or by
   the frontend/backend while preserving the token boundary.
5. Confirm the production callback host, TLS and trusted-proxy assumptions.

## Analysis Result

The repository is compatible with the proposed integration, but it is not
ready for a safe direct implementation by replacing the current API-key
provider. The security-sensitive storage, session and task-association
decisions above are mandatory prerequisites. Story 0160 can proceed with
mocked, non-secret OAuth/provider boundaries after those decisions are approved.
