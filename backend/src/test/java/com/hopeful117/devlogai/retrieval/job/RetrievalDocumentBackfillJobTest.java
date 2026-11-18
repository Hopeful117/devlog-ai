package com.hopeful117.devlogai.retrieval.job;

import com.hopeful117.devlogai.retrieval.client.EmbeddingClient;
import com.hopeful117.devlogai.retrieval.client.dto.EmbeddingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "devlog.retrieval.backfill.enabled=true",
        "devlog.retrieval.backfill.batch-size=50"
})
class RetrievalDocumentBackfillJobTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private RetrievalDocumentBackfillJob backfillJob;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    void shouldRunWithoutErrorOnEmptyDatabase() {
        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(new float[1536]), "text-embedding-3-small", 1536)));

        backfillJob.runBackfill();

        // No exception — passes
    }

    @Test
    void shouldMakeZeroEmbeddingCallsOnSecondRunWhenDataUnchanged() {
        UUID projectId = insertProject();
        UUID insightId = insertInsight(projectId);
        float[] vector = new float[1536];

        when(embeddingClient.embed(anyList(), anyString()))
                .thenReturn(Optional.of(new EmbeddingResponse(List.of(vector), "text-embedding-3-small", 1536)));

        backfillJob.runBackfill();
        int callsAfterFirstRun = mockingDetails(embeddingClient).getInvocations().size();

        backfillJob.runBackfill();
        int callsAfterSecondRun = mockingDetails(embeddingClient).getInvocations().size();

        // Second run adds no new calls beyond the first run
        assert callsAfterFirstRun >= 1 : "First run should call embed at least once";
        assert callsAfterSecondRun == callsAfterFirstRun : "Second run must make zero embedding calls";
    }

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Backfill Test " + id, "backfill-test-" + id, now, now);
        return id;
    }

    private UUID insertInsight(UUID projectId) {
        UUID analysisId = insertAnalysis(projectId);
        UUID insightId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        // proposal_id and validation_id are nullable — legacy insights omit them
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
}
