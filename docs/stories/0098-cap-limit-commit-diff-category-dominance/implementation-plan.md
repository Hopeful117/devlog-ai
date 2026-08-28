# Story 0098 - Implementation Plan

## Status

**DESIGNED**

## Summary

Cap COMMIT_DIFF category dominance by introducing a configurable maximum category
share ceiling in `BudgetedDiverseEvidenceSelector`, restoring budget diversity
across evidence categories.

Introduces:
- `maximumCategorySharePct` field in `EvidencePrecisionPolicy` — a ceiling
  percentage that limits how many items any single kind can claim.
- Ceiling enforcement in `BudgetedDiverseEvidenceSelector.selectOrdinary()` — a
  hard cap applied after the existing `categoryEligible()` check.

No ranking, floor, kindAllowance, prompt, provider, ADR, MCP, RAG, or
enrichment change.

## Governing Decisions

- ADR-063 (Accepted, Human Context Supremacy amendment) governs this Story.
- ADR-044 preserved: content enrichment stays restricted to SOURCE_FILE/TEST_FILE.
- ADR-038 preserved: commit diff evidence connected per Story 0097.
- Single bounded envelope: 60-item `maximumEvidenceItems` budget — ceiling operates
  WITHIN this budget.
- Story 0095 category floors preserved unchanged.
- Strong relevance override preserved: items with score ≥ 75 can bypass kindAllowance
  but NOT the ceiling.

## Execution Ownership

| Label | Meaning for this Story |
|---|---|
| `[AGENT]` | The agent may implement and verify the backend slice. |
| `[HUMAN]` | The human reviews, approves, and commits. |

## Exact Production Files

### Modified Backend Files

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidencePrecisionPolicy.java` | Add `maximumCategorySharePct` field with default 100. |
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java` | Add ceiling enforcement in `selectOrdinary()`. |
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligence.java` | Set maximumCategorySharePct=20 for ENGINEERING_STORY_PRECISION. |

No new files created in production code.

## Backend Contract

### `EvidencePrecisionPolicy.maximumCategorySharePct`

Maximum percentage of the budget that any single kind can claim. Derived ceiling:
`maximumCategoryItems = ceil(budget * maximumCategorySharePct / 100)`.

Default: 100 (no limit, backwards compatible).

### Ceiling enforcement in `selectOrdinary()`

After the existing `categoryEligible()` check, add:
```java
Integer kindCount = kindCounts.get(kind);
if (kindCount != null && kindCount >= maximumCategoryItems) {
    state.discardedCount++;
    continue;
}
```

This is a hard cap: strong relevance can override kindAllowance but NOT the ceiling.

## Implementation Slices

### Slice 1 - Policy Extension

Files:
- `EvidencePrecisionPolicy.java`

Work:
- Add `int maximumCategorySharePct` field.
- Update constructor to accept the new field with default 100.
- Update validation: `maximumCategorySharePct` must be ≥ 0 and ≤ 100.

Completion signal: existing tests still pass, new field defaults to 100.

### Slice 2 - Ceiling Enforcement

Files:
- `BudgetedDiverseEvidenceSelector.java`

Work:
- Read `maximumCategorySharePct` from `policy`.
- Compute `maximumCategoryItems = ceil(budget * maximumCategorySharePct / 100)`.
- Add ceiling check in `selectOrdinary()` after `categoryEligible()`.
- Pass `maximumCategoryItems` to `selectKnowledgeFloor()` so ceiling also applies
  to floor-selected items (consistency).

Completion signal: ceiling prevents items beyond the limit from being selected.

### Slice 3 - Profile Configuration

Files:
- `DeterministicContextIntelligence.java`

Work:
- Set `maximumCategorySharePct=20` for ENGINEERING_STORY_PRECISION.
- Verify UNRESTRICTED profiles use default 100.

Completion signal: ENGINEERING_STORY_PRECISION has maximumCategorySharePct=20.

### Slice 4 - Unit Tests

Files:
- `BudgetedDiverseEvidenceSelectorTest.java`

Work:
- Add test: ceiling prevents items beyond limit.
- Add test: strong relevance can bypass kindAllowance but not ceiling.
- Add test: unused ceiling capacity flows to ordinary pass.
- Add test: ceiling applies uniformly to all kinds.
- Verify: ENGINEERING_STORY_PRECISION has maximumCategorySharePct=20.

Completion signal: all new tests pass, existing tests unchanged-green.

### Slice 5 - Five-Intent Benchmark

Work:
- Run five-intent benchmark via MCP endpoint.
- Verify COMMIT_DIFF ≤ 12 items across all intents.
- Verify diversity restored (other categories fill remaining budget).
- Record before/after comparison.

Completion signal: benchmark confirms improvement.

## Verification Plan

### Unit Tests
- `BudgetedDiverseEvidenceSelectorTest` — ceiling enforcement tests
- `KnowledgeFloorSelectionTest` — floor + ceiling interaction tests
- Existing 984 backend tests — no regression

### Integration Tests
- Five-intent benchmark via `curl localhost:18080/api/v1/projects/devlog-ai/engineering-context?intent=<intent>`
- Verify per-kind counts in selected evidence

### Manual Verification
- Inspect selected evidence includes non-COMMIT_DIFF categories
- Verify GIT_HISTORY, ADR, knowledge kinds appear in output
