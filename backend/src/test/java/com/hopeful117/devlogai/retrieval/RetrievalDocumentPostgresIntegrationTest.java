package com.hopeful117.devlogai.retrieval;

import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class RetrievalDocumentPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private RetrievalDocumentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    // --- persistence ---

    @Test
    void persistAndRetrieveDocumentWithoutEmbedding() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        RetrievalDocument doc = RetrievalDocument.builder()
                .projectId(projectId)
                .sourceType("INSIGHT")
                .sourceId(sourceId)
                .trustTier("TRUSTED")
                .status("CURRENT")
                .title("Monorepo boundary analysis")
                .content("Java Core owns deterministic context construction.")
                .contentHash("abc123")
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build();

        RetrievalDocument saved = repository.save(doc);
        assertThat(saved.getId()).isNotNull();

        Optional<RetrievalDocument> found = repository.findByProjectIdAndSourceTypeAndSourceId(
                projectId, "INSIGHT", sourceId);

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Monorepo boundary analysis");
        assertThat(found.get().getEmbedding()).isNull();
    }

    // --- project isolation ---

    @Test
    void documentBelongsOnlyToItsProject() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        UUID sourceId = UUID.randomUUID();

        repository.save(RetrievalDocument.builder()
                .projectId(projectA)
                .sourceType("ADR")
                .sourceId(sourceId)
                .trustTier("TRUSTED")
                .status("CURRENT")
                .title("ADR-001")
                .content("Body")
                .contentHash("hash1")
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build());

        Optional<RetrievalDocument> fromB = repository.findByProjectIdAndSourceTypeAndSourceId(
                projectB, "ADR", sourceId);
        assertThat(fromB).isEmpty();

        Optional<RetrievalDocument> fromA = repository.findByProjectIdAndSourceTypeAndSourceId(
                projectA, "ADR", sourceId);
        assertThat(fromA).isPresent();
    }

    // --- UNIQUE constraint: (project_id, source_type, source_id) ---

    @Test
    void duplicateSourceKeyViolatesUniqueConstraint() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        repository.save(RetrievalDocument.builder()
                .projectId(projectId)
                .sourceType("STORY")
                .sourceId(sourceId)
                .trustTier("HUMAN_AUTHORED")
                .status("CURRENT")
                .title("Story A")
                .content("Content A")
                .contentHash("hash-a")
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build());

        RetrievalDocument duplicate = RetrievalDocument.builder()
                .projectId(projectId)
                .sourceType("STORY")
                .sourceId(sourceId)
                .trustTier("HUMAN_AUTHORED")
                .status("CURRENT")
                .title("Story A duplicate")
                .content("Content B")
                .contentHash("hash-b")
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build();

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- content-hash idempotency ---

    @Test
    void existsByContentHashDetectsMatchingRow() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();
        String hash = "sha256-" + UUID.randomUUID();

        repository.save(RetrievalDocument.builder()
                .projectId(projectId)
                .sourceType("INSIGHT")
                .sourceId(sourceId)
                .trustTier("TRUSTED")
                .status("CURRENT")
                .title("Title")
                .content("Content")
                .contentHash(hash)
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build());

        assertThat(repository.existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(
                projectId, "INSIGHT", sourceId, hash)).isTrue();

        assertThat(repository.existsByProjectIdAndSourceTypeAndSourceIdAndContentHash(
                projectId, "INSIGHT", sourceId, "different-hash")).isFalse();
    }

    // --- similarity search ---

    @Test
    void similaritySearch_returnsCandidateCloseToQueryVector() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        // Insert a document with a known embedding: [1, 0, 0, ..., 0] (1536 dims)
        float[] docVector = new float[1536];
        docVector[0] = 1.0f;
        insertDocumentWithEmbedding(projectId, sourceId, "INSIGHT", "Bounded context isolation", docVector);

        // Query with the same vector → cosine distance = 0 (identical direction)
        float[] queryVector = new float[1536];
        queryVector[0] = 1.0f;

        var results = repository.findByProjectIdAndSimilarity(projectId, queryVector, 10, 0.5f);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getSourceId()).isEqualTo(sourceId);
    }

    @Test
    void similaritySearch_excludesCandidateBeyondMaxDistance() {
        UUID projectId = insertProject();

        // Insert a document with [1, 0, ..., 0]
        float[] docVector = new float[1536];
        docVector[0] = 1.0f;
        insertDocumentWithEmbedding(projectId, UUID.randomUUID(), "INSIGHT", "Title A", docVector);

        // Query with [0, 1, 0, ..., 0] → cosine distance = 1.0 (orthogonal)
        float[] queryVector = new float[1536];
        queryVector[1] = 1.0f;

        var results = repository.findByProjectIdAndSimilarity(projectId, queryVector, 10, 0.5f);

        assertThat(results).isEmpty();
    }

    @Test
    void similaritySearch_respectsProjectIsolation() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();

        float[] vector = new float[1536];
        vector[0] = 1.0f;
        insertDocumentWithEmbedding(projectA, UUID.randomUUID(), "INSIGHT", "Project A insight", vector);

        float[] queryVector = new float[1536];
        queryVector[0] = 1.0f;

        var fromB = repository.findByProjectIdAndSimilarity(projectB, queryVector, 10, 0.5f);

        assertThat(fromB).isEmpty();
    }

    @Test
    void similaritySearch_excludesDocumentsWithNullEmbedding() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        repository.save(RetrievalDocument.builder()
                .projectId(projectId)
                .sourceType("INSIGHT")
                .sourceId(sourceId)
                .trustTier("TRUSTED")
                .status("CURRENT")
                .title("No embedding yet")
                .content("Content")
                .contentHash("hash-no-emb")
                .indexedAt(Instant.now())
                .metadata(Map.of())
                .build());

        float[] queryVector = new float[1536];
        queryVector[0] = 1.0f;

        var results = repository.findByProjectIdAndSimilarity(projectId, queryVector, 10, 0.5f);

        assertThat(results).isEmpty();
    }

    @Test
    void similaritySearch_excludesSupersededDocuments() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        float[] vector = new float[1536];
        vector[0] = 1.0f;
        insertDocumentWithEmbeddingAndStatus(projectId, sourceId, "INSIGHT", "Superseded insight",
                vector, "SUPERSEDED");

        float[] queryVector = new float[1536];
        queryVector[0] = 1.0f;

        var results = repository.findByProjectIdAndSimilarity(projectId, queryVector, 10, 0.5f);

        assertThat(results).isEmpty();
    }

    // --- helper ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Retrieval Test " + id, "retrieval-test-" + id, now, now);
        return id;
    }

    private void insertDocumentWithEmbedding(UUID projectId, UUID sourceId,
            String sourceType, String title, float[] vector) {
        insertDocumentWithEmbeddingAndStatus(projectId, sourceId, sourceType, title, vector, "CURRENT");
    }

    private void insertDocumentWithEmbeddingAndStatus(UUID projectId, UUID sourceId,
            String sourceType, String title, float[] vector, String status) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        String vectorStr = sb.toString();

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO retrieval_documents
                    (id, project_id, source_type, source_id, trust_tier, status,
                     title, content, content_hash, embedding, embedding_model, indexed_at, metadata)
                VALUES (gen_random_uuid(), ?, ?, ?, 'TRUSTED', ?,
                        ?, 'Content', ?, CAST(? AS vector), 'text-embedding-3-small', ?, '{}'::jsonb)
                """,
                projectId, sourceType, sourceId, status,
                title, "hash-" + sourceId, vectorStr, now);
    }
}
