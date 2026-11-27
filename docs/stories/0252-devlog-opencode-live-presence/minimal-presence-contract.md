# Story 0252 — Minimal Live Presence Contract (Approved Scope)

## Purpose and accepted trust boundary

This is a proposed **GUI-local presentation contract**, not a wire schema,
authorization grant, or DevLog business contract. It applies only after formal
acceptance of the pending ADR-084 amendment and separate authorization of Story
0252 implementation.

The adapter consumes native OpenCode `MountedSession`, the already-exposed
server `Data` cache/transcript, and supported native events. It derives a
bounded in-memory view for the currently routed session. It introduces no
Companion RPC, server observer, event bridge, MCP/REST request, credential
handling, persistence, or external transmission.

The owner accepts OpenCode's trusted GUI plugin model for this limited
presentation feature. Filtering data to the route is presentation scoping, not
server-side authorization and not isolation from another trusted plugin. This
contract does not permit querying/enumerating other sessions.

## Minimal local evidence projection

The implementation may retain only the following derived values in window
memory, scoped by current `session.key` and server/location identity:

- recognized source: direct tool part or outer Code Mode `execute`;
- allowlisted normalized DevLog tool name;
- direct-call native tool-part ID, when present;
- current evidenced platform state and timestamp, if available;
- explicitly decoded DevLog task status, if present;
- derived avatar state and a short-lived terminal presentation marker, if
  supported by existing presentation behavior.

Do not create or expose a session snapshot API. Do not copy the transcript or
store raw event/message objects. Unknown, missing, stale, malformed, or
incomplete evidence is kept as unavailable/unknown in the reducer and is never
converted into success, failure, or disconnection.

## Evidence and state semantics

### Activity recognition

- Direct MCP: inspect only assistant tool parts whose normalized name matches
  the explicit DevLog allowlist. Ignore unrelated tools.
- A direct tool part in `running` is positive evidence of dispatch and supports
  `WORKING`. A `streaming`/input phase alone is not confirmed dispatch.
- Code Mode: inspect only explicit nested `toolCalls` metadata or the structured
  result on an outer `execute` part. In the pinned OpenCode Code Mode result,
  rows expose `name` but no nested status, call ID, or correlated business
  result; a recognized name-only row therefore establishes `UNKNOWN`, not
  `IDLE`, `WORKING`, `READY`, or `ERROR`. If another supported metadata shape
  explicitly provides row status, `running` supports `WORKING` and `error`
  supports only a tool-level `ERROR`.
- Code Mode rows have no nested call ID. Do not fabricate one, correlate a
  nested output to a row, or claim per-call concurrency beyond current
  metadata. Aggregate only recognized active/error evidence per outer execute
  part.
- A generic `execute` part, arbitrary tool output, assistant text, or a prompt
  mentioning DevLog is not sufficient evidence of a DevLog call.

### Avatar states

| State | Required evidence |
|---|---|
| `IDLE` | No currently observed recognized DevLog activity for the current route after its native session view is available. A recognized Code Mode tool name without status is inconclusive, not idle. It does not imply DevLog/MCP health. |
| `WORKING` | A recognized direct DevLog part or Code Mode nested row is positively `running`, or a direct result explicitly reports a pending task status (`SUBMITTED`, `PENDING`, or `RUNNING`). A name-only Code Mode result does not qualify. Earlier streaming/input alone does not qualify. |
| `READY` | A direct DevLog call has a confirmed terminal result **and** its returned structured content explicitly establishes an applicable successful DevLog task outcome (for example, `COMPLETED`/`SUCCEEDED`). Host/tool completion alone is insufficient. Do not derive `READY` from a Code Mode outer completion or uncorrelated nested result. |
| `ERROR` | A recognized DevLog direct tool part reports error, or its result explicitly reports an applicable MCP/business failure. A Code Mode DevLog row may support a tool-level error only; do not infer business-task failure from it. |
| `DISCONNECTED` | Not supported in the first increment unless a reliable native signal specifically establishes loss of the displayed boundary. `ServerRef.connected=false`/reconnecting concerns the OpenCode server connection, and `mcp.status.changed` alone names a server without carrying a status; neither alone proves the DevLog MCP is disconnected. |

The live implementation need not use all five states. It is acceptable to omit
`READY` where a successful DevLog business outcome cannot be unambiguously
decoded, and omit `DISCONNECTED` absent positive boundary-specific evidence. If
the route/cache is not ready or observation is incomplete, retain a
neutral/unavailable presentation; do not relabel uncertainty as `IDLE`.

## Route lifecycle, concurrency, and privacy

- Read/process only the currently routed session. Subscribe only to necessary
  native tool/session event types; discard unrelated-session events immediately
  after the minimum route-scope check.
- Clear projection and detach prior subscriptions on session or server/location
  change, panel close, and plugin disposal. Late callbacks must not affect a new
  route.
- Use native part IDs for direct-call concurrency. For Code Mode, do not invent
  nested identities; represent only aggregate activity supported by each outer
  tool's current metadata.
- Reconcile from already-loaded current-session transcript on panel reopen.
  Do not enumerate sessions or fetch additional history solely for presence.
- Never retain raw transcripts, prompts, tool arguments/results, credentials,
  or error payloads in Companion-owned storage or logs. Derived state remains
  bounded and in memory only.
- Observation and rendering errors must not affect OpenCode tool execution.

## Compatibility and fallback

Reuse Story 0251's avatar/presentation components. Keep synthetic preview
visibly separate from live evidence. Keep the static/neutral fallback when the
route, cache, or supported evidence is unavailable. The UI must not claim
server-side authorization or isolation from another trusted GUI plugin.

Future features needing data beyond the native GUI surface, cross-window
delivery, persistence, or external transmission require their own authorization
and privacy analysis; this contract grants none of those capabilities.
