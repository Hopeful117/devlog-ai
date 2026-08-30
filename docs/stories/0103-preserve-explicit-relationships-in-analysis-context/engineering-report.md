# Story 0103 — Engineering Report

## Status

**HUMAN_IMPLEMENTATION_REVIEW_APPROVED_AUTHORIZED_FOR_COMMIT**

## Corrected Architecture

Story 0103 is a composition story, not a selection story.

```text
AnalysisContext.knowledgeRelations
        ↓ preserve unchanged
SelectedKnowledge.knowledgeRelations
        ↓ composition only
SelectedKnowledgePromptProjectionService
        ↓ Policy A using actual selected endpoint identity membership
PromptProjection.relationshipHighlights
```

## Final Production Changes

### 1. `SelectedKnowledge.java`
- Added `knowledgeRelations` field
- Preserves canonical relation snapshots as selected knowledge input to composition

### 2. `KnowledgeSelectionServiceImpl.java`
- Copies `context.knowledgeRelations()` unchanged
- Adds preservation rule marker: `KNOWLEDGE_RELATION_PRESERVATION`
- Includes preserved relations in selection digest inputs
- Does **not** apply Policy A

### 3. `SelectedKnowledgePromptProjectionService.java`
- Adds additive `relationshipHighlights`
- Implements Policy A at the projection boundary only
- Uses actual selected endpoint identity membership
- Applies explicit deterministic bound `20`
- Uses minimal deterministic ordering:
  1. `relationType`
  2. `sourceEntityType`
  3. `sourceEntityId`
  4. `targetEntityType`
  5. `targetEntityId`
  6. canonical relation `id`

## Final RelationshipHighlight Shape

```java
record PromptRelationshipHighlight(
    String relationType,
    PromptRelationshipEndpoint source,
    PromptRelationshipEndpoint target
) {}

record PromptRelationshipEndpoint(
    String entityType,
    String entityId
) {}
```

No labels, rationale, provenance, lineage, ranking metadata, or inferred relationships were added.

## Policy A Implementation

Eligible endpoint combinations remain:

- `INSIGHT -> INSIGHT`
- `INSIGHT -> ENGINEERING_EVENT`
- `ENGINEERING_EVENT -> INSIGHT`
- `ENGINEERING_EVENT -> ENGINEERING_EVENT`

Actual projection requires endpoint membership:

- `INSIGHT(id)` must exist in `selectedInsights`
- `ENGINEERING_EVENT(id)` must exist in `selectedEngineeringEvents`

`DECISION` and `CHALLENGE` endpoints are excluded in this slice.

Example eligible relation from tests:

```text
INSIGHT(A) -> ENGINEERING_EVENT(E)
```

Projected only when both `A` and `E` are already independently selected.

Example excluded same-type relation from tests:

```text
INSIGHT(A) -> INSIGHT(B)
```

Excluded when `A` is selected but `B` is absent from `selectedInsights`.

## Boundedness

- Owner: projection layer only
- Constant: `MAX_RELATIONSHIP_HIGHLIGHTS = 20`
- Status: tunable V1 implementation policy, accepted but not empirically validated by the current zero-eligible runtime benchmark
- Justification: upstream selection already caps independently selected relation-bearing endpoints at `10` insights and `10` engineering events

This keeps relationship composition explicit, additive, deterministic, and simple.

## Shared Prompt Path

Verified path remains:

```text
PromptProjection
→ ObjectMapper.convertValue(..., Map.class)
→ PromptRequest.selectedKnowledge
→ HTTP JSON
→ Python selectedKnowledge dict
→ existing builders serialize the shared selectedKnowledge payload
```

Result:

- No Python production change required
- No Python schema change required
- No frontend production change required

## Selector Boundary Correction

- Story 0103 production changes were removed from `BudgetedDiverseEvidenceSelector.java`
- Final expected status: no Story 0103 production diff remains in that file
- Story 0098 selector semantics remain intact

## Benchmark Interpretation

### Corrected BEFORE Understanding

- Baseline `127a58e` already contains Story 0098
- Therefore pre-0098 `COMMIT_DIFF` dominance numbers must not be reused as Story 0103 baseline numbers
- Exact Story 0103 runtime BEFORE benchmark was not captured before implementation

### AFTER Results

Real product workflow benchmark results:

- `describe-project-v1`: canonical `44`, eligible `0`, bound `0`, projected `0`, payload `0`, `COMMIT_DIFF = 12`, proposals `6`
- `architecture-overview-v1`: canonical `44`, eligible `0`, bound `0`, projected `0`, payload `0`, `COMMIT_DIFF = 12`, proposals `0`
- `analyze-engineering-decision-v1`: canonical `44`, eligible `0`, bound `0`, projected `0`, payload `0`, `COMMIT_DIFF = 12`, proposals `4`

In all three runs, the canonical relations were `INSIGHT -> INSIGHT` with endpoint IDs not present in the independently selected insights/events, so Policy A correctly excluded them.

## Conclusion

Story 0103 now matches the HUMAN-approved boundary exactly:

- preserve in selection
- filter in projection
- no selection expansion
- no selector mutation
- no Python change

Technical relationship preservation works. Runtime relationship preservation path is technically validated. Runtime eligible relationship coverage is zero in the current benchmark. Qualitative Analysis improvement is not demonstrated because the project state did not produce any Policy-A-eligible endpoint matches.

Decision grounding remains unchanged and out of scope.
