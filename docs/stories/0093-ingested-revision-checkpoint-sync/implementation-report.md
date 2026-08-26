# Implementation Report — Story 0093

## Branch

`feature/story-0093-ingested-revision-checkpoint-sync` (base: `main @ de890f8`)

## Story

0093 — Introduce ingestedRevision Checkpoint and Deterministic Repository Synchronization Pipeline

## Status

READY_FOR_COMMIT_APPROVAL

## Production Changes

### New package `repositorysync`

1. `RepositorySyncJob.java` — durable per-source job entity
   (`repository_sync_jobs`, V44): `id/project/source/fromRevision(nullable)/
   toRevision(NOT NULL immutable SHA)/reason/status/attempt/createdAt/
   startedAt/completedAt/failure`; enums `SyncReason`
   {REPOSITORY_CHANGE_DETECTED, MANUAL_SYNC, RECOVERY, INITIAL_IMPORT} and
   `SyncStatus` {PENDING, RUNNING, COMPLETED, FAILED}.
2. `RepositorySyncJobRepository.java` — pending queue ordering, RUNNING
   lookup, per-source active-job existence guard.
3. `RepositorySyncJobStateService.java` — owns all durable transitions inside
   short transactions: `claim()` (RUNNING + immutable `SyncTarget` snapshot
   carrying the fully loaded Source), `markCompleted()`, `markFailed()`, and
   startup recovery `requeueInterruptedJobs()`
   (`ApplicationReadyEvent`: RUNNING → PENDING).
4. `RepositorySyncJobExecutor.java` — scheduled claimer with explicit phases:
   - Phase 1 (no transaction): `workspaceManager.synchronize(source,
     toRevision)` + ancestry guard `git merge-base --is-ancestor from to`
     (divergence → sanitized failure before any import);
   - Phase 2 (own tx): existing deterministic history import
     (`ProjectHistoryService.importHistory(source, workspace)`, SHA dedup);
   - Phase 3: `freshnessService.recordIngestedRevision(resolvedRevision)`
     BEFORE `state.markCompleted(jobId)` so a crash can never lose a completed
     ingestion;
   - failures: `markFailed(jobId, sanitize(exception))` — exception simple
     name + message with `scheme://…` redacted, bounded to 300 chars.

### Modified — freshness model

5. `ProjectFreshnessStatus.java` — added `PARTIALLY_FRESH`.
6. `ProjectFreshnessClassifier.java` — new 4-input overload
   `classify(baselineExists, current, baseline, ingested)`:
   NO_BASELINE / UNKNOWN unchanged; CURRENT when knowledge == observed;
   PARTIALLY_FRESH iff ingestion caught up while knowledge behind; else STALE.
7. `ProjectSourceFreshness.java` — `ingested_revision` column mapping.
8. `ProjectFreshnessPersistenceService.java` — observations preserve stored
   ingestion state (never wipe/regress); new `recordIngestedRevision(...)`
   normalizes SHA, requires an existing checkpoint row, reclassifies status.
9. `ProjectFreshnessService.java` — exposes `recordIngestedRevision(...)` as
   the ONLY ingestion-advancement path.
10. `ProjectFreshnessResponse.java` — additive `Source.ingestedRevision`.

### Modified — detector integration

11. `ScheduledRepositoryChangeDetector.java` — after every observation,
    schedules a job when `observed != ingestedRevision` (not only on observed
    change) and no PENDING/RUNNING job exists for the source; reason is
    `INITIAL_IMPORT` when nothing was ingested yet, otherwise
    `REPOSITORY_CHANGE_DETECTED`. Detector performs no Git history work.

### Modified — infrastructure

12. `SourceRepository.java` — `findWithProjectById` join-fetch for detached,
    lazy-safe Phase 1 usage.
13. `V44__add_ingested_revision_and_repository_sync_jobs.sql` — additive
    column + new table (FK cascade to projects/sources, two indexes).

## What Is NOT Changed

- Understanding/AI/collection/profile/proposal code paths — untouched.
- MCP tools/resources/contracts, `RepositoryContextAdapter`,
  `EngineeringContextFacade*` — untouched.
- Cockpit / frontend — untouched.
- Existing Refresh Understanding workflow — untouched (decomposition deferred).
- History import internals (full walk + SHA dedup kept; range optimization
  documented as remaining debt).

## Transaction Boundaries (actual)

| Phase | Content | Transaction |
|---|---|---|
| claim | load job+source, PENDING→RUNNING | short tx (state service) |
| 1 | fetch/checkout to immutable SHA | **no tx held** |
| 2 | commit metadata + changed paths persist | own tx (existing service) |
| 3a | ingestedRevision advance + reclassification | short tx |
| 3b | job COMPLETED/FAILED | short tx |

No database transaction spans Git/network operations anywhere in the pipeline.

## Test Results

Focused suites:

```
ProjectFreshnessClassifierTest ............ 5 tests  PASS
ProjectFreshnessClassifierTest (new cases)  PARTIALLY_FRESH transition, STALE retained, CURRENT independent
ProjectFreshnessPersistenceServiceTest .... 4 tests  PASS
ProjectFreshnessServiceTest ............... 5 tests  PASS
ScheduledRepositoryChangeDetectorTest ..... 8 tests  PASS
RepositorySyncJobExecutorTest ............. 7 tests  PASS
```

Full pipeline (`./backend/mvnw -pl backend -am clean verify -B`):

```
Tests run: 920, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

## Invariant Verification

- [x] Sync is deterministic — zero AI/LLM/significance code in call graph
- [x] Immutable SHA targets only (`to_revision NOT NULL`; branch never stored)
- [x] Per-source isolation (jobs/checkpoints keyed by `source_id`)
- [x] `ingestedRevision` advances only via `recordIngestedRevision` after persistence succeeds
- [x] Failure leaves checkpoint untouched (unit test + runtime force-push proof)
- [x] Sync never touches `baselineRevision` (asserted in persistence test)
- [x] No tx across Git I/O (phase structure above)
- [x] Replay idempotent ((source_id, commit_hash) uniqueness)
- [x] Reads gained no hidden mutations (only new writes are job lifecycle + explicit checkpoint API)
- [x] Failure sanitization unit-tested (credential redaction)
- [x] Crash recovery requeues RUNNING zombies at startup

## Runtime Validation Summary (details in engineering-report.md)

Local bare-repository lab inside the deployed stack proved the full lifecycle:
baseline X (observed=ingested=baseline=X, CURRENT) → external commit Y →
detector STALE + job X→Y → executor COMPLETED → `ingestedRevision=Y`,
**PARTIALLY_FRESH**, `search_project_history` finds Y, changed paths queryable,
AI untouched (1 task total, from the sanctioned baseline Understanding).
Force-push rewrite Z: job FAILED ("not an ancestor"), checkpoint preserved.
Lab project deactivated after the experiment.
