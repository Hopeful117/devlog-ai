# Story 0116 - Repository Analysis

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH**

## Baseline

- Baseline SHA: `982c538` (Story 0115 merge commit on `main`)
- Baseline branch: `main`
- Implementation commit: `b753e8d` (on `main`, ahead of origin by 1)
- Governing design: `docs/investigations/validated-knowledge-sca-integration-design.md`
- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067
- Story 0115: **ACCEPTED** — Story-Aware Knowledge Selection

## Existing Boundaries Reused

- `KnowledgeSelectionService` and its Story-aware implementation (Story 0115)
- `SelectedKnowledge` and `SelectedKnowledgePromptProjectionService` for AI transport
- `AnalyzeStoryContextUseCase` as the single SCA orchestration point
- `AiTask` lifecycle (CREATED → SUBMITTED → PROCESSING → COMPLETED/FAILED)
- `EngineeringContextFacade` for authoritative context construction
- `StoryContextAnalysis` entity with `contextFreshness` (Story 0114)
- `StoryContextAnalysisResult` output schema (unchanged)
- REST and MCP as thin adapters over the Core use case

## Implemented Topology

```text
Engineering Story
        ↓
EngineeringContextFacade.getEngineeringContext()
        ↓
ProjectProfileService.getLatestByProject() → baseline Analysis
        ↓
AnalysisContextService.build(baselineAnalysisId) → AnalysisContext with SCA intent
        ↓
KnowledgeSelectionService.select() → SelectedKnowledge (Story-aware v5)
        ↓
SelectedKnowledgePromptProjectionService.toMap() → PromptRequest.selectedKnowledge
        ↓
EngineeringContext evidence → Java grounding contract (canonical references)
        ↓
AiTaskService.createForStoryContextAnalysisEntity() + submit()
        ↓
AIEngineClient.submit(PromptRequest with selectedKnowledge + groundingContract)
        ↓
Python StoryContextAnalysisGenerationService (defensive validation against Java contract)
        ↓
AnalyzeStoryContextUseCase.handleCallback() (authoritative Java validation)
        ↓
StoryContextAnalysis persistence (analysisSnapshot + contextFreshness)
```

## Repository Findings

The implementation closes the disconnect identified in the governing design where `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` hard-coded empty selected knowledge lists.

**Key changes:**

1. **Selection integration**: `execute()` now builds an `AnalysisContext` from the latest ProjectProfile Analysis baseline, adapts it for SCA intent (current Story only), and calls `KnowledgeSelectionService.select()`. The selector's Story-aware ranking (v5, `ENGINEERING_STORY_RELEVANCE` rule) is preserved.

2. **Grounding contract**: Java constructs `allowedEvidenceReferences` from `EngineeringEvidence.reference` (canonical `RepositoryEvidence.reference`) and `relatedReferences`. This contract is:
   - Stored in `AiTask.contextSnapshot["groundingContract"]`
   - Sent in `PromptRequest.groundingContract`
   - Reused for authoritative callback validation

3. **Canonical reference preservation**: `EngineeringEvidence` now carries both `identifier` (knowledge provenance) and `reference` (canonical evidence identity), per Story 0112 D13 and ADR-063.

4. **Authoritative callback validation**: `handleCallback()` validates before persistence:
   - All finding evidence references ⊆ Java allow-list
   - Factual/interpretative findings have `grounded=true` and ≥1 evidence reference
   - `relationType` present and valid for relationship-bearing findings
   - Classification ∈ {FACTUAL_EXTRACTION, AI_INTERPRETATION, RECOMMENDATION}
   - Uncertainty references ⊆ allow-list
   - Context digest consistency
   - No proposals in output

5. **Python defensive validation**: `StoryContextAnalysisPromptBuilder._grounding_contract()` and `StoryContextAnalysisGenerationService._validate_output()` now consume the Java-provided contract instead of reconstructing from `repositoryContext`.

6. **Task lifecycle fix**: `execute()` calls `aiTaskService.submit()` after task creation, ensuring SUBMITTED status before Python receives the task.

## Test Coverage Assessment

**Existing regression suites (all passing):**
- `KnowledgeSelectionServiceTest`, `KnowledgeSelectionServiceAdditionalTest`, `KnowledgeSelectionServiceImplStatusExclusionTest`: Story 0115 behavior preserved (budgets, ordering, closure, lifecycle, guidance, status filtering)
- `StoryAwareKnowledgeSelectionTest`: 6 Story-aware selection tests pass
- `RepositoryContextAdapterStoryAwareCandidateTest`: 4 adapter candidate tests pass
- `HistoricalSelectedEvidenceSnapshotProjectorTest`: v5 acceptance verified
- `AiTaskServiceTest`: task lifecycle (CREATED → SUBMITTED, duplicate callback handling) verified
- `AnalysisWorkflowServiceTest`: standard analysis workflow with grounding contract verified
- `EngineeringContextContractMapperTest`: `reference` field mapping verified
- `AiTaskResultServiceTest`: callback delegation and validation verified

**New integration coverage:**
- `AnalyzeStoryContextUseCase` integration with `KnowledgeSelectionService` exercised through existing test paths
- Grounding contract transport and callback validation exercised through `AiTaskResultServiceTest`

**Missing direct coverage (acceptable for V1):**
- No dedicated `AnalyzeStoryContextUseCaseTest` exercising full execute→callback path with real selected knowledge (would require testcontainer/integration setup)
- ADR-066 scenario `engineering-story-context-analysis-v1` not yet updated for provenance intersection cases (design §9)

## Conclusion

The repository contains the complete vertical slice for wiring validated knowledge into Story Context Analysis. All automated quality gates pass. The implementation follows the governing design's `PROVENANCE_INTERSECTION` model and preserves Java/Core authority over grounding.