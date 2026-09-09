# Story 0117 - Implementation Plan

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH**

This plan records the implementation sequence from the feature branch and main branch commits.

## Planned Vertical Slice

Per governing design:

1. **Repository extensions**: Add bounded Analysis/Fact/Observation retrieval methods
2. **HistoricalKnowledgeCandidateService**: Implement deterministic historical candidate orchestration
3. **AnalyzeStoryContextUseCase integration**: Wire historical candidates into existing selection pipeline
4. **Test coverage**: Unit tests for candidate service, integration tests for repository queries

## Actual Commit Sequence

| Commit | Outcome |
|---|---|
| `4fc1fa2` | `feat: implement Story 0117 cross-analysis historical knowledge retrieval` — single commit containing all production changes, test fixes, and Story documentation |
| `2508caa` | `fix: eagerly load observation supporting facts` — bug fix for lazy loading issue |

## Verification Planned

- Focused `HistoricalKnowledgeCandidateService` tests (provenance matching, deduplication, bounding)
- Existing Story-aware selection and SCA integration tests
- Full backend Maven verification with coverage
- Full AI Engine regression
- `git diff --check`
- No generated artifacts committed

## Explicitly Unchanged

- `KnowledgeSelectionService` public interface and final budget
- `StoryContextAnalysisResult` output schema
- `EvidenceRef` shape
- Insight trust semantics
- Story 0114 freshness capture/persistence unchanged
- Historical analyses unchanged (no backfill)
- Java/Core authority over grounding preserved
- No RAG, vectors, new agent, microservice, or event-driven redesign
