# Story 0092 — Automatically Detect Repository HEAD Changes

## Status

In progress

## Date

2026-08-26

## Background

Story 0091 (`docs/stories/0091-project-freshness-checkpoints/`, merged as PR
#74) gave DevLog honest freshness checkpoints: per-source
`observedRevision`/`baselineRevision`, additive freshness metadata in
`get_engineering_context`, and the read-only MCP resource
`devlog://projects/{slug}/freshness`. **ADR-062** (primary architectural
authority) formalized the separation between *change detection*,
*synchronization* and *understanding*, and explicitly allowed “scheduled
probes as detection adapters” while forbidding polling-as-architecture.

Today `observedRevision` still advances only when a human clicks
“Check freshness”, runs a Maintenance remediation, or refreshes
understanding. DevLog cannot notice on its own that the world changed.

## Goal

> When the repository moves externally from X to Y, DevLog autonomously
> observes Y and freshness becomes STALE — with zero user action and zero
> knowledge advancement.

```text
baseline = X, observed = X          → CURRENT
external commit → HEAD = Y
detector observes Y (scheduled)     → observedRevision = Y, baseline = X
                                    → STALE
nothing else happens
```

DevLog knowledge remains at X until a future synchronization capability
explicitly advances it. Detection ≠ synchronization.

## Scope

### In scope (change detection ONLY)

1. **Revision probe adapter** — cheap immutable HEAD lookup per active Git
   source via `git ls-remote <url> <ref>` through the existing
   `GitCommandExecutor`; no clone, no fetch, no checkout, no reset, no
   working-tree mutation. Works identically for remote and local-path
   repositories (file/local transport needs no network).
2. **Freshness boundary extension** —
   `ProjectFreshnessService.recordObservedRevision(...)` recording an
   externally obtained observation through the EXISTING classification
   (`save()` + `findLatestComparable`), so CURRENT/STALE/NO_BASELINE/UNKNOWN
   semantics are preserved verbatim. Baseline is never touched by detection.
3. **Thin scheduled adapter** — `ScheduledRepositoryChangeDetector`
   iterating eligible sources (active `GIT_REPOSITORY`) on a configurable
   `fixedDelay`; orchestration only: probe → record → log. Per-source
   failure isolation (previous checkpoints preserved; no fabricated status;
   no UNKNOWN overwrite).
4. **Minimal configuration** — `devlog.repository-observation.{enabled,
   interval, initial-delay}` following repository conventions, with a
   conservative development default (5 minutes) and kill-switch.
5. Documentation updates where behavior actually changed.

### Out of scope (explicitly)

- No synchronization/import/diff/projection work; no Understanding trigger;
  no AI invocation; no proposals; no Maintenance remediation.
- No RepositorySyncJob / planner / queue / worker / retries / reaper.
- No domain-event infrastructure (direct application-service invocation).
- No distributed locking (single-instance assumption documented).
- No frontend changes (target 0 files).
- No fix for the hidden read-path Git synchronization (interaction analyzed
  and documented; detector is read-only so they cannot corrupt each other).
- No UI.

## Architectural authority

- **ADR-062 — Repository Synchronization Lifecycle and Freshness Checkpoints**
  (detection ≠ synchronization ≠ understanding; polling is an adapter).
- Story 0091 artifacts (checkpoint model, contract, resource).
- Investigation `docs/investigations/repository-synchronization-freshness.md`.
- ADR-041 (Accepted): passive monitoring must be provider-independent — the
  probe relies on plain Git plumbing, never on a hosting provider API.

## Success criteria

Core Story test: stored observed=X, baseline=X → probe returns Y →
persisted observed=Y, baseline=X, status STALE. Additionally: unchanged
observations stay CURRENT with advancing `checkedAt`; NO_BASELINE preserved;
multi-source projects derive PARTIALLY_FRESH through the existing contract;
one failing source never blocks others; disabled configuration probes nothing;
runtime validation proves the full loop including the negative proof that
`search_project_history` does NOT advance to Y automatically.
