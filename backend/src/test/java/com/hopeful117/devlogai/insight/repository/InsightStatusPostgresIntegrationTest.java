package com.hopeful117.devlogai.insight.repository;

import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class InsightStatusPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InsightRepository insightRepository;

    @Test
    void shouldReturnOnlyActiveInsightsAndExcludeNonActiveStatuses() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);

        OffsetDateTime now = OffsetDateTime.now();
        UUID activeId = UUID.randomUUID();
        UUID archivedId = UUID.randomUUID();
        UUID supersededId = UUID.randomUUID();

        insertInsight(activeId, projectId, analysisId, "ACTIVE", now);
        insertInsight(archivedId, projectId, analysisId, "ARCHIVED", now.plusSeconds(1));
        insertInsight(supersededId, projectId, analysisId, "SUPERSEDED", now.plusSeconds(2));

        List<Insight> results = insightRepository
                .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        assertEquals(1, results.size());
        assertEquals(activeId, results.get(0).getId());
        assertEquals(InsightStatus.ACTIVE, results.get(0).getStatus());
        assertFalse(results.stream().anyMatch(i -> i.getId().equals(archivedId)));
        assertFalse(results.stream().anyMatch(i -> i.getId().equals(supersededId)));
    }

    @ParameterizedTest
    @EnumSource(value = InsightStatus.class, names = {"ARCHIVED", "SUPERSEDED"})
    void shouldExcludeNonActiveInsightFromActiveQuery(InsightStatus nonActiveStatus) {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);

        insertInsight(UUID.randomUUID(), projectId, analysisId, "ACTIVE", OffsetDateTime.now());
        insertInsight(UUID.randomUUID(), projectId, analysisId,
                nonActiveStatus.name(), OffsetDateTime.now().plusSeconds(1));

        List<Insight> results = insightRepository
                .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        assertEquals(1, results.size());
        assertEquals(InsightStatus.ACTIVE, results.get(0).getStatus());
        assertFalse(results.stream().anyMatch(i -> i.getStatus() == nonActiveStatus));
    }

    @Test
    void shouldOrderResultsByCreatedAtDescThenIdDesc() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);

        OffsetDateTime base = OffsetDateTime.now();

        UUID olderId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID olderSameTsId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID newerId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        insertInsight(newerId, projectId, analysisId, "ACTIVE", base.plusSeconds(10));
        insertInsight(olderSameTsId, projectId, analysisId, "ACTIVE", base);
        insertInsight(olderId, projectId, analysisId, "ACTIVE", base);

        List<Insight> results = insightRepository
                .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        assertEquals(3, results.size());
        assertEquals(newerId, results.get(0).getId());
        assertEquals(olderSameTsId, results.get(1).getId());
        assertEquals(olderId, results.get(2).getId());
    }

    @Test
    void shouldNotExcludeHistoricalRowsFromUnfilteredQuery() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);

        insertInsight(UUID.randomUUID(), projectId, analysisId, "ACTIVE", OffsetDateTime.now());
        insertInsight(UUID.randomUUID(), projectId, analysisId, "ARCHIVED", OffsetDateTime.now());
        insertInsight(UUID.randomUUID(), projectId, analysisId, "SUPERSEDED", OffsetDateTime.now());

        List<Insight> results = insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        assertEquals(3, results.size());
        assertTrue(results.stream().anyMatch(i -> i.getStatus() == InsightStatus.ACTIVE));
        assertTrue(results.stream().anyMatch(i -> i.getStatus() == InsightStatus.ARCHIVED));
        assertTrue(results.stream().anyMatch(i -> i.getStatus() == InsightStatus.SUPERSEDED));
    }

    @Test
    void shouldReturnEmptyWhenNoActiveInsights() {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = insertProjectAndAnalysis(projectId);

        insertInsight(UUID.randomUUID(), projectId, analysisId, "ARCHIVED", OffsetDateTime.now());
        insertInsight(UUID.randomUUID(), projectId, analysisId, "SUPERSEDED", OffsetDateTime.now());

        List<Insight> results = insightRepository
                .findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                        projectId, List.of(InsightStatus.ACTIVE));

        assertTrue(results.isEmpty());
    }

    private UUID insertProjectAndAnalysis(UUID projectId) {
        String projectName = "project-" + projectId.toString();
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, projectId, projectName, projectName,
                OffsetDateTime.now(), OffsetDateTime.now());

        UUID analysisId = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, type, status, started_at, created_at, updated_at)
                values (?, ?, ?, 'COMPLETED', ?, ?, ?)
                """, analysisId, projectId, "ARCHITECTURE_REVIEW",
                OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now());
        return analysisId;
    }

    private void insertInsight(UUID id, UUID projectId, UUID analysisId,
                               String status, OffsetDateTime createdAt) {
        jdbc.update("""
                insert into insights
                    (id, project_id, analysis_id, type, severity, status, title, content, created_at, updated_at)
                values (?, ?, ?, 'ARCHITECTURAL', 'INFO', ?, 'Test insight', 'Test content', ?, ?)
                """, id, projectId, analysisId, status, createdAt, createdAt);
    }
}
