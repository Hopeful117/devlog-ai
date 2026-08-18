# Implementation Report — Story 0081

## Branch

`story/0081-preserve-repository-context-evidence-priority`

## Commit

`456bbfc` — `fix(context): preserve evidence priority through RepositoryContext`

## Changes

### RepositoryContextEngine.java

Removed the re-sort in `build()` (lines 85-91) that changed evidence ordering from score-based to layer-grouped:

```java
// Before
List<RepositoryEvidence> selected = selection.selected().stream()
        .sorted(Comparator.comparingInt(
                        (RepositoryEvidence value) -> value.layer().ordinal())
                .thenComparing(Comparator.comparingInt(
                        RepositoryEvidence::relevanceScore).reversed())
                .thenComparing(RepositoryEvidence::reference))
        .toList();

// After
List<RepositoryEvidence> selected = selection.selected();
```

### RepositoryContextEngineTest.java (NEW)

5 tests covering:
1. Score-based priority preserved over layer grouping
2. High-score DECISION appears before low-score SOURCE_FILE
3. Tie-breaking by layer ordinal ASC then reference ASC
4. Diversity selections preserve position
5. Integration with AgentContextProjectionService — high-priority evidence survives under budget

## Test Results

- 25/25 targeted tests pass
- 812/812 full suite tests pass
- No regressions

## Verification

The fix was verified by Test 5 (`projectionRemovesLowestPriorityEvidenceFirst`), which directly reproduces the structural failure:
- 3 evidence items: DECISION (score 88), INSIGHT (score 82), SOURCE_FILE (score 48)
- Constrained projection budget (2,000 bytes)
- Before fix: DECISION and INSIGHT removed, SOURCE_FILE survives
- After fix: DECISION and INSIGHT survive, SOURCE_FILE removed
