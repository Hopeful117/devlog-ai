# Story 0091 — Project Freshness Checkpoints

## Status

In progress

## Date

2026-08-26

## Background

The architecture investigation
[`docs/investigations/repository-synchronization-freshness.md`](../../investigations/repository-synchronization-freshness.md)
(2026-08-26) established that DevLog knowledge advances only through explicit
user-triggered refreshes while `get_engineering_context` silently mixes
live-HEAD structure evidence with stale persisted knowledge — with no
freshness signal of any kind. It also identified a concrete defect: the
freshness baseline query (`findLatestComparable`, story 0021) selects
snapshots whose analysis has `targetRevision IS NULL`, but story 0085 now
always persists the resolved revision, so new understanding snapshots can
never become freshness baselines.

This story implements the investigation's recommended first step:
**Project Freshness Checkpoints**. It is the foundation layer that makes
DevLog able to say honestly which revision of project truth it knows.

## Goal

> After this story, DevLog must not silently present a stale knowledge
> baseline as current when it knows the observed repository revision differs.

Concretely:

```text
repository observed = Y
context baseline    = X (Y != X)

get_engineering_context
→ freshnessStatus = STALE (or PARTIALLY_FRESH across sources)
→ repositoryRevision = Y
→ contextRevision = X
→ warning PROJECT_CONTEXT_STALE

devlog://projects/{slug}/freshness
→ exact per-source freshness state

repository = X, baseline = X
→ freshness CURRENT, no extra Maintenance click needed
```

## Scope

### In scope

1. **ADR-062** — *Repository Synchronization Lifecycle and Freshness
   Checkpoints* (Proposed): formalizes change detection ≠ synchronization ≠
   understanding; per-source freshness checkpoints; read purity direction;
   polling-is-an-adapter; no silent mixed revisions.
2. **Baseline comparability repair** — `findLatestComparable` must select
   post-0085 snapshots (revision-pinned analyses, including `IN_PROGRESS`
   ones whose deterministic snapshot exists) instead of requiring
   `targetRevision IS NULL`. Legacy null-revision snapshots remain eligible.
3. **Checkpoint advancement without re-probing** — after a successful
   understanding execution at resolved revision X, the freshness checkpoint is
   recorded from the already-known revision (no second fetch/rev-parse), so
   freshness reports `CURRENT` immediately.
4. **Freshness in the engineering-context contract** — additive
   `EngineeringContextMetadata` extension: aggregate `freshnessStatus`
   (including derived `PARTIALLY_FRESH`), `repositoryRevision`,
   `contextRevision`, per-source breakdown, and warnings
   `PROJECT_CONTEXT_STALE` / `PROJECT_CONTEXT_PARTIALLY_FRESH`.
5. **MCP resource** — `devlog://projects/{projectSlug}/freshness`, read-only,
   backed by a new backend summary endpoint reusing
   `ProjectFreshnessService.summary`; follows the stories 0088/0089 resource
   conventions (URI factory + anti-drift tests).

### Out of scope (explicitly)

- No scheduler, no polling detector, no `RepositoryChanged` event.
- No SynchronizationPlanner, no RepositorySyncJob, no retries/reaper.
- No webhooks / Git hooks / Workspace events.
- No automatic synchronization, automatic understanding or automatic AI.
- No Maintenance redesign; no frontend changes (target: 0 frontend files).
- No fix for the `PROJECTION_REFRESH_GAP` dead enum (documented debt).
- No wiring for the orphaned `TemporalAssessmentService`.
- No removal of the hidden read-path Git synchronization (CASE B: documented,
  made observable; removal deferred to its own story).

## Design source

- Investigation report §8-B1 (drift), §11/§17 (checkpoints), §20–§22
  (detection vs sync lifecycle), §24–§25 (read honesty), §28 (first step),
  ADR audit §31.

## Success criteria

- Drift test: a post-0085 completed understanding analysis with non-null
  `targetRevision` becomes a freshness baseline (test would fail before this
  story).
- After successful understanding at X → `GET .../freshness-checks/latest`
  references X as both current and baseline with status CURRENT.
- Stale scenario: knowledge at X, repository observed at Y → context response
  carries STALE/PARTIALLY_FRESH status, revisions, and explicit warning;
  nothing synchronizes automatically.
- Resource returns per-source state, project-scoped, read-only.
- All existing quality gates pass; additive JSON compatibility preserved.
