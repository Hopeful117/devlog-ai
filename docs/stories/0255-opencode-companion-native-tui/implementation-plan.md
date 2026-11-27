# Implementation Plan — Story 0255

**Approved by the owner on 2026-11-27 (Human Approval Gate 2).** The owner
also accepted the TUI-local projection amendment to ADR-084 and reconciliation
of the Story. Production implementation was separately authorized by the
owner on 2026-11-27. This plan does not authorize dependency installation,
plugin activation, configuration changes, or runtime probes.

## Preconditions and gates

1. **Story 0255 scope approved by the owner on 2026-11-27.**
2. **Repository Analysis approved by the owner.**
3. **TUI-local projection amendment to ADR-084 accepted by the owner on
   2026-11-27** and recorded in the canonical ADR.
4. **This reconciled Implementation Plan approved by the owner on 2026-11-27
   (Gate 2).**
5. **Story 0255 reconciled to the accepted architecture and approved by the
   owner on 2026-11-27.**
6. **Static SDK check completed before implementation:** inspected the exact
   `@opencode/plugin@2.4.0`, `@opencode/client@2.4.0`, and
   `@opentui/solid@0.9.4` package declarations. Required plugin, session cache,
   slot, command, and cleanup APIs are declared. This does not establish the
   installed CLI/TUI build or runtime behavior.
7. Any dependency installation, TUI activation, configuration edit, Desktop
   launch/reload, or runtime experiment needs separate explicit authorization.
   This task authorizes none of those actions.
8. Story 0254's deferred Context Inspector/Code Mode context and backend
   Context Trace stay out of scope.
   Do not amend the Preflight Gate in this Story.

## Proposed change boundary

Subject to the gates above, expected files are limited to:

- `opencode-mod/devlog-companion/package.json` and lockfile, only if the
  approved distribution exposes a TUI entrypoint without breaking Desktop's
  GUI-only contribution. Preferred candidate: add an independent `./tui`
  export while preserving `./gui-experimental`; prove the pinned loader
  behavior before adopting this.
- A native TUI adapter entrypoint, expected to be `src/tui.tsx` if supported by
  the pinned SDK.
- Existing pure functions in `src/gui-presence.js` (`deriveGuiPresence`,
  `deriveGuiHistory`) and their fixtures, reusing them directly if the pinned
  TUI message shape is compatible. Keep native TUI rendering and subscriptions
  separate from `src/gui.tsx`. Any necessary shared-function change must retain
  Desktop behavior and its regression tests.
- Focused TUI adapter tests and narrowly updated package documentation.
- `cli.json` installation guidance for a TUI-only/local CLI contribution,
  subject to confirming the target release's discovery path. No active
  configuration file is changed by implementation without separate approval.

No server/RPC implementation, Claude code, backend/Core, MCP/REST contract,
Preflight Gate, global configuration, Desktop UI code, or generated GUI bundle
is in scope unless the plan is materially revised and re-approved.

## Proposed work sequence

### 1. Freeze compatibility and packaging baseline

- Record clean/dirty status and exact base revision; preserve all existing
  worktrees and user configuration.
- Record current GUI package exports, allowlist, tests, behavior and package
  contents as a non-regression baseline.
- Prefer adding a distinct `./tui` entry next to `./gui-experimental`, with no
  server/RPC export. Confirm that Desktop continues to load the GUI entry and
  the TUI loads only the TUI entry. If the host cannot isolate them, stop and
  ask the owner before proposing a separate package.
- Capture the exact candidate host tuple, distinguishing CLI/TUI, server,
  Desktop/sidecar, SDK, OpenTUI, Solid, runtime, and OS. Package declarations
  alone do not establish compatibility.

### 2. Validate the native TUI surface before implementation

- Reuse the collected current TUI documentation; do not restart a broad SDK
  investigation. Read only the pinned package metadata/types needed to confirm
  TUI plugin discovery, `setup`, session panel/route identity, message cache,
  event cleanup, and `cli.json` loading. Record each as supported, unsupported,
  experimental, or unknown for the exact version.
- The documented interfaces support `session.panel` session identity,
  `context.data.session.message.list(sessionID)`, and disposable typed event
  subscriptions. Remote TUI-only loading is documented via local `cli.json`
  while connected to a remote server. Treat these as API-documentation evidence,
  not runtime validation for the pinned 2.4.0 package.
- Runtime confirmation of discovery/loading requires separate owner
  authorization and an isolated profile.
- Exact TUI message-part compatibility and Code Mode `metadata.toolCalls`
  availability remain unknown. If static types or permitted fixture evidence
  cannot establish the supported native shape, keep nested Code Mode DevLog
  activity unknown and report that limitation; do not probe by launching TUI.

### 3. Apply the approved TUI-local projection decision (mandatory before code)

- Production implementation authorization was explicitly granted by the owner
  on 2026-11-27. It does not authorize dependency installation, plugin
  activation, configuration changes, or runtime probes.
- In the TUI adapter, read only the current panel/session's already-loaded
  native cache. Do not call `message.sync`, fetch transcript, enumerate
  sessions, inspect remote files, or make additional DevLog/MCP/REST requests.
- The TUI-local scope is presentational, not a security boundary. Treat the
  enabled plugin as trusted local code without claiming sandboxing or
  per-session authorization. Normal server authentication remains unchanged.
- Do not add Companion RPC, server observer, persistence, or backend
  authorization changes.

### 4. Implement minimal TUI adapter

- Add a concise terminal-native status contribution and namespaced command
  opening a current-session panel or route; append to host slots rather than
  replace built-ins. Keep rendering separate from Desktop.
- Show compact DevLog presence, observed platform activity state, latest
  recognized activity and honest unknown/unavailable states. Preserve the
  already approved bounded History view, but do not add a broader history
  browser. No Context Inspector.
- Reuse the existing pure `deriveGuiPresence` / `deriveGuiHistory` functions
  directly if type/fixture checks confirm identical message shape and behavior.
  Do not extract a general-purpose cross-platform framework. Do not alter these
  functions unless required; any change must preserve Desktop behavior and run
  all existing Desktop projection tests.
- Code Mode remains the primary workflow. Use only native metadata actually
  present in loaded message parts. The outer `execute` result proves only the
  host execution result; missing nested names/status/results remain unknown.
- Use terminal-readable text and semantic theme tokens. Do not port SVG,
  animation, or desktop-specific CSS.
- Keep state ephemeral and bounded; close/reset on session, server, location,
  project/workspace switch and plugin teardown. Remove every subscription and
  command/slot registration.
- Fail observationally: catch/report only safe plugin diagnostics; never alter
  tool input/output, authorization, or host control flow.

### 5. Deterministic validation

- Unit tests: run existing `gui-presence.js` tests unchanged; add TUI adapter
  fixtures for current session, missing/loading cache, empty inspected window,
  direct DevLog tool outcomes, explicit/conflicting task statuses, Code Mode
  outer execution with and without nested names, unknown nested result,
  stale/late update, scope switch, sanitization and caps.
- TUI lifecycle tests: command/slot registration, panel close/reopen, session
  switch, plugin cleanup and listener disposal. No cross-session data.
- Package checks: explicit `./gui-experimental` + `./tui` exports, package
  contents, no server/RPC part, GUI dependency/behavior preserved, and
  no surprise SDK/OpenTUI dependencies in Desktop.
- Proposed validation commands after implementation and dependencies are
  already available or separately approved:
  `npm test`, `npm run typecheck`, `npm run build`, `npm run pack:dry-run`,
  and `git diff --check` in `opencode-mod/devlog-companion/`.
- Do not regenerate or commit the GUI bundle unless the approved change
  requires it; inspect any generated diff. Tests/builds do not certify host
  runtime behavior.
- `git diff --check` and full diff review.

### 6. Runtime validation (not authorized by this plan)

No TUI launch/reload, active config change, remote session, Desktop launch, or
runtime harness is included in this plan. If runtime validation is later
requested, obtain a separate owner authorization and define a marker-owned
disposable profile, exact tuple, sanitized evidence, stop criteria and cleanup
before running it. Test local and remote TUI as separate topologies.

## Stop conditions

- Pinned host lacks the documented API required for the minimal feature.
- The current plugin loading model cannot keep Desktop and TUI behavior
  independent.
- Dependency installation, runtime activation, or configuration changes would
  be required without their separate explicit authorization.
- The pinned SDK lacks the required TUI APIs, or loaded-message/Code Mode
  metadata cannot support truthful projection.
- A new server/RPC, backend, MCP/REST, auth, Gate, Claude, or global config
  change appears necessary.
- Any observation would need fabricated Code Mode identity/outcome or would
  promote host success into DevLog task success.
- Any probe would need normal user configuration, an external provider, or a
  real DevLog call without additional authorization.

## Completion evidence

Record source revision, approved scope, version matrix, package/dependency
provenance, test commands/results, package contents, runtime topology and
sanitized results, cleanup verification, known gaps, Desktop non-regression
result (or `NOT EVALUATED`), and human acceptance separately. Passing tests
alone do not establish host support.

## Current workflow state

The owner approved the Story, Repository Analysis, TUI-local ADR-084 amendment,
reconciled Story, and this Implementation Plan, then explicitly authorized
production implementation on 2026-11-27. Dependency installation, runtime
activation, and configuration changes were separate gates. The owner separately
authorized global CLI activation and a brief TUI launch, then requested
completion of package and runtime validation. Exact locked dependencies were
installed and `@opentui/solid@0.9.4` was declared as a development dependency
to satisfy typechecking; full package checks pass. The runtime request was
validated in an isolated local profile: plugin loading and status-slot rendering
pass. Panel/history interaction and remote/SSH topology remain incomplete; no
remote server target was supplied.
