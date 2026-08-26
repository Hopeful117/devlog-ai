# Story 0092 — Implementation Report

Date: 2026-08-26 · Branch: `feature/story-0092-detect-repository-head-changes`
· Base: `4dcd0dc` (main incl. merged 0091) · Authority: ADR-062.

## 1. What was implemented

### Configuration (conventions preserved)

`application.properties`:

```properties
devlog.repository-observation.enabled=${REPOSITORY_OBSERVATION_ENABLED:true}
devlog.repository-observation.interval=${REPOSITORY_OBSERVATION_INTERVAL:300s}
devlog.repository-observation.initial-delay=${REPOSITORY_OBSERVATION_INITIAL_DELAY:30s}
```

Typed record `RepositoryObservationProperties` (`enabled`, `interval`,
`initialDelay`), registered via `@EnableConfigurationProperties`.

### Probe port + ls-remote adapter

New package `repositoryobservation`:

- `RepositoryRevisionProbe` — port: “observe the current immutable HEAD
  revision; strictly read-only; detection produces observations only”.
- `LsRemoteRepositoryRevisionProbe implements RepositoryRevisionProbe` —
  `git ls-remote <repositoryUrl> refs/heads/<defaultBranch>` (or symbolic
  `HEAD` when no branch configured); parses `<SHA>\t<ref>`, normalizes
  40/64-hex lowercase; empty/invalid output → `GitCommandException`
  (never fabricates). Reuses `GitCommandExecutor`
  (`GIT_TERMINAL_PROMPT=0`, timeout) — same credential/transport behavior as
  clone; repository URLs never logged. No clone/fetch/pull/checkout/reset;
  works identically for remote and local/file transports.
- `GitCommitIdentity` made public (pure normalization helper, previously
  package-private).

### Freshness boundary extension

`ProjectFreshnessService.recordObservedRevision(projectId, sourceId,
observedRevision)` — records an externally obtained observation through the
EXISTING `persistence.save(...)` classification against `findLatestComparable`.
CURRENT/STALE/NO_BASELINE/UNKNOWN semantics unchanged; `checkedAt = now`;
baseline untouched by construction (only understanding/synchronization calls
`recordObservedBaseline`). Same validation guards as `check()`.

### Scheduled adapter

- `SourceRepository.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(...)`
  with `@EntityGraph("project")` (lazy-safe outside transactions).
- `ScheduledRepositoryChangeDetector` (`@Scheduled`,
  **fixedDelay** `${devlog.repository-observation.interval:300s}`, initial
  delay configurable): per eligible source — read previous checkpoint → probe
  → record → log. INFO only when observed SHA changed (short forms, never the
  URL); DEBUG for unchanged cycles; WARN + full preservation of the previous
  checkpoint on any failure; one source’s failure never stops the cycle.
- `RepositoryObservationConfiguration`: `@Configuration @EnableScheduling
  @ConditionalOnProperty(... havingValue="true", matchIfMissing=false)` —
  kill-switch removes all scheduling infrastructure from the context.

## 2. Runtime validation (executed live)

Ephemeral stack: Postgres 17 container (15432), local uvicorn ai-engine
(18001), backend on 18099 with `REPOSITORY_OBSERVATION_INTERVAL=15s`,
`INITIAL_DELAY=20s`. Tracked repo: throwaway clone in gitignored
`backend/target/demo-clone`, X = `3a749c7fc13d`. The developer’s own Docker
stack (18080–18083) untouched.

| # | Action | Result |
|---|---|---|
| 1 | project+source created; wait one detector cycle | **detector recorded autonomously**: `NO_BASELINE / ESTABLISH_BASELINE, observed=X` — no human action |
| 2 | understanding refresh at X | `CREATED @ targetRevision=3a749c7…` |
| 3 | freshness latest | **CURRENT**, observed=baseline=X |
| 4 | external empty commit Y=`1ace633…`; wait ~40 s (2 cycles); NO manual call | freshness latest → **STALE / REFRESH_RECOMMENDED**, observed=Y, baseline=X, checkedAt auto-advanced |
| 5 | resource payload (`freshness-checks/summary`) | per-source STALE with Y/X |
| 6 | `get_engineering_context?intent=story 0092 validation` | metadata: `freshness.status=STALE`, `repositoryRevision=Y(1ace633…)`, `contextRevision=X(3a749c7…)`, warnings include **PROJECT_CONTEXT_STALE** |
| 7 | **negative proof**: commit search `query="external head change"` (the message of Y) | **totalMatches: 0** — persisted knowledge NOT advanced to Y |
| 8 | control: search X-era term “freshness” | totalMatches: 20 (existing knowledge intact) |

Detection ≠ synchronization demonstrated end-to-end. Environment cleaned
(processes stopped, container removed, demo clone/workspaces deleted).

## 3. Quality gates

- `mvn -pl backend verify` — **exit 0**, JaCoCo ≈ **85 %**
  (7 547 missed of 51 098 instructions).
- `mvn -pl mcp-server verify` — **exit 0**.
- `git diff --check` — clean. Frontend untouched (0 files).
