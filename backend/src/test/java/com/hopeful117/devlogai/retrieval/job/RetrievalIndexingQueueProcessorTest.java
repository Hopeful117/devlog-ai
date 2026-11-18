package com.hopeful117.devlogai.retrieval.job;

import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import com.hopeful117.devlogai.retrieval.entity.RetrievalIndexingQueueEntry;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import com.hopeful117.devlogai.retrieval.repository.RetrievalIndexingQueueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class RetrievalIndexingQueueProcessorTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private RetrievalIndexingQueueProcessor processor;

    @Autowired
    private RetrievalIndexingQueueProcessorDelegate delegate;

    @Autowired
    private RetrievalIndexingQueueRepository queueRepository;

    @Autowired
    private RetrievalDocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    void successfulInsight_markedDone() {
        UUID projectId = insertProject();
        UUID insightId = insertInsight(projectId);

        RetrievalIndexingQueueEntry entry = insertPendingEntry(projectId, "INSIGHT", insightId);

        float[] vector = new float[1536];
        vector[0] = 1.0f;
        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        delegate.processEntry(entry);

        RetrievalIndexingQueueEntry saved = queueRepository.findById(entry.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("DONE");
        assertThat(documentRepository.findByProjectIdAndSourceTypeAndSourceId(
                projectId, "INSIGHT", insightId)).isPresent();
    }

    @Test
    void deletedInsight_markedDoneImmediately() {
        UUID projectId = insertProject();
        UUID nonExistentInsightId = UUID.randomUUID();

        RetrievalIndexingQueueEntry entry = insertPendingEntry(projectId, "INSIGHT", nonExistentInsightId);

        delegate.processEntry(entry);

        RetrievalIndexingQueueEntry saved = queueRepository.findById(entry.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo("DONE");
    }

    @Test
    void embeddingException_incrementsAttemptsAndEventuallyFails() {
        UUID projectId = insertProject();
        UUID insightId = insertInsight(projectId);

        RetrievalIndexingQueueEntry entry = insertPendingEntry(projectId, "INSIGHT", insightId);

        when(embeddingClient.embed(anyList(), anyString()))
                .thenThrow(new RuntimeException("Embedding service unreachable"));

        // 1st attempt
        delegate.processEntry(entry);
        RetrievalIndexingQueueEntry after1 = queueRepository.findById(entry.getId()).orElseThrow();
        assertThat(after1.getStatus()).isEqualTo("PENDING");
        assertThat(after1.getAttempts()).isEqualTo(1);
        assertThat(after1.getLastError()).contains("Embedding service unreachable");

        // 2nd attempt
        delegate.processEntry(after1);
        RetrievalIndexingQueueEntry after2 = queueRepository.findById(entry.getId()).orElseThrow();
        assertThat(after2.getStatus()).isEqualTo("PENDING");
        assertThat(after2.getAttempts()).isEqualTo(2);

        // 3rd attempt → FAILED
        delegate.processEntry(after2);
        RetrievalIndexingQueueEntry after3 = queueRepository.findById(entry.getId()).orElseThrow();
        assertThat(after3.getStatus()).isEqualTo("FAILED");
        assertThat(after3.getAttempts()).isEqualTo(3);
    }

    @Test
    @Transactional
    void markEmbeddingStaleBySourceType_clearsEmbeddingColumn() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO retrieval_documents
                    (id, project_id, source_type, source_id, trust_tier, status,
                     title, content, content_hash, embedding, embedding_model, indexed_at, metadata)
                VALUES (gen_random_uuid(), ?, 'INSIGHT', ?, 'TRUSTED', 'CURRENT',
                        'Title', 'Content', 'hash-stale', CAST(? AS vector), 'old-model', ?, '{}'::jsonb)
                """,
                projectId, sourceId, zeroVector(1536), OffsetDateTime.now());

        documentRepository.markEmbeddingStaleBySourceType("INSIGHT", "old-model");

        var doc = documentRepository.findByProjectIdAndSourceTypeAndSourceId(
                projectId, "INSIGHT", sourceId).orElseThrow();
        assertThat(doc.getEmbedding()).isNull();
        assertThat(doc.getContent()).isEqualTo("Content");
    }

    @Test
    void batchProcessor_processesMultiplePendingEntries() {
        UUID projectId = insertProject();
        UUID insightId1 = insertInsight(projectId);
        UUID insightId2 = insertInsight(projectId);

        insertPendingEntry(projectId, "INSIGHT", insightId1);
        insertPendingEntry(projectId, "INSIGHT", insightId2);

        float[] vector = new float[1536];
        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        processor.process();

        long doneCount = queueRepository.findAll().stream()
                .filter(e -> e.getProjectId().equals(projectId) && "DONE".equals(e.getStatus()))
                .count();
        assertThat(doneCount).isEqualTo(2);
    }

    // --- helpers ---

    private RetrievalIndexingQueueEntry insertPendingEntry(UUID projectId, String sourceType, UUID sourceId) {
        return queueRepository.save(RetrievalIndexingQueueEntry.builder()
                .projectId(projectId)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .queuedAt(Instant.now())
                .status("PENDING")
                .attempts(0)
                .build());
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Queue Test " + id, "queue-test-" + id, now, now);
        return id;
    }

    private UUID insertInsight(UUID projectId) {
        UUID analysisId = insertAnalysis(projectId);
        UUID insightId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO insights (id, project_id, analysis_id, type, severity, status, title, content, created_at, updated_at)
                VALUES (?, ?, ?, 'ARCHITECTURAL', 'INFO', 'ACTIVE', 'Test insight', 'Content', ?, ?)
                """, insightId, projectId, analysisId, now, now);
        return insightId;
    }

    private UUID insertAnalysis(UUID projectId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO analyses (id, project_id, type, status, started_at, created_at, updated_at)
                VALUES (?, ?, 'REPOSITORY', 'COMPLETED', ?, ?, ?)
                """, id, projectId, now, now, now);
        return id;
    }

    private static String zeroVector(int dims) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dims; i++) {
            if (i > 0) sb.append(',');
            sb.append("0");
        }
        sb.append(']');
        return sb.toString();
    }
}
