# Implementation Report — Story 0252

## Repository State

- Branch: `story/0251-devlog-companion-dynamic-presence`
- HEAD at implementation start: `0414bd390ebaa267c39a7e97b27c371eda95f84a`
- Worktree: the approved ADR-084 amendment and Story 0252 preparation documents
  were already modified/untracked before production implementation. No commit
  or push was made.

## Governance

- Governing decision: ADR-084, including the owner-accepted amendment dated
  2026-11-27.
- Authorized Story: 0252. The owner approved the amendment and revised
  Story/plan, then explicitly authorized the scoped implementation on
  2026-11-27.
- Implementation boundary: OpenCode GUI only. No Claude, shared runtime,
  server/RPC observer, DevLog backend, Gate, MCP/REST request, active Desktop
  configuration, or infrastructure changes.
- DevLog preflight: task/snapshot
  `6c7cd556-1111-4519-a223-9e774b01749f` completed at repository revision
  `922e83e94c3c9328eb3f7959e7111609ca666c37`, but context was stale,
  partially fresh, truncated, and LOW confidence. It did not retrieve Story
  0252's contract; the approved local Story/ADR and current repository were
  authoritative for this implementation.
- Context7: unavailable because the monthly quota was exceeded. OpenCode 2.4.0
  API behavior was checked against the pinned package declarations and
  version-matched source in the isolated audit tree; TypeScript validation
  passed against the installed pinned dependencies.

## Files Changed

- `opencode-mod/devlog-companion/src/gui.tsx`: bind the panel to reactive
  `MountedSession`; observe only its loaded message cache; subscribe to
  session-scoped tool/execution invalidations and clean them up on route or
  panel lifecycle; render evidence-backed status and neutral `UNKNOWN`.
- `opencode-mod/devlog-companion/src/gui-presence.js`: add a bounded,
  GUI-local allowlist, direct-result decoder, Code Mode row decoder, pure
  presence derivation, and route-filtered native event subscription helper.
- `opencode-mod/devlog-companion/src/presentation.js`: add a neutral live
  `UNKNOWN` visual without changing Story 0251 preview normalization.
- `opencode-mod/devlog-companion/tests/gui-presence.test.js`: cover allowlist,
  evidence semantics, Code Mode limits, stale activity, route-filtered event
  subscriptions, cleanup, bounds, and payload non-retention.
- `opencode-mod/devlog-companion/tests/presentation.test.js` and
  `tests/gui-package.test.js`: verify neutral live unknown and package allowlist.
- `opencode-mod/devlog-companion/package.json`: include the new GUI-local helper
  in the GUI-only package allowlist.
- `opencode-mod/devlog-companion/README.md`: document the live presentation and
  its trust/evidence limits.
- `opencode-mod/devlog-companion/dist/gui-experimental.cjs`: regenerate the
  exported production GUI bundle.
- `docs/decisions/ADR-084.md` and `docs/stories/0252-devlog-opencode-live-presence/`:
  record approved governance, scope, contract, plan, manual checklist, rollback
  boundary, and this report.

No Claude Companion, shared runtime, server/RPC implementation, backend,
package lock, or user data was changed. After separate user authorization for
runtime validation, the global development-profile OpenCode plugin path was
corrected from a different worktree to this worktree's package directory; this
configuration is outside the repository.

## Implementation Summary

The Desktop panel derives a small in-memory presence state from only the
currently routed session's already-loaded native transcript. Direct DevLog
tool parts and explicit Code Mode `toolCalls` evidence are recognized; host
completion is kept distinct from explicit DevLog task status. The pinned
OpenCode Code Mode terminal result exposes nested tool names without nested
status/call IDs, so a recognized name-only result yields `UNKNOWN`; no nested
success, failure, or running state is invented or correlated. No Companion
RPC, server observer, transcript fetch, or additional DevLog request was added.

## Validation

From `opencode-mod/devlog-companion/`:

```text
npm ci --offline
npm run pack:dry-run
```

`npm run pack:dry-run` ran the production GUI build, all Node tests, TypeScript
typecheck, and package dry-run. After the runtime-discovered decoder correction,
results: **33 tests passed**, typecheck/build passed, package dry-run passed
with seven files. The package lock remained unchanged.

An independent read-only security review of the earlier implementation
returned **PASS** with no material security issues. It confirmed the documented
route-filtering/trusted-plugin limitation. The later bounded name-only decoder
correction has not received a second independent review.

Manual Desktop validation confirmed the new bundle's explanatory text after
the user re-pointed the development profile and restarted Desktop. A subsequent
authorized read-only DevLog operation through Code Mode still displayed `IDLE`;
the operation response was stale/truncated and gave no explicit successful task
outcome. This exposed the mismatch between the implementation's expected
`{tool,status}` rows and the pinned Code Mode's `{name}` terminal rows. The
decoder and deterministic tests were corrected. On the post-correction Desktop
retest, the owner observed `WORKING` followed by `UNKNOWN` for one authorized
read-only Code Mode operation. Its response was stale/truncated and did not
explicitly establish a successful DevLog task outcome, so `UNKNOWN` was the
expected terminal presentation; `READY` was not expected. The owner also
closed/reopened the panel on the same route and observed `UNKNOWN`, then
navigated to another session (`IDLE`) and returned to the original route
(`UNKNOWN`). These observations pass the tested panel-reopen and route
reconciliation scenarios without inspecting the other session's activity. A
later authorized Code Mode call attempt ended with the DevLog MCP connection
closing and its process exiting with code 143; the owner observed `WORKING`
then `ERROR`. This validates the Code Mode tool-error presentation only, not a
direct native MCP path or a DevLog business-task failure. No retry was made.
Context7 retrieval was unavailable due quota. Other visual states and broader
Desktop behavior remain unqualified.

## Known Limitations

- GUI route filtering is presentation scoping, not server authorization or
  isolation from other trusted GUI plugins.
- `READY` requires a direct result with explicit successful task status.
  Name-only Code Mode terminal rows establish only that a recognized tool was
  called; they cannot establish its nested status or business-task success.
- A `WORKING` → `ERROR` observation during MCP process termination is a
  tool/integration error only; it does not establish a DevLog task failure.
- `DISCONNECTED` is not claimed. Missing, empty, loading, interrupted, or
  ambiguous evidence is shown as neutral `UNKNOWN`.
- Context7 quota prevented current Context7 retrieval; source/declaration and
  pinned-package checks were used instead.
- Claude qualification remains outstanding; no cross-platform compatibility
  claim is made.
- Manual runtime checks passed for the authorized Code Mode operation, panel
  close/reopen, and route switch/return. Other visual states and human Story
  acceptance remain pending.

## Git and Readiness

- `git diff --check`: passed after the decoder adjustment.
- Commit created: NO.
- Push performed: NO.
- Desktop relaunched by owner after separately authorized development-profile
  path correction: YES.
- Read-only DevLog operation: YES, through Code Mode; its Companion displayed
  `WORKING` then `UNKNOWN` after the decoder correction, and `WORKING` then
  `ERROR` when a later MCP connection attempt failed with process exit 143.
- Readiness: **READY_FOR_HUMAN_REVIEW** for code and automated validation;
  the documented manual runtime scenarios passed; other visual states and
  Story acceptance remain pending.
