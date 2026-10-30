# Story 0160 - Implementation Plan

## Plan Status

PROPOSED - requires explicit human approval before implementation.

## Governing Decisions

- ADR-006: AI output remains non-trusted until Core validation and promotion.
- ADR-067: Java/Core owns context, authorization and trusted knowledge;
  Python owns probabilistic provider execution.
- ADR-070: identity and project authorization remain distinct.
- ADR-072: Sign in with ChatGPT is an explicit user-authorized provider path;
  OAuth credentials remain inside the AI Engine provider boundary.

## Implementation Strategy

Implement the feature in four security-isolated layers:

```text
OAuth routes
  -> one-time transaction store
  -> credential store / token refresh
  -> user-authorized OpenAI Responses provider
  -> existing generation services and Core callback
```

The existing `openai` API-key provider remains unchanged. A new provider mode
is selected explicitly and fails closed when user authorization is absent.

## Scope of Changes

### 1. Configuration and validation

Add explicit settings for:

- `LLM_PROVIDER=openai_chatgpt`;
- OpenAI authorization, token and Responses endpoints;
- registered client identifier and redirect URI;
- requested scopes and API resource;
- OAuth transaction TTL;
- credential storage mode and database/path configuration;
- credential encryption key supplied only by the deployment secret mechanism;
- whether live ChatGPT authorization is enabled.

Validation rules:

- production/live mode fails at startup when the redirect URI, encryption key,
  client identifier or required endpoint configuration is absent;
- development/test mode may use injected fakes, but never an implicit bypass;
- the API-key provider does not satisfy ChatGPT OAuth configuration checks;
- no setting is allowed to contain an access or refresh token value.

### 2. OAuth transaction boundary

Add an injectable transaction store and service that:

- generates a high-entropy `state`, PKCE verifier/challenge and OIDC `nonce`;
- binds the transaction to a server-side browser session identifier, redirect
  URI and requested scope/resource values;
- expires transactions after a short configured TTL;
- atomically consumes a transaction before processing the callback;
- rejects missing, expired, reused, mismatched or malformed callbacks;
- validates OAuth error responses before any token exchange;
- never serializes the verifier, code, token or nonce into logs.

The first implementation will use an explicit store interface with a test fake.
The production adapter will use a protected shared store selected by
configuration; process-local memory is permitted only in tests and explicitly
local development.

### 3. Credential boundary

Add an opaque internal credential identifier and a credential store interface.
The store will:

- encrypt access and refresh tokens at rest using an application encryption key
  supplied by the deployment secret manager;
- store only minimum identity metadata, granted scopes, token timestamps and
  provider/resource binding;
- return tokens only to the provider service after ownership and provider
  binding checks;
- support atomic replacement during refresh;
- invalidate credentials on refresh rejection, disconnect or explicit revoke;
- never expose raw token values in domain models, task contracts, logs or
  exception messages.

The implementation must not add a plaintext token file or environment variable
for per-user credentials. A missing production-grade store configuration must
disable the live provider rather than silently use an unsafe store.

### 4. OAuth callback and identity session

Add narrowly scoped routes for start, callback, status and disconnect. The
routes will:

- use secure, HttpOnly, SameSite session cookies or the existing deployment's
  equivalent protected session boundary;
- avoid accepting a caller-provided principal or project scope as authority;
- associate the resulting opaque credential identifier with the authenticated
  local session;
- expose only redacted status such as connected, reauthorization required or
  disconnected;
- use generic public error messages for callback/token failures;
- emit correlation ID, event category and outcome only.

The exact local-session-to-Core-principal association must be represented as
opaque metadata and validated against the existing task authorization contract.
No OAuth token is forwarded to Core.

### 5. OpenAI ChatGPT-plan provider

Add a provider implementation behind an injected HTTP/SDK transport. It will:

- resolve a credential internally;
- refresh an expired access token at most once per generation attempt;
- call only `https://api.openai.com/v1/responses` or the configured equivalent;
- send `Authorization: Bearer <access token>` only at the HTTP transport;
- force `store=false` and `stream=true` on every request;
- consume streamed events incrementally;
- collect structured output using the existing output contract;
- accept success only after `response.completed`;
- map `response.failed`, `response.incomplete`, timeout, cancellation and
  subscription-sharing errors to bounded provider failures;
- exclude raw OpenAI error bodies from ordinary logs and callback payloads.

The provider will not change the `Prompt` contract to carry a token. An opaque
credential reference or provider context will be resolved outside serialized
prompt/task payloads.

### 6. Task association and Core boundary

Before wiring user credentials into processing, add a contract test proving:

- the authorized task carries only an opaque credential reference or equivalent
  non-secret binding metadata;
- Core remains responsible for principal/project authorization;
- AI Engine resolves the credential only after task authorization succeeds;
- task snapshots and callbacks contain no token or refresh material;
- an absent, foreign or revoked credential fails closed.

If the existing Core task contract cannot safely carry this opaque binding, stop
implementation at this boundary and produce a follow-up ADR/Story rather than
adding an undocumented field or bypass.

### 7. Observability and redaction

Add structured event categories and safe fields only:

- `oauth_start`, `oauth_callback`, `oauth_scope_check`;
- `credential_refresh`, `credential_invalidated`, `credential_disconnect`;
- `chatgpt_request_started`, `chatgpt_request_completed`;
- `chatgpt_request_failed`.

Allowed fields are correlation ID, opaque credential ID, provider, model,
scope-presence booleans, status category, sanitized error code and duration.
Never log authorization codes, tokens, ID tokens, prompts, response bodies,
repository content, cookies or full URLs containing query parameters.

Add a log-capture test that fails if any fixture secret appears in logs,
exceptions, HTTP errors or callback payloads.

## Test Plan

### Unit tests

- configuration rejects incomplete live OAuth settings;
- state, PKCE and nonce generation and exact callback binding;
- expired, reused and mismatched transaction rejection;
- granted-scope validation and identity-only rejection;
- encrypted credential round-trip and wrong-key failure;
- credential ownership/provider/resource binding;
- refresh replacement and invalidation;
- disconnect removes local credential access;
- no raw secret in exception or log output.

### HTTP contract tests

- token endpoint receives the original redirect URI and PKCE verifier;
- authorization code is never exchanged after state failure;
- Responses request contains `store=false` and `stream=true`;
- bearer token is present only in the outbound HTTP authorization header;
- no private ChatGPT endpoint is called;
- stream success requires `response.completed`;
- failed/incomplete/interrupted streams are not reported as success.

### Integration tests

- start/callback/status/disconnect flow with mocked OpenAI endpoints;
- refresh flow with mocked expiration and rotation;
- task processing with an opaque credential reference;
- revoked/foreign/missing credential fails before provider invocation;
- existing API-key provider behavior remains unchanged;
- existing AI Engine and Core callback tests remain green.

### Security validation

- targeted secret scan of changed files and diff;
- dependency audit for newly added OAuth/encryption/session packages;
- tests for cookie flags and callback CSRF protection;
- tests proving no credentials reach Core, MCP, task snapshots or logs;
- manual review of production configuration with real credentials excluded.

## Documentation Reconciliation

Update only the canonical runtime/configuration documentation required by the
new provider:

- root environment-variable reference;
- AI Engine setup/configuration instructions;
- deployment runbook for redirect URI, TLS, secret store, key rotation,
  disconnect and reauthorization.

No real client secret, encryption key, token or callback credential may appear
in documentation or examples.

## Delivery Sequence

1. Approve this implementation plan.
2. Implement configuration, interfaces and test fakes without live secrets.
3. Implement transaction and credential boundaries with security tests.
4. Implement the streamed provider and request contract tests.
5. Reconcile the Core task association and stop if it requires a new decision.
6. Run focused AI Engine tests, security validation and the broader relevant
   suite.
7. Perform documentation reconciliation and independent code review.
8. Enable live OAuth only after deployment configuration is reviewed by a human.

## Validation Commands

```bash
cd ai-engine && python -m pytest tests/test_config.py tests/test_llm_providers.py -q
cd ai-engine && python -m pytest -q
git diff --check
```

Additional security/dependency commands will be selected from the repository's
available tooling after implementation, without printing secret-bearing
configuration.

## Known Constraints

- The current AI Engine has no established browser session or credential store.
- The current provider lifecycle is process-scoped, so user credential
  selection must not be implemented by mutating a global provider instance.
- The current generic exception logging is too permissive for OAuth and must be
  bypassed or sanitized at the new boundaries.
- Live validation requires human-provided OpenAI application registration,
  redirect URI, TLS endpoint and secret-store configuration.

## Readiness

READY_FOR_HUMAN_PLAN_REVIEW
