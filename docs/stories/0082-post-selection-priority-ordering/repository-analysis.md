# Repository Analysis — Story 0082

## 1. Current Ranking Comparator

`DeterministicEvidenceRanker.rank()` sorts candidates by:

```java
Comparator.comparingInt(RepositoryEvidence::relevanceScore).reversed()
    .thenComparing(value -> value.layer().ordinal())
    .thenComparing(RepositoryEvidence::reference)
```

This produces a score-DESC → layer-ordinal-ASC → reference-ASC order.

## 2. Selector Two-Phase Behavior

`BudgetedDiverseEvidenceSelector.select()`:

1. **Diversity phase:** For each preferred layer (in profile order), pick the first
   qualifying candidate. Insert into `state.selected` in diversity order.
2. **Ordinary phase:** Iterate through remaining ranked candidates. Append to
   `state.selected` in ranked order.

The output `state.selected` is an ArrayList preserving insertion order.

## 3. Selector Output Ordering

The selector output order is: diversity picks (in preferred layer order) followed
by ordinary picks (in ranked candidate order). This is an implementation
consequence of the two-phase algorithm, NOT a deliberate survival-priority
contract.

The `SelectionDecision` records contain `selected`, `reason`, `relevanceScore`,
`estimatedTokens` — enough to reconstruct original rank but NOT explicitly
encoding survival priority.

## 4. RepositoryContextEngine Transformations

After Story 0081, `RepositoryContextEngine.build()` preserves the selector output
order directly:

```java
List<RepositoryEvidence> selected = selection.selected();
```

No sorting or reordering is applied.

## 5. Enrichment Transformations

`SelectedJavaSymbolEnricher` and `SelectedFileContentEnricher` both use
`stream().map()` over the selected list, preserving input order. They do NOT
reorder evidence.

## 6. Projection Tail-Removal Semantics

`AgentContextProjectionService.removeTailEvidence()` removes evidence from the
END of the list:

```java
while (remaining.size() > 1) {
    remaining.removeLast();
    // ...
}
```

This interprets list position as survival priority: earlier = higher priority.

## 7. Consumers of RepositoryContext.evidence()

- `AgentContextProjectionService.initial()` — stream().map(), preserves order
- `SelectedKnowledgePromptProjectionService` — stream().map(), preserves order
- `EngineeringContextContractMapper` — stream().map(), preserves order
- `KnowledgeSelectionServiceImpl` — uses .size() only
- Tests — no ordering assertions in integration tests

## 8. Consumer Ordering Assumptions

No consumer assumes specific ordering semantics. All use `stream().map()` which
preserves input order. No test asserts specific ordering of the final evidence
list.

## 9. Relevant Existing Tests

`RepositoryContextEngineTest`:

- `preservesSelectorScoreBasedPriorityOverLayerGrouping` — verifies selector
  output order is preserved (mock returns score-ordered list)
- `highScoreDecisionAppearsBeforeLowScoreSourceFile` — same pattern
- `sameScoreTieBreaksByLayerOrdinalThenReference` — verifies tie-breakers
- `selectorDiversitySelectionsPreservePosition` — verifies diversity picks keep
  position
- `projectionRemovesLowestPriorityEvidenceFirst` — verifies projection removes
  from tail

These tests use mock selectors that return items in score-ordered lists. They do
NOT test the case where diversity picks (low score) are inserted before ordinary
picks (high score).

## 10. Recommended Modification Point

**Location:** `RepositoryContextEngine.build()`, after line 84.

**Current code:**
```java
List<RepositoryEvidence> selected = selection.selected();
```

**Proposed change:** Sort `selected` by conservation order before passing to
RepositoryContext constructor.

**Why here:** This is the single point where the selected list is extracted from
the enrichment pipeline and before it is used for diagnostics, byLayer, digest,
and RepositoryContext construction. Sorting here affects all downstream consumers
consistently.

**Why not in selector:** The selector's responsibility is selection and diversity.
Adding conservation ordering would mix concerns.

**Why not in projection:** The projection should consume an already-ordered list.
Making projection understand ranking or selector internals would create a
boundary leak.

## 11. Why This Does Not Change the Selected SET

The sort operates on `selection.selected()`, which is the already-selected list.
Sorting reorders the same elements; it does not add or remove any. The
`selection.decisions()` list is unchanged and continues to record the original
selection reasons.

## 12. Why This Does Not Create a Second Ranking Engine

The conservation order uses the same `relevanceScore` field and the same
tie-breakers (layer ordinal, reference) already established by the ranker. It
does not recompute scores or introduce new criteria. It merely ensures the
selected list is ordered by the same priority signal the ranker already produced.

## 13. Why This Is Not Trusted-Knowledge Special-Casing

The conservation order applies uniformly to ALL evidence items regardless of kind.
A SOURCE_FILE with score=88 would survive before an ENGINEERING_STORY with
score=79. No kind-based exceptions are introduced.
