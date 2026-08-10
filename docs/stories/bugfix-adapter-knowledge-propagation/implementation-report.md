# Implementation Report — Adapter Knowledge Propagation Bugfix

## Summary

Fixed `RepositoryContextAdapter` to propagate all `ProjectContextSnapshot` knowledge fields into the synthesized `AnalysisContext`. Three fields were previously dropped: `validatedEngineeringEvents` (propagation bug), `openChallenges` and `knowledgeRelations` (missing fields in `AnalysisContext`).

## Files Changed

| File | Change |
|---|---|
| `AnalysisContext.java` | Added `openChallenges` and `knowledgeRelations` fields + copyOf + updated convenience constructors |
| `RepositoryContextAdapter.java` | Pass all 3 snapshot fields in canonical constructor call |
| `AnalysisContextServiceImpl.java` | Pass 2 new fields in normal analysis path |
| `RepositoryContextAdapterTest.java` | Updated fixture to 11-arg constructor; added 3 regression tests |

## Validation

- **Compilation**: OK
- **Tests**: 518 pass, 0 failures (1 pre-existing `contextLoads` excluded)
- **New tests**: 3 (propagation of `validatedEngineeringEvents`, `openChallenges`, `knowledgeRelations`)
- **Existing tests**: All pass unchanged (convenience constructors default new fields to `List.of()`)

## Documentation Reconciliation

Documentation update: Not required.

Rationale: This is a bugfix restoring an existing propagation contract. No API surface, configuration, or user-facing behavior changes. The `AnalysisContext` record gains two new fields with backward-compatible defaults.

## Scope Adherence

- ✅ `validatedEngineeringEvents` propagated
- ✅ `openChallenges` propagated
- ✅ `knowledgeRelations` propagated
- ✅ Existing fields continue to propagate
- ✅ No new ranking/scoring behavior
- ✅ No migration
- ✅ No AI behavior
- ✅ No changes to collectors, ranking weights, or evidence kinds
- ✅ Deterministic propagation only
