package com.hopeful117.devlogai.retrieval.repository;

import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetrievalDocumentRepository extends JpaRepository<RetrievalDocument, UUID> {

    Optional<RetrievalDocument> findByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, String sourceType, UUID sourceId);

    void deleteByProjectIdAndSourceTypeAndSourceId(
            UUID projectId, String sourceType, UUID sourceId);

    boolean existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(
            UUID projectId, String sourceType, UUID sourceId, String contentHash);

    /**
     * Upserts a retrieval document with its embedding vector using native SQL.
     * JPA cannot bind the pgvector type directly; the embedding is cast explicitly via ::vector.
     */
    @Modifying
    @Query(value = """
            INSERT INTO retrieval_documents
                (id, project_id, source_type, source_id, source_revision,
                 trust_tier, status, title, content, content_hash,
                 embedding, embedding_model, indexed_at, metadata)
            VALUES
                (:id, :projectId, :sourceType, :sourceId, :sourceRevision,
                 :trustTier, :status, :title, :content, :contentHash,
                 CAST(:embedding AS vector), :embeddingModel, :indexedAt, CAST(:metadata AS jsonb))
            ON CONFLICT (project_id, source_type, source_id)
            DO UPDATE SET
                content       = EXCLUDED.content,
                content_hash  = EXCLUDED.content_hash,
                embedding     = EXCLUDED.embedding,
                embedding_model = EXCLUDED.embedding_model,
                indexed_at    = EXCLUDED.indexed_at,
                metadata      = EXCLUDED.metadata
            """, nativeQuery = true)
    void upsertWithEmbedding(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") UUID sourceId,
            @Param("sourceRevision") String sourceRevision,
            @Param("trustTier") String trustTier,
            @Param("status") String status,
            @Param("title") String title,
            @Param("content") String content,
            @Param("contentHash") String contentHash,
            @Param("embedding") String embedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("indexedAt") Instant indexedAt,
            @Param("metadata") String metadata
    );

    @Query(value = """
            SELECT * FROM retrieval_documents
            WHERE project_id = :projectId
              AND status = 'CURRENT'
              AND embedding IS NOT NULL
              AND (embedding <=> CAST(:queryEmbedding AS vector)) <= :maxDistance
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """, nativeQuery = true)
    List<RetrievalDocument> findByProjectIdAndSimilarityNative(
            @Param("projectId") UUID projectId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("limit") int limit,
            @Param("maxDistance") double maxDistance);

    default List<RetrievalDocument> findByProjectIdAndSimilarity(
            UUID projectId, float[] queryEmbedding, int limit, float maxDistance) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < queryEmbedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(queryEmbedding[i]);
        }
        sb.append("]");
        return findByProjectIdAndSimilarityNative(projectId, sb.toString(), limit, maxDistance);
    }
}
