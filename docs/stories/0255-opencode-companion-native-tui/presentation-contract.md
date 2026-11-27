# Proposed Presentation Contract — OpenCode TUI Companion

This contract is proposed for Story 0255 review. It describes presentation
semantics, not a server/API contract, permission boundary, or authorization
grant.

**Data-source gate:** this contract assumes an owner-approved, server-scoped
Companion projection. The current ADR amendment's local-cache exception is
Desktop-GUI-specific; this document does not extend it to TUI. No TUI-local
projection may be implemented unless the owner separately approves an ADR
amendment. The existing Companion RPC is not presumed authorized.

## Scope identity

The current view is keyed by the connected server instance, location/project
and workspace where supplied, and active session. A change to any applicable
key invalidates the previous view before new data is displayed. The server
boundary must enforce the applicable authorization; the currently selected
route is a presentation selector, not proof of authorization.

Do not enumerate or retain other sessions. Do not broadcast activity to other
TUI instances. Do not persist Companion operation history or raw source data.

## State model

Keep two independent dimensions:

### Platform observation

- `ATTEMPTED` — the host exposed an attempt, but dispatch is not established.
- `RUNNING` — the host explicitly reports an active operation.
- `COMPLETED` — the host reports completion of that platform tool/execution.
- `ERROR` — the host reports an execution error.
- `INTERRUPTED` — explicit interruption evidence, without implying tool/task
  terminal outcome unless separately present.
- `UNKNOWN` / `INCOMPLETE` — event, status, data, or correlation is missing,
  ambiguous, late, stale, or unsupported.

### DevLog task observation

- An allowlisted explicit task status only, e.g. `SUBMITTED`, `PENDING`,
  `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `REJECTED`, or
  `POLL_TIMEOUT`, if present in the observed supported response.
- `UNKNOWN` or `NOT_APPLICABLE` when no unambiguous explicit status is
  available.
- `POLL_TIMEOUT` with last-known `SUBMITTED` is still pending/last-reported,
  not terminal failure.

Platform `COMPLETED` does not imply DevLog `COMPLETED`. An MCP error, host
error, DevLog task status, context freshness and connectivity are separate
facts. A missing tool event is not evidence of no activity, success, or
disconnection.

## Now

- Compact text/glyph summary; use color only as a supplement.
- `IDLE`: inspected current session has no observed active/recent operation.
  It does not assert DevLog service health.
- `WORKING`: show whether this means an attempt, confirmed host execution, or
  explicitly pending task; do not blur them.
- `READY`: only a positive, applicable DevLog outcome is explicitly evidenced.
- `ERROR`: show observed host/task failure without allowing a trace-read or
  presentation error to rewrite it.
- `UNKNOWN`: neutral wording for loading, interruption without terminal
  evidence, stale or unsupported states.
- `DISCONNECTED`: not part of the first proposal unless the specific required
  connection boundary has positive evidence. A timeout alone is insufficient.

## History

- Bounded to the newest 20 recognized rows from no more than 40 loaded messages,
  80 parts/message and 80 nested Code Mode metadata rows, matching current GUI
  projection caps unless TUI-specific evidence justifies a separately approved
  limit.
- Only allowlisted DevLog names, supported host outcome, explicit unambiguous
  task status, native host call ID, and host-supplied timing may be shown.
- Missing and unsupported transcript data has a distinct unavailable state;
  an inspected window with zero matching rows is an empty state and makes no
  claim about older, uninspected history.
- Code Mode is represented at the outer `execute` granularity. Names may be
  shown only where native metadata supports them; nested results do not receive
  invented IDs, timestamps, order, status, outcome or association.
- Never retain/render prompts, raw arguments, raw results, full error payloads,
  credentials, tokens or arbitrary diagnostic strings.

## Interaction and accessibility

- Use a collision-resistant, plugin-prefixed command and panel/route ID.
- Preserve built-in TUI layout and keyboard behavior. Do not seize global
  shortcuts or replace a host slot in the initial increment.
- Panel close, session switch, server/location switch, plugin reload and
  teardown clear scoped state and dispose listeners.
- Text labels communicate meaning without requiring color; respect terminal
  width, reduced capability and narrow-screen/full-screen panel behavior.
- Loading, empty, unknown, incomplete and error states are distinct and
  non-alarming when evidence is merely unavailable.

## Explicit exclusions

No Context tab, Code Mode nested context inspection, Context Trace, extra MCP or
REST request, credentials, persistence, remote filesystem fallback, server
observer/RPC, cross-session state or claim of parity with Desktop/Claude.
