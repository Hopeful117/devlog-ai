# Implementation Report — Story 0255

## Repository State

- Branch: detached worktree
- HEAD: `03d22b118f1c6c747b99d90283b0c1ca0539cbf2`
- Worktree: modified `docs/decisions/ADR-084.md`; Story 0255 artifacts and
  Companion package files are new/modified in this worktree. No unrelated
  worktree or user configuration was changed.

## Governance

- Governing ADR: ADR-084, including the owner-accepted TUI-local projection
  amendment dated 2026-11-27.
- Authorized Story: Story 0255 — OpenCode Companion — Native TUI.
- Owner approvals: Story, Repository Analysis, accepted ADR amendment, and
  Implementation Plan approved; production implementation explicitly
  authorized on 2026-11-27.
- The owner separately authorized activation through global `cli.json` and a
  brief TUI launch on 2026-11-27. The owner later requested completion of
  validation, authorizing installation of exact locked dependencies. Broader
  runtime qualification and final Story acceptance remain separate gates.
- DevLog preflight: Story Agent task/snapshot
  `ed78a5cc-65fd-4dd4-bd64-6ffed3030e70`; `STALE` /
  `PARTIALLY_FRESH`, `resolutionState: UNKNOWN`, truncated, LOW confidence.
  It reaffirmed generic ADR boundaries but did not retrieve the untracked 0255
  artifacts. Local accepted ADR/Story and repository source were authoritative.
- DevLog lifecycle: `DEVLOG_LIFECYCLE_ERROR` — repository mapping provides
  only the project slug, not the base URL/project UUID required by the
  repository lifecycle script. No lifecycle start operation was attempted;
  there is no DevLog lifecycle state claim.

## Files Changed

- `docs/decisions/ADR-084.md`: recorded the accepted bounded TUI-local
  projection amendment.
- `docs/stories/0255-opencode-companion-native-tui/story.md`: reconciled to
  the accepted TUI-local projection and recorded approvals.
- `docs/stories/0255-opencode-companion-native-tui/implementation-plan.md`:
  recorded Gate 2 and implementation authorization.
- `docs/stories/0255-opencode-companion-native-tui/repository-analysis.md`:
  updated with exact SDK declaration evidence and remaining unknowns.
- `docs/stories/0255-opencode-companion-native-tui/adr-084-amendment-proposal.md`:
  retained as accepted amendment provenance.
- `docs/stories/0255-opencode-companion-native-tui/remote-installation.md`:
  aligned remote notes with accepted local projection.
- `opencode-mod/devlog-companion/package.json`: added an independent `./tui`
  export, declared the pinned TUI JSX development dependency, and included its
  sources in package contents; preserved the GUI export.
- `opencode-mod/devlog-companion/package-lock.json`: locked
  `@opentui/solid@0.9.4` and its dependency graph for reproducible typechecking.
- `opencode-mod/devlog-companion/src/tui.tsx`: added a native TUI status
  contribution, session panel, namespaced palette/slash command, and scoped
  event cleanup; imports `Context` from the SDK subpath that exports it.
- `opencode-mod/devlog-companion/src/tui-projection.js`: added a small TUI view
  model reusing the existing pure presence and bounded History projections.
- `opencode-mod/devlog-companion/tests/tui-projection.test.js`: covered empty,
  unavailable, no-activity, direct-tool and Code Mode uncertainty states.
- `opencode-mod/devlog-companion/tests/gui-package.test.js`: checks independent
  exports and that the TUI source adds no Companion transport or GUI import.
- `opencode-mod/devlog-companion/tsconfig.json`: included the TUI entry in
  package typechecking.
- `opencode-mod/devlog-companion/README.md`: documented package separation,
  TUI behavior, and validation limitations.
- `/home/ludo/.config/opencode/cli.json` (outside the repository): added the
  authorized local package path to `plugins`; unrelated values were preserved.

## Implementation Summary

The TUI now has a compact current-session status line and a bounded History
panel opened with the namespaced `devlog-companion` command. It reads only
`context.data.session.message.list(sessionID)`, applies the existing pure
allowlisted projections, and removes event subscriptions and UI registrations
on disposal. It adds no RPC, observer, transcript sync, DevLog request,
credential access, persistence, or server/backend change.

Empty cache, unavailable data, and an inspected window without recognized
DevLog operations are rendered distinctly. The TUI API has no separate
message-loading indicator; empty data therefore keeps presence `UNKNOWN`.
Code Mode nested names are recognized only when explicitly present in native
metadata, and nested outcomes remain unknown.

## Architectural Decisions Preserved

- Java/Core, backend, MCP/REST, authorization, grounding, and Preflight Gate
  boundaries remain unchanged.
- Desktop and Claude adapters are not replaced or merged. The existing GUI
  export and generated GUI bundle were preserved.
- Session filtering is presentation scoping, not authorization.
- Host execution completion is not promoted to DevLog task success.

## Tests Added/Modified

- `tests/tui-projection.test.js`: projection states and outcome distinctions.
- `tests/gui-package.test.js`: export/package boundary and no-transport checks.
- Existing Desktop projection test files were not modified.

## Commands Executed

```bash
npm pack --pack-destination /tmp/opencode/story-0255 @opencode/plugin@2.4.0
npm pack --pack-destination /tmp/opencode/story-0255 @opencode/client@2.4.0
npm pack --pack-destination /tmp/opencode/story-0255 @opentui/solid@0.9.4
node --test tests/context-inspector.test.js tests/gui-package.test.js \
  tests/gui-presence.test.js tests/presentation.test.js tests/tui-projection.test.js
npm test
npm run typecheck
npm run build
npm run pack:dry-run
opencode --version
opencode service status
opencode api get /api/info
timeout --signal=INT --kill-after=3s 12s script -q -e \
  -c 'TERM=xterm-256color opencode --log-level debug' \
  /tmp/opencode/story-0255/tui-startup-2.typescript
git diff --check
```

Dependency installation/lock verification was run with:

```bash
npm ci --ignore-scripts --no-audit --no-fund
```

The pinned package tarballs were source-inspection inputs in `/tmp`.
`@opentui/solid@0.9.4` was added as an exact development dependency after
typecheck exposed the undeclared JSX runtime/type dependency. The final lockfile
was then reproducibly installed and verified with
`npm ci --ignore-scripts --no-audit --no-fund`. No install scripts were
executed.

## Test Results

- Full `npm test`: **68 passed, 0 failed**.
- Initial `npm run typecheck` exposed an incorrect `Context` import and missing
  `@opentui/solid` declaration. After correcting the SDK subpath import and
  adding exact dev dependency `@opentui/solid@0.9.4`, typecheck **passed**.
- `npm run build`: **passed**; GUI bundle was regenerated byte-identically.
- `npm run pack:dry-run`: **passed**, including build, tests, typecheck and
  package contents inspection; no tarball was created.
- `git diff --check`: passed.
- Exact `@opencode/plugin@2.4.0` and `@opencode/client@2.4.0` declarations were
  inspected from package tarballs. TUI API declaration compatibility is
  established. OpenCode CLI `v2.4.2` loaded the local TUI entry and its setup
  completed in startup logs; the owner then confirmed that the Companion
  functions in their TUI.
- Isolated runtime qualification used a marker-owned temporary XDG profile and
  project. A local server was started on an ephemeral loopback port; a
  synthetic empty session was created, and CLI/TUI `v2.4.2` connected to it.
  The terminal capture contained the rendered `DevLog:` status-slot marker.
  The isolated database contained zero session messages, confirming no prompt
  was submitted. No MCP/DevLog server was configured. The CLI and server were
  stopped, plugin cleanup was observed, and the temporary profile was removed.
- The harness did not confirm panel/history rendering or route-change behavior.
  The owner's earlier report that the Companion works remains owner-reported
  evidence, not an independent panel assertion.
- Desktop runtime was not run. A brief CLI TUI startup probe confirmed plugin
  setup in logs but did not verify visible panel interactions. After activation,
  the owner confirmed on 2026-11-27 that the TUI Companion functions. This is
  an owner-reported runtime smoke confirmation; the exact topology, version
  tuple, tested interactions, and full checklist were not specified.

## Quality Validation Outcome

| Check | Applicable | Executed | Outcome / evidence |
|---|---|---|---|
| Unit tests | Yes | Yes | Passed: 68 tests across context inspector, package boundary, GUI projection, presentation, and TUI projection suites. |
| TUI adapter/lifecycle integration | Yes | Partial | Isolated local CLI/TUI v2.4.2 rendered the `DevLog:` status slot for an empty synthetic session; process shutdown and plugin cleanup were observed. The panel/history rendering and route-change behavior were not independently confirmed by the harness. |
| Typecheck / compile | Yes | Yes | Passed after correcting the SDK `Context` import and adding pinned `@opentui/solid@0.9.4`. |
| Build | Yes | Yes | Passed; the generated GUI bundle remained byte-identical. |
| Package dry-run | Yes | Yes | Passed; package contents include separate GUI/TUI entrypoints and no server/RPC part. |
| Lint / format scripts | No | No | No lint or format script is defined in the Companion package. Markdown whitespace/link checks and `git diff --check` passed. |
| Static analysis / SonarQube | No | No | No applicable package/repository SonarQube project or static-analysis command was found. |
| Coverage gate | No | No | No coverage command or coverage threshold is configured for this package. |
| Runtime / representative TUI outcome | Yes | Partial | Isolated local topology, CLI/server v2.4.2, status-slot marker rendered. Owner also confirms Companion functionality. Remote topology and panel/history interaction remain unverified. |

**Validation sufficiency:** tests, typecheck, build, and package dry-run pass.
Incomplete adapter lifecycle/topology evidence still prevents full runtime
qualification or a compatibility claim.

## Known Limitations

- No independent runtime evidence for TUI event reactivity, local/remote
  topology, or plugin teardown. The startup probe and owner-reported
  confirmation are not a versioned compatibility qualification.
- Nested Code Mode metadata availability is not guaranteed by the SDK's
  generic metadata type.
- No Desktop runtime regression claim is made; existing Desktop projection
  sources/tests were preserved.

## Documentation Reconciliation

- Updated `opencode-mod/devlog-companion/README.md` because the package now
  exposes a native TUI entry, has a distinct CLI configuration/load path, and
  records the limited owner-confirmed runtime smoke result.
- Updated the canonical Story, implementation plan, repository analysis,
  installation notes, validation checklist, and this report to reflect the
  accepted local-projection architecture, activation, test evidence, and
  remaining limits.
- No backend, API, or server installation documentation changed because this
  implementation adds no server behavior or contract.

## Vault Outcome

- Vault consulted during Repository Analysis: **NO**.
- Outcome: **No vault action**.
- Rationale: this Story implements a repository-specific OpenCode adapter;
  accepted ADR/Story artifacts and pinned SDK declarations are the authoritative
  sources. No transverse principle or reusable cross-project guidance was
  identified that warranted a vault proposal.
- Provenance: current repository ADR-084, Story 0255, package sources, and
  exact-version SDK declarations inspected from package tarballs.

## Independent Review

- Engineering review: **PASS**. The reviewer initially raised a keymap cleanup
  concern; it was withdrawn after confirming the pinned SDK declares
  `keymap.layer()` returns `void` and guarantees removal on plugin deactivation.
  No material engineering findings remain.
- Security/privacy review: **PASS**. No material issue found in the bounded
  local projection, event scoping, sanitization, or package boundary.
- Both reviews noted incomplete runtime lifecycle/topology qualification;
  dependency-backed package validation has since passed.
- Review findings are recorded in [Code Review Report](code-review-report.md).

## Implementation-Level Decisions Made

- Exported `./tui` directly to `src/tui.tsx`; no TUI Vite build plugin exists in
  the pinned SDK export map. GUI remains exported from its existing generated
  bundle.
- Kept TUI rendering and lifecycle in a separate adapter while reusing only
  the existing pure projection functions.
- Added exact `@opentui/solid@0.9.4` as a development dependency so TypeScript
  can resolve the JSX runtime/types required by the TUI adapter.

## Blockers / Repository Inconsistencies

- No unresolved production-scope blocker. Dependency installation for
  validation and global CLI activation were separately authorized and
  performed.

## Git Diff Summary

The implementation report was first prepared before delivery operations. See
the Engineering Report and pull-request delivery record for the final commit and
push state.

## Commit/Push

- Commit created: NO
- Push performed: NO

## Readiness

**IMPLEMENTATION CHECKS PASS; OWNER ACCEPTED DELIVERY WITH THE DOCUMENTED
RUNTIME EVIDENCE LIMITATION.** Tests, typecheck, build, package dry-run,
engineering review, and security review pass. The owner accepted the partial
runtime evidence and directed Story closure. The tested version/topology and
full lifecycle checklist remain incomplete; this is not a compatibility claim
for other versions or topologies. Final PR validation was still required at the
time this report was prepared; PR #254 was subsequently merged into `main`.
This delivery update does not expand runtime evidence.
