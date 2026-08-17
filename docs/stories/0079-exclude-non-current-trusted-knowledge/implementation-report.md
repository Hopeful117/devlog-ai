# Story 0079 — Exclude Non-Current Trusted Knowledge from Current Context — Implementation Report

## Summary

* Repository analysis identified a **current-context correctness risk**: trusted
  Insights with status `ARCHIVED` or `SUPERSEDED` were at risk of appearing in
  the deterministic engineering context because two load sites used an unfiltered
  repository query.
* **Current production code already contains the intended ACTIVE-only behaviour.**
  Both `KnowledgeSelectionServiceImpl` and `RepositoryContextAdapter` already invoke
  `insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId,
  List.of(InsightStatus.ACTIVE))`. `InsightServiceImpl#getByProject`,
  `InsightPromotionService`, and `TrustedKnowledgeDuplicateAuditService` were also
  already ACTIVE-only. Human Context (`ProjectContextProviderImpl`) was already
  ACTIVE-only.
* Story 0079 therefore **consolidates** the behavior with deterministic regression
  coverage rather than introducing additional production logic. **Zero production
  files were modified.**
* Historical/ARCHIVED/SUPERSEDED Insights remain persisted and accessible via the
  unfiltered `findByProjectIdOrderByCreatedAtDesc` query for future explicit
  historical/evolution retrieval (ADR-059 deferred).

## Delivered Artifacts

| Type | File | Change |
|---|---|---|
| Test | `backend/src/test/.../insight/repository/InsightStatusPostgresIntegrationTest.java` | **new** — 5 tests (4 regular + 1 parameterized) |
| Test | `backend/src/test/.../knowledge/selection/KnowledgeSelectionServiceImplStatusExclusionTest.java` | **new** — 2 tests |
| Test | `backend/src/test/.../projectcontext/RepositoryContextAdapterStatusExclusionTest.java` | **new** — 2 tests |
| Doc | `implementation-plan.md` | Updated to reflect implemented state |
| Doc | `implementation-report.md` | This file |
| Doc | `engineering-report.md` | This file |

**Production code: 0 files modified.**

## Validation

### Backend full suite

```
Tests run: 803, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

### Test breakdown (new tests — 11 total)

#### Repository-level integration test (`InsightStatusPostgresIntegrationTest`)

`@SpringBootTest` + `@Testcontainers` + `PostgreSQLContainer("postgres:17-alpine")`.

| Test | Assertion |
|---|---|
| `shouldReturnOnlyActiveInsightsAndExcludeNonActiveStatuses` | Persist ACTIVE, ARCHIVED, SUPERSEDED; ACTIVE-only query returns exactly 1 (ACTIVE); ARCHIVED/SUPERSEDED not present |
| `shouldExcludeNonActiveInsightFromActiveQuery(ARCHIVED)` | Parameterized: ARCHIVED insight excluded from ACTIVE-only query |
| `shouldExcludeNonActiveInsightFromActiveQuery(SUPERSEDED)` | Parameterized: SUPERSEDED insight excluded from ACTIVE-only query |
| `shouldOrderResultsByCreatedAtDescThenIdDesc` | Staggered timestamps + equal-timestamp tie-break → `createdAt DESC, id DESC` |
| `shouldNotExcludeHistoricalRowsFromUnfilteredQuery` | `findByProjectIdOrderByCreatedAtDesc` still returns all 3 statuses (historical preservation) |
| `shouldReturnEmptyWhenNoActiveInsights` | Only ARCHIVED + SUPERSEDED persisted → ACTIVE-only query returns empty |

#### Service-level behavioural test (`KnowledgeSelectionServiceImplStatusExclusionTest`)

Mockito unit test.

| Test | Assertion |
|---|---|
| `shouldIncludeActiveInsightInCurrentSelection` | ACTIVE Insight appears in `selectedInsights()`, `existingArchitectureKnowledge()`, forwarded captor; `verify(never())` on unfiltered query |
| `shouldProduceEmptySelectionWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery` | Empty result; no fallback to unfiltered query; digest produced |

#### Adapter-level behavioural test (`RepositoryContextAdapterStatusExclusionTest`)

Mockito unit test.

| Test | Assertion |
|---|---|
| `shouldForwardActiveInsightToRepositoryContext` | ACTIVE Insight forwarded to `repositoryContextService.build`; `verify(never())` on unfiltered query |
| `shouldForwardEmptyWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery` | Empty forwarded list; no fallback; `RepositoryContext` built normally |

## Acceptance Criteria Verification

| # | Criterion | Status |
|---|---|---|
| 1 | ACTIVE Insight enters current/deterministic engineering context | ✅ integration + service + adapter |
| 2 | ARCHIVED Insight cannot enter current context | ✅ repository integration (parameterized) |
| 3 | SUPERSEDED Insight cannot enter current context | ✅ repository integration (parameterized) |
| 4 | Zero ACTIVE Insights → valid empty result, no fallback | ✅ repository + service + adapter |
| 5 | Deterministic ordering (`createdAt DESC, id DESC`) | ✅ repository integration + existing service determinism test |
| 6 | Historical rows remain persisted and accessible via unfiltered query | ✅ repository integration (`shouldNotExcludeHistoricalRowsFromUnfilteredQuery`) |
| 7 | No fallback to unfiltered retrieval | ✅ service + adapter tests (`verify(never())`) |
| 8 | Full backend suite green (803) | ✅ |
| 9 | No migration / entity / repository-schema change | ✅ verified |
| 10 | No Human Context behavioural change | ✅ verified — already ACTIVE-only |

## Final Assessment

All acceptance criteria satisfied. The production behavior was already correct;
Story 0079 added deterministic regression coverage that proves the filtering
contract at the repository level (real Postgres via Testcontainers) and at the
service/adapter level (Mockito with `verify(never())` on unfiltered queries and
captor-based behavioural assertions). No production code, migration, entity,
repository interface, ranking, MCP, RAG, or Temporal Knowledge changes.
