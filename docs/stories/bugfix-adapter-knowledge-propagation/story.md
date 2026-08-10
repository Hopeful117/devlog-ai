# Bugfix: Adapter Knowledge Propagation

**Status**: Completed
**Type**: Bugfix
**Date**: 2026-08-10

## Problem

`RepositoryContextAdapter.synthesizeAnalysisContext()` drops `validatedEngineeringEvents`, `openChallenges`, and `knowledgeRelations` from `ProjectContextSnapshot` when synthesizing the `AnalysisContext` for the Engineering Story Context path.

## Solution

Propagate all three fields. Add missing fields to `AnalysisContext` record. Add regression tests.

## Acceptance Criteria

1. ✅ `validatedEngineeringEvents` from `ProjectContextSnapshot` survive adapter synthesis
2. ✅ `openChallenges` survive adapter synthesis
3. ✅ `knowledgeRelations` survive adapter synthesis
4. ✅ Existing project-context fields continue to be propagated
5. ✅ No new ranking/scoring behavior introduced
6. ✅ No new persistence or migration
7. ✅ Existing tests continue to pass (518/518)
8. ✅ Focused regression tests protect against silent field dropping
9. ✅ Engineering Story Context path remains backward compatible
10. ✅ Change is deterministic, no AI behavior
