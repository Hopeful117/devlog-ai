# DevLog Companion for OpenCode (compatibility spike)

This package is an independent OpenCode V2 spike. It does not depend on or load
the Claude Companion, does not call DevLog, and does not read Context Trace.

## Development

- Node.js 26.10.0 is the inventoried development runtime.
- `@opencode/plugin` is pinned to 2.4.0. The locally declared SDK version is
  evidence for this implementation target, not proof of the SDK loaded by every
  OpenCode Desktop/sidecar combination.
- Run `npm ci`, `npm test`, `npm run build`, and `npm pack --dry-run` from this
  directory. The unit suite uses Node's built-in test runner.
- The GUI contribution is experimental. A successful build does not qualify
  plugin discovery, RPC authorization/isolation, rendering, or interaction.

The server stores only bounded, in-memory, sanitized call metadata. It does not
retain tool arguments, prompts, raw outputs, or error messages. `execute.before`
records an observed attempt only. Missing terminal events age into
`INCOMPLETE`; they never become success. Platform execution, MCP result and
DevLog task status remain separate facts.

The GUI requests only the currently routed session snapshot through that
session's OpenCode server. The server resolves the session and checks its
project, location, and workspace against the plugin instance before returning
data; mismatches fail closed. This is a location-consistency check, not
caller-to-session authorization. No cross-session event stream is broadcast.
OpenCode's caller authentication remains host-owned, and session authorization
must be independently demonstrated before making an isolation claim. If the
plugin instance has no explicit workspace ID, RPC snapshots fail closed.

## Installation and runtime

Do not install or load this package in a user OpenCode profile as part of the
implementation task. Runtime checks require separate explicit authorization.
The isolated runtime evidence and remaining limits are documented in
`docs/stories/0248-devlog-opencode-native-compatibility-spike/runtime-validation-protocol.md`
and `implementation-report.md`.
