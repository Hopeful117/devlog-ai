# Story 0118 - Repository Analysis

## Status

**IMPLEMENTED - COMMITTED ON BRANCH**

## Baseline

- Baseline SHA: `504a867` (Story 0117 merge on `main`)
- Implementation commit: `db50dfb` (on feature branch)
- Governing ADRs: ADR-006, ADR-063 (§28, §42), ADR-067
- Story 0117: **ACCEPTED** — Cross-Analysis Historical Knowledge Retrieval

## Existing Boundaries Reused

- `SecureRepositoryContentReader` for document body reading
- `RepositoryContextEngine` and collector pipeline for evidence production
- `KnowledgeSelectionService` for final selected-knowledge authority
- `AnalyzeStoryContextUseCase` as SCA orchestration point
- `EngineeringContextFacade` for context construction
- `RepositoryContextService` for repository context building
- REST and MCP as thin adapters

## Implemented Topology

```text
Current Engineering Story
        |
        v
resolve RepositoryRevisionScope once
  (project / source / revision)
        |
        v
deterministic document discovery
  (current Story body + explicit ADR/Story/roadmap references)
        |
        v
bounded document body reading
  (SecureRepositoryContentReader at pinned revision)
        |
        v
ADR status parsing (deterministic, ## Status heading only)
        |
        v
RepositoryEvidence production
  (HUMAN_AUTHORED, canonical reference, content, status metadata)
        |
        v
existing RepositoryContextEngine pipeline
  (ranking, selection, enrichment, budget)
        |
        v
existing grounding contract
  (canonical reference in allowlist)
```

## Repository Findings

The implementation allows Story Context Analysis to receive bounded, revision-pinned bodies of explicitly related human-authored repository documents.

**Key changes:**

1. **RepositoryRevisionScope**: Immutable value object resolved once per SCA execution from Story/Analysis/Source.

2. **Document types**: DocumentReference, DocumentStatus, AdrStatusParser, DocumentReferenceExtractor, DocumentBudgetPolicy for deterministic document handling.

3. **DocumentBodyCollector**: Main collector implementing RepositoryContextCollector interface, handling workspace resolution, content reading, ADR parsing, one-hop traversal.

4. **Infrastructure wiring**: Scope propagated through ContextRequest, RepositoryContextService, KnowledgeSelectionService, AnalyzeStoryContextUseCase.

5. **Evaluation**: ADR-066 fixture updated with document evidence item.

## Test Coverage Assessment

**New unit tests (40 tests):**
- `AdrStatusParserTest`: 9 tests for ADR status extraction
- `DocumentReferenceExtractorTest`: 8 tests for markdown reference extraction
- `DocumentBudgetPolicyTest`: 5 tests for budget configuration
- `DocumentReferenceTest`: 9 tests for canonical reference
- `RepositoryRevisionScopeTest`: 9 tests for revision scope

**New integration tests (9 tests):**
- `DocumentBodyCollectorTest`: 3 tests for basic collector behavior
- `DocumentBodyCollectorIntegrationTest`: 6 tests for pipeline behavior

**Existing regression suites (all passing):**
- All KnowledgeSelectionService tests unchanged
- All RepositoryContextEngine tests unchanged
- All AnalyzeStoryContextUseCase tests updated and passing

## Conclusion

The repository contains the complete vertical slice for revision-pinned repository documents in Story Context Analysis. All automated quality gates pass. The implementation follows deterministic, bounded, provenance-driven principles and preserves all existing authority models.
