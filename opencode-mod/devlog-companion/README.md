# DevLog Companion for OpenCode Desktop (visual milestone)

This local package exports only its experimental Desktop GUI. Its default
build presents an `IDLE` visual state; it does not call DevLog, register an RPC
endpoint, load the server observer, or read Context Trace. It does not depend on
or load the Claude Companion.

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
- Desktop must load this local plugin directory from the global
  `opencode.json(c)` of a dedicated development profile. Do not add it to the
  normal profile.
- The GUI is deliberately the only exported plugin part for this milestone.
  The server implementation remains in `src/server.js` for later work but is
  not loadable through this package export map.
