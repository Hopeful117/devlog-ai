# DevLog Companion for OpenCode Desktop

This local package exports only its experimental Desktop GUI. Its default
build derives bounded in-memory presence and recent DevLog activity from the
currently routed session's already-loaded OpenCode GUI data. Now remains the
default view; History is reconstructed from the same loaded transcript rather
than appended from event notifications. It adds no Companion RPC, server
observer, transcript request, DevLog request, or Context Trace read. Route
filtering is presentation scoping, not server authorization or isolation from
another trusted GUI plugin. It does not depend on or load the Claude Companion.

## Development

- Node.js 26.10.0 is the inventoried development runtime.
- `@opencode/plugin` is pinned to 2.4.0; the installed Desktop/sidecar is 2.4.2.
  The GUI API is experimental, and this version pairing still requires Desktop
  visual validation.
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
- Desktop must load this local plugin directory from the global
  `opencode.json(c)` of a dedicated development profile. Do not add it to the
  normal profile.
- The GUI is deliberately the only exported plugin part for this milestone.
  The server implementation remains in `src/server.js` for later work but is
  not loadable through this package export map.
