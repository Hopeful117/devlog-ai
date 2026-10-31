# Repository Analysis - Story 0174

## Status

Repository Analysis completed. Human approval is required before producing the
Implementation Plan. No production implementation is authorized by this
artifact.

## Scope

Story 0174 addresses renewal or reacquisition of the short-lived technical MCP
transport bearer while preserving the existing actor, authorization, delegation
and provider-credential boundaries. It must not change human identity semantics,
Core authorization, ChatGPT OAuth ownership or task-scoped binding behavior.

## Governance and Context

- Story 0174 is the canonical Story for transport bearer renewal and session
  reconnection.
- ADR-077 requires technical MCP authentication to remain separate from human
  delegation and requires fail-closed renewal behavior.
- ADR-078 defines the normal OpenCode path as a stable `AI_AGENT` principal and
  keeps bearer renewal independent from delegation and provider OAuth renewal.
- Story 0171 explicitly excludes bearer renewal from its scope.
- Story 0172 owns runtime instance lifecycle, process ownership, redacted events
  and status/doctor diagnostics; Story 0174 must extend those contracts rather
  than create a second runtime supervisor.
- The MCP investigation identifies the static process-scoped bearer as root
  cause RC1 and recommends a bounded local launcher/MCP transport fix.

The DevLog Story Agent applicability check was attempted for project `devlog-ai`
but failed because authentication is required. Repository artifacts are therefore
the authoritative context for this analysis. The local Obsidian vault was not
consulted because the repository Story, ADRs and investigation provide the
required context.

## Current Implementation

### Repository-owned launcher

`tools/devlog_mcp/runtime.py` currently:

- reads the technical client secret from a machine-local file;
- calls the Keycloak token endpoint once with `client_credentials`;
- stores the resulting bearer only in the child process environment;
- starts one Java MCP child and supervises its STDIO streams;
- writes bounded redacted lifecycle events and a runtime manifest;
- preserves STDOUT for MCP protocol traffic;
- terminates only the owned child on parent disappearance or STDIN close.

`acquire_token()` returns only the token string. It does not retain expiry,
refresh metadata or a renewal callback. The supervisor has no operation-level
transport retry or bearer replacement path after child startup.

### MCP server

`FailClosedMcpAuthenticatedPrincipalProvider` validates the bearer and supports
the configured bearer fallback for STDIO. It classifies the configured technical
actor as `AI_AGENT` when the JWT actor claim matches the configured client id.
The server is a resource server and forwards authenticated calls to Core. It is
not currently an appropriate owner for reacquiring the launcher secret or
restarting its own STDIO process.

### Diagnostics

`tools/devlog_mcp/status.py` is read-only and reports instance manifests, last
events and endpoint reachability. It does not validate the current bearer or
trigger renewal, which is consistent with Story 0172's read-only invariant.

## Relevant Files

- `tools/devlog_mcp/runtime.py`: primary renewal/reconnection boundary.
- `tools/devlog_mcp/status.py`: read-only diagnostics to extend with renewal
  state only if the existing non-secret manifest/event contract requires it.
- `tools/devlog_mcp/tests/test_runtime.py`: standard-library runtime tests.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/service/FailClosedMcpAuthenticatedPrincipalProvider.java`:
  bearer validation and actor classification; avoid moving secret acquisition
  here.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/config/McpOidcSecurityConfiguration.java`:
  issuer, audience and JWT validation.
- `TOOLS.md`: operator behavior and safe diagnostics.
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/implementation-plan.md`:
  established supervisor and ownership constraints.
- `docs/investigations/devlog-mcp-opencode-chatgpt-oauth-stability.md`:
  root cause, controlled reproduction protocol and unknowns.

## Proposed Boundary

The smallest coherent implementation boundary is the repository-owned launcher
supervisor plus its tests and operator documentation. The machine-local wrapper
should remain a thin entry point. Core, AI Engine, database schema, delegation
records and ChatGPT credential storage should not change.

The renewal component must own only the technical transport bearer. It must not:

- create or select a human principal;
- renew or widen a delegation;
- access ChatGPT credentials;
- pass bearer material through MCP tool results, prompts or model context;
- classify Core authorization failures as transport expiry.

## Decisions Required in the Implementation Plan

The following are not fixed by the Story and must be explicitly resolved in the
next gate:

1. Expiry discovery: decode only non-secret JWT expiry metadata locally, use a
   bounded proactive margin, or reacquire only after an explicit transport
   authentication failure.
2. Renewal ownership: perform reacquisition in the launcher supervisor and
   replace/restart the owned child, or add a narrowly scoped in-process reload
   boundary. The implementation must not weaken STDIO integrity.
3. Retry semantics: determine whether a failed JSON-RPC operation is safe to
   retry after one renewal. Non-idempotent or uncertain operations must fail
   rather than duplicate tool execution.
4. In-flight requests: define the behavior for requests crossing child
   replacement, including bounded shutdown, response ownership and client-visible
   diagnostics.
5. Failure contract: define when to emit `TOKEN_RENEWAL_FAILED` versus
   `MCP_REAUTH_REQUIRED`, and how Core `401`, Core authorization failures and
   provider failures remain distinct.
6. Concurrency: define a single-flight renewal/backoff policy so concurrent
   expired requests cannot create an unbounded token-request storm.
7. Manifest/event fields: extend Story 0172 only with bounded non-secret fields
   needed to explain renewal state and retention; do not persist expiry tokens or
   secret responses.

## Acceptance-Criteria Mapping

- AC-1 requires a controlled synthetic short-lived token test across expiry.
- AC-2 requires at most one renewal/retry and explicit non-authentication
  non-retry behavior.
- AC-3 requires fail-closed child/runtime handling and bounded diagnostics.
- AC-4 relies on Story 0172's PID plus process-start ownership proof.
- AC-5 requires all renewal diagnostics to remain on STDERR/events, never STDOUT.
- AC-6 is enforced by keeping renewal independent from actor/delegation logic.
- AC-7 requires a distinct diagnostic taxonomy across transport/Core/provider
  boundaries.
- AC-8 requires redaction tests covering bearer, client secret, delegation proof,
  OAuth code, prompt and provider payload patterns.
- AC-9 requires fresh-runtime reacquisition and idempotent repeated recovery.
- AC-10 requires runtime tests, relevant MCP tests, documentation and explicit
  reporting of environment-dependent live validation.

## Validation Plan

The Implementation Plan should include, at minimum:

- standard-library unit tests for token expiry parsing or bounded reacquisition;
- synthetic token endpoint tests for success, rejection, timeout and malformed
  responses;
- bounded renewal concurrency tests;
- one-renewal/retry and non-authentication no-retry tests;
- STDIO byte-integrity tests across renewal/reconnection;
- in-flight and child-ownership safety tests;
- redaction tests for all sensitive values;
- fresh-runtime and repeated-recovery tests with disposable credentials;
- existing MCP module regression tests;
- `git diff --check` and relevant broader verification.

Live Keycloak validation is environment-dependent and must use disposable or
synthetic credentials only. No current machine bearer or provider credential
should be printed, persisted or used as test output.

## Risks and Unknowns

- The exact Keycloak access-token TTL is not versioned in the repository.
- The current local launcher configuration and client secret remain outside the
  repository.
- Restarting the Java child can affect an in-flight JSON-RPC operation and may
  duplicate a tool call if retry rules are too broad.
- A `401` can originate from transport authentication or a downstream Core
  authorization decision; classification must be based on the explicit boundary,
  not status alone.
- A token endpoint outage must not cause a renewal loop or hide the original
  transport failure.

## Recommendation

Proceed to Implementation Planning only after human approval of this Repository
Analysis. The plan should favor a bounded launcher-owned renewal/reacquisition
mechanism, preserve the Story 0172 supervisor contract, and fail closed rather
than transparently retrying an operation whose safety cannot be proven.

## Approval Required

Human approval required before Implementation Planning.
