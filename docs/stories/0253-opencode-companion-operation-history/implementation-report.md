# Implementation Report — Story 0253

## Status

**Owner accepted on 2026-11-27 after implementation and focused Desktop
validation.** Route change/return was skipped; direct-tool and running-to-
terminal scenarios remain unvalidated. PR #252 is open; merge is pending.

## Repository and governance

- Worktree: `/tmp/opencode/devlog-ai-story-0251`
- Implementation branch: `story/0252-devlog-opencode-live-presence`.
- Delivery branch: `story/0253-opencode-companion-operation-history`.
- Implementation started from a worktree whose tracked tree matched
  `origin/main` at `fa96f5a28ab367c0ae11017b484e7031d56da6ab`.
- The pre-existing untracked
  `docs/stories/0252-devlog-opencode-live-presence/repository-analysis.md` was
  preserved.
- DevLog preflight limitation: task/snapshot
  `7ae0ac23-52d4-4462-98f2-3dae9b09b613` showed `COMPLETED` when read, but
  resuming the original task with reconstructed arguments returned
  `409 RESOURCE_CONFLICT`. Per owner instruction, no new investigation or
  retry was initiated. The available context was stale/partially fresh and
  truncated, resolved at `922e83e94c3c9328eb3f7959e7111609ca666c37`, and
  provided no usable synthesis. Repository Story/ADR and current code were
  used as authority.
- Context7 was unavailable because the monthly quota was exceeded. The pinned
  local OpenCode plugin dependencies and repository contracts were used.

## Files changed

- `opencode-mod/devlog-companion/src/gui-presence.js` — bounded sanitized
  History projection; explicit task-status conflict handling; Code Mode
  metadata/result reconciliation and shared row budget.
- `opencode-mod/devlog-companion/src/gui.tsx` — Now/History views, accessible
  view controls, empty/unavailable states, evidence-qualified row rendering.
- `opencode-mod/devlog-companion/tests/gui-presence.test.js` — History,
  conflict, privacy, bounds, reconciliation, ordering, recomputation, and
  route-projection fixtures.
- `opencode-mod/devlog-companion/tests/gui-package.test.js` — current
  GUI-only package assertion wording.
- `opencode-mod/devlog-companion/package.json` — package description.
- `opencode-mod/devlog-companion/README.md` — History semantics and limits.
- `opencode-mod/devlog-companion/dist/gui-experimental.cjs` — regenerated
  production GUI bundle.
- Story 0253 `story.md`, `operation-history-contract.md`,
  `implementation-plan.md`, `desktop-checklist.md`, and
  `repository-analysis.md`, and `implementation-report.md` — authorization,
  validation, and implementation evidence.

No Claude Companion, backend, server/RPC, Gate, infrastructure, active Desktop
configuration, or user data was modified.

## Implementation summary

Now remains the default view and preserves the existing presence projection,
avatar, animation, and route-scoped native subscriptions. The view controls
only change rendered content; they do not dispose or suspend presence
observation.

History is recomputed from the already-loaded transcript of the currently
routed session when the existing native invalidation events fire. Recomputing
the bounded projection replaces rows on part updates rather than appending
duplicate event records. Direct rows contain only an allowlisted tool name,
bounded native host ID when available, host outcome, one unambiguous explicit
task status, and native timing when present. Pending/poll statuses are labeled
last-reported.

Code Mode rows are grouped under the outer execution. Metadata and result rows
are reconciled as a distinct-name union; nested identities, per-call counts,
and outcomes are not inferred. Name-only rows remain unknown. Projection work
is bounded to 40 messages, 80 parts per message, 80 nested rows total per outer
execution across both sources, and 20 History rows. Each Code Mode row retains
at most eight tool names; host IDs over 128 characters are omitted. No raw
prompts, arguments, responses, credentials, or error payloads are retained or
rendered.

The independent review identified conflicting-status ambiguity, per-row size
bounds, metadata/result reconciliation, last-reported status labeling, and a
shared Code Mode inspection budget. These were corrected and covered by
fixtures. The final review follow-up returned **PASS**. It noted that actual
Desktop route/panel lifecycle behavior still requires the owner checklist;
automated tests do not certify that runtime.

## Validation

From `opencode-mod/devlog-companion/`:

| Check | Result |
|---|---|
| Focused projection tests: `node --test tests/gui-presence.test.js` | Passed, 23 tests |
| Full package tests: `npm run test` via the final package build | Passed, 46 tests |
| TypeScript: `npm run typecheck` | Passed |
| GUI production build: `npm run build:gui` via final package dry-run | Passed |
| Package dry-run: `npm run pack:dry-run` | Passed; package contains 7 files |
| `git diff --check` | Passed |
| Manual OpenCode Desktop validation | Partial; owner-reported results below |

Intermediate package runs exposed two issues that were corrected before final
validation: a preview-only string remained in the production bundle after a
conditional-render change, and the shared Code Mode budget helper had implicit
TypeScript `any` types. The preview branch was restored to a compile-time
conditional, JSDoc types were added, and the final typecheck and full package
dry-run passed. The final GUI artifact is
`opencode-mod/devlog-companion/dist/gui-experimental.cjs`, SHA-256
`a84240c0ae8945890a5ed0a929b1729609484782e1a319b5f38b653a12000b8f`.

## Manual Desktop validation instructions

Use [desktop-checklist.md](desktop-checklist.md) with the already-installed
local plugin and the built artifact above. First confirm that the already-active
development profile points to this package. If the active path needs changing,
stop and ask the owner before changing it. Ask separately before reloading
Desktop or triggering any new real DevLog operation. A safe existing transcript
is sufficient to validate History; no new DevLog operation is inherently
required.

Owner-reported on 2026-11-27: the expected empty state appeared for a session
with no recognized operation; Now and its avatar/state remained available; and
one separately authorized read-only `get_engineering_context` Code Mode
operation appeared once, grouped under the outer execution, with nested outcome
unknown. No second real operation was triggered after that single authorized
call. Route switch/return, panel close/reopen, direct-tool row rendering, and
running-to-terminal update are addressed as described below. The owner skipped
route switch/return because no suitable safe route was available.

After the Code Mode History row appeared, the owner closed and reopened the
panel on the same route and confirmed that the row remained available without
another DevLog operation. This is owner-reported runtime evidence, not a
cross-route lifecycle result.

## Remaining limitations

- Route switch/return was skipped because no suitable safe route was available.
  Direct-tool row rendering and a running-to-terminal update have not been
  runtime-validated. Owner observations cover the empty state, Now availability,
  one Code Mode terminal row, and panel close/reopen on the same route only.
- The OpenCode GUI API remains experimental and the installed host/sidecar
  pairing is not newly certified by the build.
- Route filtering remains presentation scoping, not an authorization or
  isolation boundary.
- History reflects only the bounded transcript window already loaded by
  OpenCode; it is not a complete or authoritative DevLog operation ledger.
- Code Mode has no fabricated nested identity, correlation, timestamp, or
  success. Context Inspector, RPC, transcript fetching, persistence, and
  cross-session history remain out of scope.
- Claude compatibility has not been tested or claimed.

## Git and readiness

- `git diff --check`: passed.
- Commit: `403b3b7fa680598fbb613cfc100fd42cff751fe2`.
- Branch pushed to `origin`: YES.
- Pull request: #252, open against `main`.
- Merge performed: NO; GitHub checks are still pending and PR review/merge
  approval remains outstanding.
- Active plugin path changed: NO.
- Desktop reloaded/launched: NO.
- Real DevLog operations: one read-only `get_engineering_context` operation,
  explicitly authorized for Desktop validation.
- Owner accepted Story: YES, 2026-11-27.
- Readiness: **ACCEPTED; READY_FOR_DELIVERY**.
