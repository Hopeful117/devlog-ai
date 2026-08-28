# Story 0097 - Implementation Plan

## Status

**APPROVED**

Human Implementation Plan Review: **APPROVED**

## Summary

Reconnect per-file `COMMIT_DIFF` evidence from `CommitDiffEvidenceCollector` to the Analysis
pipeline via a shared retrieval primitive (`retrieveCandidates`), replacing sole reliance on
aggregated `COMMIT_DIFF_SUMMARY` facts.

Introduces:
- `retrieveCandidates()` on `RepositoryContextService` / `RepositoryContextEngine` — a shared
  retrieval primitive that exposes pre-composition candidates to consumer-specific composition.
- `build(..., additionalCandidates)` — an overload that merges promoted candidates into the normal
  ranking/selection pipeline.
- `promoteCommitDiffCandidates()` in `KnowledgeSelectionServiceImpl` — Analysis-specific composition
  that retrieves, filters, deduplicates, and bounds per-file COMMIT_DIFF evidence.

No ranking, budget, floor, prompt, provider, ADR, MCP, RAG, or enrichment change.

## Governing Decisions

- ADR-063 (Accepted, Human Context Supremacy amendment) governs this Story.
- ADR-044 preserved: content enrichment stays restricted to SOURCE_FILE/TEST_FILE.
- Single bounded envelope: 60-item `maximumEvidenceItems` budget in `ContextBudget` — promoted
  candidates are PART of this budget, not in addition.
- No duplicate retrieval: shared primitive reuses existing collectors.
- `COMMIT_DIFF_SUMMARY` facts preserved: both aggregate and granular evidence coexist.
- No ranking/floor/budget changes: existing `DeterministicEvidenceRanker` and
  `BudgetedDiverseEvidenceSelector` behavior unchanged.

## Execution Ownership

| Label | Meaning for this Story |
|---|---|
| `[AGENT]` | The agent may implement and verify the backend slice. |
| `[HUMAN]` | The human reviews, approves, and commits. |

## Exact Production Files

### Modified Backend Files

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextService.java` | Add `retrieveCandidates()` and `build(..., additionalCandidates)` interface methods. |
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java` | Implement `retrieveCandidates()` (runs collectors, returns candidates) and `build(..., additionalCandidates)` (merges additional candidates before ranking/selection). |
| `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java` | Replace `@RequiredArgsConstructor` with explicit constructor accepting `maximumPromotedCommitDiffCandidates`. Add `promoteCommitDiffCandidates()` method. |

No new files created in production code.

## Backend Contract

### `retrieveCandidates(context, intent, guidance, validatedInsights)`

Runs all registered `RepositoryContextCollector`s and returns the raw candidate list. No ranking,
selection, enrichment, or budget enforcement.

### `build(context, intent, guidance, validatedInsights, additionalCandidates)`

Same as existing `build(...)` but merges `additionalCandidates` after collector output and before
ranking. The existing `BudgetedDiverseEvidenceSelector` enforces the 60-item budget, deduplication,
token limits, and kind-allowance constraints on the merged pool.

### `promoteCommitDiffCandidates(context, intent, guidance, insightCandidates)`

1. Calls `retrieveCandidates(...)` to get all collector output.
2. Filters for `RepositoryContextLayer.COMMIT_DIFF`.
3. Deduplicates by reference (preserves first occurrence).
4. Bounds by `maximumPromotedCommitDiffCandidates` (default 15, configurable via
   `devlog.analysis.commit-diff-promotion.max-items`).
5. Returns the bounded list for `build(..., additionalCandidates)`.

## Implementation Slices

### Slice 1 - Shared Retrieval Primitive

Files:
- `RepositoryContextService.java`
- `RepositoryContextEngine.java`

Work:
- Add `retrieveCandidates()` to the interface.
- Add `build(..., additionalCandidates)` to the interface.
- Implement both in `RepositoryContextEngine`.

Completion signal: existing tests still pass, `retrieveCandidates()` returns collector output.

### Slice 2 - Analysis Promotion

Files:
- `KnowledgeSelectionServiceImpl.java`

Work:
- Replace `@RequiredArgsConstructor` with explicit constructor.
- Add `@Value("${devlog.analysis.commit-diff-promotion.max-items:15}")` bound.
- Add `promoteCommitDiffCandidates()` method.
- Call it in `select()` and pass result to `build(..., additionalCandidates)`.

Completion signal: existing tests pass, new promotion method filters and bounds correctly.

### Slice 3 - Test Coverage

Files:
- `Story0097CommitDiffReconnectionTest.java` (new)
- `KnowledgeSelectionServiceTest.java` (modified)
- `KnowledgeSelectionServiceAdditionalTest.java` (modified)
- `KnowledgeSelectionServiceImplStatusExclusionTest.java` (modified)
- `RepositoryContextAdapterTest.java` (modified)
- `RepositoryContextAdapterStatusExclusionTest.java` (modified)

Work:
- 13 focused tests covering shared retrieval, promotion, boundedness, deduplication, regression.
- Update existing test stubs for 5-arg `build()`.

Completion signal: all 56 focused tests pass, full backend 984 tests pass.

## Exact Test Files

### New Backend Tests

| File | Required coverage |
|---|---|
| `Story0097CommitDiffReconnectionTest.java` | 13 tests: shared retrieval, engine reuse, analysis promotion, selector pass-through, item boundedness, token budget, deduplication, summary regression, empty retrieval, intent sensitivity, SelectedKnowledge persistence, human visibility, collector reuse. |

### Modified Backend Tests

| File | Required coverage |
|---|---|
| `KnowledgeSelectionServiceTest.java` | Updated constructor/when/verify calls for 5-arg `build()`. |
| `KnowledgeSelectionServiceAdditionalTest.java` | Updated constructor/when/verify calls for 5-arg `build()`. |
| `KnowledgeSelectionServiceImplStatusExclusionTest.java` | Updated constructor/when/verify calls for 5-arg `build()`. |
| `RepositoryContextAdapterTest.java` | Added `anyList` import; adapter calls 4-arg `build()` which delegates to 5-arg. |
| `RepositoryContextAdapterStatusExclusionTest.java` | Adapter calls 4-arg `build()` which delegates to 5-arg. |

## AC Traceability

| Acceptance criterion | Implementation slices | Primary verification |
|---|---|---|
| AC1 shared retrieval primitive | Slice 1, 3 | `sharedRetrievalExposesPerFileCommitDiffBeforeComposition` |
| AC2 engine reuse | Slice 1, 3 | `repositoryContextEngineUsesSharedRetrievalInternally` |
| AC3 analysis promotion | Slice 2, 3 | `analysisPromotionMergesCommitDiffIntoCandidatePool` |
| AC4 selector pass-through | Slice 1, 3 | `promotedCandidatesPassThroughBudgetedDiverseEvidenceSelector` |
| AC5 item boundedness | Slice 1, 3 | `finalEvidenceNeverExceedsMaximumEvidenceItems` |
| AC6 token budget | Slice 1, 3 | `tokenBudgetRemainsEnforced` |
| AC7 deduplication | Slice 1, 3 | `duplicateReferencesDoNotProduceDuplicateFinalEvidence` |
| AC8 summary regression | Slice 2, 3 | `commitDiffSummaryFactsRemainAvailable` |
| AC9 empty retrieval | Slice 1, 3 | `emptyRetrievalProducesValidBehavior` |
| AC10 intent sensitivity | Slice 1, 3 | `intentSensitivityRemainsUnchanged` |
| AC11 SelectedKnowledge persistence | Slice 2, 3 | `commitDiffSurvivesToSelectedKnowledge` |
| AC12 human visibility | Slice 1, 3 | `commitDiffInRepositoryContextIsHumanVisible` |
| AC13 no duplicate retrieval | Slice 1, 3 | `sharedRetrievalReusesExistingCollectors` |

## Quality Gates

Focused backend regression:

```bash
cd backend
./mvnw -Dtest="Story0097CommitDiffReconnectionTest,KnowledgeSelectionServiceTest,KnowledgeSelectionServiceAdditionalTest,KnowledgeSelectionServiceImplStatusExclusionTest,RepositoryContextEngineTest,RepositoryContextServiceTest,RepositoryContextAdapterTest,RepositoryContextAdapterStatusExclusionTest,BudgetedDiverseEvidenceSelectorTest" test
```

Full backend gate:

```bash
cd backend
./mvnw clean verify
```

## Scope Guards

Implementation must leave these areas unchanged:

- `DeterministicEvidenceRanker` ranking behavior
- `BudgetedDiverseEvidenceSelector` budget/floor/dedup logic
- `factScore()` scoring
- `SelectedFileContentEnricher` content enrichment scope
- `KnowledgeSelectionService` knowledge budget (40/25/10/5/60)
- prompt composition, AI provider, MCP
- persistence schema, migrations
- ADR status

No new ADR, cache framework, RAG/vector infrastructure, or progressive expansion is allowed.
