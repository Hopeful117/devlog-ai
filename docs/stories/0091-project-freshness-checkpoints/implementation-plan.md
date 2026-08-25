# Story 0091 — Implementation Plan

Date: 2026-08-26 · Design sources: investigation report §8-B1, §11, §17–§25,
§28; ADR-062.

## Step 1 — ADR-062 (done first)

`docs/decisions/ADR-062.md` — Proposed. Formalizes: change detection ≠
synchronization ≠ understanding; per-source checkpoints
(`observedRevision` / `baselineRevision`); reuse of existing enums; no
re-probe rule; single ownership point; no-silent-stale-context invariant;
read-purity direction; polling-as-adapter; future lifecycle intention only.

## Step 2 — Baseline comparability repair

`ProjectProfileSnapshotRepository.findLatestComparable`:

- Remove `analysis.targetRevision is null`.
- Replace `status = COMPLETED` with
  `status in (COMPLETED, IN_PROGRESS)` — a snapshot exists ⇒ the deterministic
  phase produced it at a known revision (ADR-061: provenance valid once
  observation occurred; AI completion must not gate deterministic freshness).
- Order by `analysis.createdAt desc, analysis.id desc` (createdAt always set;
  avoids NULLS-FIRST ambiguity of `completedAt desc` on Postgres).
- FAILED/PENDING remain excluded (compensated/not-yet-built runs must not
  anchor freshness).

Mandatory regression test (fails before this story): post-0085 completed
understanding analysis with non-null targetRevision + snapshot resolved
revision → found as baseline. Plus IN_PROGRESS eligibility and FAILED
exclusion coverage.

## Step 3 — Checkpoint advancement on understanding refresh

- `ProjectFreshnessPersistenceService.recordObservation(projectId, sourceId,
  observedRevision, baselineAnalysisId, checkedAt)`: upsert row with
  `current = baseline = normalize(observed)`, `status = CURRENT`,
  `guidance = REFRESH_NOT_NEEDED`, `requestedRevision = "origin/<defaultBranch>"`
  (mirrors `GitWorkspaceManager` convention), `baselineAnalysis` linked.
- `ProjectFreshnessService.recordObservedBaseline(...)` public boundary:
  validates project + active GIT_REPOSITORY source (same guards as `check`),
  delegates to persistence. Single ownership point (ADR-062 §4).
- Call site: `ProjectUnderstandingService.execute` after successful
  `workflowService.start` for `CREATED` outcome, using
  `prepared.resolvedRevision()` (already observed by synchronize — no second
  probe). Failure of checkpoint recording must not fail the refresh
  (log + continue): freshness is a projection.
- Tests: service-level collaboration test (CREATED → recorded with X;
  REUSED → not recorded; recording failure swallowed).

## Step 4 — Contract extension (additive)

`devlog-contracts/.../EngineeringContextMetadata`: append nullable fields

```java
String freshnessStatus                       // CURRENT|STALE|NO_BASELINE|UNKNOWN|PARTIALLY_FRESH|null
String repositoryRevision                    // best-known observed repo revision (single-source derivation)
String contextRevision                       // knowledge baseline (single comparable source)
List<FreshnessSource> freshnessSources       // persisted per-source state

record FreshnessSource(UUID sourceId, String name, String status, String guidance,
                       String observedRevision, String contextRevision, Instant checkedAt)
```

Meanings (documented in ADR + code):

- `repositoryRevision`: latest revision actually observed for the project's
  repository — derivation priority: (a) distinct revision resolved during
  this context build from evidence metadata when exactly one value exists;
  (b) else single persisted source row's currentRevision; else null.
  Never a fabricated merge of multiple values.
- `contextRevision`: baseline analyzedRevision of the (single) comparable
  source; null when zero or several comparables exist (breakdown carries
  truth for multi-source).
- Aggregate `freshnessStatus`: all-equal → that status; mixed with ≥1 CURRENT
  and ≥1 STALE → PARTIALLY_FRESH; otherwise worst-of order STALE >
  NO_BASELINE > UNKNOWN > CURRENT; empty → null (nothing known yet).

Warnings appended to the existing list:

- `PROJECT_CONTEXT_STALE` — any source where observed != baseline (persisted),
  or live-build revision differs from the served baseline (mixed-revision
  response, covers the hidden structure-fetch divergence).
- `PROJECT_CONTEXT_PARTIALLY_FRESH` — aggregate == PARTIALLY_FRESH.

Mapper signature becomes `toContract(projectContext, repositoryContext,
intent, freshnessSummary)`; facade injects `ProjectFreshnessService`.
Pure helper `EngineeringContextFreshnessEnricher`-style logic kept inside the
mapper package for determinism and testability. Existing fixtures updated;
JSON evolution additive (new optional fields only).

## Step 5 — Backend summary endpoint

`ProjectFreshnessController`: add `@GetMapping("/summary")` →
`service.summary(projectId)` reusing existing business logic.

## Step 6 — MCP resource

- `DevlogResourceUriFactory.freshness(slug)` → `devlog://projects/{slug}/freshness`
  (+ factory test update).
- `DevlogResourceClient.getFreshnessSummary(projectId)` →
  `GET /projects/{projectId}/freshness-checks/summary`.
- `FreshnessResource` (`@McpResource(uri="devlog://projects/{projectSlug}/freshness",
  name="project-freshness", mimeType="application/json")`):
  `requireProjectId(slug)` → client call via `support.get(...)` mapping
  errors per conventions. READ-ONLY: no check(), no git, no mutation
  (trade-off documented: exposes last-known observations).
- Tests: `FreshnessResourceTest` (known project passthrough; unknown project
  clean not-found; payload is projectId-scoped so no cross-project leakage)
  + `ResourceUriTemplateSyncTest.freshnessTemplateMatchesFactory`.

## Step 7 — Tests matrix

| Area | New tests |
|---|---|
| Drift | Postgres integration: post-0085 COMPLETED & IN_PROGRESS snapshots eligible; legacy null-revision eligible; FAILED excluded |
| Checkpoint | recordObservedBaseline persistence/service tests; understanding orchestration collaboration test |
| Metadata | mapper tests: fresh / stale / partially-fresh multi-source / no-baseline / warnings incl. live-divergence case |
| Resource | FreshnessResourceTest + URI sync + factory |

## Step 8 — Quality gates

`mvn -pl devlog-contracts,backend,mcp-server verify` (or full `-am` build),
JaCoCo per module, `git diff --check`; frontend untouched (no frontend run
needed). Runtime validation scenario executed if docker environment permits;
otherwise documented with exact manual steps.

## Commit plan

1. `docs(adr): define synchronization lifecycle and freshness checkpoints`
2. `docs(story): define freshness foundation story 0091`
3. `fix(freshness): restore comparable baselines for revision-pinned analyses`
4. `feat(freshness): advance freshness checkpoints from understanding refreshes`
5. `feat(context): expose freshness revisions and stale warnings in engineering context`
6. `feat(mcp): expose project freshness resource`
7. `test(freshness): cover baselines, stale scenarios and resource isolation`
8. `docs(story): complete engineering reports for story 0091`
