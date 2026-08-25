# Story 0092 — Implementation Plan

Date: 2026-08-26 · Authority: ADR-062 · Feeds Story 0091 checkpoint model.

## Step 1 — Configuration

`application.properties` (conventions preserved):

```properties
# ADR-062 / story-0092: automatic HEAD change detection.
# Detection ONLY — never synchronization, understanding or AI.
devlog.repository-observation.enabled=${REPOSITORY_OBSERVATION_ENABLED:true}
devlog.repository-observation.interval=${REPOSITORY_OBSERVATION_INTERVAL:300s}
devlog.repository-observation.initial-delay=${REPOSITORY_OBSERVATION_INITIAL_DELAY:30s}
```

New record `RepositoryObservationProperties`
(`@ConfigurationProperties("devlog.repository-observation")`: `enabled`,
`Duration interval`, `Duration initialDelay`) registered beside the
scheduling config, mirroring `AIEngineProperties`.

## Step 2 — Probe port + ls-remote adapter

New package `repositoryobservation`:

```java
public interface RepositoryRevisionProbe {
    String probeHead(Source source);   // immutable HEAD SHA; throws on failure
}
```

`LsRemoteRepositoryRevisionProbe implements RepositoryRevisionProbe`
(`@Component`, reuses `GitCommandExecutor`; working dir = configured
`collection.workspace-root`, created on demand):

- ref = `refs/heads/<defaultBranch>` when a branch is configured, else
  symbolic `HEAD` (§9: no “main” assumption; provider-independent);
- parse `<SHA>\t<ref>`, normalize 40/64-hex lowercase;
- empty/invalid output → `GitCommandException` (no fabricated revision);
- never logs the repository URL.

## Step 3 — Freshness boundary extension

`ProjectFreshnessService.recordObservedRevision(projectId, sourceId,
observedRevision)`:

- same validation guards as `check()` (project exists, active GIT_REPOSITORY
  source);
- delegates to the existing `persistence.save(...)` → classification against
  `findLatestComparable` (CURRENT/STALE/NO_BASELINE/UNKNOWN semantics
  unchanged); `checkedAt = now`;
- invalid identity failures wrapped like `check()` does.

Baseline untouched by construction: only understanding/synchronization paths
call `recordObservedBaseline`.

## Step 4 — Scheduled adapter

- `SourceRepository`: add `@EntityGraph(attributePaths = {"project"})
  List<Source> findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(SourceType type)`.
- `ScheduledRepositoryChangeDetector` (`@Component`): method
  `detectRepositoryChanges()` annotated
  `@Scheduled(fixedDelayString = "${devlog.repository-observation.interval:300s}",
  initialDelayString = "${devlog.repository-observation.initial-delay:30s}")`.
  Per eligible source: read previous row (`latest`) → probe →
  `recordObservedRevision` → log INFO only when the observed SHA changed,
  DEBUG otherwise; any RuntimeException → WARN with previous checkpoint
  explicitly preserved; cycle continues to next source.
- `RepositoryObservationConfiguration`:
  `@Configuration @EnableScheduling @EnableConfigurationProperties(...)`
  guarded by `@ConditionalOnProperty(prefix = "devlog.repository-observation",
  name = "enabled", havingValue = "true", matchIfMissing = false)`.
  fixedDelay chosen over fixedRate (§16 non-overlap, single-thread default
  scheduler, single-instance assumption documented).

## Step 5 — Tests

| # | Requirement | Test |
|---|---|---|
| §37 | probe uses ls-remote, never fetch/pull/checkout/reset | `LsRemoteRepositoryRevisionProbeTest` command capture |
| §9 | branch ref vs HEAD fallback; SHA normalization; unknown-ref failure | same file |
| §30 | unchanged X → CURRENT, checkedAt advances | persistence/classifier (existing) + service delegation test |
| §31 | X stored, probe Y → observed Y / baseline X / STALE | `recordObservedRevision` service test (+ runtime) |
| §32 | no baseline → NO_BASELINE preserved | classifier tests exist + service guard test |
| §33 | multi-source mixed | `ScheduledRepositoryChangeDetectorTest` orchestration (+ aggregate via mapper tests from 0091) |
| §34 | failure isolation | detector test: probe A throws, B recorded, A untouched |
| §35 | repeated observation idempotent | detector test double-cycle |
| §36 | disabled config probes nothing | `ApplicationContextRunner` bean-absence test |
| §24 | eligibility active GIT_REPOSITORY only | query shape + detector unit test |
| §20 | baseline never advanced by detection | asserted in service tests (save args) |

## Step 6 — Docs

`docs/architecture.md`: short “Automatic repository observation” note —
detection ≠ synchronization; STALE is an observation, not a command. Story
artifacts completed afterwards.

## Commit plan

1. `docs(story): define automatic HEAD change detection story 0092`
2. `feat(observation): add ls-remote repository revision probe`
3. `feat(freshness): record externally observed revisions through freshness boundary`
4. `feat(observation): schedule per-source HEAD change detection`
5. `test(observation): cover probe, isolation, idempotence and kill-switch`
6. `docs(story): complete engineering reports for story 0092`

## Quality gates

Backend full `verify` (+JaCoCo), mcp-server `verify`, `git diff --check`,
frontend untouched. Runtime validation per mission §39 with ephemeral stack:
interval forced to ~15–30 s, DevLog-tracked throwaway clone, external commit Y,
zero manual actions, negative proof via commit search.
