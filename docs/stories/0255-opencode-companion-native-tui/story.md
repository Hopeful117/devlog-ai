# Story 0255 — OpenCode Companion — Native TUI

## Status and authorization

**Story and reconciled TUI-local projection approved by the owner on
2026-11-27. Implementation Plan also approved by the owner on 2026-11-27
(Gate 2). The owner explicitly authorized production implementation on
2026-11-27. The owner separately authorized local CLI plugin activation and a
brief TUI launch on 2026-11-27. Dependency installation and broader runtime
qualification were separate gates at implementation authorization; see the
subsequent authorizations below.

The owner later requested runtime qualification. An isolated local
TUI-to-same-host-server qualification was performed and is recorded in the
[TUI checklist](tui-checklist.md). It confirms local plugin loading and status
slot rendering only; panel/history interaction and remote/SSH qualification
remain incomplete.

On 2026-11-27 the owner explicitly accepted this runtime evidence limitation
and directed Story closure. This accepts the stated limitation for delivery; it
does not claim untested interactions or topologies are supported. Final PR
validation remains required before merge.

Story number 0255 was not present in `origin/main` at the preparation revision
`03d22b118f1c6c747b99d90283b0c1ca0539cbf2`.

## Objective

Offer a small, terminal-native DevLog Companion in OpenCode's interactive TUI,
complementing (not replacing) the existing OpenCode Desktop Companion and
Claude Companion. The proposed first increment is a current-session presence
indicator and bounded History view, using only native evidence already
available through the accepted TUI-local presentation projection in ADR-084
and preserving unknown or incomplete outcomes.

The TUI must be useful in both a terminal running on the same host as OpenCode
and a local TUI connected to an authenticated remote OpenCode server, if those
topologies pass their own versioned validation. It derives a bounded,
presentation-only projection from only the current routed session's
already-loaded native TUI data, under the accepted 2026-11-27 amendment to
ADR-084.

## Governing context

- [ADR-084](../../decisions/ADR-084.md), including its accepted
  2026-11-27 amendment. OpenCode Desktop remains the primary presentation;
  TUI is an intentional secondary target. Adapters remain native and
  independently operable. No feature parity or runtime support is inferred.
- [Story 0248](../0248-devlog-opencode-native-compatibility-spike/story.md),
  its [implementation report](../0248-devlog-opencode-native-compatibility-spike/implementation-report.md)
  and [runtime protocol](../0248-devlog-opencode-native-compatibility-spike/runtime-validation-protocol.md).
  The server/RPC prototype has useful bounded-projection patterns but is not
  currently exported by the package. Desktop rendering, full lifecycle,
  caller-to-session authorization, and other listed gaps stay separate.
- [Story 0253](../0253-opencode-companion-operation-history/story.md) and
  [operation-history contract](../0253-opencode-companion-operation-history/operation-history-contract.md):
  no fabricated nested Code Mode identity, chronology, or per-call outcome.
- [Story 0254](../0254-opencode-companion-local-context-inspector/story.md):
  keep its accepted GUI-local behavior intact. Code Mode context inspection
  and backend Context Trace remain explicitly deferred.
- Existing package `opencode-mod/devlog-companion/` and its tests, as they
  exist at the preparation revision.
- Current official OpenCode TUI/plugin and CLI-plugin documentation, consulted
  2026-11-27. These describe TUI slots, session panels, commands, typed events,
  session/message caches, cleanup, and separate `cli.json` loading. They are
  current documentation, not version-pinned evidence for the package's
  `@opencode/plugin@2.4.0`.

The DevLog Story Agent preflight completed as task/snapshot
`be65962e-ef11-4271-8a90-edc522bc601f` at the preparation revision. Its
project freshness was `STALE` / `PARTIALLY_FRESH`, its evidence was truncated
and its resolution state was `UNKNOWN` (confidence LOW). It usefully flagged
the missing TUI-specific evidence; it does not establish a project decision.
The current repository and official OpenCode documentation were reconciled
directly.

## Proposed scope

After Story, plan, and implementation authorization:

1. Pin one OpenCode CLI/TUI build and the exact SDK/runtime dependencies; verify
   which documented TUI APIs exist in that build before coding.
2. Add a native TUI entry point to the existing Companion distribution only
   if package loading and Desktop's existing GUI-only behavior can remain
   independent. Otherwise present an isolated distribution option and its
   tradeoffs for approval; do not migrate packaging silently.
3. Add a concise terminal-native entry point (for example, one status/footer
   contribution and a namespaced command opening a session panel or route).
4. Show `Now` presence and bounded `History` for the currently routed session
   from its already-loaded native TUI session/message data. This is
   presentation scoping only, not authorization; do not fetch or sync
   transcripts for Companion.
5. Reuse existing pure presence/activity projection functions only where
   message-shape compatibility and dependency boundaries are demonstrated.
   Keep TUI rendering and lifecycle separate from Desktop. Preserve Desktop
   behavior with regression tests for any shared-function change.
6. Keep direct tool execution, Code Mode outer execution, and DevLog task
   outcome distinct. Preserve the 0253 rule: no inferred nested call IDs,
   timestamps, ordering, status, or result attribution.
7. Bound and sanitize retained state; scope it to the current server/location,
   project/workspace and session; clear it on route/scope changes and dispose
   all listeners and UI registrations on unload.
8. Verify the TUI-only installation path and remote topologies without
   changing active Desktop configuration, Claude, the Preflight Gate, or user
   configuration outside an explicitly approved disposable setup.

## Out of scope

- Changes to Claude Companion, backend/Core, MCP/REST contracts, credentials,
  authorization, or the Preflight Gate.
- New RPC/server observer behavior, new MCP/REST requests, Context Trace reads,
  persistence, telemetry, or cross-session broadcast unless a separate
  approved design proves them necessary. The existing RPC is not presumed
  authorized for TUI use.
- Code Mode nested context/result inspection; backend Context Inspector; any
  reopening of deferred 0254 work.
- Reconstructing the GUI's 0254 Context view in TUI in this increment.
- Full parity with Desktop or Claude; an OpenCode support claim for untested
  versions or topologies.
- Global `opencode.json(c)` or `cli.json` edits, personal-profile install,
  activation, service restart, real DevLog operation, or runtime test without
  separate explicit owner authorization. The specific local `cli.json`
  activation and brief CLI launch described in the implementation report were
  separately authorized; this does not authorize other profiles or hosts.
- Changing the existing package export map or dependencies before compatibility
  and packaging evidence and owner approval.

## Proposed acceptance criteria

1. A version/evidence matrix identifies CLI/TUI build, TUI/plugin SDK, OpenTUI
   and Solid dependencies, server version, configuration source, and each
   topology tested. Unknown pairings stay `UNKNOWN`.
2. A TUI entry point loads through the documented TUI discovery mechanism on
   the pinned build, and can be independently disabled/removed without
   disabling the existing Desktop GUI contribution.
3. The contribution is terminal-native, discoverable, namespaced to avoid
   command/panel collisions, and leaves built-in slots intact (append rather
   than replace unless separately justified).
4. Presence and History derive only from the current routed session's
   already-loaded native TUI data under ADR-084's accepted local-projection
   amendment; unavailable data, an empty inspected window, and observed no
   activity are distinct.
5. Platform execution outcome and explicit DevLog task outcome remain separate.
   Missing terminal events, interruption without outcome, ambiguous statuses,
   Code Mode nesting, and stale data never become fabricated success/failure.
6. The projection is bounded, sanitized, in memory, and scoped to server,
   location/project/workspace, and session. Route switches, server changes,
   panel close, reload, and teardown cannot leak stale UI state.
7. The adapter performs no new server access. Session filtering is
   presentation scoping, not an authorization boundary; normal server
   authentication remains unchanged.
8. Observer/presentation errors do not change tool execution or bypass
   permissions. The UI obtains no DevLog credential and does not make new MCP,
   REST, or Context Trace requests.
9. Deterministic tests cover projection/formatting, limits, unknowns, concurrency
   and ordering where evidence permits, scope changes, remote disconnects, and
   cleanup. Typecheck/build/package checks pass.
10. Owner-led TUI validation passes on each claimed topology. Desktop behavior
   is checked separately and no Claude compatibility claim is inferred.
11. Installation, upgrade/removal, rollback, and sanitized runtime evidence are
   recorded. Automated checks, runtime results, and human acceptance remain
   separate.

## Accepted projection decision and implementation gate

The owner accepted the TUI-local presentation-projection amendment to ADR-084
on 2026-11-27. It authorizes only bounded, sanitized, in-memory projection from
the already-loaded native data of the current routed session. It does not
create an authorization boundary, Companion RPC, observer, transcript
retrieval, persistence, or new server access. See the dated amendment in
[ADR-084](../../decisions/ADR-084.md).

Before implementation, verify the pinned TUI SDK exports/types and supported
native message shape. Exact Code Mode nested metadata availability remains
unverified; absent evidence must be displayed as unknown. No runtime launch or
dependency installation is authorized by Story/plan approval.

## Approval gates

1. **Story scope approved by the owner on 2026-11-27.**
2. **Repository Analysis approved by the owner.**
3. **ADR-084 TUI-local projection amendment accepted by the owner on
   2026-11-27.**
4. **Reconciled Implementation Plan approved by the owner on 2026-11-27
   (Gate 2).**
5. Owner explicitly authorizes any dependency installation, isolated host
   activation, configuration change, or runtime probe.
6. **Production implementation explicitly authorized by the owner on
   2026-11-27.**
7. The owner's runtime-qualification request authorized the isolated local
   profile validation recorded in the TUI checklist. Remote/SSH and Desktop
   runtime qualification require their own exact environment and evidence.
   Final Story acceptance remains a separate owner gate.

See the accompanying [Repository Analysis](repository-analysis.md),
[Implementation Plan](implementation-plan.md),
[Implementation Report](implementation-report.md),
[accepted ADR-084 amendment proposal](adr-084-amendment-proposal.md),
[Presentation Contract](presentation-contract.md),
[Desktop Checklist](desktop-checklist.md), [TUI Checklist](tui-checklist.md),
and [Remote Installation Notes](remote-installation.md). The plan is approved;
the checklists are not authorization to execute runtime validation.
