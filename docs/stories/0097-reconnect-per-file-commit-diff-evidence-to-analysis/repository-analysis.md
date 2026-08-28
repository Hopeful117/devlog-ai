# Repository Analysis - Story 0097

## Story

**0097 - Reconnect Per-File COMMIT_DIFF Evidence to Analysis Pipeline**

Status entering this mission: `READY_FOR_REPOSITORY_ANALYSIS`.

This analysis is repository-analysis only. It introduces no production code, test, commit, or remote
mutation.

## Governing Architecture

ADR-063 (Accepted, Human Context Supremacy amendment) governs this Story. ADR-044 (content
enrichment restriction) and ADR-036 (commit-level code diff analysis) are preserved.

Key invariants:

| Invariant | Repository-analysis result |
|---|---|
| Single bounded envelope | 60-item `maximumEvidenceItems` in `ContextBudget` — promoted candidates are PART of this budget |
| No duplicate retrieval | Shared primitive reuses existing collectors |
| No ranking changes | `DeterministicEvidenceRanker` behavior unchanged |
| No floor/budget changes | `BudgetedDiverseEvidenceSelector` behavior unchanged |
| `COMMIT_DIFF_SUMMARY` preserved | Both aggregate and granular evidence coexist |
| No ADR-044 broadening | Content enrichment stays restricted to SOURCE_FILE/TEST_FILE |
| No RAG/vector | Not introduced |
| No progressive expansion | Not introduced |

## Repository State

| Item | Observed value |
|---|---|
| Branch | `main` |
| HEAD | `1761f3384d1a9cfd795a37f7d06bd90dcd37c013` |
| Worktrees | One: `/home/ludo/Bureau/workspace/devlog-ai` |

## Current Commit Evidence Flow

The Analysis pipeline currently receives commit evidence through two independent paths:

### Path 1: Aggregated `COMMIT_DIFF_SUMMARY` Facts

```text
CommitDiffEvidenceCollector.collect(request)
  -> COMMIT_DIFF_SUMMARY fact (aggregate statistics)
  -> Stored in AnalysisContext.validatedFacts()
  -> Selected by KnowledgeSelectionServiceImpl.selectExistingKnowledge()
  -> Included in SelectedKnowledge.selectedFacts()
```

This path produces statistical summaries (e.g., "5 commits, 12 files changed") but not per-file
details.

### Path 2: Per-File `COMMIT_DIFF` Evidence (disconnected)

```text
CommitDiffEvidenceCollector.collect(request)
  -> COMMIT_DIFF RepositoryEvidence (per-file details)
  -> Stored in RepositoryContext.evidence()
  -> NOT surfaced to Analysis pipeline
```

The per-file evidence is collected by `CommitDiffEvidenceCollector` but is only available in
`RepositoryContext.evidence()` — it is never promoted into the Analysis candidate/selection pipeline.

## Problem

The Analysis pipeline relies solely on `COMMIT_DIFF_SUMMARY` facts for commit evidence. Per-file
`COMMIT_DIFF` evidence is collected but not surfaced to Analysis, meaning the model cannot reason
over the actual files changed in recent commits.

## Shared Retrieval Primitive

`RepositoryContextService` currently has only:

```java
RepositoryContext build(AnalysisContext, IntentDefinition, UserGuidance, List<Insight>);
```

There is no way for Analysis to retrieve pre-composition candidates without triggering full
composition (ranking, selection, enrichment, budget enforcement).

## Proposed Solution

1. Add `retrieveCandidates(...)` to `RepositoryContextService` — a shared retrieval primitive that
   exposes collector output without ranking/selection/enrichment.
2. Add `build(..., additionalCandidates)` overload — merges promoted candidates into the existing
   pipeline.
3. Add `promoteCommitDiffCandidates()` in `KnowledgeSelectionServiceImpl` — retrieves, filters,
   deduplicates, and bounds per-file COMMIT_DIFF evidence.

## Impact Assessment

| Area | Impact |
|---|---|
| `RepositoryContextEngine` | Add 2 interface methods, implement 2 methods |
| `KnowledgeSelectionServiceImpl` | Add 1 private method, change constructor |
| Existing tests | Update 5 test stubs for 5-arg `build()` |
| Ranking/selection | No changes |
| Budget/floor | No changes |
| Prompt/provider | No changes |
| Persistence | No changes |
| ADR | No new ADR |

## Five-Intent Benchmark (Post-Implementation)

### MCP Endpoint Results (pre-0097 baseline)

| Intent | Total | COMMIT_DIFF | GIT_HISTORY | Other | Knowledge |
|---|---|---|---|---|---|
| history | 60 | 43 | 10 | 7 | 0 |
| architecture | 60 | 43 | 10 | 7 | 5 |
| recent-sync | 60 | 42 | 11 | 7 | 0 |
| persistence | 60 | 43 | 10 | 7 | 0 |
| decision-governance | 60 | 43 | 10 | 7 | 5 |

### Comparison with Story 0095 Baseline

| Metric | Pre-0095 | Post-0095 | Current (pre-0097) |
|---|---|---|---|
| Candidate pool | 238 | 245-250 | 238 |
| Git selected | 59-60 | 53 | 43 COMMIT_DIFF + 10 GIT = 53 |
| Knowledge ≤1 | Yes | No (5-7) | 0-5 |
| COMMIT_DIFF in evidence | 0 | 0 | 43 |

### Analysis Pipeline Impact

Story 0097's promotion mechanism is currently redundant because `CommitDiffEvidenceCollector` already
produces 43 per-file COMMIT_DIFF items that flow through the normal pipeline. The promoted
candidates (15) are deduplicated by the selector. Effect: **NO_MEASURABLE_CHANGE**.

### Bottleneck Identification

**NEXT_CONFIRMED_BOTTLENECK = CATEGORY_SELECTION** — COMMIT_DIFF consumes 42-43 of 60 items
(70-72%) via strong relevance bypass, exhausting the budget before other evidence types.

### Promotion Bound Justification

The bound of 15 is derived from existing architecture: `ceil(60 × 0.25)` = `ceil(budget ×
kindSharePct / 100)`. Same formula as `BudgetedDiverseEvidenceSelector.kindAllowance()`.

## Readiness

**READY_FOR_IMPLEMENTATION_PLAN**

Next step: **IMPLEMENTATION_PLAN**.
