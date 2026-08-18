# Implementation Plan — Story 0082

## Summary

Add post-selection conservation ordering in `RepositoryContextEngine.build()`
to ensure evidence is ordered by `relevanceScore DESC` before projection.

## Production Changes

### File: `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`

**Location:** Line 84, inside `build()` method.

**Previous code:**
```java
List<RepositoryEvidence> selected = selection.selected();
```

**New code (lines 85-90):**
```java
List<RepositoryEvidence> selected = selection.selected().stream()
        .sorted(Comparator.comparingInt(RepositoryEvidence::relevanceScore)
                .reversed()
                .thenComparing(value -> value.layer().ordinal())
                .thenComparing(RepositoryEvidence::reference))
        .toList();
```

**Import added:** `java.util.Comparator` (line 21).

**Rationale:** The comparator establishes deterministic conservation priority
(`relevanceScore DESC → layer ordinal ASC → reference ASC`), ensuring projection
tail removal removes lowest-priority evidence first.

## Test Changes

### File: `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngineTest.java`

6 tests total (4 updated, 1 new, 1 existing):

1. **`highScoreEvidenceOrderedBeforeLowScoreDiversityPick`** — Verifies score-based
   reordering places high-score evidence before low-score diversity picks.

2. **`selectedSetUnchangedAfterReordering`** — Verifies SET equality after
   reordering (no items added/removed).

3. **`sameScoreTieBreaksByLayerOrdinalThenReference`** — Verifies deterministic
   tie-breaking: layer ordinal ASC, then reference ASC.

4. **`diversitySelectedEvidenceStillPresentAfterReordering`** — Verifies diversity-
   selected evidence remains in the output set.

5. **`projectionRemovesLowestPriorityEvidenceFirst`** — End-to-end test proving
   projection keeps high-score DECISION (88) and INSIGHT (82).

6. **`diversityLowScoreRemovedBeforeOrdinaryHighScoreUnderBudgetPressure`** —
   Verifies low-score diversity evidence is removed before high-score ordinary
   evidence under budget pressure.

## Behavior Change

**Before:** Evidence order = selector insertion order (diversity picks first, then
ordinary picks in ranked order). Projection tail removal consumed this order as
survival priority, causing priority inversion.

**After:** Evidence order = conservation priority (score DESC, layer ordinal ASC,
reference ASC). Projection tail removal now naturally removes lowest-priority
evidence first. The selected SET is identical; only the order changes.

## Benchmark Expectation

- FULL selected set: unchanged (60 evidence)
- AGENT surviving: ~36 evidence
- ENGINEERING_STORY survivors: ~3 (up from 2)
- INSIGHT survivors: 2 (unchanged)
- DECISION survivors: 1 (unchanged)
- Minimum diverse layers: >=3 (met)
- Budget: within 32,768 bytes and 8,192 tokens

## Rollback/Safety

Reverting to `selection.selected()` restores the previous behavior. No schema,
API, or contract changes.

## Non-Goals

- Changing the selector's selection algorithm
- Changing the projection's removal algorithm
- Adding trusted-knowledge special-casing
- Adding diversity-aware projection
- Modifying ContextProfile or precision policy
