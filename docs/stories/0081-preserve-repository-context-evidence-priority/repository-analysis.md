# Repository Analysis — Story 0081

## Branch

`story/0081-preserve-repository-context-evidence-priority`

## Current Ordering Pipeline

```
Candidates
  → DeterministicEvidenceRanker.rank()
  → BudgetedDiverseEvidenceSelector.select()
  → RepositoryContextEngine.build()  ← RE-SORT HERE
  → RepositoryContext.evidence()
  → AgentContextProjectionService.project()
  → removeTailEvidence()
```

## Exact Current Ordering Transformation

### Ranker Output (DeterministicEvidenceRanker.java:42-46)

```java
.sorted(Comparator.comparingInt(RepositoryEvidence::relevanceScore).reversed()
        .thenComparing(value -> value.layer().ordinal())
        .thenComparing(RepositoryEvidence::reference))
```

**Order:** score DESC → layer ordinal ASC → reference ASC

### Selector Output (BudgetedDiverseEvidenceSelector.java)

Iterates through ranked candidates in order. `state.selected` preserves insertion order. Output is ordered by rank (score DESC within same layer).

### RepositoryContextEngine Re-sort (RepositoryContextEngine.java:85-91)

```java
List<RepositoryEvidence> selected = selection.selected().stream()
        .sorted(Comparator.comparingInt(
                        (RepositoryEvidence value) -> value.layer().ordinal())
                .thenComparing(Comparator.comparingInt(
                        RepositoryEvidence::relevanceScore).reversed())
                .thenComparing(RepositoryEvidence::reference))
        .toList();
```

**Order:** layer ordinal ASC → score DESC → reference ASC

**This is the defect.** The re-sort changes the primary sort dimension from score to layer ordinal, which groups trusted knowledge at the end of the list.

### Projection (AgentContextProjectionService.java:111)

```java
List<AgentRepositoryContext.Evidence> evidence = context.evidence().stream()
        .map(value -> evidence(value, selectedReasons.get(value.reference())))
        .toList();
```

Consumes evidence in the order provided by `RepositoryContext.evidence()`.

### Tail Removal (AgentContextProjectionService.java:223-224)

```java
while (remaining.size() > 1) {
    remaining.removeLast();
```

Removes from the LAST index. Items at the end of the list are removed first.

## Consumer Ordering Dependencies

| Consumer | Access Pattern | Ordering Dependency |
|---|---|---|
| AgentContextProjectionService | `context.evidence().stream().map(...)` | **DEPENDS_ON_PRIORITY_ORDER** — projection consumes evidence in order, removes from tail |
| EngineeringContextContractMapper | `repositoryContext.evidence().stream().map(...)` | ORDER_INDEPENDENT — just maps to contract |
| SelectedKnowledgePromptProjectionService | `repositoryContext.evidence().stream().map(...)` | ORDER_INDEPENDENT — just maps to prompt |
| KnowledgeSelectionServiceImpl | `repositoryContext.evidence().size()` | ORDER_INDEPENDENT — just counts |
| EngineeringStoryContextServiceImpl | `repositoryContext.evidence().size()` | ORDER_INDEPENDENT — just logs |

## Diversity Semantics

`BudgetedDiverseEvidenceSelector.selectDiverseEvidence()` adds evidence from preferred layers in iteration order. This may add evidence from lower-ranked layers before higher-ranked layers. However, the selector's `selectOrdinary()` then fills in remaining capacity by iterating through the full ranked list.

The selector output order represents **selection execution order**, which includes:
1. Diversity selections (may be from any layer)
2. Ordinary ranked selections (score DESC)

The selector output order is suitable as projection priority because:
- Diversity selections are intended to be included (they represent minimum representation)
- Ordinary selections follow score-based priority
- The combination represents the selector's authoritative "what should survive" ordering

However, diversity selections from lower-scored layers may appear at the start of the list (before higher-scored items). This is acceptable because diversity selections are explicitly included and should survive projection.

## Projection Interaction

When `RepositoryContext.evidence()` is layer-sorted:
- RELATED_SOURCE_CODE (ordinal 1) → first in list
- GIT_HISTORY (ordinal 2) → second group
- COMMIT_DIFF (ordinal 3) → third group
- ADR (ordinal 4) → fourth group
- ROADMAP (ordinal 5) → fifth group
- VALIDATED_INSIGHT (ordinal 6) → last group

`removeTailEvidence()` removes from position 59 backwards, hitting VALIDATED_INSIGHT first, then ROADMAP, then ADR.

## Deterministic Guarantees

The same candidates/profile/budget must produce the same:
- ranked list (deterministic comparator)
- selected list (deterministic iteration)
- RepositoryContext evidence order (deterministic re-sort)
- projection survivors (deterministic tail removal)

After fix, RepositoryContext evidence order will match selector output order (score-based), which is also deterministic.

## Benchmark Reference

Post-Story 0080 runtime evidence (60 selected, 35 survived):

| Position | Layer | Kind | Score | Projected? |
|---|---|---|---|---|
| 1 | RELATED_SOURCE_CODE | SOURCE_FILE | 48 | YES |
| 2-8 | GIT_HISTORY | COMMIT | 81-86 | YES |
| 9-51 | COMMIT_DIFF | CHANGED_FILE | 80-89 | PARTIAL (27/43) |
| 52 | ADR | DECISION | 88 | NO |
| 53-57 | ROADMAP | ENGINEERING_STORY | 79-81 | NO |
| 58-60 | VALIDATED_INSIGHT | INSIGHT | 80-86 | NO |

## Layer Ordinal Map

| Layer | Ordinal |
|---|---|
| CURRENT_ANALYSIS | 0 |
| RELATED_SOURCE_CODE | 1 |
| GIT_HISTORY | 2 |
| COMMIT_DIFF | 3 |
| ADR | 4 |
| ROADMAP | 5 |
| VALIDATED_INSIGHT | 6 |
| PREVIOUS_ANALYSIS | 7 |
| PROJECT_DOCUMENTATION | 8 |

## Serialized Size (Not Causal)

| Kind | Avg Canonical Size |
|---|---|
| CHANGED_FILE | 2,585B |
| COMMIT | 1,823B |
| DECISION | 2,135B |
| ENGINEERING_STORY | 1,870B |
| INSIGHT | 1,888B |
| SOURCE_FILE | 3,378B |

Trusted knowledge is NOT larger. Size is not the cause.

## Conclusion

The only consumer that depends on evidence ordering is `AgentContextProjectionService`. The re-sort in `RepositoryContextEngine.build()` changes the ordering from score-based to layer-based, which makes the tail removal remove trusted knowledge before repository evidence. The fix is to preserve the selector's output order in `RepositoryContext.evidence()`.
