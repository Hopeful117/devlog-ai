# Companion Compatibility Matrix — Story 0256

Evidence is limited to the revisions and observations listed here. `Implemented`
describes source behavior, not platform certification. Owner reports are not
independent observations. Unknown versions/topologies remain unqualified.

| Platform/topology | Implemented functionality | Automated validation | Independently observed runtime | Owner-reported behavior | Not evaluated / deferred | Tested versions |
|---|---|---|---|---|---|---|
| Claude Companion | Native Claude plugin: Now/presence, Context Trace and History views, install script. | No dedicated Claude Companion tests or install regression suite found. | No current independent runtime qualification established by the checked Story reports. | No platform behavior claimed by Stories 0252–0255. | Loaded version; current interaction behavior; regression baseline. Full parity is not claimed. | Source manifest `0.2.0`; local installed-plugin inventory records cache `0.1.0`; actual loaded version unknown. |
| OpenCode Desktop | `./gui-experimental`: Now, bounded History, direct-result Context Inspector. Nested Code Mode Context and backend Context Trace are deferred. | GUI projection/selection/package tests; Stories 0254/0255 retained a 68-test passing baseline. Story 0256 adds one package-contract test (69 total); current test/build/typecheck/package dry-run passes. | No independent Desktop run established for 0254/0255; 0252/0253 checklists contain owner observations. | Story 0252 reports owner-observed states, close/reopen and route return; 0253 reports Now/History and one Code Mode row plus same-route reopen. | Active Desktop plugin path remains a Story worktree; exact Desktop/sidecar tuple and current visual/runtime behavior unknown. | Package source `0.1.1`; SDK `@opencode/plugin@2.4.0`; Desktop/sidecar tuple unknown. |
| OpenCode TUI, local CLI + same-host server | `./tui`: status slot, namespaced panel command, bounded current-session History. | Three TUI projection tests in the original 68-test baseline; current 69-test package suite, typecheck/build/pack dry-run pass. | A prior isolated source/plugin smoke run with CLI/TUI and server `2.4.2` loaded the local plugin and rendered the `DevLog:` status slot for an empty synthetic session; shutdown cleanup was observed. This was not a Story 0256 stable-artifact installation test. | Owner separately reported Companion functioning; panel interaction not independently confirmed by the automated harness. | Stable-artifact host resolution remains distinct and must be verified; panel/history rendering and route/session switching remain incomplete. Stable artifact was not activated by Story 0256. | Prior runtime CLI/TUI/server `2.4.2`; package source `0.1.1`; SDK `2.4.0`; `@opentui/solid@0.9.4`; `solid-js@1.9.17`; Linux PTY, `xterm-256color`. |
| OpenCode TUI process hosted on remote machine through SSH | The adapter can be installed locally to the process/config host in principle; this is not a compatibility claim. | No SSH-hosted install or test. | None. | None. | Package provisioning, CLI version, terminal, auth and cleanup on SSH host not evaluated. | Unknown. |
| Local OpenCode TUI connected to remote OpenCode server | Native CLI-local plugin mechanism is documented; projection is intended to use current routed native data only. | No remote-server-specific tests. | None. | None. | Plugin discovery source, session data origin/scope, reconnect behavior, duplicate server/client registration and authorization boundary not runtime-evaluated. | Unknown. |

## Package installation evidence

| Evidence | Result |
|---|---|
| Source package version after Story 0256 changes | `0.1.1` |
| Reviewed package source commit | `30ca3577302bfa0c41ec663a812dad5ed03b8d40` |
| Final release artifact identity | SHA-256 `257b55a142c647f70f142e71a3c8610c3490f2f841a3e9d9ee9724e5c6caf974`; npm shasum `5daba09d33a77c03d0c41776995e5fb807acbde0` |
| Artifact identity convention | Package version plus full source commit SHA in the user-owned immutable directory |
| Packaged lock | `npm-shrinkwrap.json` is included by the package whitelist and asserted by the package test |
| Dependency contract | `@opencode/plugin@2.4.0` runtime dependency; `@opentui/solid ^0.9.4` and `solid-js ^1.9.12` peer dependencies |
| Stable user-owned installation | **PASS** at `~/.local/share/opencode/plugins/devlog-companion/0.1.1-30ca3577302bfa0c41ec663a812dad5ed03b8d40`; all 11 archived files match the release tarball |
| Installed dependency resolution | **PASS** — locked `npm ci --omit=dev --ignore-scripts`; 391 packages; `@opencode/plugin@2.4.0`, `@opentui/solid@0.9.4`, `solid-js@1.9.17`; all resolved paths are under the stable installation |
| Dependency audit | **26 moderate, 0 high/critical** in `npm audit --omit=dev`; no automatic fixes applied; reachability/impact not established; owner risk review required before activation |
| GUI/TUI static exports | **PASS** — GUI resolves to `dist/gui-experimental.cjs`; TUI resolves to `src/tui.tsx`; SDK GUI/TUI and OpenTUI JSX/ Solid modules resolve under stable path; no server entry is packaged |
| Node CommonJS SDK GUI resolution | **NOT RESOLVED** — `@opencode/plugin/gui` has an ESM import condition and no Node `require` condition |
| OpenCode host loading | `NOT EVALUATED` — OpenCode was not launched; actual Desktop CJS/SDK loading and TUI host activation are not inferred from static module resolution |
| Active configuration migration or OpenCode activation | `NOT EVALUATED` — not authorized/performed |

## Cross-platform boundaries

- Claude, Desktop, and TUI remain independent native integrations; this Story
  does not extract a shared runtime or assert feature parity.
- A successful package build or test does not establish host runtime
  compatibility.
- Code Mode nested Context Inspector and backend Context Trace retrieval stay
  deferred.
- Stories 0249 and 0250 remain deferred.
- Desktop/TUI local projection is presentation scoping, not authorization.

## Existing Claude installation procedure (unchanged)

The repository's `claude-mod/devlog-companion/install.sh` copies the source
`.claude-plugin`, `hooks`, and `types` directories into
`~/.claude/plugins/marketplaces/devlog/plugins/devlog-companion` and instructs
the owner to restart Claude Desktop. Re-running it overlays/copies those
directories; the script does not implement a versioned install, backup,
rollback, or removal operation. No Claude install, update, removal, setting, or
source change was performed for Story 0256. Do not infer the currently loaded
runtime version from the script or repository manifest: the inspected installed
record says `0.1.0`, the source manifest says `0.2.0`, and loaded runtime
identity remains unknown.
