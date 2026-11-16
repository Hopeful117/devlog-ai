# Story 0186 - MCP Activity: Agent Session Correlation

## Status

**DESIGN CONSOLIDATED — AWAITING IMPLEMENTATION AUTHORIZATION**

This Story defines the design for enriching MCP tool events with agent session
identity and surfacing it in the Operation Lifecycle View. No implementation is
authorized until explicit human authorization is received.

## Problem

The Operation Lifecycle View (Story 0185) shows one row per tool invocation.
Each row carries an `instanceId` — the identifier of the Python runtime process
that relayed the event — and an `operationId` unique to that invocation.

A user looking at several pages of operations cannot answer:

- Which agent session produced these operations?
- Are these operations from Claude Code or OpenCode?
- Did a single agent session perform all of them, or were multiple sessions
  active in parallel?
- Which operations belong to the session that was active this afternoon?

The `instanceId` is a process-level identifier, not a session-level one. It
changes every time the MCP runtime restarts, which can happen mid-session. It
does not carry the agent provider name. The current view has no way to group
operations by session or to identify the agent type.

## Goal

Associate each MCP tool event with the identity of the agent session that
produced it, when that identity is reliably available, and expose it in the
Operation Lifecycle View so that users can understand which agent and which
session each operation belongs to.

The goal for V1 is enrichment and display. Session-level aggregation (a "session
activity" view) is explicitly out of scope for this Story.

## Governing Artifacts

- Story 0185 — MCP Activity: Operation Lifecycle View (implemented, PR #181)
- ADR-006 — AI Proposal and Knowledge Promotion Workflow
- ADR-067 — DevLog Engineering Story Context Agent

## Current State (post PR #181)

- Table `mcp_tool_events` has `instance_id` and `operation_id` but no
  `agent_session_id` or `agent_provider`.
- The Python runtime (`runtime.py`) does not read `CLAUDE_CODE_SESSION_ID` or
  `AI_AGENT` from its environment.
- The ingest payload (`McpToolEventIngestRequest`) carries no session identity.
- The `/mcp-operations` response includes `instanceId` but nothing that
  identifies the agent type or session.

## Sources of Agent Identity

### Claude Code (confirmed available)

When Claude Code starts an MCP server as a child process, it sets the following
environment variables in the child's environment. These are confirmed present in
the runtime's process environment on this machine:

| Variable | Example | Semantics |
|---|---|---|
| `CLAUDE_CODE_SESSION_ID` | `cc7d159d-a138-4b73-b14e-1e7279d013ab` | UUID, stable for the duration of a Claude Code session |
| `AI_AGENT` | `claude-code_2-1-325_agent` | Provider and version, e.g. `claude-code_2-1-325_agent` |
| `CLAUDE_CODE_CHILD_SESSION` | `1` | Present and set to `1` when this is a sub-session (worktree, fork) |

`CLAUDE_CODE_SESSION_ID` is the primary session identity for Claude Code.
`AI_AGENT` encodes both the provider (`claude-code`) and the build version.

### OpenCode (heuristic only)

OpenCode does not expose a stable session identifier in the child process
environment (verified on OpenCode 1.18.11). The runtime already records
`parentOpenCodePid` and `parentOpenCodeStartTime` in the manifest, which can
correlate instances to a specific process lifetime but not to a logical session.

For OpenCode, `agentSessionId` will be `NULL` unless a future OpenCode version
exposes a session identifier via environment variable.

### Identity not in scope

- `CLAUDE_CODE_USER_EMAIL` and `CLAUDE_CODE_ACCOUNT_UUID` are available but not
  included. User email is PII. Account UUID is an account-level identifier, not
  a session identifier.
- Claude Desktop session identity: unverified. If `AI_AGENT` contains
  `claude-desktop`, the provider is identified; session identity follows the
  same `CLAUDE_CODE_SESSION_ID` pattern if Claude Desktop uses the same
  convention. Verification is deferred.
- Conversation-level identity (multiple sessions within a single conversation)
  is out of scope for V1.

## Design Decisions

### D1 — Schema change: two nullable columns in `mcp_tool_events`

Add `agent_session_id VARCHAR(128) NULL` and `agent_provider VARCHAR(128) NULL`
to the `mcp_tool_events` table via a Flyway migration.

Both columns are nullable to preserve backward compatibility with existing rows
(which will have NULL values) and with OpenCode-originated events where no
session identity is available.

No new table. No foreign key. These are denormalized enrichment fields on the
event row.

### D2 — Runtime captures identity at startup

The Python runtime reads `CLAUDE_CODE_SESSION_ID` and `AI_AGENT` from
`os.environ` once at startup, before the MCP JAR process starts.

Both values are stored in the instance manifest (`manifest.json`) under
`agentSessionId` and `agentProvider`. If either variable is absent, the
corresponding field is omitted from the manifest (not stored as a placeholder).

The runtime emits a `LAUNCHER_STARTED` event with `agentSessionId` and
`agentProvider` as fields when available.

### D3 — Ingest payload carries optional session identity

`McpToolEventIngestRequest` gains two optional fields: `agentSessionId` and
`agentProvider` (both nullable, no validation beyond length).

For each event emitted during a session, the runtime includes these fields in
the ingest payload if they were captured at startup. This denormalizes the
session identity onto every event row, which is intentional: it allows the
projection query to surface the identity without joining a separate sessions
table, and it means session identity is preserved even if events arrive
out-of-order.

### D4 — Projection query exposes session identity

The `findOperationsByProject` SQL projection (Story 0185) is extended to include
`agent_session_id` and `agent_provider` from the STARTED event (preferred) or
the terminal event (fallback via `COALESCE`).

Both fields are exposed in `McpOperationView` and in the
`GET /api/v1/projects/{projectId}/mcp-operations` response.

### D5 — Angular display: provider badge per row

The Angular operation row gains a compact provider badge: `CC` for
`claude-code`, `OC` for `opencode`, `CD` for `claude-desktop`, `?` when
`agentProvider` is null or unrecognized.

The full `agentSessionId` UUID is shown in a tooltip on the badge or in a
detail expansion — not inline in the list row (too verbose).

### D6 — `instanceId` semantics unchanged

`instanceId` continues to identify the Python runtime process, not the session.
One session may contain multiple `instanceId` values if the runtime restarts.
The correlation between instances and sessions is provided by the shared
`agentSessionId` value, not by any change to `instanceId`.

### D7 — CLAUDE_CODE_CHILD_SESSION: captured, not surfaced (V1)

If `CLAUDE_CODE_CHILD_SESSION=1`, this is recorded in the manifest and the
`agentProvider` field may carry a suffix (e.g., `claude-code/child`) to allow
future differentiation. In V1, it is not surfaced in the UI. The decision on
how to present sub-sessions (worktree forks, parallel sessions) is deferred.

### D8 — No session lifecycle endpoint

This Story does not introduce a `/mcp-sessions` endpoint or a session-level
aggregation query. The session identity is an enrichment field on the existing
`/mcp-operations` response. Session-level views are deferred to a future Story.

### D9 — OpenCode graceful degradation

Events originating from OpenCode will have `agentSessionId = null` and
`agentProvider` derived from `AI_AGENT` if it is set by OpenCode (possible in
future versions) or null otherwise.

The Angular badge displays `?` for null provider. The tooltip shows "Provider
unknown — upgrade your MCP runtime to capture agent identity."

## In Scope

- Flyway migration: `agent_session_id VARCHAR(128) NULL`, `agent_provider
  VARCHAR(128) NULL` added to `mcp_tool_events`.
- Python runtime (`runtime.py`): read `CLAUDE_CODE_SESSION_ID` and `AI_AGENT`
  at startup; store in manifest; include in every ingest payload.
- `McpToolEventIngestRequest`: two optional fields (`agentSessionId`,
  `agentProvider`).
- `McpToolEventService` / `insertIfAbsent`: map new fields to the entity.
- `McpToolEvent` entity and `insertIfAbsent` native query: include new columns.
- `McpOperationProjection` and `findOperationsByProject` SQL: surface
  `agent_session_id` and `agent_provider` from the STARTED event (COALESCE
  terminal as fallback).
- `McpOperationView` / `McpOperationPageResponse`: expose the two new fields.
- Angular `McpOperation` model: add `agentSessionId: string | null` and
  `agentProvider: string | null`.
- Angular `McpOperationActivitySection`: render a provider badge per row.
- Unit tests: `McpOperationServiceTest` for the two new fields (present, null,
  fallback to terminal).
- Testcontainers integration test: event with session identity, event without,
  COALESCE from terminal when STARTED is absent.
- Angular spec: badge rendering for each provider code and null.

## Out of Scope

- A `/mcp-sessions` endpoint or session-level aggregation view.
- Storing or displaying `CLAUDE_CODE_USER_EMAIL` or `CLAUDE_CODE_ACCOUNT_UUID`.
- Any session-awareness in the Python runtime beyond capturing and forwarding
  the two environment variables.
- Claude Desktop session identity verification (deferred).
- Conversation-level identity or agent memory correlation.
- Any change to the MCP JAR or the Java MCP server.
- Removal or replacement of `instanceId` from any existing contract.
- New ADR.

## Schema Migration

```sql
-- VXX__add_agent_identity_to_mcp_tool_events.sql
ALTER TABLE mcp_tool_events
    ADD COLUMN agent_session_id VARCHAR(128),
    ADD COLUMN agent_provider   VARCHAR(128);
```

No index is added in V1. If future queries filter by `agent_session_id`, an
index can be added in a later migration.

## Response Contract (updated)

```
GET /api/v1/projects/{projectId}/mcp-operations?page=0&size=20

200 OK
{
  "content": [
    {
      "instanceId": "string",
      "operationId": "string",
      "toolName": "string | null",
      "status": "ACTIVE | RESPONDED | FAILED | INTERRUPTED",
      "generation": 1,
      "startedAt": "ISO-8601 | null",
      "completedAt": "ISO-8601 | null",
      "durationMs": 123 | null,
      "reasonCode": "string | null",
      "agentSessionId": "UUID string | null",
      "agentProvider": "string | null"
    }
  ],
  "totalOperations": 42,
  "page": 0,
  "size": 20
}
```

`agentSessionId` and `agentProvider` are null for events recorded before this
Story is deployed, and for OpenCode-originated events (until OpenCode exposes a
session identifier).

## Acceptance Criteria

### AC-1 — Schema migration

The `mcp_tool_events` table gains `agent_session_id` and `agent_provider`
columns. Existing rows are unaffected (columns default to NULL).

### AC-2 — Runtime captures session identity

When `CLAUDE_CODE_SESSION_ID` and `AI_AGENT` are present in the environment,
the Python runtime includes them as `agentSessionId` and `agentProvider` in
every ingest payload it sends during the session.

### AC-3 — Ingest stores session identity

Events ingested with `agentSessionId` and `agentProvider` present have those
values stored in `mcp_tool_events`. Events ingested without those fields have
NULL values.

### AC-4 — Projection exposes session identity

The `/mcp-operations` response includes `agentSessionId` and `agentProvider` on
each operation row. When the STARTED event carries session identity, it is
used. When only a terminal event exists (orphan terminal), the terminal event's
values are used via COALESCE.

### AC-5 — Null for unidentified sessions

Operations whose events have no `agent_session_id` (pre-migration rows or
OpenCode without session env var) return `agentSessionId: null` and
`agentProvider: null` in the response.

### AC-6 — Provider badge in Angular

The Angular operation row displays a provider badge:
- `CC` when `agentProvider` starts with `claude-code`
- `OC` when it starts with `opencode`
- `CD` when it starts with `claude-desktop`
- `?` when `agentProvider` is null or unrecognized

The full `agentSessionId` is accessible via the badge tooltip.

### AC-7 — Testcontainers integration

A Testcontainers test validates the projection with: (a) an event carrying
`agent_session_id` and `agent_provider`, (b) an event with NULL values, (c) an
orphan terminal carrying session identity (COALESCE fallback).

### AC-8 — Angular spec

The Angular spec covers badge rendering for each provider code and the null
case.

### AC-9 — Existing tests unaffected

All tests from Story 0185 (`McpOperationPostgresIntegrationTest`,
`McpOperationServiceTest`, `McpOperationControllerWebMvcTest`, Angular spec)
pass without modification.

### AC-10 — Python runtime backward compatible

The runtime gracefully handles missing environment variables. If
`CLAUDE_CODE_SESSION_ID` is absent (OpenCode, local dev without Claude Code),
the ingest payload omits `agentSessionId` and `agentProvider`. No error, no
crash, no log noise.

## Implementation Plan

1. **Flyway migration** — add two nullable columns to `mcp_tool_events`.
2. **Python runtime** — read `CLAUDE_CODE_SESSION_ID` and `AI_AGENT` at
   startup; store in manifest; pass in every `ingest_event` call.
3. **Ingest backend** — `McpToolEventIngestRequest` + `McpToolEventService` +
   `insertIfAbsent` native query: wire the two new fields end-to-end.
4. **Projection** — extend `McpOperationProjection`, `findOperationsByProject`
   SQL, `McpOperationView`, and `McpOperationPageResponse`.
5. **Angular** — model, service (no change needed), component badge.
6. **Tests** — Testcontainers, unit, Angular spec.

Steps 1–3 are a vertical slice (ingest path). Steps 4–6 are the read path.
They can be implemented sequentially in one branch.

## Risks

- **OpenCode without session env var**: `agentSessionId` will be NULL for
  OpenCode-originated events until OpenCode adds support. This is the expected
  graceful degradation, not a bug.
- **Claude Code API change**: if a future Claude Code version removes or renames
  `CLAUDE_CODE_SESSION_ID`, the runtime must handle its absence silently.
  The nullable design and the `os.environ.get(...)` pattern ensure this.
- **`CLAUDE_CODE_CHILD_SESSION`**: sub-sessions (worktree forks) share the
  parent `CLAUDE_CODE_SESSION_ID`. If the user runs two parallel worktree
  sessions simultaneously, their operations will share the same `agentSessionId`
  unless Claude Code issues a distinct UUID per sub-session. This ambiguity is
  noted but not resolved in V1.
- **Operation sort regression**: the Story 0185 audit identified a minor
  non-total sort when two operations from different instances share the same
  `operationId` and `startedAt`. Adding `instanceId` to the ORDER BY is
  recommended alongside this Story's SQL changes (low risk, non-breaking).

## Human Decisions Required

- Authorize this Story scope.
- Authorize implementation start (separate gate from scope approval).
- Confirm the schema approach (D1: two nullable columns on `mcp_tool_events`
  vs. a separate `mcp_agent_sessions` table).
- Confirm that `CLAUDE_CODE_CHILD_SESSION` sub-sessions are intentionally left
  ambiguous in V1 (D7).
- Confirm that `CLAUDE_CODE_USER_EMAIL` and `CLAUDE_CODE_ACCOUNT_UUID` are
  explicitly excluded from V1 scope.
- Confirm the provider badge format (D5: short codes CC/OC/CD vs. full name).
