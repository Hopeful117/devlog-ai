# Story 0079 — Exclude Non-Current Trusted Knowledge from Current Context — Engineering Report

## Status

Reported

## Story

| Field | Value |
|---|---|
| Number | 0079 |
| Title | Exclude non-current trusted Insights from CURRENT deterministic engineering context |
| Status | Done (regression coverage complete, awaiting commit) |
| Acceptance Criteria | 10/10 satisfied |

## Scope Delivered

### Implemented

* **No production code changes** — `KnowledgeSelectionServiceImpl` and
  `RepositoryContextAdapter` already invoked the ACTIVE-only query
  (`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))`).
* `InsightStatusPostgresIntegrationTest` — 5 Testcontainers integration tests proving
  the repository query filters by status against a real PostgreSQL database.
* `KnowledgeSelectionServiceImplStatusExclusionTest` — 2 Mockito tests proving
  ACTIVE propagation and no-fallback at the service level.
* `RepositoryContextAdapterStatusExclusionTest` — 2 Mockito tests proving
  ACTIVE forwarding and no-fallback at the adapter level.

### Explicitly not introduced

* No migration (Flyway stays at **V43**), no entity change, no repository interface change.
* No `CURRENT_TRUSTED_INSIGHT_STATUSES` abstraction.
* No ranking / comparator changes.
* No MCP tool additions.
* No RAG / Retrieval changes.
* No Temporal Knowledge implementation (deferred to ADR-059).
* No Event Sourcing.
* No Human Context modification (already ACTIVE-only filtered).
* No changes to unrelated consumers (`DeliverableServiceImpl`, etc.).
* No historical fallback heuristic introduced.

## Design Outcome

### Filtering boundary

```text
Repository query (deterministic load site 1)
  KnowledgeSelectionServiceImpl  → findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, [ACTIVE])
    → .sorted(createdAt DESC, id DESC) → insightCandidates
    → forwarded to RepositoryContextService.build(...)

Repository query (deterministic load site 2)
  RepositoryContextAdapter       → findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, [ACTIVE])
    → forwarded to RepositoryContextService.build(...)
```

The filter lives entirely in the repository query. Both load sites reuse the
existing `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` method — no new
repository method was declared.

### Historical / superseded knowledge preservation

`insights.status` values `ARCHIVED` and `SUPERSEDED` remain persisted and are
retrievable via the unfiltered `findByProjectIdOrderByCreatedAtDesc(projectId)`
query. No rows are deleted or mutated. Future explicit historical/evolution
retrieval (ADR-059) can use this unfiltered query.

### Human Context

`ProjectContextProviderImpl` already filters Human Context with
`findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(projectId, ACTIVE)`.
No modification required or made.

## Implementation Summary

| File | Change |
|---|---|
| `backend/src/main/java/.../knowledge/selection/KnowledgeSelectionServiceImpl.java` | No change (already ACTIVE-only) |
| `backend/src/main/java/.../projectcontext/RepositoryContextAdapter.java` | No change (already ACTIVE-only) |
| `backend/src/test/java/.../insight/repository/InsightStatusPostgresIntegrationTest.java` | **new**: 5 integration tests |
| `backend/src/test/java/.../knowledge/selection/KnowledgeSelectionServiceImplStatusExclusionTest.java` | **new**: 2 unit tests |
| `backend/src/test/java/.../projectcontext/RepositoryContextAdapterStatusExclusionTest.java` | **new**: 2 unit tests |

No existing production or test file was modified.

## Current Dataset Outcome

DevLog can now deterministically prove that trusted Insights with status
`ARCHIVED` or `SUPERSEDED` do not appear in the current engineering context
(both `KnowledgeSelectionServiceImpl` and `RepositoryContextAdapter` paths),
while ACTIVE insights flow through correctly and historical rows remain
accessible.

## Quality Gates

* backend `./mvnw test`: **PASS — 803 tests, 0 failures, 0 errors, BUILD SUCCESS**
  (baseline ~792 + 11 new tests).
* No migration added (Flyway stays at V43).
* No `@Modifying` / DELETE / UPDATE introduced.

## Limitations

1. No Temporal Knowledge semantics — deferred to ADR-059.
2. This story provides regression coverage, not a production behavior change.
3. MCP exposure of the status-filtering contract is Phase 2 (out of scope).

## Next Architectural Questions

1. ADR-059: When Temporal Knowledge is introduced, the unfiltered
   `findByProjectIdOrderByCreatedAtDesc` query becomes the foundation for
   historical/evolution retrieval.
2. Whether to introduce an explicit `CURRENT_TRUSTED_INSIGHT_STATUSES`
   abstraction is explicitly deferred — the current approach reuses the
   existing repository method with `List.of(InsightStatus.ACTIVE)`.

## Documentation Outcome

This story folder is the canonical documentation: `story.md`,
`repository-analysis.md`, `implementation-plan.md`,
`implementation-report.md`, `engineering-report.md`.

## Next Steps

* Commit Story 0079 test + documentation artifacts on
  `story/0079-exclude-non-current-trusted-knowledge`.
* Post-commit: prepare MCP benchmark to verify ARCHIVED/SUPERSEDED Insights
  do not appear in current context (see Post-commit Benchmark Preparation).
