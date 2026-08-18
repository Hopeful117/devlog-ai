# Engineering Report — Story 0081

## Problem

After Story 0080 fixed evidence projection ordering, agent projection still removed trusted knowledge (ADR, INSIGHT, ENGINEERING_STORY) before lower-value repository evidence (SOURCE_FILE, COMMIT, CHANGED_FILE).

Post-Story 0080 benchmark: 35/60 evidence survived, but all survivors were lower-priority evidence. High-value trusted knowledge was removed despite having competitive or superior relevance scores.

## Root Cause

`RepositoryContextEngine.build()` re-sorted selected evidence from score-based priority (score DESC) to layer-based grouping (layer ordinal ASC). This placed trusted knowledge (ADR=ordinal 4, ROADMAP=ordinal 5, VALIDATED_INSIGHT=ordinal 6) at the end of the list, where `AgentContextProjectionService.removeTailEvidence()` removed it first.

## Architectural Analysis

### Ordering Pipeline

```
Candidates
  → DeterministicEvidenceRanker.rank()     [score DESC → layer ordinal ASC → ref ASC]
  → BudgetedDiverseEvidenceSelector.select() [preserves rank order]
  → RepositoryContextEngine.build()         [RE-SCORE: layer ordinal ASC → score DESC → ref ASC]
  → RepositoryContext.evidence()
  → AgentContextProjectionService.project()
  → removeTailEvidence()                    [removes from LAST index]
```

### Consumer Dependencies

| Consumer | Ordering Dependency |
|---|---|
| AgentContextProjectionService | **DEPENDS_ON_PRIORITY_ORDER** — consumes in order, removes tail |
| EngineeringContextContractMapper | ORDER_INDEPENDENT |
| SelectedKnowledgePromptProjectionService | ORDER_INDEPENDENT |
| KnowledgeSelectionServiceImpl | ORDER_INDEPENDENT |
| EngineeringStoryContextServiceImpl | ORDER_INDEPENDENT |

Only one consumer depends on evidence ordering. The re-score was the only transformation between selector output and projection input that changed the ordering.

### Diversity Semantics

Selector diversity behavior is preserved. The selector's output order (which includes diversity selections) is now preserved through to projection. Diversity selections are explicitly included and should survive projection.

### Deterministic Guarantees

All stages remain deterministic:
- Ranker: deterministic comparator
- Selector: deterministic iteration
- RepositoryContext: now preserves selector output order (deterministic)
- Projection: deterministic tail removal

## Solution

Remove the re-sort in `RepositoryContextEngine.build()`. The selector's output order is the authoritative source of evidence priority.

## Verification

- 5 new tests in RepositoryContextEngineTest
- 12 existing projection tests unchanged and green
- 812/812 full suite tests pass

## Lineage Phase 2 Trace Fields

Missing trace information (not implemented in this story):
- candidate position
- ranking position
- selection position
- RepositoryContext position
- projection survival/removal
