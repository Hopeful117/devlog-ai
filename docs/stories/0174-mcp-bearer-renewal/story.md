# Story 0174 - MCP Transport Bearer Renewal and Session Reconnection

## Status

**IMPLEMENTED - AWAITING HUMAN REVIEW**

Story 0173 is closed. The Repository Analysis and Implementation Plan were
approved by the human project owner. Human acceptance of this implementation
remains pending.

## Dependency

Implementation must start only after:

- Story 0173 - Story Context Budget Observability and Provider Failure
  Diagnostics is implemented, validated and accepted;
- the current MCP runtime state has been reviewed using the runtime diagnostics;
- the human project owner explicitly authorizes this Story's Repository Analysis
  and Implementation Plan gates.

## Problem

The local MCP launcher obtains one short-lived Keycloak
`client_credentials` bearer at process startup and exports it to the MCP
process. The bearer is not renewed or reacquired while the process remains
alive. When the token expires, subsequent MCP calls fail authentication until
the MCP process or OpenCode session is restarted.

The current runtime therefore treats a process-scoped token as if it were a
session-lifetime credential. The failure is especially confusing because Core,
AI Engine and ChatGPT OAuth may remain healthy while MCP authentication has
expired.

## Goal

Keep a long-lived local MCP session usable across transport-bearer expiry by
renewing or reacquiring technical authentication automatically, without exposing
token material to OpenCode tools, prompts or the model.

When renewal is impossible, fail closed with a precise, actionable diagnostic
that distinguishes transport authentication from Core authorization,
delegation, AI credential and provider failures.

## Governing Artifacts

- ADR-077 - MCP Actor Identity and Human Delegation Boundary
- ADR-078 - DevLog Internal Agent and External Agent Client Identity
- Story 0171 - MCP Actor and Human Delegation Contract
- Story 0172 - MCP Runtime Observability and Instance Lifecycle
- Investigation: `docs/investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md`
- `TOOLS.md`

Story 0171's explicit delegation model and Story 0171's exclusion of bearer
renewal remain unchanged. This Story addresses transport lifecycle only.

## In Scope

- Detect or safely handle expiration of the technical MCP transport bearer.
- Renew or reacquire the bearer using the approved Keycloak technical-client
  boundary.
- Replace the bearer used by the owned MCP process without exposing its value to
  MCP tools, OpenCode prompts or model context.
- Retry a bounded transport operation only when the failure is explicitly
  attributable to an expired/invalid transport bearer.
- Reconnect or restart the owned MCP child safely when in-process replacement is
  not possible, preserving STDIO protocol integrity and process ownership rules.
- Distinguish transport authentication failure from Core authorization,
  delegation and provider-credential failures.
- Emit bounded lifecycle events and failure codes such as:
  `TOKEN_RENEWAL_STARTED`, `TOKEN_RENEWAL_SUCCEEDED`,
  `TOKEN_RENEWAL_FAILED`, `TOKEN_EXPIRED`, `MCP_REAUTH_REQUIRED`.
- Preserve safe runtime manifests, redaction and instance ownership diagnostics.
- Add unit, integration and controlled long-session tests using synthetic or
  disposable credentials only.
- Document the renewal/reconnection behavior and operator recovery procedure.

## Out of Scope

- Changing `AI_AGENT`, `HUMAN` or delegated execution semantics.
- Creating or selecting a human principal from MCP arguments or conversation
  text.
- Introducing a remote MCP OAuth login flow.
- Moving ChatGPT access/refresh tokens into Core, MCP or OpenCode.
- Changing AI Engine provider credential storage or refresh behavior.
- Changing `AiCredentialBinding` lifecycle or extending an expired binding.
- Implementing Core human delegation; Story 0171 and ADR-077 govern that boundary.
- Story Context Agent context budgets, prompt size or LLM output limits; Story
  0173 governs those concerns.
- A generic process supervisor, global MCP singleton or durable task queue.
- Logging bearer values, client secrets, delegation proofs, OAuth codes, prompts,
  repository content or provider output.

## Invariants

1. The bearer authenticates only the registered technical MCP actor.
2. Token renewal never creates, selects or changes a human principal.
3. Token renewal never renews or widens human delegation.
4. Core remains authoritative for authentication-derived authorization and
   delegation validation.
5. ChatGPT credentials remain inside the AI Engine provider boundary.
6. An expired or invalid bearer fails closed if renewal cannot be completed.
7. Only the owned MCP instance may be replaced or restarted.
8. STDOUT remains reserved for MCP protocol traffic.
9. Renewal is bounded and idempotent; concurrent failures must not cause an
   unbounded token-request storm.
10. A transport renewal failure is never reported as an LLM, OAuth-provider or
    Story Context failure.

## Current Evidence

- `tools/devlog_mcp/runtime.py` calls `acquire_token` once during startup.
- The resulting token is placed in `DEVLOG_MCP_BEARER_TOKEN` for the child
  process environment.
- No token expiry is decoded or tracked.
- No `401` recovery or token replacement path exists.
- `tools/devlog_mcp/status.py` reports process and endpoint reachability but does
  not validate the current bearer.
- The existing MCP investigation identifies this as root cause `RC1 - Stale
  non-renewed MCP access token`.
- Story 0172 provides instance ownership, lifecycle events, redaction and
  read-only status/doctor capabilities, but intentionally did not implement
  bearer renewal.

## Acceptance Criteria

### AC-1 - Long-lived session renewal

In a controlled test with a short-lived synthetic Keycloak token, an MCP runtime
continues to serve authenticated calls after the initial token expires by
renewing or reacquiring technical authentication.

### AC-2 - Bounded retry after expiry

When an authenticated call fails specifically because the transport bearer is
expired or invalid, the runtime performs at most one bounded renewal/retry for
that operation. Non-authentication failures are not retried as token failures.

### AC-3 - Fail-closed renewal failure

When Keycloak is unavailable, the client secret is invalid, or renewal is
rejected, the runtime stops or disables the owned MCP instance safely and emits
`TOKEN_RENEWAL_FAILED` or `MCP_REAUTH_REQUIRED` without leaking secret material.

### AC-4 - Session and process ownership

Renewal/reconnection affects only the MCP child owned by the current runtime
manifest. A live unrelated OpenCode/MCP instance is never terminated or reused.

### AC-5 - Protocol integrity

MCP JSON-RPC traffic remains exclusively on STDOUT. Renewal diagnostics remain
on STDERR or the bounded runtime event log.

### AC-6 - Identity and delegation separation

Renewal preserves the same technical actor semantics and does not create or
select a human principal, delegation or provider credential.

### AC-7 - Diagnostic distinction

Runtime diagnostics distinguish at least:

- token acquisition failure;
- token expiration/invalidity;
- token renewal failure;
- Core authentication failure;
- Core authorization/delegation failure;
- AI credential/provider failure.

### AC-8 - Secret safety

Automated tests prove that bearer values, client secrets, delegation proofs,
OAuth codes, prompts and provider payloads do not appear in manifests, events,
STDERR diagnostics or test output.

### AC-9 - Restart and recovery

A controlled test proves that a fresh MCP runtime reacquires authentication and
does not depend on conversation memory or a stale process. Repeated recovery is
idempotent.

### AC-10 - Documentation and validation

Operator documentation describes renewal behavior, failure categories, safe
diagnostics and recovery. Runtime tests and the relevant MCP module tests pass;
environment-dependent live validation is explicitly reported.

## Expected Validation

- `python3 -m unittest discover -s tools/devlog_mcp/tests -v`
- focused MCP server authentication and transport tests;
- controlled Keycloak/token lifecycle integration test with disposable
  credentials;
- redaction and STDIO protocol integrity tests;
- repeated renewal/reconnection and ownership safety tests;
- `git diff --check`;
- broader relevant module verification after focused tests pass.

## Risks

- Restarting a STDIO child to replace a token may interact with in-flight MCP
  requests and must preserve protocol ownership.
- Retrying after a `401` can duplicate a tool operation unless the operation is
  known to be safe or the protocol boundary guarantees request identity.
- Token expiry discovery may differ between local launcher and Java MCP runtime;
  ownership must remain explicit.
- A renewal loop could create a hidden availability or token-request storm if
  backoff and concurrency are not bounded.
- Incorrect diagnostic classification could still mislead operators into
  investigating ChatGPT OAuth when the transport bearer is the actual failure.

## Implementation-Level Decisions Deferred

- Whether renewal belongs entirely in the launcher or requires MCP runtime
  support.
- Whether expiry is tracked from token claims, token endpoint metadata or a
  bounded reacquisition-on-failure policy.
- Whether a child restart or an in-process credential reload is the safe V1
  mechanism.
- The exact event field names and retention policy within Story 0172's existing
  runtime event contract.
- The exact handling of an in-flight JSON-RPC request during reconnection.

These choices belong in the approved Repository Analysis and Implementation Plan
for this Story. They must preserve ADR-077/078 and the invariants above.

## Governance

This Story was intentionally deferred until Story 0173 was closed. Its
implementation does not authorize Keycloak changes, secret rotation, deployment
changes or Story acceptance.

The implementation, documentation reconciliation and code review artifacts are
the current workflow outputs. Human review and acceptance remain required.
