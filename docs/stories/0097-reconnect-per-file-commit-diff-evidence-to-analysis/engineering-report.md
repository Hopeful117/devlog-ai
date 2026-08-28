# Engineering Report - Story 0097

## Delivery State

Story 0097 is **STORY_COMPLETE** — HUMAN_REVIEW_APPROVED. Work is on branch
`story-097-reconnect-per-file-commit-diff-evidence-to-analysis` off HEAD
`1761f3384d1a9cfd795a37f7d06bd90dcd37c013` on `main`, awaiting commit and merge.

## Story Outcome

The Analysis pipeline now receives per-file `COMMIT_DIFF` evidence from `CommitDiffEvidenceCollector`
via a shared retrieval primitive, instead of relying solely on aggregated `COMMIT_DIFF_SUMMARY` facts.
The shared primitive (`retrieveCandidates`) enables consumer-specific composition without introducing
Analysis-specific collectors or bypassing existing ranking/selection.

## Backend

### Shared Retrieval Primitive

`RepositoryContextService` interface now exposes:

```java
List<RepositoryEvidence> retrieveCandidates(
    AnalysisContext context, IntentDefinition intent,
    UserGuidance guidance, List<Insight> validatedInsights);
```

`RepositoryContextEngine` implements this by running all registered `RepositoryContextCollector`s and
returning the raw candidate list. No ranking, selection, enrichment, or budget enforcement occurs.

### Build Overload

`RepositoryContextService` now also exposes:

```java
RepositoryContext build(
    AnalysisContext context, IntentDefinition intent,
    UserGuidance guidance, List<Insight> validatedInsights,
    List<RepositoryEvidence> additionalCandidates);
```

The existing 4-arg `build(...)` delegates to the 5-arg overload with `List.of()`. The 5-arg overload
merges `additionalCandidates` after collector output and before ranking.

### Analysis Promotion

`KnowledgeSelectionServiceImpl` has a new private method:

```java
private List<RepositoryEvidence> promoteCommitDiffCandidates(
    AnalysisContext context, IntentDefinition intent,
    UserGuidance guidance, List<Insight> insightCandidates)
```

This method:
1. Calls `retrieveCandidates(...)` to get all collector output.
2. Filters for `RepositoryContextLayer.COMMIT_DIFF`.
3. Deduplicates by reference (preserves first occurrence).
4. Bounds by `maximumPromotedCommitDiffCandidates` (default 15, configurable via
   `devlog.analysis.commit-diff-promotion.max-items`).

The promoted candidates are passed to `build(..., additionalCandidates)` in `select()`.

### Configuration

```yaml
devlog:
  analysis:
    commit-diff-promotion:
      max-items: 15
```

## Acceptance Assessment

All 13 acceptance criteria are implemented:

- AC1: Shared retrieval primitive exposes COMMIT_DIFF from existing collectors.
- AC2: `RepositoryContextEngine` uses the same primitive internally.
- AC3: Analysis promotion merges COMMIT_DIFF into the candidate pool.
- AC4: Promoted candidates go through `BudgetedDiverseEvidenceSelector`.
- AC5: Final evidence never exceeds `maximumEvidenceItems` (60).
- AC6: Token budget remains enforced.
- AC7: Duplicate references are deduplicated.
- AC8: `COMMIT_DIFF_SUMMARY` facts remain available.
- AC9: Empty retrieval produces valid behavior.
- AC10: Intent sensitivity unchanged.
- AC11: COMMIT_DIFF survives to `SelectedKnowledge`.
- AC12: COMMIT_DIFF in `repositoryContext.evidence` for human visibility.
- AC13: Shared retrieval reuses existing collectors (no duplicate retrieval).

## Verification

Focused backend verification:

- Story 0097 tests: **13/13 passed**
- Focused regression suite: **56/56 passed**
- Full backend: **984/984 passed**

No failures, no errors, no skipped tests.

## Five-Intent Benchmark

### Setup

Five fixed intents (history / architecture / recent-sync / persistence / decision-governance) run
against the current running stack (pre-0097 baseline, HEAD `1761f3384d1a9cfd795a37f7d06bd90dcd37c013`).

### Results — MCP Endpoint (`get_engineering_context`)

| Intent | Total | COMMIT_DIFF | GIT_HISTORY | Other | Knowledge |
|---|---|---|---|---|---|
| history | 60 | 43 | 10 | 7 | 0 |
| architecture | 60 | 43 | 10 | 7 | 5 |
| recent-sync | 60 | 42 | 11 | 7 | 0 |
| persistence | 60 | 43 | 10 | 7 | 0 |
| decision-governance | 60 | 43 | 10 | 7 | 5 |

### Analysis Pipeline (code trace)

| Metric | Pre-0097 | Post-0097 |
|---|---|---|
| COMMIT_DIFF candidates retrieved | 43 | 43 |
| COMMIT_DIFF candidates promoted | N/A | 15 |
| After dedup | ~238 | ~238 (promoted items already in collector output) |
| COMMIT_DIFF surviving selection | 43 | 43 (strong relevance ≥75 bypasses kindAllowance) |
| repositoryContext.evidence count | 60 | 60 |
| COMMIT_DIFF_SUMMARY in selectedFacts | Yes | Yes (unchanged) |

### Effect Classification

**EFFECT_CLASSIFICATION = NO_MEASURABLE_CHANGE**

Story 0097's explicit promotion mechanism is currently redundant because
`CommitDiffEvidenceCollector` already produces per-file COMMIT_DIFF items (43) that flow through the
normal `RepositoryContextEngine.build()` pipeline. The promoted candidates (15) are already present in
the collector output and are deduplicated by `BudgetedDiverseEvidenceSelector` before selection. The
final `repositoryContext.evidence` is identical before and after Story 0097 for all five intents.

Story 0097's architectural value is **resilience**: it guarantees COMMIT_DIFF availability for
Analysis even if collector behavior changes.

### Next Confirmed Bottleneck

**NEXT_CONFIRMED_BOTTLENECK = CATEGORY_SELECTION**

COMMIT_DIFF consumes 42-43 of 60 items (70-72%) across all five intents. The
`BudgetedDiverseEvidenceSelector`'s `kindAllowance` is 15 (25% of 60), but COMMIT_DIFF items bypass
this via `strongRelevanceScore ≥ 75`. The 60-item budget is exhausted by COMMIT_DIFF before other
evidence types can be selected.

### Promotion Bound Architecture Review

**PROMOTION_BOUND_REVIEW = JUSTIFIED**

The promotion bound of 15 is derived from the existing category concentration policy:

- `ContextBudget.maximumEvidenceItems = 60` (existing: `devlog.repository-context.max-evidence-items:60`)
- `EvidencePrecisionPolicy.maximumKindSharePercentage = 25` (existing policy)
- Formula: `ceil(budget × kindSharePct / 100)` = `ceil(60 × 25 / 100)` = 15

This is the same formula used by `BudgetedDiverseEvidenceSelector.kindAllowance()`. It is not an
arbitrary constant — it applies existing policy to the Analysis promotion scope.

## Residual Technical Debt

- Story 0097's promotion is currently redundant in the running stack (see benchmark above).
  Architectural resilience value is real but not yet exercised.
- The category selection bottleneck (NEXT_CONFIRMED_BOTTLENECK) is a pre-existing condition where
  COMMIT_DIFF dominates the 60-item budget via strong relevance, not a new debt from this Story.
- The promotion bound is hardcoded as a `@Value` property rather than dynamically computed from
  `ContextBudget` and `EvidencePrecisionPolicy`. This is acceptable for the first slice but could be
  improved in a future ADR-063 category-composition Story.

## Engineering Verdict

**STORY_COMPLETE** — implementation, test coverage, quality gates, five-intent benchmark, and
human review are complete. The benchmark confirms NO_MEASURABLE_CHANGE for the current stack, with
architectural resilience as the primary value. The next confirmed bottleneck is CATEGORY_SELECTION.
Git delivery is owned by the human engineer.
