# Story 0160 - Implementation Report

## Status

BLOCKED - OAuth transaction and credential boundaries are implemented, but the
live provider execution path is not enabled.

## Implemented

- Added explicit ChatGPT OAuth configuration with live mode disabled by
  default and fail-closed validation.
- Aligned the OAuth flow with the OpenAI open-source registration contract:
  `dynamic_agent_client`, `agent_name_hint`, `ext_agent_host_id`, the issued
  per-account client ID and the official discovery endpoints.
- Added persistent OAuth transaction/session storage in SQLite.
- Added authorization start route:
  `GET /api/v1/auth/chatgpt/start`.
- Added callback route:
  `GET /api/v1/auth/chatgpt/callback`.
- Added status route:
  `GET /api/v1/auth/chatgpt/status`.
- Added disconnect route:
  `POST /api/v1/auth/chatgpt/disconnect`.
- Added high-entropy state, PKCE verifier/challenge and nonce generation.
- Added one-time state consumption bound to the browser session and redirect
  URI.
- Added authorization-code exchange with the original redirect URI and PKCE
  verifier.
- Added ID-token signature, issuer, audience and nonce validation through the
  configured JWKS endpoint.
- Stored exchanged credentials through the existing encrypted credential store;
  raw tokens are not returned in API responses or logs. The issued client ID,
  validated subject and ID token are retained encrypted with the credential.
- Added secure HttpOnly/SameSite session cookie handling.
- Added explicit `openai_chatgpt` provider selection with scoped credential
  resolution and an SSE Responses API transport.
- Enforced `store=false`, `stream=true`, bearer-header-only token transport and
  success only after `response.completed`.
- Enforced the required plan scopes before provider invocation.
- Added one-attempt access-token refresh using the associated refresh token;
  refresh rejection invalidates the local credential and never falls back to an
  API key.
- Added provider SSE wire coverage for bearer-header transport and the required
  completion event.

## Validation

Passed:

```text
uv run pytest tests/test_chatgpt_oauth.py \
  tests/test_credential_store.py \
  tests/test_credential_resolver.py \
  tests/test_binding_registry.py \
  tests/test_service_auth.py \
  tests/test_config.py -q
32 passed

uv run python -m compileall -q app
```

## Remaining Work

- Propagate the opaque binding association through the Core task submission
  contract; the current provider resolves the registered task binding before
  invocation.
- Add broader provider wire coverage for failure events and explicit
  non-disclosure capture tests.
- Configure the deployment encryption key, stable host ID and a loopback or
  production redirect URI before enabling `CHATGPT_OAUTH_ENABLED`.
- Complete one live OpenAI authorization only after confirming the account is
  eligible for the open-source ChatGPT plan-usage flow.

## Safety

`CHATGPT_OAUTH_ENABLED` defaults to `false`. No real OAuth exchange or live
provider call was executed. The existing `openai` API-key provider remains
unchanged and there is no fallback between provider credential modes.

The full AI Engine suite currently has four unrelated failures in the existing
comparative evaluation fixtures because the external Trading OS projection
manifest has a byte-length mismatch.

## Documentation Outcome

Runtime configuration is represented in `.env.example` and Docker Compose.
The implementation uses the official OpenAI endpoints and does not require a
static client ID; OpenAI returns the issued client ID during first registration.

## Readiness

BLOCKED - do not enable live OAuth until the provider execution path and its
wire-level tests are complete.
