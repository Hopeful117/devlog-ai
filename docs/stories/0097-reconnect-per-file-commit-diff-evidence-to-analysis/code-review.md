# Code Review - Story 0097

## Scope Reviewed

- `RepositoryContextService.java` interface changes (new methods)
- `RepositoryContextEngine.java` implementation (retrieveCandidates, build overload)
- `KnowledgeSelectionServiceImpl.java` (constructor change, promoteCommitDiffCandidates)
- `Story0097CommitDiffReconnectionTest.java` (13 new tests)
- Modified test stubs (5 test files)

## Findings

No blocking findings remain.

### Test Mock Correctness (resolved)

The enricher mocks in the test helper methods (`engine`, `engineWithSelector`, `engineBounded`,
`engineWithCollector`) were initially returning hardcoded `SelectionResult` objects that bypassed the
real `BudgetedDiverseEvidenceSelector`. This caused test 5 (item boundedness) to fail because the
selector's 5-item budget was not enforced — the mocks returned all 20 collector output items
regardless.

**Resolution**: All enricher mocks now use `thenAnswer` to pass through the selector's selection:

```java
when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation -> {
    EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
    return new SelectedJavaSymbolEnricher.EnrichmentResult(selection, List.of());
});
```

This correctly models the enricher behavior: enrichers receive the selector's bounded output and
return it with optional enrichment, not override it.

## Architecture and Contract

- The shared retrieval primitive (`retrieveCandidates`) is a pure collector-output method with no
  ranking, selection, or budget enforcement. This correctly separates retrieval from composition.
- The `build(..., additionalCandidates)` overload merges promoted candidates into the existing
  pipeline. The existing `BudgetedDiverseEvidenceSelector` enforces the 60-item budget, deduplication,
  token limits, and kind-allowance constraints on the merged pool.
- `promoteCommitDiffCandidates()` correctly filters by `COMMIT_DIFF` layer, deduplicates by
  reference, and bounds by `maximumPromotedCommitDiffCandidates` (default 15).
- The existing 4-arg `build(...)` delegates to the 5-arg overload with `List.of()`, preserving
  backward compatibility for all existing callers.

## Security and Human Factors

- No new endpoints, persistence, or external surface was introduced.
- No secrets, credentials, or sensitive data handling changed.
- The promotion method has no security implications beyond existing scope.

## Verification Reviewed

- Story 0097 tests: **13/13 passed** — covers all 13 ACs.
- Focused regression suite: **56/56 passed** — no regressions in existing tests.
- Full backend: **984/984 passed** — no regressions in the entire codebase.

## Residual Risks

- No new risks introduced by this Story.
- The 60-item budget envelope is shared between promoted COMMIT_DIFF and all other evidence types.
  This is by design (ADR-063 single bounded envelope) and not a risk.

## Promotion Bound Architecture Review

**PROMOTION_BOUND_REVIEW = JUSTIFIED**

The promotion bound of `maximumPromotedCommitDiffCandidates = 15` is derived from the existing
category concentration policy, not an arbitrary constant:

- `ContextBudget.maximumEvidenceItems = 60` (existing: `devlog.repository-context.max-evidence-items:60`)
- `EvidencePrecisionPolicy.maximumKindSharePercentage = 25` (existing policy)
- Formula: `ceil(budget × kindSharePct / 100)` = `ceil(60 × 25 / 100)` = 15

This is the same formula used by `BudgetedDiverseEvidenceSelector.kindAllowance()`. The bound
controls how many COMMIT_DIFF candidates Analysis explicitly promotes, preventing unbounded candidate
pool inflation while staying consistent with the selector's category concentration rules.

Without this bound, Analysis could promote an unbounded number of COMMIT_DIFF candidates (collector
produces ~43), inflating the candidate pool before ranking/selection. The bound ensures the
promotion is proportional to the existing kind-share policy.

## Benchmark Results

Five-intent benchmark (history / architecture / recent-sync / persistence / decision-governance)
against the running stack confirms:

- **EFFECT_CLASSIFICATION = NO_MEASURABLE_CHANGE** — Story 0097's promotion is currently redundant
  because `CommitDiffEvidenceCollector` already produces 43 per-file COMMIT_DIFF items that flow
  through the normal pipeline. Promoted items (15) are deduplicated by the selector.
- **NEXT_CONFIRMED_BOTTLENECK = CATEGORY_SELECTION** — COMMIT_DIFF consumes 42-43 of 60 items
  (70-72%) via strong relevance bypass, exhausting the budget before other evidence types.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** - no blocking findings remain; the implementation is additive,
tested, and aligned with ADR-063 and all 13 acceptance criteria. The promotion bound is objectively
justified from existing architecture.
