package com.hopeful117.devlogai.retrieval.contract;

import java.util.Map;
import java.util.UUID;

/**
 * Contract for domain objects that can be indexed in the retrieval_documents table.
 * Implementors provide the data needed to create or update a RetrievalDocument row.
 */
public interface IndexableDocument {

    UUID projectId();

    String sourceType();

    UUID sourceId();

    /** Nullable; e.g. Git SHA for ADR documents, null for Insights. */
    String sourceRevision();

    String trustTier();

    String status();

    String title();

    /** Plain-text content to embed. Must not contain credentials or auth tokens. */
    String content();

    /** Additional structured metadata stored as JSONB (may be empty). */
    Map<String, Object> metadata();
}
