# Engineering Report — Story 0082

## Branch

`story/0082-post-selection-priority-ordering`

## Story

0082 — Post-Selection Priority Ordering for Projection Survival

## Engineering Assessment

### Problem Classification

**C. POST_SELECTION_REORDERING_IS_CORRECT**

The `BudgetedDiverseEvidenceSelector` outputs evidence in diversity-first order.
Diversity picks are inserted first (in preferred layer order), then ordinary ranked
items follow. The `AgentContextProjectionService.removeTailEvidence()` removes
evidence from the tail, interpreting "late in selector output" as "lowest survival
priority."

This creates a priority inversion where lower-score diversity evidence survives
while higher-score ordinary evidence is removed.

### Architecture Ownership

**F. CURRENT_RESPONSIBILITIES_INSUFFICIENTLY_DEFINED**

The selector's output order is an implementation artifact of the two-phase algorithm,
not a deliberate survival-priority signal. The projection service assumed this order
represented survival priority.

### Investigation Classification

**SMALL_DETERMINISTIC_CORRECTION**

This is a minimal, low-risk change that corrects an implementation artifact at the
selector-projection boundary. It does not introduce new architectural concepts or
require new ADRs.

## Solution

Added a single post-selection conservation sort in `RepositoryContextEngine.build()`
that reorders evidence by `relevanceScore DESC -> layer ordinal ASC -> reference ASC`
before passing to projection.

### Why This Works

The `AgentContextProjectionService.removeTailEvidence()` uses `remaining.removeLast()`
to remove evidence from the tail of the list. With conservation ordering, the tail
now contains the lowest-priority evidence, so removal is correct.

### Risk Assessment

- **Risk: LOW** — Single sort operation on already-selected evidence
- **Rollback:** Revert to `selection.selected()` to restore previous behavior
- **Scope:** 1 production file, 1 import added, 5 lines of logic
- **Testing:** 6 focused tests, 813 full backend tests, all passing

## Behavioral Changes

### Priority Inversion Fix

**Before (broken):**
```
selector output: [diversity(48), ordinary(90), ordinary(82)]
projection removes from tail: removes 82, removes 90, keeps 48
```

**After (fixed):**
```
conservation order: [ordinary(90), ordinary(82), diversity(48)]
projection removes from tail: removes 48, keeps 90 and 82
```

### Evidence Survival Impact

- ENGINEERING_STORY: +1 survivor (score 81 now survives instead of being removed)
- DECISION: unchanged (score 88 always survives)
- INSIGHT: unchanged (score 82 survives)
- SOURCE_FILE: -1 survivor (score 48 now removed instead of surviving)

## Test Coverage

| Category | Test | Status |
|---|---|---|
| Score ordering | `highScoreEvidenceOrderedBeforeLowScoreDiversityPick` | PASS |
| SET preservation | `selectedSetUnchangedAfterReordering` | PASS |
| Tie-breaking | `sameScoreTieBreaksByLayerOrdinalThenReference` | PASS |
| Diversity preserved | `diversitySelectedEvidenceStillPresentAfterReordering` | PASS |
| Projection regression | `projectionRemovesLowestPriorityEvidenceFirst` | PASS |
| Budget pressure | `diversityLowScoreRemovedBeforeOrdinaryHighScoreUnderBudgetPressure` | PASS |

## Documentation Reconciliation

- `story.md`: Matches implementation. Problem, cause, consequence, correction all accurate.
- `implementation-plan.md`: Updated to reflect actual code changes and test names.
- `implementation-report.md`: Created with accurate change description and test results.
- `engineering-report.md`: This document.

## ADR Assessment

**NO NEW ADR REQUIRED**

This change does not introduce a new architectural decision. It corrects an
implementation artifact at the selector-projection boundary. The conservation
ordering is an internal detail of `RepositoryContextEngine.build()`, not a new
architectural concept.

## Git Hygiene

- Branch: `story/0082-post-selection-priority-ordering`
- Files modified: 2 (RepositoryContextEngine.java, RepositoryContextEngineTest.java)
- Untracked: `docs/stories/0082-post-selection-priority-ordering/` (story artifacts)
- No .env, *.pyc, __pycache__, IDE files, temporary files, or benchmark artifacts
- No unrelated changes

## Post-Merge Benchmark Expectations

- FULL selected set: unchanged (60 evidence)
- AGENT surviving: ~36 evidence
- ENGINEERING_STORY survivors: ~3 (up from 2)
- INSIGHT survivors: 2 (unchanged)
- DECISION survivors: 1 (unchanged)
- Minimum diverse layers: >=3 (met)
- Budget: within 32,768 bytes and 8,192 tokens
