# Story 0117 - Repository Analysis

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH**

## Baseline

- Baseline SHA: `98852b6` (Story 0116 consolidation on `main`)
- Implementation commits: `4fc1fa2`, `2508caa`
- Governing ADRs: ADR-063, ADR-067
- Story 0116: **ACCEPTED** — Wire Validated Knowledge into SCA

## Existing Boundaries Reused

- `KnowledgeSelectionService` and its Story-aware implementation (Story 0115)
- `SelectedKnowledge` and `SelectedKnowledgePromptProjectionService` for AI transport
- `AnalyzeStoryContextUseCase` as the single SCA orchestration point
- `AiTask` lifecycle (CREATED → SUBMITTED → PROCESSING → COMPLETED/FAILED)
- `EngineeringContextFacade` for authoritative context construction
- `StoryContextAnalysis` entity with `contextFreshness` (Story 0114)
- REST and MCP as thin adapters over the Core use case

## Implemented Topology

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

## Repository Findings

The implementation extends Story Context Analysis to consider bounded historical Facts and Observations from previous Analyses.

**Key changes:**

1. **Repository extensions**: Added bounded retrieval methods for Analysis, Fact, and Observation entities.

2. **HistoricalKnowledgeCandidateService**: Implements deterministic provenance matching, deduplication, and bounding.

3. **Integration**: Historical candidates flow through existing selection pipeline without changing selection semantics.

4. **Observation handling**: Lazy loading fix ensures Observation supporting facts are eagerly loaded.

## Test Coverage Assessment

**Existing regression suites (all passing):**
- `KnowledgeSelectionServiceTest`, `KnowledgeSelectionServiceAdditionalTest`
- `StoryAwareKnowledgeSelectionTest`
- `AiTaskServiceTest`
- `AnalysisWorkflowServiceTest`

**New coverage:**
- `HistoricalKnowledgeCandidateServiceTest`: 271 tests covering provenance matching, deduplication, bounding
- `KnowledgeRepositoryPostgresIntegrationTest`: 139 tests for repository queries

## Conclusion

The repository contains the complete vertical slice for cross-analysis historical knowledge retrieval. All automated quality gates pass. The implementation follows deterministic, bounded, provenance-driven principles and preserves existing authority models.
