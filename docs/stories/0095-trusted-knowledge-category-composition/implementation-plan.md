# Implementation Plan — Story 0095

## Summary

Two production seams, both inside RepositoryContextEngine's existing
boundaries, governed by ADR-063: (1) bounded intent-relevant Fact/Observation
retrieval in the adapter; (2) availability-aware knowledge floors in the
selector. No MCP schema change; no other consumer touched.

## Production Changes

### 1. New — `repositorycontext/intelligence/IntentTerms.java`

Small final helper: `extract(String)` → sorted distinct lowercase tokens
(`[a-z0-9]+`, length ≥3) — the split rule already used by the ranker/structure
collector, extracted once for reuse by the adapter (documented shared
primitive per ADR-063; other call sites intentionally unchanged).

### 2. Modified — `fact/repository/FactRepository.java`,
`observation/repository/ObservationRepository.java`

Add paged overloads:
- `Page<Fact> findByAnalysisIdOrderByDetectedAtDesc(UUID, Pageable)`
- `Page<Observation> findByAnalysisIdOrderByCreatedAtDesc(UUID, Pageable)`

### 3. Modified — `projectcontext/RepositoryContextAdapter.java`

Inject the two repositories + `IntentTerms`. In `synthesizeAnalysisContext`:
resolve `baselineAnalysis = snapshot.latestProjectProfile() == null ? null :
…analysis()`; if non-null fetch windows (200) and select top **8 facts / 6
observations** scored by intent-term overlap (ties → more recent); pass the
results into `AnalysisContext` instead of `List.of()`. No profile ⇒ empty
(today's behavior). Constants documented as V1 policy, not ADR numbers.

### 4. Modified — `repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`

Add `selectKnowledgeFloor(candidates, request, policy, state)` between the
diversity and ordinary passes:
- `floor = clamp(request.budget().maximumEvidenceItems() / 10, 2, 8)`;
- iterate ranked candidates with kind ∈ KNOWLEDGE_KINDS (INSIGHT,
  ENGINEERING_STORY, DECISION, ARTIFACT, MILESTONE, CHALLENGE,
  ENGINEERING_EVENT, FACT, OBSERVATION);
- admit while floor count remains, subject to existing
  relevance/kind-allowance/item/token gates;
- reason `SELECTED_BY_CATEGORY_FLOOR`.
Availability-aware: loop ends when knowledge candidates exhaust; ordinary pass
reuses all unused capacity.

## Behavior Change

Before: knowledge categories compete only through global rank after Git
consumes ~all slots ⇒ ≤1 insight / 0 stories / no facts.
After: up to 6 floor seats for relevant trusted/project knowledge (only when
candidates clear relevance), Git keeps ≥54; facts/observations participate for
the first time through a bounded deterministic pre-pool step.

## Test Changes

New `BudgetedDiverseEvidenceSelectorTest` cases:
1. abundant Git candidates + ranked INSIGHT/STORY candidates ⇒ floor selects
   them before budget exhaustion (starvation fixed);
2. floor never admits below-minRelevance items (no forced irrelevant);
3. fewer knowledge candidates than floor ⇒ ordinary pass fills remaining
   budget to exactly maximumEvidenceItems (unused capacity reused);
4. total item + token budgets still enforced with floors active;
5. Git retains majority representation with floors active.

New `RepositoryContextAdapterBoundedKnowledgeTest` cases:
6. matching facts within window are selected, ordered by matches then recency,
   capped at 8/6;
7. window bound respected (301 facts ⇒ only ≤200 inspected — via repository
   paged overload verification);
8. analysis scoping: facts of another project/analysis never appear;
9. no baseline profile ⇒ empty facts/observations (backward compatible);
10. provenance fields (id/content/detectedAt/evidenceReferences) preserved.

Existing suites must stay green except where floors legitimately change
selection assertions (none known; will verify).

## Benchmark Plan

Same five intents pre/post against live stack; per-category selected counts +
manual relevance inspection of every newly selected non-Git item (§38).

## Rollback/Safety

Revert two files restores prior behavior; new repository overloads and helper
are additive/inert. No schema, contract, or persisted-state changes.

## Non-Goals

Freshness projection · Engineering Event · documentation · history-search
refactor · ContextPack · RAG · KnowledgeReference DTO framework.
