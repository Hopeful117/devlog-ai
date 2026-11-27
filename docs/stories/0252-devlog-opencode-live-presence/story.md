# Story 0252 — DevLog Companion — OpenCode Live Presence

## Status

**ADR-084 amendment and revised Story/Implementation Plan approved; production
implementation authorized by the owner on 2026-11-27. The scoped code and
automated validation are complete; manual Desktop validation and human Story
acceptance remain pending. This authorization is limited to the approved
GUI-local implementation and automated checks. Desktop launch/reload, active
configuration changes, and real DevLog operations remain outside its scope and
require separate authorization.

Stories 0249 and 0250 remain suspended and are not resumed by this Story.
Story 0251's simulated visual states remain a presentation-only baseline; they
are not live evidence.

The preferred implementation is a GUI-local, in-memory presentation projection
from native data already exposed by OpenCode Desktop for the currently routed
session. The owner explicitly accepts OpenCode's trusted GUI plugin model for
this limited feature. Route filtering is presentation scoping, **not**
server-side authorization or a per-session security boundary. This work must
not add RPC, a server observer, broader data access, persistence, external
transmission, MCP/REST calls, or backend changes. The accepted ADR-084 amendment
records this narrow exception; production implementation remains gated on
explicit implementation authorization.

## Objective

Replace the static visual baseline with a useful, reactive Desktop Companion
using native evidence already available to the trusted GUI plugin. Scope all
reading, processing, and in-memory derived state to the currently routed
`MountedSession`. Preserve the existing presentation components and distinguish
tool execution outcomes from explicitly decoded DevLog business-task outcomes.

This is a narrowly accepted presentation trust assumption, not a claim that
route filtering authorizes a server request or isolates the plugin from other
trusted plugins. No additional data access or delivery path is in scope.

## Governing context

- [ADR-084](../../decisions/ADR-084.md), including its accepted dated
  amendment. Original requirements remain authoritative except for the narrow
  presentation-only and sequencing clarifications in that amendment.
- Story 0251's approved visual/presentation baseline and implementation report.
- Story 0248's versioned runtime evidence and limitations; it does not certify
  GUI rendering for this feature or the current Desktop/sidecar pairing.
- Current OpenCode GUI, native data, and presentation sources/tests as evidence
  of repository state, not runtime certification.

## Proposed scope

- Observe only the currently routed session using reactive `MountedSession`,
  its native `Data` cache/transcript, and supported native tool/session events.
- Derive a bounded, sanitized presence projection in window memory; reuse the
  Story 0251 presentation components. Do not enumerate other sessions or retain
  unrelated activity.
- Recognize direct DevLog tool parts and Code Mode `toolCalls` metadata only
  where the available evidence is sufficient. Do not invent nested call IDs or
  correlate nested outcomes beyond the host evidence.
- Add focused deterministic tests, the existing GUI build/package checks, and a
  short manual Desktop procedure for the already-installed local plugin. Any
  real DevLog operation is performed by the owner or under separate explicit
  authorization.
- Record unsupported states and runtime/version limitations. A passing build is
  not Desktop runtime evidence.

## Explicit non-goals

- Stories 0249/0250, runtime harness/infrastructure, or Preflight Gate changes.
- Claude files/behavior, shared runtime components, shared-core extraction, or
  package migration.
- Companion RPC, server observer dependency, operation-history UI, Context
  Inspector, REST/backend changes, credentials, or extra MCP calls.
- TUI, remote workflows, cross-window projection, or broad session enumeration.
- Persistence or external transmission of derived presence; retention of raw
  transcript/prompt/tool/error data in Companion-specific storage/logs.
- Active Desktop configuration edits, plugin installation, Desktop launch or
  reload without separate authorization, deployment, or user-data changes.
- Treating timeout, missing event, unknown status, or unavailable cache as
  `ERROR` or `DISCONNECTED`.

## Acceptance criteria (proposed)

  1. Presence is derived only from native evidence for the currently routed
     session. Tool execution outcome and DevLog business-task outcome remain
     distinct; missing/incomplete evidence never implies terminal success.
   2. `WORKING` requires a recognized DevLog tool part or Code Mode row with
      positive running evidence, or an explicit pending DevLog task status in a
      direct result. Code Mode rows have no nested call ID; the implementation
      does not invent nested call identity/correlation. The pinned Code Mode
      result shape exposes names without nested status; a recognized name-only
      row is therefore `UNKNOWN`, not `IDLE`, `WORKING`, `READY`, or `ERROR`.
  3. `READY` requires an explicitly decoded successful DevLog task outcome.
     Host/tool completion alone is insufficient. `ERROR` requires a confirmed
     relevant tool/MCP/task failure. `DISCONNECTED` is omitted unless reliable
     native connectivity evidence for the displayed boundary is established.
  4. `IDLE` means no currently observed recognized activity for the routed
     session; it does not assert DevLog/MCP health. Ambiguous/unloaded/
     interrupted evidence is not relabeled as another terminal state.
  5. Session or server-location changes clear/reconcile local state. Multiple
     operations remain separate where native IDs exist; Code Mode without IDs is
     represented only to the granularity the metadata supports. Unrelated tools
     do not affect DevLog presence.
  6. Closing/reopening the panel and plugin disposal remove listeners and stale
     state. Only necessary sanitized derived fields remain in memory; no raw
     transcript, prompt, arguments, result, credentials, or error payload is
     persisted or logged.
  7. The feature uses only GUI-native cache/transcript/events for the route; it
     adds no RPC, server observer, session enumeration, MCP/REST request,
     persistence, external transmission, backend change, or auth change.
  8. Focused deterministic tests and normal GUI build/package checks pass.
     Manual Desktop results are recorded separately; no runtime support is
     claimed from static checks alone.
  9. Claude files, shared runtime, backend/integration contracts, Stories
     0249/0250, Gate, and active Desktop configuration remain unchanged.

## Approval gates

The owner approved the ADR-084 amendment and revised Story/Implementation Plan
and explicitly authorized production implementation on 2026-11-27. Desktop
reload/configuration changes and any real DevLog operation require separate
explicit authorization. Human acceptance remains separate from implementation
completion.
