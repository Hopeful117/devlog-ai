# Story 0093 — Introduce ingestedRevision Checkpoint and Deterministic Repository Synchronization Pipeline

## Status

**READY_FOR_COMMIT_APPROVAL**

## Objective

Close the gap proven by Stories 0091/0092 between change detection and knowledge:

```text
CHANGE_DETECTION != SYNCHRONIZATION != UNDERSTANDING
```

DevLog can observe that a repository advanced from X to Y (0092) and can
represent that it is STALE (0091), but nothing deterministically advances the
persisted repository truth toward Y without running the entire Understanding
pipeline. This Story introduces:

1. an explicit per-source **ingestedRevision** checkpoint — the highest
   immutable revision for which all deterministic SYNC stages completed;
2. a durable **RepositorySyncJob** lifecycle executing the minimum
   deterministic sync payload (commit metadata + changed paths);
3. detector integration: an observed advance beyond ingestedRevision schedules
   a job targeting the immutable observed SHA;
4. the **PARTIALLY_FRESH** freshness state for
   `observed = Y, ingested = Y, baseline = X`.

## Problem

After 0092, detection advances only the observation checkpoint:

```text
Repository = Y, Observed = Y, Ingested = (absent), Baseline = X → STALE
```

`search_project_history` cannot find commit Y because no deterministic stage
imports it. The only path that imports it today is Refresh Understanding,
which conflates Git synchronization, collection, profiling and AI work, and
requires a user action plus AI cost. ADR-062 explicitly deferred the
synchronization lifecycle ("detection only"); this Story implements that
deferred lifecycle as the smallest coherent vertical slice.

## Resolution

### Checkpoint semantics (per Project + Source)

| Checkpoint | Meaning | Advanced by |
|---|---|---|
| `currentRevision` (observed) | revision observed from the repository | detection / freshness checks |
| `ingestedRevision` | highest revision whose deterministic SYNC completed | sync pipeline only, after successful persistence |
| `baselineRevision` | revision represented by Understanding knowledge | Understanding only |

Observations never mutate `ingestedRevision`; deterministic sync never mutates
`baselineRevision` or any knowledge state.

### RepositorySyncJob (durable, source-scoped)

Fields: `id, project, source, fromRevision (nullable), toRevision (immutable
SHA), reason, status, attempt, createdAt, startedAt, completedAt, failure`.

Lifecycle: `PENDING → RUNNING → COMPLETED`, `PENDING/RUNNING → FAILED`.

Reasons: `REPOSITORY_CHANGE_DETECTED, MANUAL_SYNC, RECOVERY, INITIAL_IMPORT`
(only `REPOSITORY_CHANGE_DETECTED` and `INITIAL_IMPORT` are produced in this
Story; the enum documents the justified vocabulary).

### Pipeline phases (no transaction across Git/network I/O)

```text
Phase 1  Git I/O outside any transaction:
         synchronize workspace to toRevision; ancestry guard for incremental targets
Phase 2  Deterministic persistence (own transaction):
         existing history import — commit metadata + changed paths, SHA dedup
Phase 3  Durable outputs (own transaction, ordered):
         recordIngestedRevision FIRST, then job COMPLETED
```

Checkpoint-before-completion ordering guarantees a crash between Phase 3 steps
can never lose a completed ingestion (replay is SHA-dedup idempotent).

### Detector integration

When detection observes a revision different from `ingestedRevision` and no
PENDING/RUNNING job exists for the Source, one job is scheduled targeting the
immutable observed SHA (`INITIAL_IMPORT` when nothing was ingested yet). The
detector remains lightweight: it never executes Git history operations.

### Freshness classification

```text
no baseline                                  → NO_BASELINE
baseline == observed                         → CURRENT
baseline != observed AND ingested == observed → PARTIALLY_FRESH
otherwise                                    → STALE
```

PARTIALLY_FRESH means exactly: *deterministic repository state is synchronized
to the observed target; Understanding has not yet advanced to that revision.*
CURRENT is never reported merely because deterministic history synchronized.

## Scope

### IN SCOPE

- `project_source_freshness.ingested_revision` column + projection field
- `ProjectFreshnessStatus.PARTIALLY_FRESH` + classifier semantics
- `repository_sync_jobs` table, entity, repository, state service, executor
- Scheduled claimer with explicit phase-separated transactions
- Startup recovery requeue of jobs left RUNNING by a dead process
- Force-push ancestry guard (`merge-base --is-ancestor`) failing safely
- Failure sanitization (no URLs/credentials persisted in job failure)
- Flyway migration V44
- Focused unit tests for every invariant above

### OUT OF SCOPE

- Automatic Understanding after sync; significance gating policy
- MCP tools/Resources for synchronization; `sync_project`
- RepositoryContextAdapter facts fix; get_engineering_context profiles
- Cockpit "New Analysis" behavior
- Explicit `rev-list X..Y` range import optimization (full-walk + dedup kept;
  documented as remaining debt)
- Force-push repair workflow (detection-only, fails safely)
- Rewriting Refresh Understanding decomposition

## Non-Negotiable Invariants

1. Synchronization is deterministic: no LLM/AI/proposal/significance code.
2. Targets are immutable SHAs, never branch names.
3. Jobs are per-source; one changing source never rebuilds another.
4. `ingestedRevision` advances ONLY after all SYNC stages persist successfully.
5. A failed job leaves `ingestedRevision` unchanged.
6. Sync never advances `baselineRevision` or any knowledge state.
7. No database transaction spans Git/network operations in the new pipeline.
8. Replays are idempotent via existing `(source_id, commit_hash)` uniqueness.
9. Reads gain no additional hidden mutations.
10. Failure details are sanitized before persistence.

## References

- ADR-041 — Passive Project Monitoring
- ADR-061 — Deterministic Repository Observation Baselines
- ADR-062 — Repository Synchronization Lifecycle and Freshness Checkpoints
- Investigation — docs/investigations/deterministic-repository-synchronization.md
- Story 0090 — search_project_history
- Story 0091 — project freshness checkpoints
- Story 0092 — detect repository HEAD changes
