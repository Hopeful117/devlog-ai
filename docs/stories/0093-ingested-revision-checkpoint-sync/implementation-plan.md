# Implementation Plan — Story 0093

## Summary

Introduce the per-source `ingestedRevision` checkpoint and the deterministic
`RepositorySyncJob` pipeline that advances it, integrated with the Story 0092
detector, without touching Understanding/AI/MCP behavior.

## Production Changes

### 1. Migration — `backend/src/main/resources/db/migration/V44__add_ingested_revision_and_repository_sync_jobs.sql`

- `ALTER TABLE project_source_freshness ADD COLUMN ingested_revision VARCHAR(64);`
- `CREATE TABLE repository_sync_jobs (…)` with FK cascade to
  projects/sources, indexes on `(project_id)` and `(source_id, status)`.

### 2. Freshness model — package `projectfreshness`

- `ProjectSourceFreshness`: add `ingestedRevision` column mapping.
- `ProjectFreshnessStatus`: add `PARTIALLY_FRESH`.
- `ProjectFreshnessClassifier`: new overload
  `classify(baselineExists, current, baseline, ingested)`:
  NO_BASELINE / UNKNOWN unchanged; CURRENT when knowledge == observed;
  else PARTIALLY_FRESH iff normalized ingested == observed; else STALE.
- `ProjectFreshnessPersistenceService`:
  - `save(...)` preserves the stored `ingested_revision` (observations never
    mutate ingestion) and classifies with the 4-input overload;
  - new `recordIngestedRevision(projectId, sourceId, revision, checkedAt)`
    — normalizes SHA, requires an existing checkpoint row, reclassifies.
- `ProjectFreshnessService`: expose `recordIngestedRevision(...)`; existing
  callers unchanged (5-arg `save` restored).
- `ProjectFreshnessResponse.Source`: add `ingestedRevision` field
  (projection version stays `project-freshness-v1`; additive field).

### 3. Sync job domain — new package `repositorysync`

- `RepositorySyncJob` entity: id/project/source/fromRevision(nullable)/
  toRevision(NOT NULL immutable SHA)/reason/status/attempt/createdAt/
  startedAt/completedAt/failure; enums `SyncReason`
  {REPOSITORY_CHANGE_DETECTED, MANUAL_SYNC, RECOVERY, INITIAL_IMPORT},
  `SyncStatus` {PENDING, RUNNING, COMPLETED, FAILED}.
- `RepositorySyncJobRepository`: pending queue query, RUNNING lookup,
  per-source existence guard for {PENDING, RUNNING}.
- `RepositorySyncJobStateService` (@Transactional boundaries):
  `claim(jobId)` → RUNNING + `SyncTarget` snapshot (or null if no longer
  claimable); `markCompleted(jobId)`; `markFailed(jobId, sanitizedFailure)`;
  `@EventListener(ApplicationReadyEvent) requeueInterruptedJobs()`
  RUNNING → PENDING (crash recovery).
- `RepositorySyncJobExecutor` (@Scheduled, default 60s/15s):
  Phase 1 `workspaceManager.synchronize(source, toRevision)` + ancestry guard
  (`git merge-base --is-ancestor from to`, divergence → sanitized failure,
  no import, checkpoint untouched);
  Phase 2 `historyService.importHistory(source, workspace)` (existing dedup);
  Phase 3 `freshnessService.recordIngestedRevision(resolvedRevision)` THEN
  `state.markCompleted(jobId)`;
  catch-all: `state.markFailed(jobId, sanitize(failure))` with URL redaction.

### 4. Detector integration — `repositoryobservation`

`ScheduledRepositoryChangeDetector`: after recording an advance, read
persisted `ingestedRevision`; if behind observation and no active job exists,
schedule one targeting the immutable observed SHA with reason
INITIAL_IMPORT (nothing ingested yet) or REPOSITORY_CHANGE_DETECTED.
Detector performs no Git history work.

## Test Changes

| File | Coverage |
|---|---|
| `ProjectFreshnessClassifierTest` | PARTIALLY_FRESH transition; STALE retained while ingestion behind; CURRENT independent of ingestion |
| `ProjectFreshnessPersistenceServiceTest` | ingestion advanced only via `recordIngestedRevision`; PARTIALLY_FRESH classification against real snapshot; unknown checkpoint rejected |
| `ScheduledRepositoryChangeDetectorTest` | initial-import scheduling (null from, immutable target); forward scheduling; duplicate suppression while PENDING/RUNNING exists; no job when caught up |
| `RepositorySyncJobExecutorTest` | phase order (import → checkpoint → COMPLETED via InOrder); initial import skips ancestry check; failure never advances checkpoint; divergence fails safely pre-import; credential redaction; foreign claim skipped; moving HEAD never mutates target |

## Behavior Change

Before: detection only flips freshness to STALE; deterministic repository
truth stays at X until a user runs Refresh Understanding (with AI cost).

After: detection additionally schedules a durable job; the pipeline
deterministically imports commit metadata + changed paths up to the observed
immutable SHA, advances `ingestedRevision`, and freshness becomes
PARTIALLY_FRESH — with zero AI involvement and no user action. Only a later
Understanding run can reach CURRENT.

## Rollback/Safety

Revert commits remove the new table/column consumers; V44 is additive
(additive column + new table), so downgrade of code alone leaves inert data.
Feature is passive by default: nothing outside the detector/executor writes
jobs; no API surface changes.

## Non-Goals

Explicit X..Y range optimization · force-push repair workflow · MCP sync tool
· significance policy · Refresh Understanding decomposition ·
RepositoryContextAdapter fixes · Cockpit fixes.
