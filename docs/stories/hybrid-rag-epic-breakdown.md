# Epic — Hybrid RAG & Retrieval Foundation — Engineering Stories Breakdown

## Status

**PROPOSED** — Awaiting human validation of ADR-080 and individual story
authorization gates before any implementation begins.

## Context

ADR-080 (Hybrid Retrieval Architecture) defines the architecture. This
document outlines the resulting Engineering Stories, their order, and their
dependencies.

## Dependency Graph

```
0200 (pgvector schema + indexable contract)
  │
  ├── 0201 (PostgreSQL full-text — independent, can ship first)
  │
  ├── 0202 (embedding pipeline)
  │    └── 0205 (event-driven synchronization)
  │
  └── 0203 (semantic retrieval collector)
       └── 0204 (hybrid fusion RRF)
                └── 0206 (live evaluation)
```

---

## Story 0200 — Retrieval Foundation: pgvector Schema and Indexable Document Contract

**Problem**: There is no vector index and no contract for what a
"retrieval document" is. Before any embedding can be stored or queried,
the schema and the indexable document model must exist.

**In scope**:
- Flyway migration: `CREATE EXTENSION IF NOT EXISTS vector` on the
  PostgreSQL 17 instance.
- `retrieval_documents` table: `id`, `project_id`, `source_type`,
  `source_id`, `source_revision`, `trust_tier`, `status`, `title`,
  `content`, `content_hash`, `embedding` (pgvector `vector(1536)`),
  `embedding_model`, `indexed_at`, `metadata` (JSONB).
- `RetrievalDocument` entity + `RetrievalDocumentRepository` in Java Core.
- HNSW index on `embedding` with `project_id` pre-filter.
- `IndexableDocument` contract: the Java interface/record that defines
  what fields a source entity must expose to be indexed.
- Unit tests: entity persistence, content-hash computation, project
  isolation.

**Not in scope**: embedding generation, querying, synchronization.

**Prerequisite for**: 0202, 0203.

---

## Story 0201 — Lexical Improvement: PostgreSQL Full-Text Search

**Problem**: The existing `DeterministicEvidenceRanker` tokenizes at the
Java level. PostgreSQL native full-text search (tsvector/tsquery) provides
stem matching, language-aware ranking, and sub-millisecond queries on
indexed columns that the Java term model cannot replicate.

**In scope**:
- `tsvector` columns on `insights`, `engineering_stories`, and
  `knowledge_events` (Decisions) tables.
- Flyway migrations to add GIN indexes on the new `tsvector` columns.
- Repository methods for full-text queries with `ts_rank`.
- New `LexicalSearchContextCollector` (or extension of
  `ProjectKnowledgeContextCollector`) that queries by full-text match.
- Trust/provenance mapping: same as existing collectors.
- Tests: full-text match vs. term-model, project isolation, empty result
  handling.

**Not in scope**: embeddings, vector queries.

**Prerequisite for**: nothing (independent improvement).
**Can ship before**: 0202, 0203.

---

## Story 0202 — Embedding Pipeline: Python AI Engine Endpoint and Backfill

**Problem**: There is no embedding service and no way to populate the
vector index. Before semantic retrieval can function, there must be a way
to generate and store embeddings for indexed documents.

**In scope**:
- Python AI Engine: new `/api/v1/embeddings` endpoint.
  - Input: `{ texts: [string], model: string }`.
  - Output: `{ embeddings: [[float]], model: string, dimensions: int }`.
  - Stateless: no DB, no index access.
  - Versioned contract (additive envelope).
- Java Core: `EmbeddingClient` (HTTP client for the new endpoint).
- Java Core: `RetrievalDocumentIndexingService` that:
  - Accepts `IndexableDocument` inputs.
  - Calls `EmbeddingClient` in batches.
  - Stores embeddings in `retrieval_documents`.
  - Is idempotent (uses `content_hash`).
- Java Core: `RetrievalDocumentBackfillJob` — one-time scheduled job that
  indexes all existing ADRs, Insights, Stories, Decisions.
- Tests: Python embedding endpoint (mock LLM provider), Java indexing
  service (test double for embedding client), backfill idempotency.

**Prerequisite for**: 0203, 0205.

---

## Story 0203 — Semantic Retrieval Collector

**Problem**: Even with embeddings stored, the `RepositoryContextEngine`
does not know how to query them. A new `RepositoryContextCollector` is
needed that uses vector similarity to retrieve candidates beyond the
lexical horizon.

**In scope**:
- `SemanticRetrievalContextCollector` implementing
  `RepositoryContextCollector` (disabled by default via
  `devlog.retrieval.semantic.enabled`).
- At collection time: embed the query (via `EmbeddingClient`) and run a
  pgvector cosine distance query with `project_id` pre-filter and
  `status = CURRENT` guard.
- Trust/provenance mapping: each candidate inherits the `trust_tier` and
  provenance of its source entity.
- Candidates returned with `kind` and `layer` matching their source type
  (ADR → `RepositoryContextLayer.ADR`, Story → `ROADMAP`, etc.).
- Configurable result limit and similarity threshold.
- Graceful degradation: returns empty list if pgvector query fails or
  embedding endpoint is unavailable.
- Tests: vector query correctness, project isolation (no cross-project
  leakage), trust tier mapping, degradation behavior.

**Prerequisite for**: 0204, 0206.

---

## Story 0204 — Hybrid Fusion: RRF Integration

**Problem**: Lexical and semantic retrievals produce independently ranked
candidates. Without fusion, whichever list happens to have higher absolute
scores dominates. Reciprocal Rank Fusion normalizes across retrieval
channels.

**In scope**:
- RRF implementation in `DeterministicEvidenceRanker` (or a new
  `HybridRetrievalFusion` pre-pass that runs before ranking).
- RRF score replaces the raw semantic score as the `SEMANTIC_RELEVANCE`
  criterion input.
- Configurable `k` parameter (default 60) and channel weights.
- The five non-semantic criteria (ARCHITECTURAL_RELEVANCE,
  HISTORICAL_RELEVANCE, RECENCY, CONFIDENCE, USER_GUIDANCE_BOOST) remain
  unchanged.
- Tests: RRF score monotonicity, channel weight effect, existing ranking
  behavior preserved when semantic collector is disabled.

**Prerequisite for**: 0206.

---

## Story 0205 — Synchronization: Event-Driven Index Maintenance

**Problem**: The backfill from Story 0202 is a one-time operation. As
Insights are accepted, ADRs are updated, and Stories change status, the
index becomes stale. Event-driven maintenance keeps it current.

**In scope**:
- Domain event listeners (Spring `@EventListener` or `@TransactionalEventListener`):
  - Insight accepted → queue for re-indexing.
  - Engineering Story status changed → queue for re-indexing.
  - ADR file updated (via DocumentBodyCollector signal) → queue for re-indexing.
  - Project deleted → cascade delete all `retrieval_documents` for project.
- Async processing queue (in-process, backed by DB state) to avoid
  hot-path latency impact.
- `content_hash` check: skip re-indexing if content has not changed.
- Re-indexing on embedding model version change: a migration step that
  clears `embedding` for affected `source_type` rows and queues a
  selective backfill.
- Tests: listener wiring, idempotent re-indexing, cascade deletion,
  stale-embedding detection.

---

## Story 0206 — Live Evaluation: Extending Story 0177 with Real Captures

**Problem**: Story 0177's evaluation used fixture candidates, not live
repository data. The production impact of hybrid retrieval has not been
measured on real captures.

**In scope**:
- Live capture tooling: an opt-in annotation or flag on
  `RepositoryContextEngine.build()` that records the candidate set,
  selected set, rejection reasons, digests, and timing to a
  capture-only storage (not production traces).
- Extend the Story 0177 evaluation harness to load live captures and run
  LEXICAL_CONTROL, SEMANTIC_EXPERIMENT, and HYBRID_EXPERIMENT conditions
  against them.
- Evaluation scenarios covering:
  - A query whose vocabulary differs from the best-matching ADR.
  - A query that should retrieve an Engineering Story but currently returns
    zero Story candidates.
  - A query that should retrieve a validated Insight that is currently
    starved.
- Comparison report: recall, noise, context tokens, latency delta.
- Tests: capture tooling non-interference with production, deterministic
  replay on live captures.

**Acceptance criterion**: the evaluation report shows a measurable recall
improvement on at least two of the three scenarios without a noise increase
that exceeds 15% and without a timeout regression.

---

## Implementation Order Recommendation

| Priority | Story | Reason |
|----------|-------|--------|
| 1 | **0201** | Independent, low risk, immediate lexical improvement |
| 2 | **0200** | Unlocks 0202 and 0203 |
| 3 | **0202** | Unlocks semantic collector |
| 4 | **0203** | First end-to-end semantic retrieval |
| 5 | **0204** | Improves fusion quality |
| 6 | **0205** | Production readiness |
| 7 | **0206** | Measurement and validation |

Stories 0200 and 0201 may be authorized and worked in parallel.
Stories 0202 and 0203 may be authorized and worked in parallel after 0200.

## What is Not in This Epic

Per Section 12 of the epic brief and ADR-080 non-goals:

- New chatbot or autonomous agent.
- Refactoring of existing Core/Python boundaries.
- LLM reranker.
- Fine-tuning.
- New knowledge validation workflow.
- Graph database.
- Generic RAG framework.
- Indexing of raw Diffs or all 44k Facts (deferred to Phase G+).
