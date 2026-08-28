# Implementation Report — Story 0097

## Summary

Reconnected per-file `COMMIT_DIFF` evidence from `CommitDiffEvidenceCollector` to the Analysis
pipeline via a shared retrieval primitive (`retrieveCandidates`), replacing sole reliance on
aggregated `COMMIT_DIFF_SUMMARY` facts.

## Files Changed

### Production Code (3 files)

1. **`RepositoryContextService.java`** — Added `retrieveCandidates()` and `build(..., additionalCandidates)` methods to the interface, enabling pre-composition candidate retrieval.

2. **`RepositoryContextEngine.java`** — Implements the new interface methods. `retrieveCandidates()` runs collectors and returns raw output. `build(..., additionalCandidates)` merges collector output with promoted candidates before ranking/selection.

3. **`KnowledgeSelectionServiceImpl.java`** — Added `promoteCommitDiffCandidates()` method that calls `retrieveCandidates()`, filters for `COMMIT_DIFF` layer, deduplicates by reference, and bounds by `maximumPromotedCommitDiffCandidates` (default 15, configurable via `devlog.analysis.commit-diff-promotion.max-items`). Replaced `@RequiredArgsConstructor` with explicit constructor accepting the bound.

### Test Code (7 files)

1. **`Story0097CommitDiffReconnectionTest.java`** (new) — 13 tests covering:
   - Shared retrieval exposes COMMIT_DIFF
   - Repository context engine uses shared retrieval internally
   - Analysis promotion merges COMMIT_DIFF into candidate pool
   - Promoted candidates pass through BudgetedDiverseEvidenceSelector
   - Item boundedness (evidence ≤ maximumEvidenceItems)
   - Token budget enforcement
   - Deduplication of duplicate references
   - COMMIT_DIFF_SUMMARY facts remain available (regression)
   - Empty retrieval produces valid behavior
   - Intent sensitivity unchanged (regression)
   - COMMIT_DIFF survives to SelectedKnowledge
   - COMMIT_DIFF in repositoryContext.evidence (human visibility)
   - Shared retrieval reuses existing collectors (no duplicate retrieval)

2. **`KnowledgeSelectionServiceTest.java`** — Updated constructor/when/verify calls for 5-arg `build()`.

3. **`KnowledgeSelectionServiceAdditionalTest.java`** — Updated constructor/when/verify calls for 5-arg `build()`.

4. **`KnowledgeSelectionServiceImplStatusExclusionTest.java`** — Updated constructor/when/verify calls for 5-arg `build()`.

5. **`RepositoryContextAdapterTest.java`** — Added `anyList` import; adapter calls 4-arg `build()` which delegates to 5-arg.

6. **`RepositoryContextAdapterStatusExclusionTest.java`** — Adapter calls 4-arg `build()` which delegates to 5-arg.

## Test Results

- **Story 0097 tests**: 13/13 pass
- **Focused regression suite**: 56/56 pass
- **Full backend test suite**: 984/984 pass

## Architecture Invariants Preserved

- No new ADR created
- ADR-044 preserved (content enrichment restricted to SOURCE_FILE/TEST_FILE)
- ADR-036 preserved (commit-level code diff analysis)
- Single bounded envelope: 60-item `maximumEvidenceItems` budget in `ContextBudget`
- No duplicate retrieval: shared primitive reuses existing collectors
- No ranking/floor/budget changes: existing `DeterministicEvidenceRanker` and `BudgetedDiverseEvidenceSelector` behavior unchanged
- `COMMIT_DIFF_SUMMARY` facts preserved: both aggregate and granular evidence coexist
- No RAG/vector search, no prompt redesign

## Five-Intent Benchmark Results

### MCP Endpoint Baseline (pre-0097 running stack)

| Intent | Total | COMMIT_DIFF | GIT_HISTORY | Other | Knowledge Items |
|---|---|---|---|---|---|
| history | 60 | 43 | 10 | 7 (ROADMAP 6, SRC 1) | 0 |
| architecture | 60 | 43 | 10 | 7 (INSIGHT 4, ADR 1, OBS 1, STORY 1) | 5 |
| recent-sync | 60 | 42 | 11 | 7 (ROADMAP 6, ADR 1) | 0 |
| persistence | 60 | 43 | 10 | 7 (ROADMAP 6, ADR 1) | 0 |
| decision-governance | 60 | 43 | 10 | 7 (INSIGHT 4, ADR 1, FACT 1, STORY 1) | 5 |

### Analysis Pipeline (code trace, not live-run)

| Metric | Pre-0097 (4-arg build) | Post-0097 (5-arg build + promotion) |
|---|---|---|
| Candidates retrieved | ~238 (collector output) | ~238 (collector output) |
| COMMIT_DIFF candidates retrieved | 43 (from CommitDiffEvidenceCollector) | 43 (from CommitDiffEvidenceCollector) |
| COMMIT_DIFF candidates promoted | N/A | 15 (bounded by `maximumPromotedCommitDiffCandidates`) |
| Candidates after merge | ~238 | ~253 (before dedup) |
| After deduplication by reference | ~238 | ~238 (promoted items already in collector output → deduplicated) |
| COMMIT_DIFF surviving final selection | 43 (via strong relevance ≥75 bypass) | 43 (same — promotion is redundant) |
| final repositoryContext.evidence count | 60 | 60 |
| COMMIT_DIFF_SUMMARY availability | Yes (in selectedFacts) | Yes (unchanged) |
| prompt-visible granular COMMIT_DIFF | Yes (43 items in evidence) | Yes (43 items — unchanged) |
| Story 0096 human-visible | Yes (in repositoryContext.evidence) | Yes (unchanged) |

### Effect Classification

**EFFECT_CLASSIFICATION = NO_MEASURABLE_CHANGE**

Story 0097's explicit promotion mechanism is currently redundant because `CommitDiffEvidenceCollector` already produces per-file COMMIT_DIFF items (43) that flow through the normal `RepositoryContextEngine.build()` pipeline. The promoted candidates (15) are already present in the collector output and are deduplicated by `BudgetedDiverseEvidenceSelector` before selection. The final `repositoryContext.evidence` is identical before and after Story 0097 for all five intents.

Story 0097's architectural value is **resilience**: it guarantees COMMIT_DIFF availability for Analysis even if collector behavior changes. But in the current running stack, it produces no measurable change.

### Next Confirmed Bottleneck

**NEXT_CONFIRMED_BOTTLENECK = CATEGORY_SELECTION**

COMMIT_DIFF consumes 42-43 of 60 items (70-72%) across all five intents. This leaves only 17-18 slots for all other evidence types. The `BudgetedDiverseEvidenceSelector`'s `kindAllowance` is 15 (25% of 60), but COMMIT_DIFF items bypass this via `strongRelevanceScore ≥ 75`. This is the category selection bottleneck — the 60-item budget is exhausted by COMMIT_DIFF before other evidence types can be selected.

### Promotion Bound Architecture Review

**PROMOTION_BOUND_REVIEW = JUSTIFIED**

The promotion bound of 15 is derived from the existing category concentration policy:

- `ContextBudget.maximumEvidenceItems = 60` (existing: `devlog.repository-context.max-evidence-items:60`)
- `EvidencePrecisionPolicy.maximumKindSharePercentage = 25` (existing policy)
- Formula: `ceil(budget × kindSharePct / 100)` = `ceil(60 × 25 / 100)` = 15

This is the same formula used by `BudgetedDiverseEvidenceSelector.kindAllowance()`. It is not an arbitrary constant — it applies existing policy to the Analysis promotion scope. The bound controls how many COMMIT_DIFF candidates Analysis explicitly promotes, preventing unbounded candidate pool inflation while staying consistent with the selector's category concentration rules.
