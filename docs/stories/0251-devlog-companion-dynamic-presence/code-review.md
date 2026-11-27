# Code Review — Story 0251

## Review Scope

Reviewed the implementation against Story 0251, its approved Repository
Analysis, and its approved Implementation Plan:

- OpenCode GUI presentation and preview build gating;
- pure presentation state and transition logic;
- unit/package tests and production bundle;
- package boundary, documentation, and implementation report;
- preserved scope boundaries and known validation limits.

No Desktop, MCP, RPC, network, or live runtime action was performed during
review.

## Reviewers and Evidence

- Independent engineering reviewer: read-only review of the GUI, plan, report,
  tests, package scripts, and generated bundle. It returned
  `INSUFFICIENT CONTEXT` only for independently verifying preservation of
  pre-existing uncommitted work; its accessible read-only environment could
  not inspect Git status/diff. Its review preceded the final allowlist addition
  of the pure GUI presentation helper.
- Independent UI/UX reviewer: `UX READY WITH FINDINGS`; no confirmed
  user-facing defect in source. It identified one low-severity optional
  observation about whether the 3-second READY preview lasts long enough.
- Orchestrator verification: Git status captured immediately before
  implementation was compared with current status. Every pre-existing modified
  path and untracked Story 0250/`runtime-tests/` path remains present. The
  static GUI baseline's DevLog mark, panel tab, and launcher also remain in the
  final GUI source. Only the planned presentation/test additions are new within
  the Companion package. The final package dry-run and updated allowlist test
  additionally verify that the pure presentation helper is included alongside
  the GUI source while server/RPC/projection files remain excluded.
- Automated evidence: final `npm run build` passed 22 Node tests and the
  TypeScript check; final `npm run pack:dry-run` passed and listed five
  allowlisted package files. The production-bundle test confirms the simulated
  preview label is absent. `git diff --check` passed.

## Findings

### Confirmed Defects

None found in the inspected source or automated validation.

### Material Risks / Limitations

1. **Desktop rendering and accessibility remain unverified.** The experimental
   renderer's theme appearance, reduced-motion behavior, focus visibility,
   screen-reader announcements, panel width, and open/close behavior need the
   owner-led Desktop check. No compatibility claim follows from build success.
2. **Preview temporarily replaces the generated GUI bundle.** The command is
   explicit, the normal build and `pack:dry-run` restore the production bundle,
   and the final worktree contains the production bundle. Whether a running
   Desktop automatically reloaded the temporary artifact was not inspected.

### Improvement Suggestion

- The UI/UX reviewer noted that `READY_PREVIEW_MS` is 3 seconds and could be
  short for deliberate comparison. This is not a source-level defect and is
  within the approved plan. The owner should confirm its adequacy during visual
  validation; changing the approved timing afterward would require checking
  whether renewed plan approval is necessary.

## Architecture and Scope Checks

- Presentation state remains separate from the observed-operation projection.
- The GUI source has no MCP, RPC, network, server-observer, or DevLog calls.
- The normal bundle remains at `IDLE`; preview controls are compiled only in the
  explicit Vite `preview` mode and are labeled simulated.
- The package retains only `./gui-experimental`; its allowlist includes only
  the GUI entry, its pure presentation helper, mark, and built output. No
  dependency or package export was added.
- Claude files, Stories 0249/0250, `runtime-tests/`, the Preflight Gate, and the
  user's OpenCode configuration were not intentionally changed by this Story.
- Documentation reconciliation is recorded in the implementation report; the
  README documents the preview/production build distinction.

## Review Outcome

**No blocking implementation defect found in the source and automated
evidence.** The independent engineering reviewer could not independently inspect
the Git preservation evidence, but the orchestrator compared the captured
pre-implementation status with the current status and confirmed all previously
modified/untracked paths remain present.

The owner later confirmed visual validation on 2026-11-27 (“Je valide le
visuel”). This review itself did not observe Desktop. Specific accessibility or
reduced-motion behavior and full compatibility remain unverified here; complete
Story acceptance and delivery remain pending.
