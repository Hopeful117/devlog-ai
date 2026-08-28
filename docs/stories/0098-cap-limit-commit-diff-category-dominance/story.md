# Story 0098 — Cap COMMIT_DIFF Category Dominance via Maximum Category Share

## Status

**DESIGNED**

## Priority

**P0 — FIRST SLICE (ADR-063 incremental)**

## Objective

Prevent any single evidence category from consuming a disproportionate share of the
60-item `ContextBudget` by introducing a configurable maximum category share ceiling
in `BudgetedDiverseEvidenceSelector`, preserving the strong relevance override for
genuinely exceptional evidence while restoring budget diversity across categories.

Governed by **ADR-063 (Accepted)**, preserving **ADR-044** (content enrichment restriction)
and **ADR-038** (commit diff evidence).

## Human Story

As a human engineer requesting an Engineering Story or Analysis,
I want the model to see evidence from multiple categories — not just the42 most
recently changed files —
so that the output is grounded in the full engineering context: Git history,
knowledge, ADR decisions, project documentation, and validated insights.

## Problem (measured baseline, code-verified)

After Story 0097 connected per-file COMMIT_DIFF evidence to the Analysis pipeline,
the five-intent benchmark shows:

| Intent | COMMIT_DIFF | GIT_HISTORY | Other | Total |
|---|---|---|---|---|
| history | 42 (70.0%) | 11 (18.3%) | 7 (11.7%) | 60 |
| architecture | 42 (70.0%) | 11 (18.3%) | 7 (11.7%) | 60 |
| recent-sync | 41 (68.3%) | 12 (20.0%) | 7 (11.7%) | 60 |
| persistence | 42 (70.0%) | 11 (18.3%) | 7 (11.7%) | 60 |
| decision-governance | 42 (70.0%) | 11 (18.3%) | 7 (11.7%) | 60 |

COMMIT_DIFF evidence consistently consumes 68-72% of the budget across all intents,
regardless of the user's actual objective. This leaves only 17-18 slots for all
other evidence categories.

### Root cause: strong relevance bypass with no category ceiling

Code trace of `BudgetedDiverseEvidenceSelector`:

1. **kindAllowance** = `ceil(min(candidates, budget) * kindSharePct / 100)`
   = `ceil(60 * 25 / 100)` = 15 per kind.
2. **categoryEligible()** returns true if `kindCount < kindAllowance` (within allowance)
   OR `relevanceScore >= strongRelevanceScore` (75) — strong relevance bypass.
3. The ranker scores COMMIT_DIFF items ≥ 75 for all intents (changed files are
   always relevant to any engineering objective).
4. Therefore all 43 unique COMMIT_DIFF candidates bypass the kindAllowance=15 limit.
5. No category ceiling exists — the strong relevance override is unbounded.
6. Result: 42-43 COMMIT_DIFF items fill 70-72% of the 60-item budget.

### Story 0095 intent reconstruction

Story 0095 introduced category floors to prevent knowledge kinds from being starved
to zero. The strong relevance bypass was NOT changed at that time — it was an
existing mechanism preserved for backwards compatibility. The current domination was
an unintended consequence: floors prevent starvation of knowledge, but do not prevent
a non-knowledge category (COMMIT_DIFF) from dominating via strong relevance.

### ADR-063 alignment

ADR-063 §5 defines consumer-specific composition policy including "category floors
and ceilings". §23 reaffirms that agent composition includes "category floors and
ceilings". Ceilings are architecturally authorized but not yet implemented.

## Resolution

### Maximum category share ceiling

Add `maximumCategorySharePct` to `EvidencePrecisionPolicy`. Derived ceiling:
`maximumCategoryItems = ceil(budget * maximumCategorySharePct / 100)`.

At budget=60 and maximumCategorySharePct=20: `ceil(60 * 20/100)` = 12 per kind.

### Enforcement point

In `selectOrdinary()`, after the existing `categoryEligible()` check: add a ceiling
check `kindCounts.get(kind) >= maximumCategoryItems` → skip candidate.

This is a hard cap: strong relevance bypass can override kindAllowance but NOT the
ceiling. The ceiling applies uniformly to all kinds.

### Configuration

| Profile | kindSharePct | strongRelevanceScore | maximumCategorySharePct |
|---|---|---|---|
| ENGINEERING_STORY_PRECISION | 25 | 75 | **20** (new) |
| UNRESTRICTED | 100 | 101 | **100** (default, no effect) |

ENGINEERING_STORY_PRECISION uses maximumCategorySharePct=20 (< kindSharePct=25)
so the ceiling is tighter than the kind allowance. Other profiles default to 100
(backwards compatible, no behavioral change).

### Expected impact

With maximumCategorySharePct=20 on ENGINEERING_STORY_PRECISION:

| Category | Current | Expected | Change |
|---|---|---|---|
| COMMIT_DIFF | 42 | ≤12 | -30 |
| GIT_HISTORY | 11 | ≤15 (kindAllowance) | — |
| ADR/DECISION | 1 | ≤15 | — |
| KNOWLEDGE_KINDS | 6-10 | 6-15 (floor + ceiling) | — |
| Other | 1-5 | remaining budget | — |

Unused capacity from the ceiling flows to the ordinary pass (same mechanism as
unused floor capacity from Story 0095).

## Scope

### IN SCOPE

- `EvidencePrecisionPolicy` — add `maximumCategorySharePct` field with default 100
- `BudgetedDiverseEvidenceSelector.selectOrdinary()` — add ceiling enforcement
- `DeterministicContextIntelligence` — set maximumCategorySharePct=20 for
  ENGINEERING_STORY_PRECISION
- Unit tests for ceiling enforcement
- Five-intent benchmark verification

### OUT OF SCOPE

- Consumer-specific ceiling configuration (all consumers share the policy)
- Intent-specific ceilings (same ceiling for all intents under a profile)
- Strong relevance override redesign
- KindAllowance modification
- Knowledge floor modification (Story 0095)
- ContextPack, prompt redesign, MCP, RAG, vectors, embeddings
- CV Analyzer, frontend changes
- Progressive expansion, document retrieval

## Acceptance Criteria

1. **Ceiling enforced**: No single kind exceeds `maximumCategoryItems` in selected evidence.
2. **Benchmark improvement**: COMMIT_DIFF ≤ 12 items across all five intents.
3. **Diversity restored**: Remaining budget filled by GIT_HISTORY, knowledge kinds,
   ADR, and other categories in rank order.
4. **Strong relevance preserved**: Items with score ≥ 75 can still bypass kindAllowance
   (but not the ceiling).
5. **Backwards compatible**: UNRESTRICTED profiles unchanged (ceiling=100%, no effect).
6. **All existing tests pass**: No regression in 984/984 backend tests.
7. **Floor mechanism preserved**: Story 0095 category floors still apply.

## Governing Decisions

- ADR-063 (Accepted, Human Context Supremacy amendment) governs this Story.
- ADR-044 preserved: content enrichment stays restricted to SOURCE_FILE/TEST_FILE.
- ADR-038 preserved: commit diff evidence connected per Story 0097.
- Single bounded envelope: 60-item `maximumEvidenceItems` budget — ceiling operates
  WITHIN this budget.
- Deterministic selection: no randomization, no LLM-based selection.

## Verification

- Unit tests: ceiling enforcement in `BudgetedDiverseEvidenceSelectorTest`
- Five-intent benchmark: `curl localhost:18080/api/v1/projects/devlog-ai/engineering-context?intent=<intent>`
- Manual inspection: verify selected evidence includes non-COMMIT_DIFF categories
