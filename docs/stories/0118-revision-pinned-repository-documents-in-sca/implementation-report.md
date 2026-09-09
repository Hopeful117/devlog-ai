# Story 0118 - Implementation Report

## Status

**IMPLEMENTED - COMMITTED ON BRANCH, AWAITING HUMAN REVIEW**

## Repository State

- Branch: `story/0118-revision-pinned-repository-documents-in-sca`
- HEAD: `db50dfb` (Story 0118 implementation)
- Baseline SHA: `504a867` (Story 0117 merge)
- Commit created by this task: YES
- Push performed by this task: NO

## Governance

- Governing ADRs: ADR-006, ADR-063 (§28, §42), ADR-067
- Authorized Story: Story 0118, Revision-Pinned Repository Documents in SCA
- Trust target: deterministic document body collection; no trusted knowledge changes
- Java/Core authority target: revision resolution, document discovery, body reading, ADR status parsing, evidence production

## Implementation Summary

Commit `db50dfb` implemented revision-pinned, bounded repository document collection for Story Context Analysis. The system deterministically reads ADR and story document bodies at a resolved revision, applies configurable budget limits, parses ADR status, extracts one-hop references, and produces `HUMAN_AUTHORED` trust-tier evidence with canonical `document:{sourceId}:{path}@{revision}` references.

## Production Components

### Backend (Java)
- `backend/.../repositorycontext/RepositoryRevisionScope.java`: immutable record for revision scope
- `backend/.../repositorycontext/DocumentReference.java`: canonical document identity
- `backend/.../repositorycontext/DocumentStatus.java`: enum for ADR status
- `backend/.../repositorycontext/AdrStatusParser.java`: deterministic parser for ADR `## Status` section
- `backend/.../repositorycontext/DocumentReferenceExtractor.java`: markdown reference extractor
- `backend/.../repositorycontext/DocumentBudgetPolicy.java`: configurable budget limits
- `backend/.../repositorycontext/collector/DocumentBodyCollector.java`: main collector
- `backend/.../repositorycontext/ContextRequest.java`: added revisionScope field
- `backend/.../repositorycontext/RepositoryContextService.java`: scope-aware build
- `backend/.../repositorycontext/RepositoryContextEngine.java`: implements scope-aware build
- `backend/.../knowledge/selection/KnowledgeSelectionService.java`: scope-aware select
- `backend/.../knowledge/selection/KnowledgeSelectionServiceImpl.java`: forwards scope
- `backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`: resolves scope
- `backend/src/main/resources/application.properties`: document budget properties

### Tests
- `backend/.../repositorycontext/AdrStatusParserTest.java`: 9 tests
- `backend/.../repositorycontext/DocumentReferenceExtractorTest.java`: 8 tests
- `backend/.../repositorycontext/DocumentBudgetPolicyTest.java`: 5 tests
- `backend/.../repositorycontext/DocumentReferenceTest.java`: 9 tests
- `backend/.../repositorycontext/RepositoryRevisionScopeTest.java`: 9 tests
- `backend/.../repositorycontext/collector/DocumentBodyCollectorTest.java`: 3 tests
- `backend/.../repositorycontext/collector/DocumentBodyCollectorIntegrationTest.java`: 6 tests

### Evaluation
- `ai-engine/evaluations/scenarios/engineering-story-context-analysis-v1/scenario.json`: added document evidence
- `ai-engine/evaluations/scenarios/engineering-story-context-analysis-v1/replay.json`: updated prompt digest

## Documentation

- `docs/stories/0118-revision-pinned-repository-documents-in-sca/story.md`: canonical Story
- `docs/stories/0118-revision-pinned-repository-documents-in-sca/final-report.md`: final report

## Recorded Verification

```text
FULL_BACKEND_TESTS = 1176 passed
JACOCO_CHECKS = MET
AI_ENGINE_TESTS = passed
ADR_066_EVALUATION = Gate PASSED (STRONG)
GIT_DIFF_CHECK = PASS
STAGED_FILES = 31 source/test/doc files
```

## Architectural Decisions Preserved

- Java deterministic authority: ADR status parsing, revision resolution, budget enforcement
- Python probabilistic boundary: document body text reaches Python; ADR status stays Java-side
- One-hop traversal: collector reads current story, extracts references, collects referenced documents
- Canonical identity: `document:{sourceId}:{path}@{revision}` ensures revision-pinned grounding
- Trust tier: Document evidence classified as `HUMAN_AUTHORED`
- `KnowledgeSelectionService` remains final selected-knowledge authority
- Existing repository-context infrastructure reused

## Known Limitations

- No automatic roadmap inclusion (requires explicit reference)
- ADR status parsing limited to `## Status` heading
- Budget enforcement is per-document, not cross-document

## Git Diff Summary

Commit `db50dfb`: 31 files changed, 2461 insertions(+), 34 deletions(-).

## Readiness

```text
IMPLEMENTATION_PRESENT = YES
COMMITTED_ON_BRANCH = YES
MERGED = NO
PUSH_PERFORMED = NO
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW = YES
```
