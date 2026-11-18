package com.hopeful117.devlogai.retrieval.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "retrieval_documents")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RetrievalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "source_type", nullable = false, updatable = false, length = 50)
    private String sourceType;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Column(name = "source_revision")
    private String sourceRevision;

    @Column(name = "trust_tier", nullable = false, length = 50)
    private String trustTier;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "title", nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /**
     * JPA cannot bind the pgvector type directly. This field is excluded from JPA
     * persistence; Story 0195 and 0196 manage it via native SQL with an explicit ::vector cast.
     */
    @Transient
    private String embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "indexed_at", nullable = false)
    private Instant indexedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
