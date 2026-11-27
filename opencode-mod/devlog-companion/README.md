# DevLog Companion for OpenCode

This local package exports independent OpenCode Desktop GUI and native TUI
contributions. The GUI remains experimental and keeps its existing build and
behavior. The TUI entry reads only the current session's already-loaded native
message cache; it does not sync/fetch transcripts, call DevLog, use Companion
RPC, add a server observer, or read Context Trace. TUI session filtering is
presentation scoping, not server authorization. Neither contribution depends
on or loads the Claude Companion.

The TUI presents compact Now/activity state and a bounded History panel from
the evidence already available in the cache. Empty or unavailable data remains
unknown; host completion does not establish DevLog task success. Raw prompts,
tool arguments/results, credentials, and raw error payloads are not retained by
the TUI projection.

## Stable local installation

This package is private and is not published to a registry. Install a reviewed
package artifact in a user-owned directory, not from a source checkout or Story
worktree. Keep each version immutable so configuration and rollback identify an
exact copy:

```text
${XDG_DATA_HOME:-~/.local/share}/opencode/plugins/devlog-companion/
  <package-version>-<full-source-commit-sha>/
```

For a reviewed clean source commit:

1. From `opencode-mod/devlog-companion`, run `npm run pack:dry-run`, then
   `npm pack --ignore-scripts`. Record the source commit and package version.
2. Extract the resulting `devlog-companion-<version>.tgz` into a new empty
   directory named with that version and full commit SHA. Do not overwrite an
   existing version directory.
3. In the extracted package directory, run
   `npm ci --omit=dev --ignore-scripts`. The shipped `npm-shrinkwrap.json`
   locks the package dependency graph. `@opencode/plugin` is an exact runtime
   dependency; `@opentui/solid` (`^0.9.4`) and `solid-js` (`^1.9.12`) are host
   peer dependencies. Do not bundle a second OpenTUI or Solid runtime.
4. Before configuring OpenCode, verify the archive's package version/exports,
   `npm ls --omit=dev`, and module resolution from this installed path. If any
   check fails, do not activate the package.

The package exports two separate native entrypoints:

- `devlog-companion/gui-experimental` — experimental Desktop GUI part.
- `devlog-companion/tui` — native CLI/TUI TSX part.

Add the installed absolute package directory as a local plugin in the dedicated
OpenCode profile's `opencode.json`/`opencode.jsonc` `plugin` list. A local TUI
may also have an explicit client-local entry in `cli.json`; the CLI can receive
TUI parts from its connected server, while `cli.json` remains a local loading
mechanism when using a remote server. This repository has not verified actual
duplicate-loading behavior or qualified the active topology. During migration,
preserve the existing `cli.json` entry and point it to the same stable artifact.
Do not remove either registration unless owner-authorized runtime evidence
establishes both the active topology and an actual duplicate contribution.

The configured path must be the exact versioned directory, not a floating
reference or symlink. Preserve every unrelated plugin entry and setting. Back
up only the configuration files that will change, verify the intended diff
before activation, and retain prior package directories and backups until the
owner has validated the replacement and explicitly authorized cleanup.

### Explicit update and rollback

For an update, repeat the reviewed-commit packaging and staging steps into a new
version+commit directory, verify it before changing configuration, then change
only the Companion path after backing up the affected file. Never update in
place or automatically at startup. For rollback, restore only the previous
Companion path (or the saved config if no unrelated edits have occurred), verify
unrelated entries remain intact, and retain both artifacts. OpenCode launch or
reload, active configuration edits, and removal of old artifacts require the
owner's explicit authorization. This guide does not change the active profile.

### Removal

Removal is not automatic. After explicit owner authorization, back up only the
configuration file(s) to be edited, remove only the Companion plugin reference
from the appropriate `plugin` list, and verify every unrelated setting and
plugin entry remains unchanged. Account for the active topology: the server
configuration may supply the TUI part on same-host connections, while a
client-local `cli.json` entry may also be active. Preserve existing registrations
unless runtime evidence from the actual topology demonstrates duplicate loading
and the owner authorizes deduplication. Apply removal only to the relevant
source(s), then reload/restart only after separate authorization. Keep the installed version directory and
backup until the owner confirms no active configuration references it and
explicitly authorizes deleting that exact version directory. Do not remove
source worktrees or the independent Claude Companion as part of OpenCode
removal.

Do not put credentials in plugin configuration or run a DevLog operation just
to validate installation. In a repository checkout, use Story 0256's
`docs/stories/0256-devlog-companion-stabilization-installation/everyday-workflow-checklist.md`
with normal existing activity; mark unavailable checks `NOT EVALUATED`. The
evidence-qualified compatibility matrix is in the same Story directory. These
Story documents are not included in the package archive.

## Development

- Node.js 26.10.0 is the inventoried development runtime.
- `@opencode/plugin` is pinned to 2.4.0. The TUI API declarations in that exact
  package expose the native `Plugin.define` entry, session panel slot, keymap
  command layer, session message cache and typed event subscriptions. This
  establishes API-level compatibility only, not host runtime validation.
- The TUI export is `devlog-companion/tui`, separate from the existing
  `devlog-companion/gui-experimental` export. The CLI `v2.4.2` loaded the TUI
  entry in an owner-authorized smoke run, and the owner confirmed that the
  Companion functions. The owner's exact topology/version tuple and the full
  lifecycle checklist are not recorded, so this is not a compatibility
  qualification. Do not add it to a normal profile without separate
  authorization.
- The GUI API remains experimental; its installed Desktop/sidecar pairing
  still requires separate visual validation.
- Run `npm run build`, `npm test`, `npm run typecheck`, and `npm run pack:dry-run`
  here. The tests use Node's built-in test runner. The normal build regenerates
  the production GUI bundle before the tests inspect it.
- `npm run build:gui:preview` creates a temporary local GUI bundle with
  selectable `IDLE`, `WORKING`, `READY`, `ERROR`, and `DISCONNECTED` states.
  Every preview is labeled simulated; it performs no DevLog, MCP, RPC, network,
  or server-observer activity. This command overwrites the local GUI bundle.
  Run `npm run build` afterward to restore the ordinary `IDLE` bundle before
  package validation. The package dry-run also runs the normal build first.
- Reduced-motion preferences suppress the nonessential SVG animations while
  preserving the visual state distinctions.
- `UNKNOWN` is rendered neutrally when the route/cache is not ready or the
  observed activity does not establish a business-task outcome. Direct tool
  completion alone does not show `READY`; Code Mode nested rows have no call ID
  and cannot be correlated to a nested business result. `DISCONNECTED` is not
  claimed without reliable evidence for that boundary.
- History is bounded to the last 20 recognized rows from at most 40 loaded
  messages, 80 parts per message, and 80 Code Mode rows per outer execution.
  Code Mode names are reconciled as a distinct-name union of available metadata
  and result rows, grouped under the outer execution, with at most eight names
  retained per row. Outer completion does not establish nested success. Host
  timestamps/durations are shown only when supplied by the native tool part.
  Conflicting explicit task statuses remain unknown; pending statuses are
  labeled last-reported. Unknown or missing outcomes remain explicit.
- History contains only allowlisted tool names, host outcome, explicit task
  status, native host call ID, and native timing when available. It does not
  retain or render raw prompts, inputs, outputs, credentials, or error payloads.
  An inspected transcript window with no recognized operations is distinct
  from a loading/unavailable transcript and does not claim that older,
  uninspected transcript content has no DevLog activity.
- TUI uses the same pure presence and History projections without importing the
  GUI renderer. The SDK's TUI cache exposes a synchronous message-list API but
  no separate loading flag; an empty list is therefore shown as an empty loaded
  window with `UNKNOWN` presence, never as idle.
- Context includes an in-panel picker for eligible direct
  `get_engineering_context` results already represented in the loaded History
  window. The History action and Context picker select the same native result.
  Direct MCP inspection is supported as a secondary capability. The owner
  primarily uses Code Mode; nested Code Mode context inspection is deferred
  because the examined native GUI interfaces do not expose the exact nested
  result with reliable identity and provenance. An outer execution result is
  not treated as an exact nested MCP result. The picker makes no
  transcript/backend request.
- Context is local inspection of an observed tool result, not a retrieved
  backend Context Trace. It is not full parity with Claude's Context Inspector.
- This is a local presentation feature, not a per-session security boundary.
  The adapter reads only the current route and keeps only a sanitized,
  in-memory projection; it does not enumerate sessions or retain raw tool data.
- Desktop must load the GUI contribution from the global
  `opencode.json(c)` of a dedicated development profile. Do not add it to the
  normal profile.
- The server implementation remains in `src/server.js` for later work and is
  not loadable through this package export map. The TUI contribution adds no
  server or RPC export.
