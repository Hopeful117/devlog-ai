# Story 0117 - Engineering Report

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
Current Engineering Story
        |
        v
existing Story scope
        |
        v
derive deterministic provenance anchors
        |
        v
bounded historical Analysis window
        |
        v
bounded historical Facts
        |
        v
HistoricalKnowledgeCandidateService
  - exact provenance matching
  - deduplication
  - candidate bounding
        |
        v
Observations linked to retained Facts
        |
        v
baseline + historical candidates
        |
        v
existing KnowledgeSelectionService
        |
        v
Story-aware ranking
        |
        v
existing budgets / closure / grounding
```

The implementation extends Story Context Analysis to consider bounded historical Facts and Observations from previous Analyses, while preserving all existing selection semantics.

## Contract Model

- `SelectedKnowledge` projection unchanged; carries existing selection version
- Historical candidates flow through existing `RepositoryContext` path
- No new transport fields or output schema changes
- `EvidenceRef` shape unchanged

## Authority Assessment

Target authority model (per ADR-063, ADR-067):

```text
JAVA_CORE = context construction + trust + validation + durability
PYTHON = generation + defensive validation + corrective retry
HISTORICAL_CANDIDATES = deterministic, bounded, provenance-driven
```

**Observed behavior:**
- Historical candidate retrieval is deterministic and provenance-driven
- No fuzzy, semantic, vector, or AI retrieval occurs
- `KnowledgeSelectionService` remains final ranking authority
- Existing grounding authority unchanged

## Execution And Durability Assessment

The implementation operates within existing transaction boundaries:

1. `execute()` builds context, retrieves historical candidates, selects knowledge
2. Historical candidates supplement baseline Facts/Observations
3. Existing selection pipeline processes combined candidates
4. No new persistence entities or transaction boundaries

## Quality Evidence

- Backend: all tests passed; JaCoCo checks met
- `git diff --check`: passed
- No generated artifacts staged

## Required Corrective Work

None identified for in-scope behavior.

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT
END_TO_END_FLOW = OPERATIONAL
ARCHITECTURAL_AUTHORITY = PRESERVED (Java/Core sole authority)
QUALITY_GATES = PASSED
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
NEXT_STATE = HUMAN_ACCEPTANCE_DECISION
```
