# Story 0193 — Retrieval Foundation: pgvector Schema and Indexable Document Contract

## Status

**DESIGN APPROVED** — Prerequisite for Stories 0195 and 0196.
Implementation authorization required before any code is written.

## Problem

There is no vector index and no contract for what a "retrieval document" is
in DevLog. The existing `RepositoryContextEngine` has no place to store or
query embeddings. Before semantic retrieval can function, the schema and the
indexable document model must exist and be tested.

## Goal

Establish the persistent foundation for the Retrieval Layer:

1. Install the `pgvector` extension on the PostgreSQL 17 instance.
2. Define the `retrieval_documents` table with full provenance metadata and
   an HNSW vector index.
3. Implement the Java entity, repository, and `IndexableDocument` contract.
4. Verify project isolation, content-hash deduplication, and schema
   correctness with integration tests.

No embedding generation, no querying, no synchronization in this story.

## Governing Artifacts

- ADR-006 — AI Proposal and Knowledge Promotion Workflow
- ADR-063 — Engineering Context Retrieval and Composition Architecture
- ADR-080 — Hybrid Retrieval Architecture (accepted)
- Story 0177 — Evaluate Hybrid Retrieval for Story Context

## Architectural Constraints

1. Java/Core remains sole authority for project scope, trust, and provenance.
2. The `retrieval_documents` table is a technical projection; it stores no
   knowledge not already in an authoritative entity.
3. Every row carries a `project_id`; no cross-project query is permitted.
4. Trust tier assignment uses `TrustTierClassifier`; no new tier logic.
5. MCP, REST, and AI Engine contracts are not changed by this story.

## In Scope

- Flyway migration `V82__install_pgvector_extension.sql`:
  `CREATE EXTENSION IF NOT EXISTS vector`.
- Flyway migration `V83__create_retrieval_documents.sql`:
  - `retrieval_documents` table with columns:
    `id UUID`, `project_id UUID NOT NULL`, `source_type VARCHAR(50)`,
    `source_id UUID`, `source_revision VARCHAR(255)`,
    `trust_tier VARCHAR(50)`, `status VARCHAR(20) DEFAULT 'CURRENT'`,
    `title TEXT`, `content TEXT`, `content_hash VARCHAR(64)`,
    `embedding vector(1536)`, `embedding_model VARCHAR(100)`,
    `indexed_at TIMESTAMPTZ`, `metadata JSONB`.
  - UNIQUE constraint on `(project_id, source_type, source_id,
    source_revision)`.
  - HNSW index: `CREATE INDEX ON retrieval_documents USING hnsw
    (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)`.
  - Regular index on `(project_id, source_type, status)`.
- `RetrievalDocument` JPA entity.
- `RetrievalDocumentRepository` with:
  - `findByProjectIdAndSourceTypeAndSourceId(...)`.
  - `deleteByProjectIdAndSourceTypeAndSourceId(...)`.
  - `existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(...)`.
- `IndexableDocument` Java interface/record:
  `projectId`, `sourceType`, `sourceId`, `sourceRevision`, `trustTier`,
  `status`, `title`, `content`, `metadata`.
- Integration tests:
  - Persist a `RetrievalDocument` with a non-null `embedding` vector.
  - Verify UNIQUE constraint enforcement.
  - Verify that a query filtered by `project_id` returns no rows from
    another project.
  - Verify `content_hash` idempotency helper.

## Out of Scope

- Embedding generation or embedding API calls.
- Semantic query execution.
- Synchronization listeners.
- Changes to `RepositoryContextEngine` or any existing collector.
- MCP or REST API changes.

## Acceptance Criteria

- [ ] `CREATE EXTENSION IF NOT EXISTS vector` runs in both development and
  CI PostgreSQL without error.
- [ ] `retrieval_documents` table created with all specified columns and
  indexes.
- [ ] `RetrievalDocument` entity persists and loads via Spring Data JPA.
- [ ] Repository methods pass integration tests.
- [ ] Project isolation test: query for project A returns no rows from
  project B.
- [ ] No existing tests broken.
- [ ] `git diff --stat` shows only: two migration files, entity, repository,
  interface, and tests.
