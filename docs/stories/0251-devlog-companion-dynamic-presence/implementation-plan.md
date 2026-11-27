# Implementation Plan

## Overview

Add a presentation-only presence component to the OpenCode Desktop GUI package.
Keep its state input separate from observed-operation projection, preserve
`IDLE` as the production default, and implement all five requested visuals
using the Claude Companion's avatar concept without importing or changing the
Claude plugin.

Expose the controls only in an explicit local preview build. The ordinary
`build` and package-validation paths must always regenerate the GUI bundle with
preview controls disabled before inspecting or packing it. The current
`./gui-experimental` export, GUI-only package boundary, panel launcher, and
close/reopen behavior remain unchanged.

The owner approved the current Repository Analysis on 2026-11-27. This plan is
the next workflow artifact only; its completion will require a separate explicit
approval before implementation begins.

A targeted Obsidian search for OpenCode Desktop/reduced-motion/workflow notes
returned no relevant results; no vault note informs this plan. The DevLog
preflight and its freshness/truncation limits are recorded in the approved
Repository Analysis.

## Planned Changes

1. **Create a pure presentation-state module.**
   - Define the five presentation inputs independently of `model.js`'s observed
     projection and `PresenceState`.
   - Provide safe normalization to `IDLE` for unsupported values and a small
     deterministic visual descriptor for state label, color, shape/treatment,
     and motion policy.
   - Keep the module free of host, network, MCP, RPC, server, and UI imports.
   - Preserve distinct error and disconnected descriptors; these simulated
     values must never imply actual execution or connectivity facts.

2. **Add the OpenCode-native avatar and state presentation.**
   - Update `src/gui.tsx` to render a compact nucleus/satellites avatar,
     accessible state text, and state-specific treatments in the current
     side panel.
   - Use the Claude visual reference as a guide: central node/ring/satellites;
     green, amber, blue, red, and muted gray state colors; subtle idle motion,
     active working motion, finite ready feedback, restrained error styling,
     and a dashed/subdued disconnected treatment.
   - Adapt sizing, contrast, and copy to the OpenCode theme and narrow side
     panel. Do not import Claude files or claim parity.
   - Preserve the existing DevLog mark, panel menu launcher, transient closable
     tab, and normal `IDLE` status.
   - Scope `READY`'s preview timer to the rendered component; reset it to `IDLE`
     after 3 seconds, cancel/restart it on selection changes, and clean it up on
     unmount. This finite interval makes the transient state observable while
     keeping the preview responsive. A timer is UI preview behavior, not a
     DevLog task-recency rule.

3. **Implement reduced-motion behavior without host-specific assumptions.**
   - Use an explicit reduced-motion policy in the presentation layer and a
     standard `prefers-reduced-motion` media-query fallback for the rendered
     motion.
   - Keep all state distinctions visible with motion disabled.
   - Do not assume an OpenCode SDK-specific reduced-motion API; the pinned SDK
     declarations did not establish one. Confirm behavior in the actual Desktop
     renderer during owner-led visual validation.

4. **Add an explicit preview build mode.**
   - Gate the controls on Vite's explicit build mode (`preview`); every ordinary
     build uses the default production mode and disables them.
   - Add a dedicated preview build command which enables five state-selection
     controls and an always-visible “simulated preview” label.
   - Keep the preview build local and temporary, writing the same GUI export
     entry so the currently configured local package can display it without a
     configuration edit. Do not run or load this build in Desktop as the agent.
   - Keep the normal build as the only package-validation build. Ensure the
     `pack:dry-run` command runs the normal build first, so a preceding preview
     build cannot accidentally be represented as the ordinary package. Order
     the normal build so it generates the production GUI artifact before tests
     that inspect package/build state.
   - Do not add a package export, server feature, RPC/MCP dependency, or preview
     asset to the package allowlist. The current allowlisted GUI source can
     contain the gated preview branch; the exported ordinary bundle must not
     render or activate the controls. Do not modify the active OpenCode profile.

5. **Add focused tests and operator guidance.**
   - Cover state normalization, all descriptor variants, error/disconnected
     distinction, select/reset behavior, finite READY reset policy, and
     reduced-motion output with the existing Node test runner.
   - Extend package tests to assert the unchanged GUI-only export/allowlist and
     verify the ordinary production bundle does not contain preview controls.
   - Document the distinction between normal and preview build commands,
     clearly state that the preview is synthetic, and document rebuilding the
     normal bundle before package validation.
   - Keep tests deterministic and add no dependency, test framework, runtime
     harness scenario, or Desktop automation.

6. **Validate and report at the implementation gate.**
   - Run focused Node tests, TypeScript type-check, normal GUI build, and
     package dry-run after the production build has been restored.
   - Separately run the preview build and focused checks for its presence and
     explicit labeling; do not use it as the package-validation output.
   - Do not launch/reload Desktop. The owner may manually inspect the preview
     after the implementation artifact is ready; record that observation
     separately from automated checks.
   - Review the complete diff and confirm Claude, Story 0249/0250,
     `runtime-tests/`, the Preflight Gate, and user configuration were not
     changed by the implementation.

## Files to Modify

- `opencode-mod/devlog-companion/src/gui.tsx` — avatar, accessible visual
  state, preview-only controls, reduced-motion presentation, and READY timer.
- `opencode-mod/devlog-companion/package.json` — preview build command and
  package dry-run command that first rebuilds the normal bundle; reorder the
  build script so the normal bundle exists before tests that inspect it.
- `opencode-mod/devlog-companion/tests/gui-package.test.js` — preserve package
  export/file-allowlist boundary and assert the production bundle omits the
  preview-only label.
- `opencode-mod/devlog-companion/README.md` — document normal/preview build
  behavior and the simulated-only evidence boundary.
- `opencode-mod/devlog-companion/dist/gui-experimental.cjs` — regenerated normal
  GUI artifact; the preview build is temporary and must not remain as the final
  package artifact.

## Files to Create

- `opencode-mod/devlog-companion/src/presentation.js` — pure presentation-state
  vocabulary, input normalization, and deterministic visual/motion descriptors.
- `opencode-mod/devlog-companion/tests/presentation.test.js` — Node tests for
  supported states, invalid input, visual distinctions, transitions, and
  reduced-motion policy.

No stylesheet or external dependency is planned unless repository evidence
during implementation demonstrates that the existing inline GUI/SVG approach
cannot meet the Story. Any such need must remain in scope and be reported before
expanding it.

## Dependencies

- Internal: the GUI imports the pure presentation module; UI and preview controls
  remain inside the existing GUI entry. The presentation module must not depend
  on `model.js`, `rpc.js`, or `server.js`.
- External: no new dependency. Continue using the pinned
  `@opencode/plugin@2.4.0`, SolidJS, Vite, TypeScript, and Node built-in test
  runner already declared by the package.
- Build ordering: production build before production bundle assertions and
  `npm pack --dry-run`; preview build only for its isolated preview checks;
  restore normal output before final diff review.
- Repository prerequisites: implementation must use the owner-approved Story
  and this Implementation Plan. Desktop reload or interactive visual
  inspection, if needed, remains owner-performed.

## Test Plan

| Coverage | Planned check |
|---|---|
| Default and invalid input | Node unit tests assert omitted/unsupported values normalize to `IDLE`; no invalid value maps to observed activity. |
| Five presentation states | Unit tests assert each supported state has its documented label/treatment and `ERROR` differs from `DISCONNECTED`. |
| Select/reset behavior | Unit tests exercise selection, explicit return to `IDLE`, and the 3-second READY expiry/reset policy; component timer cleanup/change behavior is checked in code review and manually in Desktop. |
| Reduced motion | Unit tests assert the presentation descriptor suppresses nonessential motion under reduced-motion policy while retaining each state's non-motion distinction; rendered media-query behavior requires owner visual check. |
| Preview separation | Build the preview mode and verify it includes labeled simulated controls; rebuild in normal mode before packaging and confirm the production GUI shows no preview controls. Neither build may import/call MCP, RPC, server observer, or DevLog. |
| Existing package contract | Existing and extended package tests assert only `./gui-experimental` is exported and package files remain limited to the GUI source, mark, and `dist`. |
| Type and bundle integrity | Run the existing TypeScript check and normal Vite GUI build. |
| Package integrity | Run the package dry-run command after the normal build; inspect its file list to confirm no preview-only or server/RPC files are included. |
| Manual visual acceptance | Owner checks all five states, keyboard-accessible preview controls, reduced-motion behavior, panel narrow-width/theme appearance, READY reset, and existing open/close behavior. Agent does not launch or reload Desktop. |

Expected commands, from `opencode-mod/devlog-companion/`:

```bash
npm run build:gui:preview
npm test -- tests/presentation.test.js
npm run build
npm test
npm run typecheck
npm run build:gui
npm run pack:dry-run
```

`npm run build:gui:preview` is a development-only local check and temporarily
overwrites the local GUI bundle. `npm run build` restores the ordinary bundle
before the production tests. `pack:dry-run` must likewise run the ordinary build
first. No test or validation has been run for this plan; these are planned
checks, not claimed results.

## Risks

- **Preview artifact accidentally packed:** preview mode targets the local GUI
  export and could replace the normal build output. Mitigate with an explicit
  preview-only command, disabled-by-default compile flag, a package dry-run
  command that rebuilds normal output first, and a bundle assertion.
- **Simulated labels mistaken for live connectivity:** especially for
  `DISCONNECTED`. Keep the “simulated preview” indicator visible in preview mode
  and keep all preview state logic independent of observed projection/network
  code.
- **Experimental Desktop renderer differs from local assumptions:** reduced
  motion, SVG animation, theme contrast, and sizing cannot be proven by unit or
  build tests. Report these as manual owner checks; do not make a compatibility
  certification claim.
- **READY timer races or leaks:** state changes or panel unmount could leave a
  stale reset. Keep timer ownership within the presentation component, cancel
  pending timers on changes/unmount, and cover reset policy deterministically.
- **Narrow panel or accessibility regressions:** controls and status may clip or
  lose meaning. Use concise labels, explicit accessible text, and verify
  keyboard operation and the actual Desktop appearance manually.
- **Existing uncommitted work overwritten:** the GUI and bundle are already
  modified in this worktree. Preserve the current work, inspect its diff before
  implementation, and change only the planned Companion files plus this
  Story's implementation report if later authorized.

No blocking architectural ambiguity remains for preparing this plan. Desktop
rendering remains experimental and must be treated as a manual validation
limitation, not a basis for expanding scope.

## Validation Checklist

- [ ] Only the OpenCode GUI presentation/package and directly required Story
      artifacts are modified.
- [ ] The regular panel defaults to `IDLE`; no simulated state is presented as
      observed DevLog, task, or connectivity evidence.
- [ ] Preview controls are present only in the explicit preview build and are
      clearly labeled simulated.
- [ ] Preview selects `IDLE`, `WORKING`, `READY`, `ERROR`, and `DISCONNECTED`;
      it can return to `IDLE`.
- [ ] READY feedback resets after the planned finite interval; timer changes and
      unmount are safe.
- [ ] ERROR and DISCONNECTED differ in label and visual treatment.
- [ ] Reduced-motion behavior removes nonessential motion while retaining
      distinguishable states.
- [ ] Avatar remains recognizable relative to Claude's visual concept and is
      adapted to the OpenCode theme; Claude files remain unchanged.
- [ ] Preview and normal builds pass the focused tests; normal bundle excludes
      preview controls.
- [ ] TypeScript check, normal GUI build, existing Node tests, and package
      dry-run pass.
- [ ] Package export remains only `./gui-experimental`; the allowlist excludes
      server/RPC/projection files and preview-only artifacts.
- [ ] No MCP/RPC/server observer, network request, backend, Gate, Story 0249/0250,
      runtime harness, external dependency, or user-profile change is introduced.
- [ ] Desktop visual and accessibility observations, if performed, are recorded
      separately and explicitly attributed to the owner; no compatibility claim
      exceeds the evidence.
- [ ] Complete diff is reviewed and all pre-existing uncommitted Story 0250 and
      `runtime-tests/` work is preserved.

## Recommendation

**Ready for implementation**

This is a technical recommendation only. It does not approve the Implementation
Plan or authorize implementation. The GUI-only boundary and preview packaging
approach are defined, the required behavior and checks are identifiable, and
remaining Desktop-renderer uncertainty is bounded to owner-led manual
validation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
