# Engineering Story 0080 — Implementation Plan

## Strategy

**Option A: Reorder existing fitting steps.**

The smallest coherent change: move the four ProjectContext reduction steps
(steps 7–10) to run **before** evidence removal (step 6). Evidence compaction
steps (1–5) remain first — they are lightweight metadata reductions that may
suffice for small overages. Evidence removal remains the final fallback.

No new methods, no new models, no new abstractions. The same steps run in a
different order.

## Fitting Behaviour — Before

```
Step 0:  fits()? → return
Step 1:  removeRelatedReferences()       [evidence]
Step 2:  compactReasons()                [evidence]
Step 3:  removeDeclarations()            [evidence]
Step 4:  removeContent()                 [evidence]
Step 5:  compactSummary()                [evidence]
Step 6:  removeTailEvidence()            [evidence removal — LAST resort]
Step 7:  removeProfileDetails()          [ProjectContext]
Step 8:  compactHumanContextInputs()     [ProjectContext]
Step 9:  removeProjectContextLists()     [ProjectContext]
Step 10: minimalProjectContext()         [ProjectContext]
```

## Fitting Behaviour — After

```
Step 0:  fits()? → return
Step 1:  removeRelatedReferences()       [evidence]
Step 2:  compactReasons()                [evidence]
Step 3:  removeDeclarations()            [evidence]
Step 4:  removeContent()                 [evidence]
Step 5:  compactSummary()                [evidence]
Step 6:  removeProfileDetails()          [ProjectContext]    ← moved up
Step 7:  compactHumanContextInputs()     [ProjectContext]    ← moved up
Step 8:  removeProjectContextLists()     [ProjectContext]    ← moved up
Step 9:  minimalProjectContext()         [ProjectContext]    ← moved up
Step 10: removeTailEvidence()            [evidence removal]  ← moved down
```

## Why This Ordering

1. Evidence compaction (steps 1–5) is cheap and may suffice for small overages.
   No change.
2. ProjectContext reduction (steps 6–9) addresses the primary budget consumer
   (~68KB) before evidence is removed. This creates budget space for evidence
   to survive.
3. Evidence removal (step 10) remains the final fallback when even the reduced
   projection cannot fit all evidence alongside the required context.

## Warning Semantics

Warning emission is unchanged — each step emits its warning when it runs. The
only change is the **order** in which ProjectContext warnings may appear
relative to evidence warnings. Existing tests assert warning **presence**, not
**order**, so no test breakage is expected from warning reordering.

## Determinism

Determinism is preserved. For identical input context and identical budgets,
the same steps run in the same order, producing the same output. The
`projectionDigest` (SHA-256 of the canonical JSON) confirms this.

## Budget Implications

No budget change. The projection budget remains 32,768 bytes / 8,192 tokens.
The fix corrects projection **behaviour**, not budget allocation.

## Ranking / Selection

Ranking and selection remain untouched. The projection consumes their output.
It does not reinterpret relevance. No candidate generation, diversity, or
profile weight changes.

## Production Files Changed

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java` | Reorder `fit()` steps: move steps 7–10 before step 6 |

**No other production files change.**

## Tests — Planned

### New tests in `AgentContextProjectionServiceTest`

1. **`shouldPreserveEvidenceWhenProjectContextIsOversized`** — Regression test
   for the production failure class. Uses oversized ProjectContext (~68KB) with
   multiple evidence items. Asserts:
   - Final context respects budget
   - At least one evidence item survives
   - `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` is absent
   - ProjectContext warnings present (ProfileDetailsRemoved or
     HumanContextInputsCompacted or ProjectContextListsRemoved or
     ProjectContextMinimal)

2. **`shouldNotReduceContextThatAlreadyFits`** — Small context under budget.
   Asserts:
   - No warnings emitted
   - Evidence unchanged
   - ProjectContext unchanged

3. **`shouldRemoveEvidenceWhenPhysicallyImpossible`** — Budget too small even
   after ProjectContext reduction. Asserts:
   - `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` emitted
   - Final context respects budget

4. **`shouldProduceDeterministicOutputForIdenticalInput`** — Same input
   projected twice. Asserts:
   - Same projectionDigest
   - Same evidence count
   - Same warnings

### Existing tests — verification

All existing tests should continue to pass without modification. Verified:

- `shouldCreateCompactTraceableDeterministicProjection` — large budget, no
  reduction needed. Unchanged.
- `shouldApplyMechanicalDegradationAndPreserveOutcomeMetadata` — small budget,
  evidence compacted. Step ordering doesn't affect outcome.
- `shouldCompactLongSummaryWhenProjectionNeedsAdditionalReduction` — summary
  compaction. Unchanged.
- `shouldFallbackToMinimalOrEmptyEvidenceWhenCompactionGetsTight` — very small
  budget. Evidence removal still runs last; outcome unchanged.
- `shouldCompactProjectContextWhenEmptyEvidenceStillDoesNotFit` — oversized
  ProjectContext with 1 evidence. After reordering, ProjectContext is reduced
  before evidence removal. The 1 evidence item may now survive (improvement).
  Test asserts profile/lists warnings OR minimal — still valid.
- `shouldRemoveOnlyTheExistingTailAsLastResort` — 8 evidence, small budget.
  Evidence compaction + removal. After reordering, if ProjectContext is small
  (it is — `projectContext()` returns minimal), steps 6–9 are no-ops, then step
  10 removes tail. Unchanged.
- `shouldFailWhenOneUsableEvidenceCannotFit` — budget too small. Unchanged.
- `shouldChangeProjectionDigestWhenSemanticEvidenceChanges` — determinism.
  Unchanged.

### Service-level test

No changes to `EngineeringStoryContextServiceTest` — it mocks the projection
service. The regression test at the projection-service level is sufficient.

## Code Review Checklist

- [ ] `fit()` step ordering is correct and complete
- [ ] All 12 warning constants are still emitted correctly
- [ ] `removeTailEvidence()` remains the last evidence-affecting step
- [ ] `removeProfileDetails()` → `compactHumanContextInputs()` →
  `removeProjectContextLists()` → `minimalProjectContext()` ordering within
  ProjectContext steps is preserved
- [ ] No new methods or classes introduced
- [ ] No ranking or selection logic touched
- [ ] No budget constants changed
- [ ] No special-casing of evidence types
- [ ] All existing tests pass
- [ ] New regression test passes
- [ ] Determinism confirmed (projectionDigest stable)

## Rollback / Regression Risk

Low. The change is a reordering of existing steps within a single method. If
any regression is detected, reverting the step order restores the previous
behaviour. No data, schema, or cross-service changes are involved.

## Post-Implementation Benchmark

After deployment, compare:

**Before:**
```
217 candidates → 60 selected → 0 agent-projected evidence
AGENT_PROJECTION_ALL_EVIDENCE_REMOVED
```

**After:**
```
217 candidates → 60 selected → N > 0 agent-projected evidence
```

Observe whether high-ranked evidence (DECISION, INSIGHT, ENGINEERING_STORY)
survives naturally because it appears first in the ranked list and
`removeTailEvidence()` removes from the tail.
