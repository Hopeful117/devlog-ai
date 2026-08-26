# Story 0092 — Code Review

Date: 2026-08-26 · Scope: commits `001a3af`, `c809d3a`, `7a38761`, `3a749c7`
on `feature/story-0092-detect-repository-head-changes`.

## Architecture

- [x] **Detector only observes.** `ScheduledRepositoryChangeDetector` =
  iterate eligible sources → probe → record → log. Zero imports, zero diff
  analysis, zero projection work, zero Understanding calls, zero AI, zero
  proposals, zero Maintenance remediation (verified by reading the full diff
  and by absence of any dependency on history/collection/ai packages).
- [x] **No synchronization.** No workspace synchronize/fetch/checkout/reset;
  knowledge paths untouched; negative runtime proof (Y invisible to commit
  search after detection).
- [x] **No RepositorySyncJob/planner/queue/retry lifecycle** — none created.
- [x] **Polling is an outer adapter** per ADR-062 §7: Git mechanics behind
  the `RepositoryRevisionProbe` port; freshness rules inside
  `ProjectFreshnessService`; scheduling only in the outermost component,
  removable by kill-switch without touching domain code. A future webhook/
  hook/Workspace source can call the same `recordObservedRevision` boundary.
- [x] No domain-event infrastructure added; direct service invocation only.

## Git behavior

- [x] Probe command is exactly `git ls-remote <url> <ref>`
  (`shouldObserveHeadWithLsRemoteOnTheTrackedBranch`,
  `shouldFallBackToSymbolicHeadWhenNoBranchIsConfigured`); mutation verbs
  proven absent (`shouldNeverInvokeWorkspaceMutatingGitOperations`).
- [x] Tracked ref semantics: explicit `defaultBranch` → `refs/heads/<b>`;
  null/blank → remote symbolic `HEAD`. No “main” hardcoding; unknown ref →
  explicit failure (`shouldFailExplicitlyWhenTheTrackedRefDoesNotExist`).
- [x] Local/file repositories flow through the same transport (no network).
- [x] Credentials: reuse of `GitCommandExecutor` environment
  (`GIT_TERMINAL_PROMPT=0`) and git’s own URL handling — no second credential
  path; URLs never appear in log statements.

## Freshness

- [x] `recordObservedRevision` delegates to the existing `save()`
  classification — CURRENT/STALE/NO_BASELINE/UNKNOWN semantics preserved;
  detector owns observedRevision + checkedAt only.
- [x] Baseline never advanced by detection: only understanding calls
  `recordObservedBaseline`; asserted in tests
  (`repeatedCyclesRemainIdempotent` verifies `never()` on it) and by runtime
  (baseline stayed X while observed became Y).
- [x] checkedAt semantics preserved: successful probes refresh the timestamp
  even when unchanged (existing `save()` behavior), distinguishing “CURRENT
  now” from “CURRENT days ago”.

## Failure handling

- [x] Per-source isolation: probe or recording failure → WARN log, previous
  checkpoint preserved, cycle continues (`isolatesProbeFailuresAndPreserves-
  PreviousCheckpoints`); whole-cycle survival guaranteed because the previous-
  checkpoint read is also inside the guarded block.
- [x] No fabricated status: failures write nothing; UNKNOWN remains a
  classification outcome only (ADR-062: no FAILED lifecycle invented).

## MCP

- [x] Freshness Resource keeps serving persisted state — detector writes
  checkpoints, resource reads them (runtime verified; no probe-on-read).
- [x] `get_engineering_context` exposes STALE metadata/warning automatically
  through Story 0091 integration (runtime verified:
  `PROJECT_CONTEXT_STALE`, repositoryRevision=Y, contextRevision=X). No
  synchronization was added to reads.

## Compatibility

- [x] Manual freshness check (`POST /freshness-checks`) untouched.
- [x] Normal understanding refresh untouched (full backend suite green).
- [x] Maintenance evaluation/remediation unaffected (suite green) and now
  benefits from automatically refreshed observations.
- [x] Kill-switch default ON but overridable; disabled context contains no
  scheduling processor bean (`RepositoryObservationConfigurationTest`).

## Notes

1. Single-instance deployment assumed; fixedDelay + single-thread scheduler
   prevent overlapping cycles (ADR-062-consistent; revisit with sync jobs).
2. Detector iterates all active Git sources — current scale trivially fine;
   bounded per-source cost (one ls-remote ≤ executor timeout).
3. A stale-while-probing window exists between HEAD movement and next cycle
   (≤ interval) — inherent to polling adapters, documented in ADR-062.
