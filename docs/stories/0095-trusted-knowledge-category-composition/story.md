# Story 0095 — Project Trusted Knowledge into Engineering Context with Category-Aware Composition

## Status

**READY_FOR_COMMIT_APPROVAL**

## Objective

First incremental implementation of ADR-063: make RepositoryContextEngine /
`get_engineering_context` compose from more than Git-heavy evidence by

1. retrieving **bounded, intent-relevant Facts and Observations** through a
   deterministic pre-pool step (never `findAll`);
2. guaranteeing **availability-aware category floors** so trusted/project
   knowledge (ACTIVE Insights, Engineering Stories, Decisions, bounded
   Facts/Observations) can no longer be starved to zero by abundant Git
   evidence;
3. keeping composition inside the existing global budget, contract-additive,
   and strictly consumer-scoped (ADR-063 §5/§7).

Governed by **ADR-063 — Engineering Context Retrieval and Composition
Architecture (Accepted)**.

## Problem (measured baseline, re-verified at current HEAD)

Live benchmark against the running stack, five fixed intents
(history / architecture / recent-sync / persistence / decision-governance):

| Metric | Value |
|---|---|
| Candidate pool | 238 (constant across intents) |
| Selected Git evidence | 59–60 of 60 budget |
| ACTIVE Insight candidates → selected | 18 → 0…1 |
| Engineering Story candidates → selected | 21 → 0 |
| Facts/Observations in candidates | structurally absent (`List.of()`) |

Root causes (code-verified):
- `RepositoryContextAdapter.synthesizeAnalysisContext` passes empty
  facts/observations into `AnalysisContext`;
- `BudgetedDiverseEvidenceSelector` has no category floors — global rank lets
  ~70 Git candidates consume the entire budget before any knowledge item ranks.

## Resolution

### Bounded Fact/Observation retrieval (pre-pool, ADR-063 §"bounded")

In the adapter only: resolve the latest comparable baseline Analysis from the
snapshot profile; fetch a **bounded recent window** (200 rows) of its Facts and
Observations via new paged repository overloads; score deterministically
against intent terms (shared `IntentTerms` helper — the same splitting rule
already used by the ranker family); keep top **8 facts / 6 observations**.
No matches ⇒ honest empty. No LLM summarization; identity/provenance/time are
preserved verbatim (collector already emits `fact:{id}` references).

### Category-aware floors (composition)

In `BudgetedDiverseEvidenceSelector`, between the diversity pass and the
ordinary rank pass: a floor pass selects up to
`floor = clamp(budget/10, 2, 8)` (=6 at the current 60-item budget) candidates
whose kind belongs to the knowledge set {INSIGHT, ENGINEERING_STORY, DECISION,
ARTIFACT, MILESTONE, CHALLENGE, ENGINEERING_EVENT, FACT, OBSERVATION}, in rank
order, still respecting minimum relevance, kind share and both budgets;
reason `SELECTED_BY_CATEGORY_FLOOR`. Availability-aware: fewer candidates ⇒
fewer reservations; unused capacity flows to the ordinary pass. Rationale for
~10%: smallest reservation that guarantees per-category opportunity while
leaving ≥90% of the budget to Git coverage; relative formula, not hard-coded
investigation counts.

### Intent sensitivity

Already produced by the existing semantic-term ranker; floors ensure that
intent-scored knowledge candidates survive instead of being budget-evicted, so
different intents now yield different *knowledge* selections where the
knowledge base supports it. Deterministic term extraction only (ADR-063 §6).

## Scope

### IN SCOPE

- `RepositoryContextAdapter` bounded fact/observation seam (+ paged repository
  overloads, shared `IntentTerms` helper)
- `BudgetedDiverseEvidenceSelector` availability-aware floor pass
- Focused tests: retrieval bounding/scoping/metadata; starvation fix;
  unused-floor reuse; budget enforcement; no-forced-irrelevant; intent
  differentiation; backward compatibility

### OUT OF SCOPE

Freshness projection fix · Engineering Event / SelectedKnowledge ·
documentation generation · search_project_history refactor · ContextPack ·
RAG/vector · KnowledgeReference DTO framework · MCP schema redesign ·
document indexing platform.

## Non-Negotiable Invariants

1. No unbounded Fact/Observation loading (hard cap before candidate pool).
2. Floors never select below-minimum-relevance items merely for diversity.
3. Total item/token budgets always enforced.
4. Trust/provenance/temporal metadata preserved on every new candidate.
5. Zero MCP contract breaking changes (existing kinds/layers reused).
6. RepositoryContextEngine remains a composition consumer (ADR-063 §7).
7. Engineering Event, documentation, validators untouched.

## References

- ADR-063 (Accepted) — governing architecture
- Investigation: docs/investigations/context-composition-trusted-knowledge.md
- Stories 0093/0094 — checkpoint + grounding semantics relied upon
