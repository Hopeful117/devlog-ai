# Story 0198 — Synchronization: Event-Driven Index Maintenance

## Status

**DESIGN APPROVED** — Requires Story 0195 (embedding pipeline) before
implementation. Implementation authorization required.

## Problem

The backfill job from Story 0195 is a one-time operation. As Insights are
accepted, Engineering Stories change status, and ADR files are updated, the
`retrieval_documents` index becomes stale. This story makes the index
self-maintaining through event-driven listeners and a selective re-indexing
mechanism.

## Goal

Keep the retrieval index current without full backfills after each change:

1. Listen to domain events that invalidate indexed documents.
2. Queue affected documents for async re-indexing.
3. Handle project deletion with cascade removal.
4. Support embedding model version change as a planned migration.

## Governing Artifacts

- ADR-080 — Hybrid Retrieval Architecture, D12 (synchronization)
- Story 0195 — Embedding Pipeline (prerequisite)

## Architectural Constraints

1. Listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)` so
   they fire only on committed changes.
2. Re-indexing is async; it must not block the domain transaction.
3. The queue is backed by a DB table (not an in-memory queue) so pending
   re-indexing survives restarts.
4. Project deletion cascades via the existing project lifecycle hooks.
5. Re-indexing on model change is an operator-initiated action, not an
   automatic background migration.

## In Scope

**Flyway migration `V87__create_retrieval_indexing_queue.sql`:**
- `retrieval_indexing_queue` table:
  `id UUID`, `project_id UUID`, `source_type VARCHAR(50)`,
  `source_id UUID`, `queued_at TIMESTAMPTZ`, `status VARCHAR(20)`,
  `attempts INT DEFAULT 0`, `last_error TEXT`.

**Domain event listeners:**
- Insight accepted (`InsightAcceptedEvent`) → enqueue Insight.
- Engineering Story status changed → enqueue Story.
- Knowledge Event (Decision) created or updated → enqueue Decision.
- ADR document updated (detected via `content_hash` change in
  `DocumentBodyCollector`) → enqueue ADR.
- Project deleted → `DELETE FROM retrieval_documents WHERE project_id = :id`.

**RetrievalIndexingQueueProcessor:**
- `@Scheduled(fixedDelay = 30s)` processor that dequeues pending items,
  calls `RetrievalDocumentIndexingService.index(...)`, marks as DONE or
  FAILED (max 3 attempts).
- Bounded diagnostics: indexed count, failed count, last error per item.

**Model change migration support:**
- `RetrievalDocumentRepository.markEmbeddingStaleBySourceType(String
  sourceType, String oldEmbeddingModel)`: sets `embedding = NULL` and
  `embedding_model = NULL` for all matching rows.
- Clearing embeddings re-triggers backfill on next `BackfillJob` run.
- This method is not called automatically; it is an operator tool.

**Tests:**
- Insight accepted → document queued within the same transaction commit.
- Queue processor: successful re-indexing → item marked DONE.
- Queue processor: embedding failure → item marked FAILED after 3 attempts,
  no exception propagated.
- Project deletion cascade: all `retrieval_documents` rows removed.
- `markEmbeddingStaleBySourceType`: sets embedding to NULL, leaves content.

## Out of Scope

- Automatic model change migration (operator-initiated only).
- Changes to MCP or REST contracts.

## Acceptance Criteria

- [ ] Insight accepted → `retrieval_documents` updated within 60s in
  integration test.
- [ ] Project deletion cascade removes all retrieval documents.
- [ ] Queue processor retries up to 3 times on failure, then marks FAILED.
- [ ] `markEmbeddingStaleBySourceType` clears embeddings without deleting rows.
- [ ] No existing tests broken.
