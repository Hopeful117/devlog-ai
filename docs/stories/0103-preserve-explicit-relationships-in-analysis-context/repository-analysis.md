# Story 0103 — Repository Analysis

## Status

**HUMAN_IMPLEMENTATION_REVIEW_APPROVED_AUTHORIZED_FOR_COMMIT**

## Baseline

- Verified baseline SHA: `127a58e`
- Verified branch during correction: `story/0103-relationship-preservation`
- Baseline repository meaning: Story 0098 already merged, ADR-064 already accepted

## Verified Original Loss Boundary

At baseline `127a58e`, `AnalysisContext.knowledgeRelations` existed, but `KnowledgeSelectionServiceImpl.select()` did not preserve it into `SelectedKnowledge`, so explicit relationships were lost before prompt projection.

## Corrected Final Boundary

```text
AnalysisContext.knowledgeRelations
        ↓ preserve all canonical relations
SelectedKnowledge.knowledgeRelations
        ↓ composition only
SelectedKnowledgePromptProjectionService.buildRelationshipHighlights()
        ↓ Policy A using actual selected endpoint identity membership
PromptProjection.relationshipHighlights
```

## Final Production Behavior

### SelectedKnowledge
- New field: `knowledgeRelations`
- Preserves canonical `AnalysisContext.knowledgeRelations` unchanged

### KnowledgeSelectionServiceImpl
- Copies `context.knowledgeRelations()` into `SelectedKnowledge`
- Does not apply Policy A
- Does not filter out `DECISION` or `CHALLENGE` relations
- Does not expand selection or retrieval

### SelectedKnowledgePromptProjectionService
- Applies Policy A only during projection
- Eligible endpoint types remain:
  - `INSIGHT`
  - `ENGINEERING_EVENT`
- Actual projection requires both endpoint IDs to be present in:
  - `selectedInsights`
  - `selectedEngineeringEvents`

### Policy A Algorithm

For each canonical `KnowledgeRelationSnapshot`:

1. source endpoint must be selected/projected:
   - `INSIGHT` source ID must exist in `selectedInsights`
   - `ENGINEERING_EVENT` source ID must exist in `selectedEngineeringEvents`
2. target endpoint must be selected/projected:
   - `INSIGHT` target ID must exist in `selectedInsights`
   - `ENGINEERING_EVENT` target ID must exist in `selectedEngineeringEvents`
3. `DECISION` and `CHALLENGE` endpoints are always excluded in this slice
4. eligible relations are sorted deterministically
5. eligible relations are truncated to explicit bound `20`
6. projected output shape is minimal:
   - `relationType`
   - `source.entityType`
   - `source.entityId`
   - `target.entityType`
   - `target.entityId`

## Final Boundedness Policy

- Bound owner: `SelectedKnowledgePromptProjectionService`
- Bound: `MAX_RELATIONSHIP_HIGHLIGHTS = 20`
- Status: tunable V1 implementation policy, not architectural constant
- Cardinality evidence used:
  - `selectedInsights` already capped at `10`
  - `selectedEngineeringEvents` already capped at `10`
  - additive relationship section therefore capped at one highlight per maximum independently selected endpoint on average

## Deterministic Ordering

Implemented ordering:

1. `relationType`
2. `sourceEntityType`
3. `sourceEntityId`
4. `targetEntityType`
5. `targetEntityId`
6. canonical relation `id` as final deterministic tiebreaker

## AFTER Benchmark Interpretation

Real workflow runs on the corrected backend showed:

- `44` canonical relationships in each run
- all `44` were `INSIGHT -> INSIGHT`, relation type `RESOLVES`
- none of those endpoint IDs matched the `10` independently selected insights in any of the three runs
- therefore Policy-A eligible count was `0`
- therefore projected `relationshipHighlights` count was `0`
- therefore the technical implementation is correct, but qualitative improvement is limited on this project state because there are no endpoint-membership-eligible relations in the benchmark runs

## Correction of Earlier Incorrect Claims

- Incorrect: Story 0103 changed `BudgetedDiverseEvidenceSelector`
- Correct: final Story 0103 production scope excludes the selector entirely
- Incorrect: Policy A belongs in `KnowledgeSelectionServiceImpl`
- Correct: `KnowledgeSelectionServiceImpl` preserves; projection enforces Policy A
- Incorrect: Python builders or schemas required production changes
- Correct: automatic shared propagation already handled the new field without Python code changes
- Incorrect: baseline `127a58e` had `COMMIT_DIFF ~73-75%`
- Correct: those values were older pre-0098 diagnostic figures and not valid for this Story baseline
