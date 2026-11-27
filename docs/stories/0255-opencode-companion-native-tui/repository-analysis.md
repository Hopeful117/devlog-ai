# Repository Analysis — Story 0255 Native TUI Companion

## Workflow status

**Repository Analysis approved by the owner on 2026-11-27 (Human Approval
Gate 1 satisfied).** The owner accepted the TUI-local ADR-084 amendment and
approved the reconciled Story and Implementation Plan on 2026-11-27. Production
implementation remains unauthorized pending its separate explicit gate.

## Status and evidence boundary

Read-only preparation at `origin/main` revision
`03d22b118f1c6c747b99d90283b0c1ca0539cbf2`. This analysis does not authorize
implementation, dependency installation, runtime activation, configuration
changes, or a compatibility claim.

The Story Agent preflight for preparation completed (`aiTaskId`/snapshot
`be65962e-ef11-4271-8a90-edc522bc601f`) but was `STALE` /
`PARTIALLY_FRESH`, `resolutionState: UNKNOWN`, truncated, with LOW confidence.
After the owner approved the Story, a second preflight completed as
`7f0e70a6-b5ca-4a86-acf2-5ba28f05be99`; it had the same freshness/truncation
limitations and could not retrieve the untracked Story 0255 or this analysis
from the current source. It therefore contributes no independent confirmation
of their scope or status. Both results flag missing TUI-specific evidence;
their suggestions are not accepted project decisions. Current local repository
artifacts remain authoritative. Context7 documentation retrieval was
unavailable because the monthly quota was exceeded; official OpenCode docs were
consulted directly.

**Lifecycle registration limitation:** repository `TOOLS.md` maps the slug
`devlog-ai` but does not provide the base URL or project UUID required by the
Engineering Story lifecycle adapter. No `register` operation was attempted and
no DevLog Story UUID is established. `DEVLOG_LIFECYCLE_ERROR`: lifecycle
registration cannot be performed from the configured repository mapping;
workflow continues without DevLog lifecycle synchronization.

## Repository and product state

- `origin/main` includes merged PR #253. The preparation worktree was clean at
  `7ecc1680`, 26 commits behind `origin/main`, and was moved to the exact
  `origin/main` commit without discarding changes.
- No `docs/stories/0255-*` artifact existed at this revision.
- Story 0248's isolated server/RPC and GUI prototype was delivered with runtime
  gaps explicitly retained. Its report records direct and Code Mode fixture
  dispatch/hook observations for a bounded CLI/SDK tuple, but leaves Desktop
  rendering, runtime concurrency, full interruption outcomes, positive
  caller-to-session authorization, and other qualifications unresolved.
- The current `opencode-mod/devlog-companion/package.json` exports only
  `./gui-experimental`; it pins `@opencode/plugin` 2.4.0. Its files include the
  GUI and pure-JS presence/context modules. The server, RPC definition, and
  earlier server projection code exist in the repository but are not reachable
  through the current package exports.
- Story 0253 and `gui-presence.js` implement bounded transcript-derived Now and
  History semantics. The projection reads allowlisted names, host outcome,
  explicit task status, and host-provided IDs/timing only. Code Mode is grouped
  under the outer execution and carries no invented nested outcome.
- Story 0254 adds a GUI-local Context view over exact direct
  `get_engineering_context` results. The owner deferred Code Mode nested
  context inspection because exact nested identity/result provenance is not
  exposed. Backend Context Trace is also deferred. Neither deferral is reopened
  by this proposal.
- Existing Desktop code is bound to `@opencode/plugin/gui`, SolidJS, a
  `MountedSession`, and GUI-native transcript/event APIs. It is not directly
  portable as a TUI renderer.
- The installed/inventoried versions in Story 0248 are historical candidate
  evidence, not this revision's verified runtime tuple: CLI 2.4.1, Desktop
  2.4.1, service CLI 2.4.1, plugin SDK 2.4.0, Node 26.10.0. Story 0248 later
  records a Desktop 2.4.2 launch failure before plugin validation and owner
  reported visibility in the normal profile; this is not a complete runtime
  certification.

## OpenCode native TUI evidence

Official documentation consulted on 2026-11-27:

- [Building TUI plugins](https://opencode.ai/docs/build/plugins/cli/)
- [Plugin overview](https://opencode.ai/docs/build/plugins/)
- [CLI plugins](https://opencode.ai/docs/cli/plugins/)
- [CLI](https://opencode.ai/docs/cli/)

The current docs describe:

- A `./tui` plugin package entry using `@opencode/plugin/tui`, native slots,
  session panels, routes, toasts, dialogs, namespaced command/keymap layers,
  themes, and cleanup/unregistration.
- TUI context data for session, message, location, MCP and related state,
  plus a generated client that can call the connected server.
- TUI event subscriptions whose handles can be disposed; session state APIs
  include message lookup/list/sync and session/location identity.
- Server-bearing plugin packages can supply TUI parts. The CLI gets the active
  plugin list from a connected server; TUI-only packages can instead load via
  `cli.json` and remain active while the CLI connects to a remote server.
- Documentation warns that host renderable internals are not plugin API and
  that OpenTUI 0.6 introduced relevant API changes. OpenTUI peers are needed
  for JSX plugins.

This is strong evidence that a native TUI adapter is a plausible direction in
the documented product, but it does **not** establish that the repository's
pinned 2.4.0 SDK/runtime exposes these current APIs, that the docs map to the
historical CLI 2.4.x tuple, or that the user's installed CLI has a matching
TUI plugin loader. No local host source or installed TUI SDK was available in
this worktree, and no runtime experiment was authorized.

### Prerequisite confirmation from the collected evidence

| Prerequisite | Existing evidence | Result and limit |
|---|---|---|
| Current session identity | The pinned `@opencode/plugin@2.4.0` declaration exposes the `session.panel` slot with `sessionID`, and `ui.panel.open` for the current session. | **Confirmed at SDK declaration level.** Host loading/runtime behavior remains untested. |
| Native session messages and tool activity | The pinned TUI `Data.session.message.list(sessionID)` returns `SessionMessageInfo[]`; the pinned `@opencode/client@2.4.0` declaration includes assistant messages with tool parts and typed tool state. | **Confirmed at SDK declaration level without extra retrieval.** The API exposes no separate `message.loading` indicator; empty data is shown as unknown rather than idle. |
| Code Mode execution metadata | The TUI SDK permits reading native tool metadata, but nested `metadata.toolCalls` is generic JSON rather than a guaranteed schema. Story 0253 says nested identity and result correlation are not established. | **Partial/unknown.** Recognize only explicit allowlisted names in already-loaded metadata. Never infer nested outcome from outer `execute`; absent metadata remains unknown. |
| Session switching and disposal | Pinned SDK declares a session-panel `sessionID`, typed `session.tool.*` and `session.execution.*` events, unsubscribe callbacks, and plugin cleanup return. | **Confirmed at declaration level.** Runtime event order, reconnection and loader cleanup remain untested. |
| Local and remote TUI | Official CLI plugin docs describe TUI-only `cli.json` plugins running locally while connected to a remote server. | **Documented, not runtime-certified.** SSH-hosted TUI is a distinct installation topology. |
| Plugin trust and authorization | TUI plugin is local CLI code and can read host-provided TUI data. No evidence establishes sandboxing or per-session access control. | **Assumption only:** deliberately enabled plugin code is trusted by the local CLI operator. Session filtering is presentation scope, not authorization. Normal remote-server auth is unchanged. No new authorization is granted. |

**Feasibility conclusion:** a useful native TUI-local projection is
architecturally feasible from the pinned SDK's declared TUI cache and native
presentation APIs, without adding a Companion server observer, RPC, DevLog
call, or transcript retrieval. This is static package evidence, **not** host
runtime validation. Exact Code Mode nested-name metadata remains unknown and
must be presented as such when absent.

## Reuse and architectural fit

**Supported interpretation:** Reuse should first target pure semantics and
fixtures, not UI or state frameworks:

- Existing allowlisted tool-name normalization, task-status decoding, bounded
  transcript projection, neutral unknown state, and sanitization patterns are
  candidates for direct reuse after examining their current contract/tests.
- `gui-presence.js` currently includes host-specific GUI transcript shapes and
  GUI event names; do not import it wholesale into TUI until equivalent TUI
  shapes and semantics are confirmed.
- `presentation.js` currently contains visual colors and animation descriptors;
  it is not a terminal design system. TUI should map states to text/glyphs and
  semantic theme tokens in a native adapter.
- `gui.tsx`, its `MountedSession` dependencies, panels, and GUI APIs must not
  leak into a shared domain module.
- No shared executable Companion Core is required by ADR-084. Shared fixtures
  and contracts are lower risk until reuse is demonstrated. Avoid adding an
  abstraction solely to make the TUI seem uniform with Desktop.

### Architecture direction and governance

ADR-084 assigns the OpenCode server adapter ownership of the Companion
projection. Its 2026-11-27 amendment permits an in-memory native-data
projection specifically in the currently routed Desktop GUI; it does not say
that a TUI-local session cache has the same allowance. The base ADR says
Desktop and TUI consume the same scoped server projection where applicable.
The existing server/RPC projection is not exported by the current package, and
Story 0248 did not establish positive caller-to-session authorization.

The owner selected TUI-local projection. This required a narrow amendment
because ADR-084 originally had a local-cache
exception for Desktop GUI only and otherwise assigns the shared projection to
the OpenCode server adapter. A proposed amendment is recorded in
[adr-084-amendment-proposal.md](adr-084-amendment-proposal.md), accepted by
the owner and recorded in canonical ADR-084.

The proposal uses only already-loaded native messages for the currently
routed TUI session. It does not infer authorization from route/session IDs,
and it does not activate the existing prototype RPC. Acceptance of the
amendment and approval of the reconciled Implementation Plan remain separate
from implementation authorization and runtime validation.

The Story was reconciled to the accepted TUI-local projection and separately
approved by the owner on 2026-11-27.

## Topologies and lifecycle

Treat these as different deployment paths, not synonyms:

1. **Local CLI/TUI + local OpenCode server:** TUI and server are on one host.
   The plugin discovery/loading source, session cache, and MCP configuration
   still need explicit validation.
2. **Local CLI/TUI + authenticated remote server:** current CLI-plugin docs say
   TUI-only plugins configured in `cli.json` remain local and active while the
   CLI connects remotely; `context.client` targets the connected server. This
   is a promising topology, not verified evidence for the pinned runtime.
3. **TUI itself running over SSH on the remote host:** install the TUI package
   in that host's CLI configuration/environment. It executes in that terminal
   process; remote home/config, terminal support, and server connection must
   be tested there. Do not read remote files from the local machine as fallback.
4. **Desktop GUI:** separate, existing `gui-experimental` artifact and
   configuration path. A TUI-only local package may not be loaded by Desktop.
   Keep the current GUI export/behavior stable and test it separately.

Server identity, location/directory, project/workspace and session must be part
of view scope where relevant. Changing any selector invalidates the old view.
Session IDs and paths are selectors, not authorization. A local plugin must not
claim it authorizes access merely because the server client is connected.

## Risks and blockers

| Finding | Classification | Consequence |
|---|---|---|
| Current official TUI docs exist, but exact SDK/runtime correspondence to the pinned package is unknown. | FACT + UNKNOWN | Pin and inspect a supported host/API tuple before implementation. |
| Desktop 2.4.2 and GUI SDK 2.4.0 remain an unqualified pairing; Story 0254 Desktop validation/acceptance is separate. | FACT | Do not use TUI success to claim Desktop or Story 0254 acceptance. |
| Story 0248 observed the V2 Preflight Gate fail to block a tested `agent:"explore"` missing-handoff case. | FACT, tuple-specific | Keep Gate untouched; do not claim governed preflight compatibility; refer any fix to separate work. |
| Exact pinned SDK availability, Code Mode nested metadata shape, event delivery, and local/remote runtime behavior remain unverified. | UNKNOWN | Verify statically against the target SDK; represent missing nested evidence as unknown; no runtime claim without separate authorization. |
| ADR-084 requires an amendment before TUI-local projection because the accepted local-cache exception currently names Desktop GUI only. | FACT + OWNER DIRECTION | Review and accept/reject the separate proposed amendment before implementation. |
| Existing Companion server RPC is not exported by the current package and positive caller-to-session authorization proof was incomplete. | FACT | Do not use this RPC; the owner-selected direction avoids server/RPC infrastructure. |
| Shared module/package layout between GUI and TUI may load extra peers/code in Desktop. | UNKNOWN | Validate package contents and independent entrypoints before choosing packaging. |
| Context7 monthly quota prevented its docs integration from being used. | FACT | Official docs were fetched directly; no code-specific current docs claim relies on Context7. |

## Recommendation

Proceed with the owner-preferred local projection only after the ADR amendment
and reconciled plan are approved. Keep the first UI compact: native presence,
observed activity state and latest recognized activity, plus only the
Story-approved bounded History (no broader history browser or Context view).
Preserve unknown/unavailable Code Mode outcomes. Keep Desktop, Claude, backend,
Gate and deferred Context behavior unchanged. Do not imply target-runtime
support before version-specific evidence exists.
