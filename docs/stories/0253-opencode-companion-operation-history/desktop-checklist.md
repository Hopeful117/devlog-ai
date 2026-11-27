# Story 0253 — Manual Desktop Validation Checklist

**Owner procedure only after implementation is ready.** This checklist does
not authorize installing a plugin, changing its active path, changing active
configuration, launching/reloading Desktop, or making a DevLog operation.

## Before validation

- [ ] Confirm Desktop is already running with the already-installed artifact
      containing the reviewed build. Otherwise stop and request separate
      authorization.
- [ ] Confirm the active local development profile already points to
      `opencode-mod/devlog-companion/`. If it does not, stop and ask the owner
      before changing the active plugin path or restarting Desktop.
- [ ] Record Desktop, sidecar/CLI, `@opencode/plugin`, OS/topology, and artifact
      revision/hash. Do not record secrets or personal paths.
- [ ] Confirm the routed session is the intended test session; do not inspect or
      enumerate other sessions.
- [ ] Ensure the currently loaded session transcript contains only content
      suitable for the owner to view in a bounded local History panel.

## Scenarios

- [ ] Confirm Now is selected by default and the existing avatar, animation,
      live state labels, and reduced-motion behavior remain intact.
- [ ] Switch between Now and History while recognized activity is changing.
      Confirm native observation continues and Now reflects the latest state
      when selected again.
- [ ] With available transcript and no matching tool parts, show the approved
      empty-history message; do not imply DevLog health.
- [ ] With one or more direct DevLog parts, show only approved sanitized fields
      and evidence-qualified outcomes.
- [ ] Confirm host completion without explicit task status is not presented as
      task success.
- [ ] With Code Mode name-only rows, show an incomplete/unknown nested entry
      beneath the outer execution; no fabricated ID, timing, or result
      correlation.
- [ ] Confirm unrelated tools and other sessions do not appear.
- [ ] Confirm loading/unavailable transcript is visibly distinct from an
      available empty transcript.
- [ ] Switch route and return; confirm no stale row paints into another route.
- [ ] Close/reopen panel; confirm rows are derived from the current already-
      loaded transcript and no extra request is triggered.
- [ ] Confirm the view remains within the approved row cap and does not expose
      raw arguments, results, prompts, credentials, or error payloads.
- [ ] Update an operation from running to terminal in the same session and
      confirm the row updates rather than duplicating.

## Record

- [ ] Mark each scenario `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` with concise
      sanitized evidence.
- [ ] Do not capture transcript contents, prompts, arguments, outputs,
      credentials, or unrelated session data.
- [ ] Record unsupported behavior and version pairing; do not infer runtime
      compatibility from a passing build.
- [x] **PASS — Story acceptance:** owner accepted Story 0253 on 2026-11-27.

## Owner-observed results — 2026-11-27

- [x] **PASS — empty state:** owner confirmed the inspected transcript was
      expected to contain no recognized DevLog operation and the empty-history
      message appeared.
- [x] **PASS — Now remains available:** owner confirmed Now, the avatar, and
      the presence state remained available. Animation and reduced-motion
      behavior were not separately inspected.
- [x] **PASS — Code Mode recognition and uncertainty:** after one separately
      authorized read-only `get_engineering_context` operation through Code
      Mode, the owner confirmed the activity appeared once in History, grouped
      under the outer execution, with nested outcome unknown rather than
      individual DevLog success.
- [x] **PASS — panel close / reopen:** owner confirmed the Code Mode History
      entry remained available after closing and reopening the panel on the same
      routed session, without a new DevLog operation.
- [ ] **SKIPPED — route change / return; owner had no suitable safe route.**
- [ ] **NOT RUN — direct-tool History row and live running-to-terminal update.**
