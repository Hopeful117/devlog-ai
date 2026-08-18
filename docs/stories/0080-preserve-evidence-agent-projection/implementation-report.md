# Engineering Story 0080 — Implementation Report

## Summary

Reordered `AgentContextProjectionService.fit()` steps so that ProjectContext
reduction runs before evidence removal. This ensures that when ProjectContext
is independently oversized, it is reduced first, creating budget space for
evidence to survive.

## Files Modified

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java` | Reordered `fit()` steps: moved ProjectContext reduction (steps 7–10) before evidence removal (step 6) |
| `backend/src/test/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionServiceTest.java` | Added 4 new regression tests |

## Production Behavior Change

**Before:**

```
evidence compaction (steps 1–5)
→ evidence removal (step 6)
→ ProjectContext reduction (steps 7–10)
```

When ProjectContext alone exceeds the budget, all evidence is removed before
ProjectContext is reduced.

**After:**

```
evidence compaction (steps 1–5)
→ ProjectContext reduction (steps 6–9)
→ evidence removal (step 10, last resort)
```

ProjectContext is reduced first, creating budget space for evidence to survive.

## Test Results

- **Focused tests:** 12/12 pass (8 existing + 4 new)
- **Full backend suite:** 807/807 pass

## Tests Added

1. `shouldPreserveEvidenceWhenProjectContextIsOversized` — Regression test for
   the production failure class. Oversized ProjectContext + multiple evidence
   items → evidence survives.
2. `shouldNotReduceContextThatAlreadyFits` — Small context under budget → no
   destructive reduction.
3. `shouldRemoveEvidenceWhenPhysicallyImpossible` — Budget too small even after
   ProjectContext reduction → exception thrown.
4. `shouldProduceDeterministicOutputForIdenticalInput` — Same input projected
   twice → same projectionDigest, evidence count, and warnings.
