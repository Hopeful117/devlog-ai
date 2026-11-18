# Story 0203 — Semantic Retrieval Collector

## Status

**DESIGN APPROVED** — Requires Stories 0200 and 0202 before implementation.
Implementation authorization required before any code is written.

## Problem

Even with embeddings stored (Story 0202), the `RepositoryContextEngine` has
no way to query them. This story delivers the first end-to-end semantic
retrieval path: embed the query, run a pgvector cosine distance search, and
return candidates as `RepositoryEvidence` items alongside structural ones.

## Goal

Implement `SemanticRetrievalContextCollector`: a new
`RepositoryContextCollector` that queries the `retrieval_documents` table
using vector similarity and contributes semantically retrieved candidates
to the existing pipeline.

## Governing Artifacts

- ADR-063 — Engineering Context Retrieval and Composition Architecture
- ADR-067 — DevLog Engineering Story Context Agent
- ADR-080 — Hybrid Retrieval Architecture, D3, D6, D7, D10, D11

## Architectural Constraints

1. The collector is **disabled by default**
   (`devlog.retrieval.semantic.enabled=false`).
2. Similarity score never changes trust tier, status, or grounding
   eligibility.
3. All queries apply `project_id` as a SQL pre-filter.
4. `status = 'CURRENT'` is a default guard; SUPERSEDED documents may be
   included if explicitly configured.
5. On embedding failure or pgvector error: return empty list, log bounded
   warning.
6. Returned `RepositoryEvidence` items use the same kind/layer/provenance
   as the equivalent structural collector for the source type.

## In Scope

**RetrievalDocumentRepository additions:**
- `findByProjectIdAndSimilarity(UUID projectId, float[] queryEmbedding,
  int limit, float maxDistance)` — pgvector `<=>` cosine distance query.

**SemanticRetrievalContextCollector:**
- Implements `RepositoryContextCollector` with configurable `@Order`
  (after structural collectors, before or after document collectors).
- At collect time:
  1. Build query text from `ContextRequest.intent().objective()` and
     `UserGuidance` priorities.
  2. Call `EmbeddingClient.embed(List.of(queryText))` for a single vector.
  3. Run pgvector cosine distance query via
     `RetrievalDocumentRepository.findByProjectIdAndSimilarity(...)`.
  4. Map each `RetrievalDocument` to `RepositoryEvidence` with:
     - `layer` derived from `source_type`
       (ADR → `ADR`, STORY → `ROADMAP`, INSIGHT → `VALIDATED_INSIGHT`,
       DECISION → `ADR`).
     - `kind` = `source_type`.
     - `reference` = `source_type.toLowerCase() + ":" + source_id`.
     - `provenance.sourceType` = `"SEMANTIC_RETRIEVAL"`.
     - `trustTier` from `RetrievalDocument.trust_tier`.
  5. Return empty list on any exception.
- Configurable: `devlog.retrieval.semantic.max-results` (default 10),
  `devlog.retrieval.semantic.max-distance` (default 0.4).

**Tests:**
- `SemanticRetrievalContextCollector` returns candidates for a query whose
  terms do not appear in the document text.
- Project isolation: vector query returns no results from another project.
- Disabled flag: returns empty list without calling `EmbeddingClient`.
- Embedding failure: returns empty list, no exception propagated.
- Layer/kind/provenance mapping verified for all four source types.
- Trust tier inherited from `RetrievalDocument` (not recomputed).

## Out of Scope

- RRF fusion (Story 0204).
- Event-driven synchronization (Story 0205).
- Changes to `DeterministicEvidenceRanker` weights.

## Acceptance Criteria

- [ ] `SemanticRetrievalContextCollector` returns at least one Insight
  candidate for a query that uses different vocabulary than the Insight's
  title.
- [ ] Project isolation verified in integration test.
- [ ] Disabled via config → zero DB calls.
- [ ] Embedding failure → empty list, no exception, bounded log warning.
- [ ] All trust tiers and layers correctly mapped.
- [ ] No existing tests broken.
