# Implementation Report — Story 0251

## Repository State

- Branch: `story/0248-opencode-native-compatibility-spike`
- HEAD at implementation start: `13c27737a97f7b86a00a0011b80bd4c92c671f78`
- Worktree: pre-existing modified Story 0248/OpenCode GUI files and untracked
  Story 0250 plus `runtime-tests/` were present and preserved. Story 0251
  artifacts are also untracked on this branch. No commit or push was made.

## Governance

- Governing decision: ADR-084.
- Authorized Story: 0251, with Repository Analysis approval and Implementation
  Plan approval/implementation authorization explicitly provided by the owner
  on 2026-11-27.
- DevLog Story registration: record
  `6756950c-1d46-444f-8fe9-20e4aeee39d2`.
- DevLog lifecycle start: recorded `IN_PROGRESS` with base commit
  `13c27737a97f7b86a00a0011b80bd4c92c671f78`.
- DevLog context: preflight task/snapshot
  `28cf7237-b921-4661-bfaa-ce6b292c6291` completed at revision
  `94fdd24c19189c6f9fb633511a04eb667ee5efe5`, but was `STALE` /
  `PARTIALLY_FRESH`, truncated, and low-confidence. It yielded no decision
  findings; current repository evidence and approved Story artifacts governed
  implementation.

## Files Changed

- `opencode-mod/devlog-companion/src/gui.tsx`: added the stateful avatar,
  motion preference handling, accessible state text, preview-only selectors,
  READY timeout, and theme-inheriting compact layout. It remains GUI-only and
  has no live DevLog, MCP, RPC, or server observer calls.
- `opencode-mod/devlog-companion/src/presentation.js`: added pure state
  normalization, visual descriptors, reduced-motion policy, and deterministic
  preview transitions.
- `opencode-mod/devlog-companion/tests/presentation.test.js`: added five
  deterministic tests for state vocabulary, invalid input, visual distinctions,
  reduced motion, state transitions, and READY timeout policy.
- `opencode-mod/devlog-companion/tests/gui-package.test.js`: added a production
  bundle assertion that preview-only controls are not present.
- `opencode-mod/devlog-companion/package.json`: added the preview build,
  includes the GUI's pure presentation source in the GUI-only allowlist, builds
  the ordinary bundle before tests, and rebuilds it before package dry-run.
- `opencode-mod/devlog-companion/README.md`: documented normal/preview builds,
  simulated-only semantics, and restoring the ordinary bundle.
- `opencode-mod/devlog-companion/dist/gui-experimental.cjs`: regenerated the
  normal production bundle; preview controls are excluded.
- `docs/stories/0251-devlog-companion-dynamic-presence/story.md`: recorded the
  approved workflow stages and pending human acceptance.
- `docs/stories/0251-devlog-companion-dynamic-presence/implementation-report.md`:
  this report.

The package's previously modified manifest, lockfile, GUI baseline, README, and
generated bundle were inspected and retained. No dependency was added, so no
lockfile update was needed. Story 0250 and `runtime-tests/` were not modified.

## Implementation Summary

The OpenCode panel now renders five separate simulated presence treatments,
with `IDLE` as the normal build state. A preview build exposes keyboard-native
state buttons and a persistent simulation label; selecting `READY` returns to
`IDLE` after 3 seconds. Reduced-motion preference suppresses nonessential
animation while preserving colors and shapes. The normal exported bundle
contains no preview label or controls and the package retains only its
experimental GUI export.

## Architectural Decisions Preserved

- Presentation state is independent of the observed-operation model and does
  not derive or fabricate runtime events.
- The GUI makes no MCP, RPC, network, or DevLog calls and acquires no
  credentials.
- Claude files and behavior remain unchanged.
- The package still exports only `./gui-experimental`; the server/RPC
  implementation remains outside its allowlist.
- No changes were made to DevLog Core, the Preflight Gate, Stories 0249/0250,
  runtime infrastructure, or the user's OpenCode configuration.

## Tests Added

- `tests/presentation.test.js`: supported values, safe fallback, five distinct
  descriptors, reduced-motion mapping, selection/reset transitions, stale
  READY-timeout protection, and the 3-second preview timeout constant.
- `tests/gui-package.test.js`: existing GUI-only export and allowlist assertion
  plus production bundle exclusion of the simulated-preview label.

## Commands Executed

From `opencode-mod/devlog-companion/`:

```bash
npm run typecheck
npm run build:gui:preview
node --test tests/presentation.test.js
grep -o 'Simulated preview' dist/gui-experimental.cjs
npm run build
npm run pack:dry-run
```

The final `pack:dry-run` command reran the normal GUI build, all Node tests, and
the TypeScript check before inspecting the package contents.

## Test Results

- Preview GUI build: passed. The generated local preview bundle contained the
  explicit “Simulated preview” label.
- Focused presentation tests: **5 passed**.
- Normal build: passed; **22 Node tests passed**, then TypeScript check passed.
- Package dry-run: passed after rebuilding the normal GUI bundle. The package
  contained six files: README, normal GUI bundle, package manifest, DevLog SVG,
  GUI source, and its pure presentation source. No server/RPC files or
  preview-only assets were packaged.
- Validation iterations: an initial production build failed because Solid's
  conditional `Show` retained the preview legend in the bundle; the package
  assertion caught it. The preview UI was changed to a compile-time conditional
  and the production-bundle assertion then passed. TypeScript also identified
  SVG attribute and context-typing issues during development; these were fixed
  before the successful final build.
- `git diff --check`: passed after implementation.
- Lint/format checks: not configured in this Companion package.
- SonarQube: not applicable to this isolated GUI package; repository Sonar
  configuration is for the Java backend.
- Runtime/E2E tests: not run; no Desktop automation infrastructure is configured
  for this package.

## Documentation Reconciliation

`README.md` was updated because the package now has distinct normal and preview
build behavior. No other product or installation documentation needed
reconciliation for this scoped change. The normal Desktop profile was not
edited, and the agent did not launch or reload Desktop.

## Manual Validation and Known Limitations

- Owner visual confirmation: on 2026-11-27 the owner stated, “Je valide le
  visuel.” This records the owner's visual validation of the Companion. The
  agent did not capture a screenshot; this statement is not expanded into
  specific screen-reader, keyboard, reduced-motion, or compatibility claims.
- Owner-led Desktop review of all five visuals, reduced-motion behavior,
  keyboard/focus and screen-reader behavior, narrow-panel/theme appearance,
  panel open/close behavior, and READY reset was not independently observed by
  the agent. The agent did not launch or reload Desktop; any aspects beyond the
  owner's visual confirmation remain unverified here.
- Automated builds do not qualify the experimental OpenCode Desktop GUI API or
  prove the installed Desktop/sidecar/SDK pairing. No compatibility certification
  is claimed.
- The pre-existing active Desktop plugin points at the local package directory.
  The preview build temporarily replaced the generated GUI bundle during
  validation; the final build and package dry-run restored the normal
  production `IDLE` bundle. Whether the already-running Desktop hot-reloaded
  either bundle was not inspected or verified.
- The Context7 documentation request could not be completed due to monthly
  quota exhaustion. Reduced-motion behavior is implemented with the standard
  browser media query and `matchMedia`, but still requires owner visual
  validation in Desktop.

## Vault Outcome

- Curated vault context was not consulted during the Repository Analysis.
- A targeted Obsidian search during planning returned no relevant notes; no
  curated vault note materially informed implementation.
- Outcome: **no vault action**. No candidate note was created, and no
  `vault-outcome.json` or transverse-memory proposal is applicable.

## Implementation-Level Decisions

- `READY` preview resets after 3,000 ms; state changes cancel the previous
  timeout, and component cleanup clears any remaining timeout.
- Vite's explicit `preview` mode enables preview controls. The ordinary build
  does not render them; `build` and `pack:dry-run` restore the normal bundle.
- Reduced-motion state is read from the browser media query and SVG animation
  elements also include a media-query fallback.

## Blockers / Repository Inconsistencies

- No implementation blocker was found.
- Manual Desktop visual/accessibility acceptance remains outstanding and is
  distinct from automated validation.
- No unapproved files were intentionally modified beyond pre-existing work
  listed in the repository state and files-changed sections.

## Git Diff Summary

No commit or push was created. Story 0248, Story 0250, and `runtime-tests/`
work remains uncommitted and preserved.

## Commit/Push

- Commit created: NO
- Push performed: NO

## Readiness

**READY_FOR_HUMAN_REVIEW** — implementation and automated package checks are
complete; manual Desktop validation and human Story acceptance remain pending.
