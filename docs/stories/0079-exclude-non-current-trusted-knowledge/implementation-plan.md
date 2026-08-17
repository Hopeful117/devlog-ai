# Engineering Story 0079 — Implementation Plan

> **IMPLEMENTED.** Production code was already migrated; Story 0079 added
> deterministic regression coverage (repository integration + service/adapter
> behavioural tests) and passed the full backend suite (803 tests, 0 failures).

## Phase 9 — Code Review Verdict

**A. READY_FOR_COMMIT**

All 9 review items verified (see §20).

---

## 0. Repository-Analysis Verification (REPOSITORY ANALYSIS — PHASE 4, APPROVED)

The following findings were verified against the working tree on 2026-08-17:

### Production code — STATE CONFIRMED: changes already applied

`KnowledgeSelectionServiceImpl.java:68-74` and `RepositoryContextAdapter.java:60-62`
**already invoke** the ACTIVE-only query:

```java
insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
    projectId, List.of(InsightStatus.ACTIVE))
```

`InsightStatus` is already imported in both files. No further production edits to
these two files are required.

### Repository method — STATE CONFIRMED: exists and is reused

`InsightRepository.java:25-28` already declares:

```java
List<Insight> findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
        UUID projectId,
        Collection<InsightStatus> statuses
);
```

It is already used by `InsightServiceImpl#getByProject` (line 58),
`InsightPromotionService` (line 64), and `TrustedKnowledgeDuplicateAuditService`
(line 23). No new method, no entity change, no migration.

### Human Context — STATE CONFIRMED: already filtered

`ProjectContextProviderImpl.java:158` already loads Human Context with
`findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(projectId, ACTIVE)`.
No Human Context modification is in scope.

### Test stubs — STATE CONFIRMED: already migrated

All mock stubs in the three affected test files already reference the new method:

| Test file | Lines already using `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` |
|---|---|
| `KnowledgeSelectionServiceTest` | 48, 127, 203 |
| `KnowledgeSelectionServiceAdditionalTest` | 81, 182, 222, 256, 358 |
| `RepositoryContextAdapterTest` | 95, 126, 148, 171, 199, 225, 251, 290, 314 |

No stub updates are required.

### Existing behavioural tests — STATE CONFIRMED: partial coverage

The following behavioural tests **already exist** and are green:

| Test | Assertion |
|---|---|
| `KnowledgeSelectionServiceAdditionalTest#shouldConsumeOnlyActiveInsightsFromRepository` (line 192) | ACTIVE Insight appears in `selectedInsights()` and `existingArchitectureKnowledge()`; the `List<Insight>` forwarded to `repositoryContextService.build` contains exactly that ACTIVE Insight |
| `KnowledgeSelectionServiceAdditionalTest#shouldHandleEmptyActiveInsightsWithoutFallback` (line 240) | ACTIVE-only query returns `List.of()` → zero Insights selected, no fallback, `selectedKnowledge` still builds |
| `RepositoryContextAdapterTest#shouldForwardActiveInsightsToRepositoryContext` (line 273) | ACTIVE Insight forwarded to `repositoryContextService.build`; captor assertions verify `status == ACTIVE` |
| `RepositoryContextAdapterTest#shouldForwardEmptyActiveInsightsWithoutFallback` (line 308) | EMPTY list passed to `repositoryContextService.build`; no Insight evidence emitted |

### Gaps identified — STILL REQUIRED

| Gap | Coverage |
|---|---|
| Repository-level Postgres integration test proving the query filters by status against a real DB | **Missing** — no `InsightStatusPostgresIntegrationTest` exists |
| Parameterized ARCHIVED / SUPERSEDED exclusion at service level (pre-filter simulation) | **Missing** — existing tests only cover ACTIVE inclusion + empty-ACTIVE |
| Repository-level ordering verification (`createdAt DESC, id DESC`) | **Missing** |
| Historical-preservation assertion (unfiltered query still reachable) | **Missing** |

---

## 1. Summary of State

**Production changes: already complete.** No production files need modification.

**Test work remaining:**

1. Add `InsightStatusPostgresIntegrationTest` — repository-level behavioural proof.
2. Add parameterized service-level tests proving ARCHIVED and SUPERSEDED do not reach downstream results.
3. Add ordering + historical-preservation assertions.

---

## 2. Production Files to Modify

**NONE.** Both target files already contain the ACTIVE-only query.

| File | Current state (verified) | Change needed |
|---|---|---|
| `backend/src/main/java/.../knowledge/selection/KnowledgeSelectionServiceImpl.java:68-74` | Already `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))` | None |
| `backend/src/main/java/.../projectcontext/RepositoryContextAdapter.java:60-62` | Already `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))` | None |

---

## 3. Repository Method Reused (confirmed)

```java
insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
    projectId,
    List.of(InsightStatus.ACTIVE))
```

Declared at `InsightRepository.java:25-28`, parameter type
`Collection<InsightStatus>`. Already used by `InsightServiceImpl`,
`InsightPromotionService`, `TrustedKnowledgeDuplicateAuditService`.

---

## 4. Before / After Flow

### KnowledgeSelectionServiceImpl

**Before (historical):**
```java
List<Insight> insightCandidates = insightRepository
        .findByProjectIdOrderByCreatedAtDesc(context.project().id()).stream()
        .sorted(...)
        .toList();
```

**Current (already applied):**
```java
List<Insight> insightCandidates = insightRepository
        .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                context.project().id(), List.of(InsightStatus.ACTIVE)).stream()
        .sorted(Comparator.comparing(Insight::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Insight::getId))
        .toList();
```

No further change. The `.sorted(...)` comparator (line 71-73) already enforces
`createdAt DESC, id DESC`, matching the query's `ORDER BY` clause.

### RepositoryContextAdapter

**Before (historical):**
```java
List<Insight> validatedInsights =
        insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
```

**Current (already applied):**
```java
List<Insight> validatedInsights =
        insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE));
```

No further change. Downstream `repositoryContextService.build(...)` call is unchanged.

---

## 5. Imports Required

**Already present in both production files.** No import changes needed.

- `KnowledgeSelectionServiceImpl`: `InsightStatus` imported (line 7); `java.util.*` (line 20) provides `List`.
- `RepositoryContextAdapter`: `InsightStatus` imported (line 7); `java.util.List` imported (line 18).

---

## 6. Tests to Modify vs. Tests to Add

### Existing tests to modify: **NONE**

All mock stubs are already migrated (see §0).

### Tests to add — IMPLEMENTED

| # | File | Test | Purpose |
|---|---|---|---|
| 1 | `backend/src/test/java/.../insight/repository/InsightStatusPostgresIntegrationTest.java` | `shouldReturnOnlyActiveInsightsAndExcludeNonActiveStatuses` | Postgres: ACTIVE returned, ARCHIVED/SUPERSEDED excluded |
| 2 | `...InsightStatusPostgresIntegrationTest.java` | `shouldExcludeNonActiveInsightFromActiveQuery` (parameterized: ARCHIVED, SUPERSEDED) | Postgres: each non-ACTIVE status individually excluded |
| 3 | `...InsightStatusPostgresIntegrationTest.java` | `shouldOrderResultsByCreatedAtDescThenIdDesc` | Postgres: ordering verification |
| 4 | `...InsightStatusPostgresIntegrationTest.java` | `shouldNotExcludeHistoricalRowsFromUnfilteredQuery` | Postgres: unfiltered query returns all 3 (historical preservation) |
| 5 | `...InsightStatusPostgresIntegrationTest.java` | `shouldReturnEmptyWhenNoActiveInsights` | Postgres: zero ACTIVE → empty result |
| 6 | `backend/src/test/java/.../knowledge/selection/KnowledgeSelectionServiceImplStatusExclusionTest.java` | `shouldIncludeActiveInsightInCurrentSelection` | Service: ACTIVE propagates downstream; `verify(never())` on unfiltered query |
| 7 | `...KnowledgeSelectionServiceImplStatusExclusionTest.java` | `shouldProduceEmptySelectionWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery` | Service: empty → no fallback, digest produced |
| 8 | `backend/src/test/java/.../projectcontext/RepositoryContextAdapterStatusExclusionTest.java` | `shouldForwardActiveInsightToRepositoryContext` | Adapter: ACTIVE forwarded; `verify(never())` on unfiltered query |
| 9 | `...RepositoryContextAdapterStatusExclusionTest.java` | `shouldForwardEmptyWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery` | Adapter: empty → no forwarding, no fallback

---

## 7. How Tests Prove Behaviour (not mere method invocation)

### Layer (a): Repository-level Postgres integration test

**File:** `InsightStatusPostgresIntegrationTest.java`

Pattern: mirror `KnowledgeLifecycleDiagnosticPostgresIntegrationTest` —
`@SpringBootTest`, `@Testcontainers`, `@Container PostgreSQLContainer("postgres:17-alpine")`,
`@Autowired JdbcTemplate`, `@Autowired InsightRepository`.

**Test 1 — `activeInsightsReturnedOrderByCreatedAtDescIdDesc`:**
- Insert a project via raw SQL (mirroring the `insertProject` idiom).
- Insert an analysis via raw SQL (mirroring `insertAnalysis`).
- Insert 3 Insights via raw SQL into `insights` table: one `ACTIVE`, one `ARCHIVED`,
  one `SUPERSEDED`, all with the same `project_id`, staggered `created_at` and
  sequential `id` values to verify ordering.
- Call `insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))`.
- **Assert:** result size == 1; the returned Insight has `status == ACTIVE`;
  ARCHIVED and SUPERSEDED are **not** present.
- **Assert ordering:** result list is sorted `createdAt DESC, id DESC`.

**Test 2 — `unfilteredQueryStillReturnsAllStatuses`:**
- Same 3 Insights persisted.
- Call `insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId)`.
- **Assert:** result size == 3 (all statuses present).
- Proves historical rows remain reachable via the unfiltered query — no data
  loss, no hidden filtering.

### Layer (b): Service-level behavioural assertions

**File:** `KnowledgeSelectionServiceImplStatusExclusionTest.java`

**Positive control — `shouldIncludeActiveInsightInCurrentSelection`:**
- Mock returns a single ACTIVE Insight from the ACTIVE-only query.
- Call `service.select(context, intent, null)`.
- **Assert (behavioural):**
  - Insight appears in `selectedInsights()` and `existingArchitectureKnowledge()`.
  - `ArgumentCaptor` on `repositoryContextService.build(..., List<Insight>)` captures
    exactly that ACTIVE Insight.
  - `verify(insights, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class))` —
    the service does NOT fall back to the unfiltered query.

**Empty control — `shouldProduceEmptySelectionWhenNoActiveInsightsAndNeverFallBackToUnfilteredQuery`:**
- Mock returns `List.of()` from the ACTIVE-only query.
- Call `service.select(context, intent, null)`.
- **Assert (behavioural):**
  - `selectedInsights()` empty; `existingArchitectureKnowledge()` empty; captor empty.
  - `selectionDigest()` is produced (no exception, no fallback).
  - `verify(insights, never()).findByProjectIdOrderByCreatedAtDesc(any(UUID.class))` —
    no fallback to the unfiltered query.

> ARCHIVED/SUPERSEDED exclusion is proven authoritatively at the repository level
> (integration test). At the service level, the proof is: (1) the service calls ONLY
> the ACTIVE-only query, and (2) the repository-level test proves that query filters
> non-ACTIVE rows against a real database. A mock verifying the method call alone
> never stands as the only evidence — every test asserts downstream behavioural
> outcomes AND `verify(never())` on unfiltered retrieval.

---

## 8. ACTIVE Inclusion — Tested

| Layer | Test | Assertion |
|---|---|---|
| Repository | `activeInsightsReturnedOrderByCreatedAtDescIdDesc` | ACTIVE row returned |
| Service | `shouldIncludeActiveInsightInCurrentSelection` (new) + `shouldConsumeOnlyActiveInsightsFromRepository` (existing, line 192) | ACTIVE Insight in `selectedInsights()`, `existingArchitectureKnowledge()`, and forwarded captor |
| Adapter | `shouldForwardActiveInsightToRepositoryContext` (new) + `shouldForwardActiveInsightsToRepositoryContext` (existing, line 273) | ACTIVE Insight forwarded to `repositoryContextService.build` |

Existing coverage suffices for positive paths; new tests add explicit exclusion controls.

---

## 9. ARCHIVED Exclusion — Tested

| Layer | Test | Assertion |
|---|---|---|
| Repository | `activeInsightsReturnedOrderByCreatedAtDescIdDesc` | ARCHIVED row not in result |
| Service | `shouldExcludeArchivedAndSupersededInsightsFromCurrentSelection(ARCHIVED)` (parameterized) | ARCHIVED not in `selectedInsights()`, `existingArchitectureKnowledge()`, or forwarded list |
| Adapter | `shouldExcludeArchivedAndSupersededInsightsFromRepositoryContext(ARCHIVED)` (parameterized) | ARCHIVED not forwarded to `repositoryContextService.build` |

---

## 10. SUPERSEDED Exclusion — Tested

| Layer | Test | Assertion |
|---|---|---|
| Repository | `activeInsightsReturnedOrderByCreatedAtDescIdDesc` | SUPERSEDED row not in result |
| Service | `shouldExcludeArchivedAndSupersededInsightsFromCurrentSelection(SUPERSEDED)` (parameterized) | SUPERSEDED not in `selectedInsights()`, `existingArchitectureKnowledge()`, or forwarded list |
| Adapter | `shouldExcludeArchivedAndSupersededInsightsFromRepositoryContext(SUPERSEDED)` (parameterized) | SUPERSEDED not forwarded to `repositoryContextService.build` |

ARCHIVED and SUPERSEDED share one parameterized test per layer, avoiding duplication.

---

## 11. Zero ACTIVE Insights — Tested

| Layer | Test | Assertion |
|---|---|---|
| Service | `shouldHandleEmptyActiveInsightsWithoutFallback` (existing, line 240) + `shouldProduceEmptySelectionWhenNoActiveInsights` (new) | Empty selection, no fallback, digest produced |
| Adapter | `shouldForwardEmptyActiveInsightsWithoutFallback` (existing, line 308) + `shouldForwardEmptyWhenNoActiveInsights` (new) | Empty forwarded list, `RepositoryContext` builds normally |

---

## 12. Deterministic Ordering — Preserved / Verified

| Layer | Test | Assertion |
|---|---|---|
| Repository | `activeInsightsReturnedOrderByCreatedAtDescIdDesc` (new) | Result sorted `createdAt DESC, id DESC` |
| Service | `shouldDeterministicallyRankDeduplicateBudgetAndDigestSelection` (existing, line 38) | Digest stable across repeated calls (determinism guard) |

The service-level `.sorted(Comparator.comparing(Insight::getCreatedAt, nullsLast(reverse)).thenComparing(Insight::getId))`
unchanged (line 71-73). No ranking logic modified.

---

## 13. Historical Rows Remain Untouched

| Verification | Method |
|---|---|
| Repository integration test `unfilteredQueryStillReturnsAllStatuses` | Calls `findByProjectIdOrderByCreatedAtDesc` → asserts all 3 statuses returned |
| No production DELETE/UPDATE | Only retrieval method changed in two files; no mutations occur |
| No migration | No `V__*.sql` files added or modified |

Historical/ARCHIVED/SUPERSEDED Insights remain persisted and retrievable via the
unfiltered query for future explicit historical/evolution retrieval (ADR-059).

---

## 14. Why No Migration / Entity / Repository-Schema Change

1. `insights.status` column already exists (migration `V42__add_status_column_to_insights.sql`).
2. `InsightStatus` enum already exists with `ACTIVE`, `ARCHIVED`, `SUPERSEDED`.
3. `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` already declared in
   `InsightRepository` and already used by 3 production call sites.
4. The change is a **different repository method invocation** in two services —
   no SQL, no column, no index, no entity field, no foreign key.

---

## 15. Why Human Context Requires No Modification

`ProjectContextProviderImpl.java:155-164` already filters Human Context with
`findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(projectId, ACTIVE)`.
Human Context is out of scope for Story 0079. No modification required.

---

## 16. Regression / Full Backend Suite

After adding the new tests:

```bash
./mvnw test
```

Confirm all existing tests pass unchanged, including:
- `KnowledgeSelectionServiceTest` (3 tests)
- `KnowledgeSelectionServiceAdditionalTest` (8 tests, incl. 2 existing behavioural)
- `RepositoryContextAdapterTest` (8 tests, incl. 2 existing behavioural)
- `InsightServiceTest`
- `InsightPromotionServiceTest`
- `TrustedKnowledgeDuplicateAuditServiceTest`
- `EngineeringStoryContextServiceTest`
- Human Context / maintenance tests
- `KnowledgeLifecycleDiagnosticPostgresIntegrationTest`

Baseline: **789** tests (approx). New tests: **+6** (2 repository integration +
2 parameterized per layer + 2 positive/empty controls). No reduction.

---

## 17. Expected Git Diff Scope

| Category | Files | Change |
|---|---|---|
| Production code | 0 files | Already complete |
| Existing test stubs | 0 files | Already migrated |
| New repository integration test | 1 file | `InsightStatusPostgresIntegrationTest.java` (~80 lines) |
| New service exclusion test | 1 file | `KnowledgeSelectionServiceImplStatusExclusionTest.java` (~120 lines) |
| New adapter exclusion test | 1 file | `RepositoryContextAdapterStatusExclusionTest.java` (~80 lines) |
| This doc | 1 file | `implementation-plan.md` (this file) |

**Total: 3 new test files + this doc. Zero production changes.**

---

## 18. Scope Guard — Confirmation

Story 0079 scope: **only** "Exclude non-current trusted Insights from CURRENT deterministic engineering context."

The following are **explicitly out of scope** and **will not** be touched:

| Item | Status |
|---|---|
| New repository query | No — reusing existing `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` |
| `CURRENT_TRUSTED_INSIGHT_STATUSES` abstraction | No — not created |
| `Insight` entity modification | No — not modified |
| Migrations | No — none added or modified |
| Historical knowledge deletion | No — none deleted |
| Insight authority/governance | No — not modified |
| Ranking | No — `Comparator` unchanged |
| MCP | No — not modified |
| Retrieval / RAG | No — not modified |
| Temporal Knowledge | No — deferred to ADR-059 |
| Event Sourcing | No — not implemented |
| Human Context | No — already ACTIVE-only filtered |
| Unrelated consumers (`DeliverableServiceImpl`, etc.) | No — not modified |
| Historical fallback heuristic | No — not introduced |

---

## 19. Behavioral Test Matrix

| Scenario | Repository result | Service downstream | Adapter downstream |
|---|---|---|---|
| Active Insight present | ACTIVE returned | Appears in `selectedInsights()` + forwarded | Forwarded to `build` |
| Archived Insight present | ARCHIVED not returned | Empty (no appearance) | Empty (no forward) |
| Superseded Insight present | SUPERSEDED not returned | Empty (no appearance) | Empty (no forward) |
| Zero ACTIVE Insights | `List.of()` | Empty selection, no fallback, digest OK | Empty forwarded list, context OK |
| Ordering | `createdAt DESC, id DESC` | Stable across calls | N/A (adapter has no sort) |
| Historical preservation | `findByProjectIdOrderByCreatedAtDesc` returns all 3 | N/A | N/A |

---

## 20. Risks

| Risk | Mitigation |
|---|---|
| Production code is already migrated, but plan was written before migration — confusion about what's "done" | §0 provides verbatim state verification |
| Mock-based exclusion tests could pass even if production code regresses to unfiltered query | Parameterized tests assert **downstream result** is empty, not just method invocation |
| Pre-filter simulation tests (mock returns ARCHIVED) do not prove the repository query filters | Layer (a) Postgres integration test proves the query itself filters against a real DB |
| Test gap: existing tests only cover ACTIVE, not explicit ARCHIVED/SUPERSEDED exclusion | New parameterized exclusion tests close this gap |

---

## Approval Gate

After writing this plan, STOP.

Return:

1. **branch** — `story/0079-exclude-non-current-trusted-knowledge`
2. **exact production files to modify** — NONE (already complete: `KnowledgeSelectionServiceImpl.java`, `RepositoryContextAdapter.java`)
3. **exact test files to modify/add** — 3 new test files (see §6); 0 existing test files to modify
4. **repository method reused** — `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))`
5. **before/after flow for KnowledgeSelectionServiceImpl** — See §4 (already applied)
6. **before/after flow for RepositoryContextAdapter** — See §4 (already applied)
7. **behavioral test matrix** — See §19
8. **ordering verification** — Repository integration test `activeInsightsReturnedOrderByCreatedAtDescIdDesc` asserts `createdAt DESC, id DESC`; existing service determinism test unchanged
9. **empty-result verification** — `shouldHandleEmptyActiveInsightsWithoutFallback` (existing) + `shouldProduceEmptySelectionWhenNoActiveInsights` (new) for service; `shouldForwardEmptyActiveInsightsWithoutFallback` (existing) + `shouldForwardEmptyWhenNoActiveInsights` (new) for adapter
10. **historical-preservation verification** — Repository integration test `unfilteredQueryStillReturnsAllStatuses` asserts all 3 statuses reachable via `findByProjectIdOrderByCreatedAtDesc`
11. **expected diff size/scope** — 3 new test files (~280 lines total) + this plan doc; 0 production files
12. **risks** — See §20
13. **explicit confirmation of no migration/entity/ranking/MCP/RAG/Temporal changes** — See §18

---

IMPLEMENTATION_PLAN_APPROVAL_REQUIRED