# Implementation Report — Story 0082

## Branch

`story/0082-post-selection-priority-ordering`

## Story

0082 — Post-Selection Priority Ordering for Projection Survival

## Status

READY_FOR_COMMIT_APPROVAL

## Production Changes

### Modified Files

1. `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
   - Added import: `java.util.Comparator` (line 21)
   - Added conservation sort: lines 85-90

2. `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngineTest.java`
   - Updated 4 existing tests
   - Added 2 new tests
   - Total: 6 tests (up from 5)

### Change Description

Added a post-selection conservation sort in `RepositoryContextEngine.build()` that
reorders `selection.selected()` by:

1. `relevanceScore DESC` (primary)
2. `layer ordinal ASC` (secondary tie-breaker)
3. `reference ASC` (tertiary tie-breaker)

This ensures `AgentContextProjectionService.removeTailEvidence()` removes the
lowest-priority evidence first, fixing the priority inversion where diversity-selected
lower-score evidence survived while higher-score ordinary evidence was removed.

### Behavioral Change

**Before:** Evidence order = selector insertion order. Projection consumed this as
survival priority.

**After:** Evidence order = conservation priority (score DESC, layer ordinal ASC,
reference ASC). Projection naturally removes lowest-priority evidence first.

### What Is NOT Changed

- Selector algorithm (BudgetedDiverseEvidenceSelector)
- Projection algorithm (AgentContextProjectionService)
- ContextProfile, precision policy, budgets
- Trusted-knowledge semantics
- API, MCP contracts, persistence, migrations
- Ranking weights or temporal-knowledge logic

## Test Results

### Focused Tests (RepositoryContextEngineTest)

```
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Full Backend Suite

```
Tests run: 813, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Test Categories Covered

| Category | Test Method | Status |
|---|---|---|
| HIGH_SCORE_SURVIVES_BEFORE_LOW_SCORE | `highScoreEvidenceOrderedBeforeLowScoreDiversityPick` | PASS |
| SELECTED_SET_UNCHANGED | `selectedSetUnchangedAfterReordering` | PASS |
| DETERMINISTIC_TIE_BREAK | `sameScoreTieBreaksByLayerOrdinalThenReference` | PASS |
| DIVERSITY_SET_PRESERVED | `diversitySelectedEvidenceStillPresentAfterReordering` | PASS |
| PROJECTION_REGRESSION | `projectionRemovesLowestPriorityEvidenceFirst` | PASS |
| PROJECTION_REGRESSION (budget) | `diversityLowScoreRemovedBeforeOrdinaryHighScoreUnderBudgetPressure` | PASS |

## Invariant Verification

- [x] Exact evidence SET preserved after reordering
- [x] Only post-selection ordering changes
- [x] Conservation ordering: relevanceScore DESC -> layer ordinal ASC -> reference ASC
- [x] Equal-score ordering is deterministic
- [x] Diversity-selected evidence remains in SET
- [x] Selector behavior unchanged
- [x] AgentContextProjectionService unchanged
- [x] ContextProfile and budgets unchanged
- [x] No trusted-knowledge special casing
- [x] No API, MCP, persistence, migration, ranking-weight, or temporal-knowledge changes
- [x] Projection tail removal now removes lower-priority evidence before higher-priority

## ADR Assessment

**Result: NO NEW ADR**

This is a small deterministic correction (classification C). It does not introduce
a new architectural decision; it corrects an implementation artifact in the existing
selector-projection boundary.

## Git Hygiene

- Branch: `story/0082-post-selection-priority-ordering`
- Only expected files modified
- No .env, *.pyc, __pycache__, IDE files, temporary files, or benchmark artifacts
- No unrelated changes
- `git diff --cached`: empty (nothing staged)
