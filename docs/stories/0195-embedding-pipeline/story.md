# Story 0195 — Embedding Pipeline: Python AI Engine Endpoint and Backfill

## Status

**DESIGN APPROVED** — Requires Story 0193 (schema) before implementation.
Implementation authorization required before any code is written.

## Problem

The vector index introduced by Story 0193 exists but is empty. Before
semantic retrieval can be tested end-to-end, there must be a way to
generate embeddings for indexed documents and store them. The Python AI
Engine must expose a stateless embedding endpoint, and Java Core must own
the lifecycle of embedding requests and storage.

## Goal

1. Add a stateless `/api/v1/embeddings` endpoint to Python AI Engine.
2. Add a Java `EmbeddingClient` and `RetrievalDocumentIndexingService`.
3. Run a one-time `RetrievalDocumentBackfillJob` that indexes all existing
   ADRs, Insights, Stories, and Decisions.

## Governing Artifacts

- ADR-067 — DevLog Engineering Story Context Agent (Python boundary)
- ADR-080 — Hybrid Retrieval Architecture, D3 (Retrieval Layer placement),
  D4 (embedding generation)
- Story 0193 — Retrieval Foundation (prerequisite)

## Architectural Constraints

1. Python AI Engine never accesses the database, vector index, or project
   knowledge. It receives texts and returns vectors.
2. Java Core owns what is embedded, when, and where it is stored.
3. The embedding endpoint is versioned; existing endpoints are unchanged.
4. Embedding calls during backfill are batched (max 50 texts per call).
5. Project isolation is enforced by Java Core before passing texts to
   Python; Python never receives project identity.
6. No sensitive data (credentials, auth tokens, raw commit content) is
   sent to the embedding endpoint.

## In Scope

**Python AI Engine:**
- `/api/v1/embeddings` POST endpoint.
- Request: `{ "texts": [string], "model": string }` (max 100 items).
- Response: `{ "embeddings": [[float]], "model": string, "dimensions": int }`.
- Uses the configured LLM provider's embedding model (e.g., OpenAI
  `text-embedding-3-small`).
- Mock provider support (returns deterministic zero vectors for tests).
- Versioned Pydantic schemas; `extra="forbid"`.
- Tests: endpoint contract, mock provider behavior, batch size enforcement.

**Java Core — EmbeddingClient:**
- HTTP client for `/api/v1/embeddings` using the existing `ai-engine`
  base URL configuration.
- Batch embedding method: `List<float[]> embed(List<String> texts)`.
- Read timeout bounded (`devlog.retrieval.embedding.timeout`, default 30s).
- Returns `Optional.empty()` on timeout or connection failure (caller
  decides to skip).

**Java Core — RetrievalDocumentIndexingService:**
- `index(IndexableDocument document)`: computes content hash, calls
  `EmbeddingClient`, stores/updates `RetrievalDocument`.
- Idempotent: if `content_hash` matches the existing row, skips the
  embedding call.
- Records `embedding_model` from the response.

**Java Core — RetrievalDocumentBackfillJob:**
- `@Scheduled` one-time job (runs once on startup if
  `devlog.retrieval.backfill.enabled=true`, default false).
- Iterates over all projects, then ADRs (from `DocumentBodyCollector`
  source), Insights, Engineering Stories, Decisions.
- Idempotent, restartable.
- Records progress diagnostics (indexed count, skipped count, error count).

**Tests:**
- Python embedding endpoint: mock provider returns deterministic vectors.
- `EmbeddingClient`: uses WireMock to verify batching and timeout handling.
- `RetrievalDocumentIndexingService`: skips re-indexing on matching
  content hash; stores updated vector on content change.
- Backfill job: idempotent on second run (zero embedding calls, no errors).

## Out of Scope

- Semantic query execution (Story 0196).
- Event-driven synchronization (Story 0198).
- Changes to `RepositoryContextEngine` or existing collectors.

## Acceptance Criteria

- [ ] `/api/v1/embeddings` endpoint passes Python tests with mock provider.
- [ ] `EmbeddingClient` batching verified (≤50 texts per call).
- [ ] `RetrievalDocumentIndexingService` idempotency verified.
- [ ] Backfill job runs without error on an empty `retrieval_documents` table.
- [ ] Second backfill run makes zero embedding API calls.
- [ ] No existing tests broken.
