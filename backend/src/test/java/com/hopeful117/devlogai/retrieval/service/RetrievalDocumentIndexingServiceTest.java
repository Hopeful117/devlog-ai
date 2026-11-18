package com.hopeful117.devlogai.retrieval.service;

import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import com.hopeful117.devlogai.retrieval.config.EmbeddingClientProperties;
import com.hopeful117.devlogai.retrieval.contract.IndexableDocument;
import com.hopeful117.devlogai.retrieval.entity.RetrievalDocument;
import com.hopeful117.devlogai.retrieval.repository.RetrievalDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
class RetrievalDocumentIndexingServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private RetrievalDocumentIndexingService indexingService;

    @Autowired
    private RetrievalDocumentRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    void shouldIndexDocumentWhenNotPresent() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();
        float[] vector = new float[1536];

        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        IndexingResult result = indexingService.index(doc(projectId, sourceId, "Initial content"));

        assertThat(result).isEqualTo(IndexingResult.INDEXED);
        assertThat(repository.findByProjectIdAndSourceTypeAndSourceId(projectId, "INSIGHT", sourceId)).isPresent();
    }

    @Test
    void shouldSkipDocumentWhenContentHashUnchanged() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();
        float[] vector = new float[1536];
        String content = "Stable content that doesn't change";

        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        IndexingResult first = indexingService.index(doc(projectId, sourceId, content));
        assertThat(first).isEqualTo(IndexingResult.INDEXED);
        verify(embeddingClient, times(1)).embed(anyList(), anyString());

        IndexingResult second = indexingService.index(doc(projectId, sourceId, content));
        assertThat(second).isEqualTo(IndexingResult.SKIPPED);
        verifyNoMoreInteractions(embeddingClient);
    }

    @Test
    void shouldReIndexWhenContentChanges() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();
        float[] vector = new float[1536];

        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        indexingService.index(doc(projectId, sourceId, "Version 1"));
        IndexingResult result = indexingService.index(doc(projectId, sourceId, "Version 2 — different content"));

        assertThat(result).isEqualTo(IndexingResult.INDEXED);
        verify(embeddingClient, times(2)).embed(anyList(), anyString());
    }

    @Test
    void shouldReturnEmbeddingFailedWhenClientReturnsEmpty() {
        UUID projectId = insertProject();
        UUID sourceId = UUID.randomUUID();

        when(embeddingClient.embed(anyList(), anyString())).thenReturn(Optional.empty());

        IndexingResult result = indexingService.index(doc(projectId, sourceId, "Some content"));

        assertThat(result).isEqualTo(IndexingResult.EMBEDDING_FAILED);
        assertThat(repository.findByProjectIdAndSourceTypeAndSourceId(projectId, "INSIGHT", sourceId)).isEmpty();
    }

    private IndexableDocument doc(UUID projectId, UUID sourceId, String content) {
        return new IndexableDocument() {
            public UUID projectId() { return projectId; }
            public String sourceType() { return "INSIGHT"; }
            public UUID sourceId() { return sourceId; }
            public String sourceRevision() { return null; }
            public String trustTier() { return "TRUSTED"; }
            public String status() { return "ACTIVE"; }
            public String title() { return "Test insight"; }
            public String content() { return content; }
            public Map<String, Object> metadata() { return Map.of(); }
        };
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Indexing Test " + id, "indexing-test-" + id, now, now);
        return id;
    }
}
