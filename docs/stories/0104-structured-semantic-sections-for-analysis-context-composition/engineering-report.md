# Story 0104 — Engineering Report

## Status

**STORY_0104_INVESTIGATION_COMPLETE_READY_FOR_HUMAN_REVIEW**

## Architecture

Story 0104 is a composition story, not a selection story.

```text
SelectedKnowledge (input, unchanged)
        ↓
SemanticSectionComposer.compose(selectedKnowledge)
        ↓
List<PromptSemanticSection> (reference-based)
        ↓
SelectedKnowledgePromptProjectionService (modified)
        ↓
PromptProjection.semanticSections
        ↓
automatic shared serialization
        ↓
AI-facing selectedKnowledge
```

## Production Changes

### 1. `SemanticSection.java` (create)

- `SectionId` enum: PROJECT_STATE, ARCHITECTURE, DECISIONS, VALIDATED_KNOWLEDGE, HISTORY, REPOSITORY_CHANGES, HUMAN_CONTEXT
- `PromptSemanticSection` record: sectionId, sectionTitle, items
- `PromptSemanticSectionItem` record: itemType, itemId, label
- Classification maps: explicit `EnumSet.of()` per type per section for all 56 FactTypes, 12 ObservationTypes, 8 InsightTypes, 9 RepositoryContextLayers, 5 ProjectHumanContextInputTypes

### 2. `SemanticSectionComposer.java` (create)

- Consumes `SelectedKnowledge`
- Applies deterministic classification using explicit maps
- Supports multi-membership (items in multiple sections)
- Creates lightweight references (itemType, itemId, label)
- Enforces deterministic section ordering (SectionId enum ordinal)
- Enforces deterministic item/reference ordering (type, label, itemId)
- Omits empty sections
- Returns `List<PromptSemanticSection>`

### 3. `SelectedKnowledgePromptProjectionService.java` (modify)

- Added `SemanticSectionComposer` dependency
- Added `semanticSections` field to `PromptProjection`
- Call `SemanticSectionComposer.compose()` during projection
- Preserve insight IDs in `projectInsight()` (stop stripping `id`)
- Existing `relationshipHighlights` behavior unchanged

## RelationshipHighlight Shape

Unchanged from Story 0103:

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

## Benchmark Interpretation

### BEFORE (Historical Reference)

| Objective | Baseline Bytes | Relationship Baseline |
|---|---|---|
| `describe-project-v1` | 68,040 | canonical=44, eligible=0, projected=0 |
| `architecture-overview-v1` | 62,136 | canonical=44, eligible=0, projected=0 |
| `analyze-engineering-decision-v1` | 66,077 | canonical=44, eligible=0, projected=0 |

### AFTER

Product benchmark requires running application with database. Not executed in this environment.

Expected payload delta from corrective investigation: +7.5–12%.

## Conclusion

Story 0104 implements the approved corrective design:

- explicit type-based classification
- multi-section membership
- lightweight reference representation
- UNCLASSIFIED policy for new types
- mandatory HUMAN_CONTEXT and VALIDATED_KNOWLEDGE membership
- deterministic ordering
- empty-section omission
- no content duplication
- no selection changes
- no Python changes
- no frontend changes

Implementation complete. 1040/1040 tests PASS. Awaiting HUMAN review.
