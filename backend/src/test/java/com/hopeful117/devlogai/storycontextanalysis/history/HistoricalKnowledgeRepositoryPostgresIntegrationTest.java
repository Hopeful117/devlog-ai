package com.hopeful117.devlogai.storycontextanalysis.history;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@Testcontainers
class HistoricalKnowledgeRepositoryPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private AnalysisRepository analysisRepository;
    @Autowired private FactRepository factRepository;
    @Autowired private ObservationRepository observationRepository;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void repositoryPrimitivesApplyBoundsBeforeReturningHistoricalRows() {
        UUID projectId = insertProject("history-project");
        UUID otherProjectId = insertProject("other-history-project");
        OffsetDateTime now = OffsetDateTime.now();
        UUID baselineId = insertAnalysis(projectId, "COMPLETED", now);
        UUID newestHistoricalId = insertAnalysis(projectId, "COMPLETED", now.minusHours(1));
        UUID olderHistoricalId = insertAnalysis(projectId, "COMPLETED", now.minusHours(2));
        insertAnalysis(projectId, "FAILED", now.plusHours(1));
        insertAnalysis(otherProjectId, "COMPLETED", now.plusHours(2));

        var analyses = analysisRepository.findHistoricalCandidates(
                projectId,
                baselineId,
                AnalysisStatus.COMPLETED,
                PageRequest.of(0, 1)
        );

        assertEquals(List.of(newestHistoricalId),
                analyses.stream().map(value -> value.getId()).toList());

        UUID newestFactId = insertFact(
                newestHistoricalId, "newest", now.minusMinutes(30));
        insertFact(olderHistoricalId, "older", now.minusHours(3));
        var facts = factRepository.findHistoricalCandidates(
                List.of(newestHistoricalId, olderHistoricalId),
                PageRequest.of(0, 1)
        );

        assertEquals(List.of(newestFactId),
                facts.stream().map(value -> value.getId()).toList());

        UUID newestObservationId = insertObservation(
                newestHistoricalId, newestFactId, "newest", now.minusMinutes(20));
        UUID unrelatedFactId = insertFact(
                newestHistoricalId, "unrelated", now.minusMinutes(10));
        insertObservation(
                newestHistoricalId, unrelatedFactId, "unrelated", now.minusMinutes(5));
        var observations = observationRepository.findHistoricalCandidates(
                List.of(newestHistoricalId),
                List.of(newestFactId),
                PageRequest.of(0, 1)
        );

        assertEquals(List.of(newestObservationId),
                observations.stream().map(value -> value.getId()).toList());
    }

    private UUID insertProject(String slug) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Project " + id, slug + "-" + id, now, now);
        return id;
    }

    private UUID insertAnalysis(UUID projectId, String status, OffsetDateTime completedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into analyses
                    (id, project_id, type, status, intent_id, intent_version,
                     started_at, completed_at, created_at, updated_at)
                values (?, ?, 'ARCHITECTURE_REVIEW', ?, 'describe-project', 'v1', ?, ?, ?, ?)
                """, id, projectId, status, completedAt.minusMinutes(1), completedAt,
                completedAt.minusMinutes(2), completedAt);
        return id;
    }

    private UUID insertFact(UUID analysisId, String content, OffsetDateTime detectedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into facts (id, analysis_id, type, content, source, detected_at)
                values (?, ?, 'DOCUMENTATION_CHANGE', ?, 'story.md', ?)
                """, id, analysisId, content, detectedAt);
        jdbc.update("""
                insert into fact_evidence_references (fact_id, reference)
                values (?, 'docs/stories/0117/story.md')
                """, id);
        return id;
    }

    private UUID insertObservation(
            UUID analysisId,
            UUID factId,
            String content,
            OffsetDateTime createdAt
    ) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into observations
                    (id, analysis_id, type, content, rule_id, rule_version, created_at)
                values (?, ?, 'ARCHITECTURE_DOCUMENTATION_PRESENT', ?, 'HISTORICAL_RULE',
                        'v1', ?)
                """, id, analysisId, content, createdAt);
        jdbc.update("""
                insert into observation_facts (observation_id, fact_id)
                values (?, ?)
                """, id, factId);
        return id;
    }
}
