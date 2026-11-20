# Repository Analysis - Story 0214

## Scope

This analysis covers the current MCP runtime, MCP server adapters, and Core
contracts relevant to idempotency and replay. Production implementation was
authorized after the planning decisions were approved.

## Governing Context

- The epic is `DESIGN MATERIALIZED - implementation not authorized`.
- ADR-081 requires explicit idempotency before replay and forbids default replay
  when execution is ambiguous.
- Story 0211 defines the retry taxonomy and bounded retry policy.
- Story 0212 owns runtime retry and bearer renewal behavior.
- Story 0213 owns the MCP-to-Core call policy and normalized Core failures.
- Story 0172 remains authoritative for runtime instance ownership, generations,
  and terminal transitions.

DevLog context was unavailable for this planning pass. The findings below are
based on the repository and the governing artifacts listed above.

## Relevant Components

| Area | Evidence | Current role |
| --- | --- | --- |
| MCP runtime | `tools/devlog_mcp/runtime.py` | Maintains local operation state, classifies retry safety, and prevents replay outside the allowlist. |
| Story Agent adapter | `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/DevLogStoryAgentTool.java` | Accepts an idempotency key and passes it to the Core client. |
| Core HTTP client | `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/client/DevlogProjectContextClient.java` | Sends the `Idempotency-Key` header for the Story Agent request. |
| Story Agent use case | `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java` | Hashes and scopes submissions, detects incompatible reuse, and reuses an existing submission when possible. |
| History sync | MCP history-sync adapter and Core history import services | Has repository-content deduplication evidence, but the MCP-level replay contract is not uniform with Story Agent. |
| Project registration | MCP project registration adapter and Core project creation path | Mutation without the same explicit idempotency-key contract. |
| Observability | `backend/src/main/java/com/hopeful117/devlogai/mcptoolobservability/McpToolEventService.java` | Deduplicates observability events by instance, operation, and event type. |
| Runtime tests | `tools/devlog_mcp/tests/` and MCP server tests | Cover retry safety, operation lifecycle, and existing Story Agent idempotency behavior. |

## Findings

### F1 - Local operation identity is not durable idempotency

The runtime `operationId` is useful for lifecycle and observability, but it is
local runtime state. It cannot prove that a mutation was not accepted by Core
after a process restart or a lost response. It must remain distinct from a
server-authoritative idempotency key.

### F2 - Story Agent has the strongest existing contract

The Story Agent path accepts an `Idempotency-Key`, binds it to a request digest
and an operation/project/principal/delegation scope, and distinguishes an
identical retry from incompatible key reuse. This is the baseline contract to
document and compare against other operations, not a reason to assume all MCP
mutations share the same guarantee.

### F3 - Mutation coverage is inconsistent

Project registration and related creation paths do not expose the same explicit
idempotency contract. A timeout after such a mutation must therefore fail
closed rather than trigger a generic replay. Those contracts remain outside
this Story.

### F4 - History synchronization needs a boundary-level guarantee

The repository import path contains content/SHA deduplication behavior. That is
not automatically equivalent to an MCP request-level idempotency guarantee:
request scope, authorization, concurrent submission behavior, and response
reuse still need to be specified.

### F5 - Observability deduplication is separate from business idempotency

The observability service has a deduplication key based on runtime event
identity. This prevents duplicate telemetry but must not be used as proof that a
business mutation is safe to replay.

### F6 - Ambiguous results already have a safe default

The runtime retry policy rejects replay for operations outside the safe
allowlist and for ambiguous results. Story 0214 should preserve this behavior
and add authoritative status/reconciliation paths rather than weakening the
guard.

### F7 - Follow-up operations need explicit treatment

Story Agent follow-up requests are a separate operation from the initial
submission. They need an explicit idempotency and correlation contract before
they can be included in an automatic replay class.

## Initial Operation Matrix

| Operation family | Effect | Initial replay class | Required evidence |
| --- | --- | --- | --- |
| Core reads and MCP resources | None | Safe | Read-only contract and bounded retry policy |
| Companion/status reads | None | Safe | Read-only contract |
| Observability event ingestion | Telemetry only | Conditional | Existing deduplication key and non-business semantics |
| Story Agent submission | Creates analysis work | Conditional with key | Core key/digest/scope contract |
| Story Agent follow-up | May create or advance work | Not yet classified | Dedicated key/correlation semantics |
| History synchronization | Imports repository history | Not yet classified | Request-level Core deduplication and response semantics |
| Project/source registration | Creates project/source state | Not safe by default | New authoritative idempotency contract or manual reconciliation |

This matrix is a planning baseline, not an acceptance decision.

## Risks

- Treating a local operation identifier as durable idempotency could duplicate a
  mutation after restart.
- Retrying a mutation after a lost response could create duplicate projects,
  sources, analyses, or imports.
- Reusing a key across projects or principals could leak a prior result or
  bypass intended authorization boundaries.
- Logging raw keys or request payloads could expose credentials or sensitive
  project data.
- Repository-content deduplication may hide duplicate request execution while
  still producing duplicate work or inconsistent response state.

## Open Decisions

1. Which operations receive an official idempotency guarantee?
2. Does MCP generate keys, or must callers provide them?
3. Should project registration remain non-replayable in this iteration?
4. What are key retention and result retention requirements?
5. What response is returned while an identical request is still in progress?
6. Is `429` handling part of this Story or a separate Story?

## Conclusion

The repository supports a focused Story around idempotency and replay safety.
The smallest safe implementation path is to preserve fail-closed behavior,
standardize the existing Story Agent contract, and only expand replayable
mutations after Core guarantees and human decisions are explicit.
