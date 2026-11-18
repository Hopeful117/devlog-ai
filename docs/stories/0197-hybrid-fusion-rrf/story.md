# Story 0197 — Hybrid Fusion: RRF Integration

## Status

**DESIGN APPROVED** — Requires Story 0196 before implementation.
Implementation authorization required before any code is written.

## Problem

Lexical and semantic retrievals produce independently ranked candidate lists.
Without fusion, the two lists compete for the same `SEMANTIC_RELEVANCE`
criterion slot in `DeterministicEvidenceRanker`. Scores from different
sources (term-frequency vs. cosine similarity) are not directly comparable.
Reciprocal Rank Fusion (RRF) normalizes across retrieval channels without
requiring a common score scale.

## Goal

Integrate RRF into the evidence ranking pipeline so that candidates
appearing in multiple retrieval channels receive a composite rank signal
that is better than either channel alone.

## Governing Artifacts

- ADR-063 — Engineering Context Retrieval and Composition Architecture
- ADR-080 — Hybrid Retrieval Architecture, D8 (RRF fusion)

## Architectural Constraints

1. RRF is a pre-pass that produces an augmented score, used as the
   `SEMANTIC_RELEVANCE` criterion input in `DeterministicEvidenceRanker`.
2. The five non-semantic criteria (ARCHITECTURAL_RELEVANCE,
   HISTORICAL_RELEVANCE, RECENCY, CONFIDENCE, USER_GUIDANCE_BOOST) remain
   unchanged.
3. When the semantic collector is disabled, RRF degrades to the existing
   term-model score (zero additional candidates).
4. The RRF computation is in-process (no additional I/O).
5. RRF parameters (`k`, channel weights) are configurable via properties.

## In Scope

**RetrievalFusionService (new):**
- `fuse(List<RepositoryEvidence> lexicalRanked,
        List<RepositoryEvidence> semanticRanked,
        int k)`: RRF formula
  `score(d) = Σ_r 1 / (k + rank_r(d))`.
- Returns a `Map<String, Integer>` from evidence reference to composite
  rank score (0–100 normalized).

**DeterministicEvidenceRanker integration:**
- Before computing per-evidence scores: separate the candidate list into
  lexical candidates (from non-semantic collectors) and semantic candidates
  (from `SemanticRetrievalContextCollector`, identified by
  `provenance.sourceType = "SEMANTIC_RETRIEVAL"`).
- Call `RetrievalFusionService.fuse(...)` to obtain composite scores.
- Use composite score (when available) as the `SEMANTIC_RELEVANCE` input
  instead of the raw term-model score.
- If the semantic collector is disabled or returns no candidates, the
  composite score falls back to the existing term-model score (behavior
  unchanged).

**Configuration:**
- `devlog.retrieval.fusion.k` (default 60).
- `devlog.retrieval.fusion.lexical-weight` (default 1.0).
- `devlog.retrieval.fusion.semantic-weight` (default 1.0).

**Tests:**
- RRF score increases for a document appearing in both lexical and semantic
  lists vs. either list alone.
- Semantic collector disabled → RRF output equals term-model scores.
- RRF monotonicity: higher rank position → higher composite score.
- Existing ranking tests pass unchanged when semantic collector is disabled.

## Out of Scope

- Changes to the five non-semantic criteria.
- Changes to `BudgetedDiverseEvidenceSelector`.
- Synchronization (Story 0198).

## Acceptance Criteria

- [ ] RRF composite score improves rank of a document present in both lists.
- [ ] Disabled semantic collector → existing behavior preserved exactly.
- [ ] Configurable `k` and weights take effect.
- [ ] All existing ranker tests pass.
- [ ] No existing tests broken.
