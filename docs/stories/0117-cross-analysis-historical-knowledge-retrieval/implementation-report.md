# Story 0117 - Implementation Report

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH, AWAITING HUMAN REVIEW**

## Repository State

- Branch: `main`
- HEAD: `504a867` (Story 0117 merge)
- Baseline SHA: `98852b6` (Story 0116 consolidation)
- Commit created by this task: YES
- Push performed by this task: NO

## Governance

- Governing ADRs: ADR-063, ADR-067
- Authorized Story: Story 0117, Cross-Analysis Historical Knowledge Retrieval
- Trust target: deterministic historical candidate retrieval; no trusted knowledge changes
- Java/Core authority target: context construction, trust, validation, persistence

## Implementation Summary

Commit `4fc1fa2` implemented deterministic cross-analysis historical knowledge retrieval. Story Context Analysis now considers bounded historical Facts and Observations from previous Analyses, supplementing the baseline ProjectProfile Analysis. Historical candidate retrieval is provenance-driven, deterministic, bounded, and compatible with the existing `KnowledgeSelectionService`.

## Production Components

### Backend (Java)
- `backend/.../repositorycontext/HistoricalKnowledgeCandidateService.java`: 
  - Extracts deterministic provenance anchors from current Story/repository scope
  - Obtains bounded Analyses and Facts via repository extensions
  - Retains only Facts whose provenance intersects supported Story anchors
  - Deduplicates by stable Fact identity and applies historical candidate bound
  - Retrieves compatible Observations with valid closure semantics
- `backend/.../analysis/repository/AnalysisRepository.java`: added `findCompletedAnalysesForProject()`
- `backend/.../fact/repository/FactRepository.java`: added `findFactsByAnalysisIds()`
- `backend/.../observation/repository/ObservationRepository.java`: added `findObservationsByFactIds()`
- `backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`: integrated historical candidates into selection pipeline

### Tests Updated
- `backend/.../repositorycontext/HistoricalKnowledgeCandidateServiceTest.java`: 271 tests
- `backend/.../knowledge/selection/KnowledgeRepositoryPostgresIntegrationTest.java`: 139 tests
- `backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCaseTest.java`: updated for historical integration

## Documentation

- `docs/stories/0117-cross-analysis-historical-knowledge-retrieval/story.md`: canonical Story with implementation evidence

## Recorded Verification

```text
FULL_BACKEND_TESTS = passed
JACOCO_CHECKS = MET
AI_ENGINE_TESTS = passed
GIT_DIFF_CHECK = PASS
STAGED_FILES = 9 source/test/doc files
```

## Architectural Decisions Preserved

- `KnowledgeSelectionService` public interface and final budget unchanged
- `StoryContextAnalysisResult` output schema unchanged
- `EvidenceRef` shape unchanged
- Insight trust semantics: context only, no trust inheritance
- Story 0114 freshness capture/persistence unchanged
- Java/Core authority over grounding preserved
- No RAG, vectors, new agent, microservice, or event-driven redesign

## Known Limitations

- No dedicated integration test with real database (uses mocks)
- Historical candidate bound is configurable but defaults are not yet tuned

## Git Diff Summary

Commit `4fc1fa2` + `2508caa`: 9 files changed, 892 insertions(+), 24 deletions(-).

## Readiness

```text
IMPLEMENTATION_PRESENT = YES
COMMITTED_ON_MAIN = YES
MERGED = YES
PUSH_PERFORMED = NO
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW = YES
```
