# Story 0118 - Implementation Plan

## Status

**IMPLEMENTED - COMMITTED ON BRANCH, AWAITING HUMAN REVIEW**

This plan records the implementation sequence from the feature branch.

## Planned Vertical Slice

Per governing design (ADR-063 §28, §42):

1. **RepositoryRevisionScope**: Immutable value object for revision resolution
2. **Document types**: DocumentReference, DocumentStatus, AdrStatusParser, DocumentReferenceExtractor, DocumentBudgetPolicy
3. **DocumentBodyCollector**: Main collector for bounded document body reading
4. **Infrastructure wiring**: ContextRequest, RepositoryContextService/Engine, KnowledgeSelectionService, AnalyzeStoryContextUseCase
5. **Test coverage**: Unit tests for all new types, integration tests for collector

## Actual Commit Sequence

| Commit | Outcome |
|---|---|
| `db50dfb` | `feat(0118): revision-pinned repository documents in SCA` — single commit containing all production changes, test fixes, and Story documentation |

## Verification Planned

- Focused `DocumentBodyCollector` tests (discovery, reading, evidence production)
- Focused `AdrStatusParser` tests (status extraction grammar)
- Focused `RepositoryRevisionScope` resolution tests
- Focused document budget allocation tests
- Existing Story-aware selection and SCA integration tests unchanged
- Full backend Maven verification with coverage
- Full AI Engine regression
- ADR-066 evaluation fixture update
- `git diff --check`
- No generated artifacts committed

## Explicitly Unchanged

- `KnowledgeSelectionService` public interface and final budget
- `StoryContextAnalysisResult` output schema
- `EvidenceRef` shape
- Insight trust semantics
- Story 0114 freshness capture/persistence unchanged
- Story 0117 historical retrieval unchanged
- Java/Core authority over grounding preserved
- No RAG, vectors, new agent, microservice, or event-driven redesign
