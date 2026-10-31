# Implementation Plan - Story 0174

## Status

Implementation Plan completed. Human approval is required before implementation.

## Objective

Keep one OpenCode-owned MCP runtime usable across technical bearer expiry by
reacquiring the bearer in the repository-owned launcher supervisor, replacing
the owned Java child safely, and preserving MCP STDIO protocol integrity.

The implementation will remain transport-only. Core authorization, actor
classification, optional human delegation, AI credential binding and ChatGPT
OAuth ownership remain unchanged.

## Decisions

### 1. Renewal owner

Renewal remains in `tools/devlog_mcp/runtime.py`. The Java MCP child continues to
receive the bearer through its private environment. The supervisor owns the
token endpoint call and never exposes the bearer through MCP tools, prompts,
events or model context.

### 2. Expiry source

`acquire_token()` will return a private token lease containing the token and an
expiry instant derived from the token endpoint's `expires_in` response. If the
endpoint omits `expires_in`, the implementation will decode only the JWT `exp`
claim for scheduling. Signature validation remains the MCP/Core responsibility;
local expiry data is never treated as authorization.

The supervisor will use a bounded renewal margin and a minimum lease duration so
malformed or already-expired responses fail closed instead of creating a tight
renewal loop.

### 3. Replacement mechanism

The supervisor will reacquire the token and restart only its owned Java child
when a new request arrives after the renewal margin and no JSON-RPC request is
outstanding. The new child receives the new bearer in its environment and must
complete the normal initialize/readiness handshake before buffered input is
forwarded.

No global lock, process lookup by name, or unrelated-instance termination will
be introduced.

### 4. In-flight and retry behavior

- Bytes already forwarded to the child are never replayed automatically, except
  for one explicitly classified MCP transport-authentication error
  (`-32600`, `Authentication is required`); that bounded retry is the only
  permitted operation replay.
- A child replacement is not performed while tracked JSON-RPC request IDs are
  outstanding.
- If expiry occurs while an operation is in flight, the runtime records a
  bounded transport diagnostic and fails closed after the child exits or the
  protocol boundary becomes unusable.
- At most one renewal/replacement is attempted for a buffered operation.
- Non-authentication errors, Core authorization failures and provider failures
  are never retried as bearer expiry.

The request gate will parse only enough JSON-RPC metadata to track request IDs;
it will continue forwarding the original bytes unchanged. Parse failure or an
unsupported framing shape must fail closed rather than risk unsafe replay.

### 5. Failure contract

The runtime will use the existing Story 0172 event writer with bounded codes:

- `TOKEN_RENEWAL_STARTED`
- `TOKEN_RENEWAL_SUCCEEDED`
- `TOKEN_RENEWAL_FAILED`
- `TOKEN_EXPIRED`
- `MCP_REAUTH_REQUIRED`

HTTP status and normalized exception categories may be retained, but no response
body, Authorization header, bearer, client secret or token endpoint payload may
be persisted.

### 6. Concurrency

Renewal is single-flight under the supervisor's existing synchronization
boundary. Concurrent input cannot start multiple token requests. A bounded
backoff/cooldown prevents repeated endpoint failures from creating a request
storm. Failure is terminal for the affected runtime operation.

## Planned Changes

### Runtime supervisor

Modify `tools/devlog_mcp/runtime.py` to:

- represent the bearer as a private lease rather than a bare string;
- parse bounded expiry metadata from token responses;
- add renewal-margin and minimum-lease configuration with safe defaults;
- gate new input before forwarding when the lease is expired or near expiry;
- reacquire the technical token and replace only the owned child;
- wait for readiness before releasing buffered input;
- track JSON-RPC request IDs and never replay already-forwarded requests;
- emit renewal and reauthorization lifecycle events;
- preserve current manifest ownership checks, retention and redaction behavior;
- keep all diagnostics on STDERR and `events.jsonl`, never STDOUT.

The implementation should be kept in the existing supervisor rather than adding
a second process supervisor or moving authentication into the Java MCP server.

### Status/doctor diagnostics

Extend `tools/devlog_mcp/status.py` only with non-secret lease state if useful
for incident diagnosis, such as `leaseState`, `lastRenewalEvent` and a bounded
renewal failure code. The command remains read-only and must never reacquire a
token, restart a process or mutate a manifest.

### Documentation

Update `TOOLS.md` with:

- proactive renewal/reacquisition behavior;
- bounded failure categories and operator meaning;
- the distinction between transport renewal, Core authorization, delegation and
  provider credential failures;
- safe recovery when `MCP_REAUTH_REQUIRED` is emitted;
- the fact that no bearer or client secret is exposed in diagnostics.

## Files

### Modify

- `tools/devlog_mcp/runtime.py`
- `tools/devlog_mcp/status.py`
- `tools/devlog_mcp/tests/test_runtime.py`
- `TOOLS.md`

### Do not modify

- Core authorization and principal resolution.
- AI Engine provider or ChatGPT OAuth code.
- Database schema and credential/delegation persistence.
- MCP tool contracts and resource payloads.
- Machine-local secret files or real OpenCode credentials.

## Test Plan

### Unit tests

- token response with `expires_in` produces a bounded lease;
- JWT `exp` fallback is used only for scheduling;
- malformed, missing and already-expired expiry data fails closed;
- renewal margin and cooldown are deterministic;
- token endpoint success, HTTP rejection, timeout and malformed response map to
  bounded diagnostics;
- redaction excludes bearer, client secret, delegation token, OAuth code,
  prompt and provider payload values.

### Protocol and lifecycle tests

- STDOUT remains byte-for-byte MCP traffic while renewal diagnostics remain on
  STDERR/events;
- a request arriving after expiry causes one renewal and one child replacement;
- the replacement child must become ready before buffered bytes are forwarded;
- already-forwarded requests are never replayed;
- outstanding request IDs prevent unsafe replacement;
- non-authentication errors do not trigger renewal;
- concurrent expiry causes one token request, not a request storm;
- child replacement preserves instance ownership and does not affect another
  runtime directory;
- repeated renewal failure emits a deterministic reauthorization outcome.

### Regression tests

- existing `python3 -m unittest discover -s tools/devlog_mcp/tests -v`;
- focused MCP module tests with `./backend/mvnw -pl mcp-server test -B`;
- `git diff --check`;
- broader relevant verification after focused tests pass.

Live Keycloak validation remains optional and environment-dependent. If run, it
must use synthetic or disposable credentials and report only bounded status and
correlation data.

## Acceptance Criteria Mapping

- AC-1: synthetic short-lived token endpoint and long-lived supervisor test.
- AC-2: one renewal attempt with explicit request tracking and no broad retry.
- AC-3: renewal endpoint rejection produces fail-closed runtime state and
  `TOKEN_RENEWAL_FAILED` or `MCP_REAUTH_REQUIRED`.
- AC-4: child replacement requires the current manifest's proven ownership.
- AC-5: protocol bytes stay on STDOUT; diagnostics stay on STDERR/events.
- AC-6: no actor, delegation or provider credential path is changed.
- AC-7: transport, Core and provider failure categories remain distinct.
- AC-8: redaction tests cover all sensitive input categories.
- AC-9: fresh runtime reacquisition and repeated recovery are tested.
- AC-10: runtime/MCP tests and operator documentation are updated, with live
  validation limitations reported.

## Risks and Mitigations

- **Unsafe replay:** do not replay forwarded requests; gate only buffered input
  and require request tracking for replacement.
- **Protocol corruption:** preserve original bytes and keep diagnostics off
  STDOUT; test the forwarding path explicitly.
- **Renewal storm:** single-flight synchronization, cooldown and terminal
  failure state.
- **PID reuse:** reuse Story 0172's PID plus process-start-time checks.
- **Misclassification:** renewal is triggered only by the local lease boundary;
  downstream authorization/provider errors do not trigger it.
- **In-flight replacement:** defer replacement while request IDs are outstanding
  and fail closed if safe handoff cannot be proven.

## Implementation Sequence

1. Add private lease and bounded token-expiry parsing.
2. Add renewal event codes and failure mapping.
3. Add safe child replacement/readiness gating.
4. Add request tracking and no-replay behavior.
5. Add concurrency/cooldown handling.
6. Extend status diagnostics and `TOOLS.md`.
7. Add focused tests, then run MCP regression and repository checks.
8. Complete documentation reconciliation, implementation report and code review.

## Approval Required

Human approval required before Implementation.
