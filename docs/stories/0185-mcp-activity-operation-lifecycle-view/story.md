# Story 0185 - MCP Activity: Operation Lifecycle View

## Status

**DESIGN CONSOLIDATED — AWAITING IMPLEMENTATION AUTHORIZATION**

This Story defines the design for a structured operation lifecycle projection
over the existing MCP tool event stream. No implementation is authorized until
explicit human authorization is received.

## Problem

The Angular Activity tab currently renders a flat, paginated list of raw
`McpToolEvent` rows. Each MCP tool invocation produces two to three events
(STARTED, then one terminal), and both rows appear independently in the feed.
This means a completed tool call occupies two or three rows, the terminal status
is not immediately visible without cross-referencing rows, and the duration
visible on the terminal row is disconnected from the STARTED row that carries
the tool name.

The view presents event-level data where the user wants operation-level data:
one row per tool invocation showing its name, status, duration, and timestamp.

## Goal

Transform the Activity tab's flat event stream into a per-operation lifecycle
view that surfaces one row per tool invocation, with its resolved status,
duration, and timestamps derived from its constituent events — without changing
the event ingestion model, the Python runtime, or the database schema.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0113 - MCP Tool Activity Companion

## Architectural Constraints

1. The `mcp_tool_events` table schema must not change. No new table, no new
   column, no schema migration. The projection is computed at query time.
2. The projection is computed by Java Core in a native SQL query. It is not
   computed in Python or in Angular.
3. No `sessionId` field is introduced. `instanceId` identifies a runtime
   process; it is not a Claude Code session identifier and must not be
   relabeled.
4. The Python runtime (`runtime.py`) must not change.
5. No new ADR is required for this Story.
6. The STALE classification is a visual indicator computed in Angular from
   existing data. It is not a new business state, not persisted, and not part
   of the backend contract.
7. Pagination is applied after the SQL aggregation, over the projected
   operations, not over raw events. The total count exposed by the response
   reflects the number of operations, not the number of events.

## In Scope

- A new Java endpoint `GET /api/v1/projects/{projectId}/mcp-operations` that
  returns paginated `McpOperationView` projections.
- A native SQL query that groups `mcp_tool_events` by `(instance_id,
  operation_id)` and resolves each operation's tool name, status, start time,
  terminal time, and duration from its constituent events.
- Terminal event selection rule: when multiple terminal events exist for the
  same operation, select the one with the latest `occurred_at`. Deterministic
  tiebreak on `event_type` (alphabetical ascending) when `occurred_at` values
  are equal.
- A new Angular service and component that calls the new endpoint and renders
  one row per operation.
- A visual STALE indicator in Angular: an operation with status ACTIVE and no
  terminal event, started more than 5 minutes ago by client time, is displayed
  with a STALE label. STALE is not a backend concept.
- Unit tests for the Java projection mapper and service.
- PostgreSQL/Testcontainers integration tests for the native SQL query,
  covering out-of-order event arrival, correlated events, isolated terminals
  without a matching STARTED, and pagination correctness.
- Angular unit tests for the STALE classification logic.
- Replacement of the existing flat event list in the Angular Activity tab with
  the new operation lifecycle view (the ingestion POST endpoint is unchanged).

## Out of Scope

- Changes to event ingestion (`POST /api/v1/mcp/tool-events`) or the Python
  runtime.
- Database schema changes.
- A `sessionId` field or any mapping from `instanceId` to Claude Code sessions.
- New ADR.
- Persisting STALE as a backend state.
- Filtering, search, or time-range queries beyond what pagination provides.

## Design Decisions

### D1 — No schema change

The projection is derived at query time from the existing `mcp_tool_events`
table. No migration, no new table, no new column.

### D2 — Projection unit is `(instanceId, operationId)`

Each distinct `(instanceId, operationId)` pair yields exactly one
`McpOperationView` row. The uniqueness constraint on
`(instance_id, operation_id, event_type)` guarantees at most one event of each
type per operation.

### D3 — Terminal selection: most recent by `occurred_at`

When multiple terminal events exist for the same `(instanceId, operationId)`,
the projection selects the one with the highest `occurred_at`. If two terminal
events share the same `occurred_at`, the tiebreak is `event_type` alphabetical
ascending (deterministic, stable). This rule does not favor any particular
terminal type: a FAILED event that occurred after a RESPONDED event wins, and
vice versa. No terminal is silently masked by another.

### D4 — STALE is a visual-only Angular indicator

An operation with status `ACTIVE` (a STARTED event exists, no terminal event
exists) that was started more than 5 minutes ago is displayed in Angular with a
STALE label. STALE is not an enum value in the backend contract, is not
persisted, and does not appear in the API response. The 5-minute threshold is
a V1 constant in the Angular component; it does not require a backend
configuration parameter.

### D5 — Pagination after aggregation

The SQL query groups events into operations first, then paginates the resulting
operation rows. `totalOperations` in the response counts the number of distinct
`(instanceId, operationId)` pairs matching the filter, not the number of raw
events. Sort order is `startedAt DESC NULLS LAST`, with `operationId ASC` as a
stable tiebreak for operations sharing the same timestamp.

### D6 — Tool name resolution

Tool name is taken from the STARTED event of the operation. If no STARTED event
exists (orphan terminal), the tool name is taken from the terminal event. If
neither carries a tool name, the response field is `null`; Angular displays
`unknown tool`.

### D7 — `instanceId` semantics preserved

`instanceId` identifies the Python runtime process (`secrets.token_hex(16)` per
process start). It is not a Claude Code session identifier and must not be
exposed or labeled as one.

### D8 — Ingestion endpoint unchanged

`POST /api/v1/mcp/tool-events` and the Python runtime that calls it are outside
this Story's scope. No change to `McpToolEvent`, `McpToolEventRepository`,
`McpToolEventService`, or `McpToolEventController` beyond what is necessary to
add the new projection query.

## Response Contract

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
      "reasonCode": "string | null"
    }
  ],
  "totalOperations": 42,
  "page": 0,
  "size": 20
}
```

`status` is derived from the terminal event's `status` field, or `ACTIVE` when
no terminal event exists. `STALE` does not appear in the response.

Required authorization: `MCP_TOOL_EVENTS_READ` (same as the existing events
endpoint).

## Acceptance Criteria

### AC-1 — Single row per operation

The endpoint returns exactly one row per `(instanceId, operationId)` pair. A
completed invocation (one STARTED + one terminal) produces one row, not two.

### AC-2 — Terminal conflict resolution

When a test fixture contains both a RESPONDED and a FAILED event for the same
operation, the row reflects the event with the later `occurred_at`. When
`occurred_at` values are equal, the tiebreak is `event_type` alphabetical
ascending. No terminal event is silently masked.

### AC-3 — ACTIVE operations

An operation whose only event is STARTED returns with `status = ACTIVE`,
`completedAt = null`, `durationMs = null`, `reasonCode = null`.

### AC-4 — Orphan terminal

An operation with a terminal event but no STARTED event returns a row with
`startedAt = null` and `toolName` taken from the terminal event (or null if
absent).

### AC-5 — Out-of-order arrival

A test fixture where the terminal event was ingested before the STARTED event
(different `receivedAt` order) still produces a correct row with `startedAt`
from the STARTED event and `completedAt` from the terminal event.

### AC-6 — Pagination after aggregation

Page 0, size 2 of a fixture containing 5 distinct operations returns 2 rows and
`totalOperations = 5`. Sort is stable across pages.

### AC-7 — PostgreSQL/Testcontainers integration

The native SQL projection query is validated by a Testcontainers test against a
real PostgreSQL instance. The test covers: correlated STARTED + terminal pairs,
out-of-order arrival, orphan terminals, terminal conflict resolution (AC-2), and
pagination (AC-6). An H2-only test is not sufficient for this criterion.

### AC-8 — STALE visual indicator

The Angular component applies the STALE label to an operation with
`status = ACTIVE` and `startedAt` more than 5 minutes before the current client
time. Operations with any terminal status or with `startedAt` within 5 minutes
are not labeled STALE.

### AC-9 — Authorization

The new endpoint refuses unauthenticated requests with 401 and requests lacking
`MCP_TOOL_EVENTS_READ` with 403.

### AC-10 — Existing ingestion unaffected

The `POST /api/v1/mcp/tool-events` endpoint, its authorization, and its
idempotency behavior are unchanged. The existing
`McpToolEventControllerWebMvcTest` suite passes without modification.

### AC-11 — Angular Activity tab updated

The Angular Activity tab renders the new `/mcp-operations` endpoint. The
previous flat event list component is replaced.

## Human Decisions Required

- Authorize this Story scope.
- Authorize implementation start (separate gate from scope approval).
- Authorize the terminal conflict resolution rule (D3: most recent by
  `occurred_at`, tiebreak `event_type` alphabetical).
- Authorize the STALE threshold (D4: 5 minutes, visual only, Angular-side).
- Confirm that the existing flat event list in the Angular Activity tab is
  replaced by the new operation lifecycle view.
