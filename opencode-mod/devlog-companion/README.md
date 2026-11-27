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
