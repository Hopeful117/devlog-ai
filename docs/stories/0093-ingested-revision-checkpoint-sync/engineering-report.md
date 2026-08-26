# Engineering Report — Story 0093

## Branch

`feature/story-0093-ingested-revision-checkpoint-sync` — based on `main @ de890f8` (Stories 0091/0092 merged). Not merged; left for human review/PR.

## Story

0093 — Introduce ingestedRevision Checkpoint and Deterministic Repository Synchronization Pipeline

## Engineering Assessment

### Problem Classification

**ARCHITECTURAL_LIFECYCLE_COMPLETION (B)** — implements the deterministic
synchronization tier that ADR-062 explicitly deferred: a durable job model, a
third per-source checkpoint, and the PARTIALLY_FRESH semantic that separates
"the world changed" (detection) from "I know what changed" (sync) from
"I understand what it means" (Understanding).

### Architecture Ownership

- **Checkpoint owner:** `projectfreshness` (entity column + classifier + the
  single `recordIngestedRevision` boundary).
- **Lifecycle owner:** `repositorysync.RepositorySyncJobStateService` (all
  durable transitions in short transactions).
- **Execution owner:** `repositorysync.RepositorySyncJobExecutor`
  (phase-separated pipeline; Git I/O transaction-free).
- **Trigger owner:** `ScheduledRepositoryChangeDetector` (schedules when
  observed ≠ ingested; never imports itself).
- **Persistence owners (reused):** existing history import
  (`ProjectHistoryService`) and workspace management (`WorkspaceManager`).

## DevLog MCP Findings Before Implementation

Against the verified `de890f8` deployment:

- `get_engineering_context(devlog-ai)` exposed the 0091 freshness block
  (`STALE`, observed `de890f8…`, baseline `a31ddba6…`) plus evidence almost
  exclusively from `GIT_HISTORY`/`COMMIT_DIFF` layers.
- Freshness metadata correctly framed X/Y reasoning; nothing described import
  internals or job infrastructure, so source inspection remained necessary.
- Known read-path gap (empty facts through `RepositoryContextAdapter`) again
  hid ADR/story/document evidence — explicitly NOT fixed here (strict non-goal;
  separate comparison investigation will precede any MCP context change).
- Story 0092's runtime proof stood as motivation: detected commit Y was absent
  from `search_project_history`.

## Synchronization Lifecycle Implemented

```text
Repository advances to immutable Y
        ↓ ScheduledRepositoryChangeDetector (ls-remote probe)
observedRevision = Y            (freshness row updated)
        ↓ observed != ingestedRevision && no active job
RepositorySyncJob PENDING       (from=ingested|null, to=Y SHA, reason)
        ↓ executor claim (short tx → RUNNING + snapshot)
Phase 1  synchronize workspace to Y — no DB tx held
         ancestry guard for incremental targets
Phase 2  deterministic import: commits + changed paths (own tx, SHA dedup)
Phase 3  recordIngestedRevision(Y) → markCompleted   (ordered!)
        ↓
PARTIALLY_FRESH  (observed=Y, ingested=Y, baseline=X)
        ↓ optional later Understanding
baselineRevision = Y → CURRENT
```

## Checkpoint Semantics (exact)

| Name | Meaning | Advanced by |
|---|---|---|
| `currentRevision` (observed) | revision observed from repository | detection / freshness checks |
| `ingestedRevision` | highest revision whose deterministic SYNC stages all persisted successfully | sync pipeline only |
| `baselineRevision` | revision represented by Understanding knowledge | Understanding only |

## Runtime Validation (deployed stack, end-to-end)

Rebuild/redeploy: `docker compose up -d --build backend`; Flyway applied
V44 ("Successfully validated 44 migrations … now at version v44");
application started cleanly; detector and executor schedulers active.

Lab: local bare repository inside the container workspace root
(`/var/lib/devlog-ai/workspaces/sync-lab.git`, file transport), registered as
a real project+source via the REST API. Timeline (server logs + SQL evidence):

1. **Baseline X established** — one sanctioned source-scoped Understanding run
   (`analysis b599bf14`, target resolved to `4ca4cd05…`). Detector then
   scheduled `INITIAL_IMPORT(NULL→4ca4cd05)` which executed
   **COMPLETED** → `ingestedRevision = 4ca4cd05`. State:
   `observed=X, ingested=X, baseline=X → CURRENT`.
2. **External commit Y introduced** (`a3ceb4db…`, "lab commit Y introduces
   deterministic sync proof") with zero user action on DevLog:
   - t+~195s detector: freshness → **STALE**, job
     `REPOSITORY_CHANGE_DETECTED(4ca4cd05→a3ceb4db)` PENDING;
   - t+~210s executor: **COMPLETED** → `ingestedRevision = a3ceb4db`;
   - freshness REST: `status=PARTIALLY_FRESH`,
     `guidance=REFRESH_RECOMMENDED`, observed=ingested=`a3ceb4db…`,
     baseline=`4ca4cd05…` — **not CURRENT**.
3. **search_project_history proof**: query "deterministic sync proof" →
   `totalMatches=1`, hit = Y's SHA with `devlog://projects/sync-lab-0093/
   commits/a3ceb4db…` resource. Changed paths persisted: `feature.txt ADDED
   +1/-0` via commit-context endpoint. No Refresh Understanding was run.
4. **No AI triggered by sync**: exactly **1** ai_task exists for the lab
   project — the sanctioned baseline analysis. Zero AI tasks attributable to
   detection/synchronization.
5. **Failure test (force-push rewrite)**: remote rewritten so ingested
   revision became unreachable (orphan root Z=`d4b37404…`). Job targeting Z →
   **FAILED** with sanitized message *"RepositorySyncDivergenceException:
   Repository history diverged: ingested revision a3ceb4db72ce is not an
   ancestor of target d4b374042a71; manual repair is required…"*.
   `ingestedRevision` unchanged at `a3ceb4db`; no persisted history deleted;
   freshness honestly STALE. Repair workflow remains documented out-of-scope.

An early iteration surfaced two defects fixed before final validation:
(a) scheduling nested under "observation changed" missed the behind-but-unchanged
case — scheduling is now keyed on `observed != ingested` with a regression test;
(b) a shell `Source` (active=false) was rejected by `GitWorkspaceManager` — the
claim snapshot now carries the fully loaded Source (join-fetch), covered by tests
and proven at runtime.

## Automated Test Results

```
./backend/mvnw -pl backend -am clean verify -B
Tests run: 920, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

New focused coverage: classifier PARTIALLY_FRESH matrix, persistence
checkpoint semantics, detector scheduling matrix (initial/forward/duplicate/
caught-up/behind-stable), executor phase ordering (InOrder), failure &
divergence safety, credential redaction, foreign-claim skip, moving-HEAD
immutability.

## Quality Pipeline Result

- Compilation: PASS (contracts + backend)
- Unit/integration tests: 920/920 green (incl. Postgres integration tests)
- JaCoCo coverage gates: ALL MET
- `git diff --check`: clean

## Remaining Limitations / Technical Debt

1. History import still performs a full walk + post-hoc dedup per job
   (correct but O(total commits)); explicit `X..Y` range optimization deferred
   pending merge/rewrite correctness proof.
2. Force-push repair workflow not implemented — divergence fails safely and
   requires manual intervention by design (V1).
3. Startup requeue assumes single-instance deployment (carried from 0092);
   multi-instance claiming would need lease semantics.
4. `MANUAL_SYNC`/`RECOVERY` reasons exist in the vocabulary but have no
   producer yet; recovery currently means automatic re-scheduling by the
   detector while ingestion lags observation.
5. Hidden read-time synchronization in `RepositoryContextEngine` remains
   (pre-existing debt); the pinned-revision prerequisite now exists for its
   future removal.
6. Existing Refresh Understanding still duplicates repository preparation;
   future decomposition should call `ensure synchronized(target)` first.

## ADR Assessment

**NO NEW ADR CREATED BY THIS STORY.**

The lifecycle implements the direction already sanctioned by ADR-062
(synchronization lifecycle + freshness checkpoints) without contradicting
ADR-041/ADR-061. Whether the concrete checkpoint/job semantics now warrant a
dedicated ADR-063 is left as a human governance decision (ADR-006 process);
this Story provides the evidence base but does not self-assign architectural
authority.

## Git Hygiene

- Production files: 8 modified, 4 new (one package); 1 migration added.
- Test files: 7 modified, 1 new package.
- Story artifacts: `docs/stories/0093-ingested-revision-checkpoint-sync/`
  (story.md, repository-analysis.md, implementation-plan.md,
  implementation-report.md, engineering-report.md, code-review.md).
- No `.env`, IDE files, build `target/` output, or unrelated changes staged.
