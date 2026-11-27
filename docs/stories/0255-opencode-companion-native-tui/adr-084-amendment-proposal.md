# Proposed ADR-084 Amendment — Native OpenCode TUI Local Projection

## Status

**ACCEPTED by the owner on 2026-11-27 and recorded in
`docs/decisions/ADR-084.md`.** This document is the proposal/provenance record;
the canonical accepted decision is ADR-084. Acceptance does not authorize
implementation or runtime experiments and does not certify a TUI/SDK version.

## Proposed amendment

Add a TUI-specific, presentation-only exception to ADR-084's responsibility
boundary and its 2026-11-27 “Narrow presentation-only projection exception”.
This amendment is limited to OpenCode's already-loaded native TUI session data;
it preserves the existing Desktop exception and every other ADR-084 invariant.

### Native TUI presentation projection

An OpenCode TUI adapter MAY derive a bounded, sanitized, in-memory
presentation projection from the native TUI data already available for its
currently routed session, without a Companion server observer or Companion
RPC. This permits a TUI-native presence summary and the already Story-approved
bounded operation History where the pinned host actually exposes sufficient
evidence.

For this exception:

- The adapter MUST read only the current routed session's already-loaded
  native session/message cache. It MUST NOT call a transcript/message sync,
  fetch, or other retrieval API solely for Companion projection.
- The adapter MUST NOT enumerate or inspect other sessions, retain activity
  from other sessions, or broadcast session data to another TUI instance.
- Projection MUST be bounded, sanitized, ephemeral and in memory. It MUST NOT
  retain raw prompts, tool arguments, raw results, credentials, tokens, or raw
  error payloads. Only allowlisted tool identity, evidenced host status,
  explicitly supported DevLog task status, native IDs where needed, and
  bounded host-supplied timing may be retained.
- Projection MUST be scoped to the current server connection, location/project
  and workspace where available, and session. Session/location/server changes,
  panel/route disposal, plugin reload and process teardown MUST clear scoped
  projection state and dispose subscriptions.
- Session filtering is **presentation scoping only**, not server-side
  authorization, proof of session ownership, or a security boundary.
- The TUI plugin is treated as trusted code deliberately enabled in the local
  CLI process. This is a trust assumption, not a claim that plugins are
  sandboxed or isolated from other trusted plugins. A connected remote server's
  normal authentication and authorization remain unchanged.
- The projection MUST make no additional DevLog MCP/REST request, make no
  Context Trace request, acquire or receive no DevLog credential, use no
  Companion RPC, and require no server observer, persistent storage, backend
  authorization change, or cross-session inspection.
- A local TUI connected to a remote server MAY project only native data that
  the normal authenticated TUI session has already made available for its
  current route. This exception grants no new server access and no local
  filesystem fallback.
- If an outcome, nested operation identity, metadata field or terminal event is
  absent, ambiguous, stale, or unsupported, the projection MUST remain
  `UNKNOWN`, incomplete or unavailable. It MUST NOT infer nested Code Mode
  business outcomes from the outer `execute` result.
- The TUI adapter MUST NOT change tool inputs/outputs, permissions, preflight
  decisions or DevLog task execution. Projection failures MUST NOT affect
  normal host operation.

### Evidence and compatibility boundary

The exception authorizes an implementation approach only after the target
version exposes and supports the required native APIs. It does not certify a
CLI, TUI, server, plugin SDK, OpenTUI/Solid, Desktop/sidecar or remote topology.
Versioned static and runtime evidence remain necessary for each support claim.

The initial Story 0255 increment MUST stay within its approved scope. The
bounded History view was explicitly included in the approved Story; no broader
history browser is authorized. Context Inspector, Code Mode nested result
inspection, backend Context Trace, server/RPC architecture and Claude changes
remain out of scope.

## Basis and evidence limits

- Current OpenCode TUI documentation, consulted 2026-11-27, documents
  `session.panel` with a session ID, cached session/message list APIs,
  typed event subscriptions with cleanup, and local `cli.json` plugin loading
  while connected to a remote server.
- The existing GUI Companion's pure projection in
  `opencode-mod/devlog-companion/src/gui-presence.js` reads loaded message
  parts and bounded Code Mode `execute` metadata, distinguishes host and task
  outcomes, and leaves nested Code Mode results without evidence as unknown.
- The repository package pins `@opencode/plugin` 2.4.0. The current docs and
  their API examples are not pinned evidence that this SDK/runtime exposes
  these TUI interfaces.
- The exact TUI message shape, especially Code Mode nested tool-name metadata,
  the corresponding event names and reactivity, local/remote behavior on the
  target tuple, and the host's plugin trust/sandbox guarantees have not been
  runtime-tested. The trust language above is therefore an explicit design
  assumption, not a verified host guarantee.
- No runtime launch, reload, active configuration change, additional DevLog
  call, or new infrastructure was used to prepare this proposal.

## Preservation

This proposed amendment:

- preserves OpenCode Desktop as the primary OpenCode presentation and keeps its
  existing accepted GUI-only local projection exception unchanged;
- preserves Claude as a first-class independent platform and changes no Claude
  files or installation;
- leaves Java/Core authority, backend contracts, authorization, MCP/REST,
  trace access, grounding, trusted knowledge and the Preflight Gate unchanged;
- does not create a shared Companion framework or require UI/rendering
  mutualization;
- does not reopen Story 0254's deferred Code Mode Context inspection or backend
  Context Trace work.

## Owner decision recorded

The owner accepted this amendment on 2026-11-27; the canonical amendment has
been recorded in ADR-084. The owner separately approved the reconciled Story
and Implementation Plan. These approvals still do not authorize production
implementation, dependency installation, runtime activation, or configuration
changes, and do not constitute final Story acceptance.
