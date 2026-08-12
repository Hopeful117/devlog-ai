package com.hopeful117.devlogai.insight.service;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateRecommendation;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrustedKnowledgeDuplicateAuditServiceTest {
    @Mock
    InsightRepository insightRepository;

    @Test
    void shouldReturnEmptyAuditWhenNoTrustedKnowledgeExists() {
        UUID projectId = UUID.randomUUID();
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        InsightDuplicateAuditResponse response = new TrustedKnowledgeDuplicateAuditService(insightRepository).audit(projectId);

        assertEquals(projectId, response.projectId());
        assertEquals(0, response.totalInsights());
        assertEquals(0, response.clusterCount());
        assertTrue(response.clusters().isEmpty());
    }

    @Test
    void shouldDetectExactDuplicateClusterDeterministically() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                insight(project, "Architecture Decision Records (ADR) Documentation",
                        "The project documents architectural decisions with ADRs.",
                        null, null, "ARCHITECTURE_DESCRIPTION", Instant.parse("2026-08-11T23:11:14Z")),
                insight(project, " Architecture Decision Records (ADR) Documentation ",
                        "The project   documents architectural decisions with ADRs.",
                        "", null, "ARCHITECTURE_DESCRIPTION", Instant.parse("2026-08-12T00:32:20Z"))
        ));

        InsightDuplicateAuditResponse response = new TrustedKnowledgeDuplicateAuditService(insightRepository).audit(projectId);

        assertEquals(1, response.clusterCount());
        assertEquals(InsightDuplicateClusterCategory.EXACT_DUPLICATE, response.clusters().getFirst().category());
        assertEquals(InsightDuplicateRecommendation.KEEP_NEWEST_AS_CANONICAL, response.clusters().getFirst().recommendation());
        assertEquals(2, response.clusters().getFirst().members().size());
    }

    @Test
    void shouldDetectLikelyRicherSuccessorCluster() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                insight(project, "REST Spring Boot Application Architecture",
                        "The project architecture is centered around a RESTful application structured with Spring Boot framework.",
                        null, null, null, Instant.parse("2026-08-11T23:10:52Z")),
                insight(project, "RESTful Spring Boot Application Architecture",
                        "The project's architecture is centered around a RESTful application developed using the Spring Boot framework, which exposes REST API controllers for external interactions.",
                        "The presence of Spring Boot and REST API controllers is explicitly confirmed by multiple observations and characteristics in the project analysis.",
                        new BigDecimal("1.0000"), "ARCHITECTURE_DESCRIPTION", Instant.parse("2026-08-12T00:32:11Z"))
        ));

        InsightDuplicateAuditResponse response = new TrustedKnowledgeDuplicateAuditService(insightRepository).audit(projectId);

        assertEquals(1, response.clusterCount());
        assertEquals(InsightDuplicateClusterCategory.LIKELY_RICHER_SUCCESSOR, response.clusters().getFirst().category());
        assertEquals(InsightDuplicateRecommendation.KEEP_RICHEST_AS_CANONICAL, response.clusters().getFirst().recommendation());
    }

    @Test
    void shouldMarkAmbiguousClusterForReview() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of(
                insight(project, "Automated Testing Structure",
                        "The project contains an automated test source tree and test files that support continuous integration and quality control.",
                        null, null, "TECHNOLOGY_DESCRIPTION", Instant.parse("2026-08-11T23:12:00Z")),
                insight(project, "Automated and Integration Testing Present",
                        "The project includes automated tests and specific integration test files, indicating a focus on quality assurance.",
                        null, null, "ARCHITECTURE_DESCRIPTION", Instant.parse("2026-08-11T23:11:30Z"))
        ));

        InsightDuplicateAuditResponse response = new TrustedKnowledgeDuplicateAuditService(insightRepository).audit(projectId);

        assertEquals(1, response.clusterCount());
        assertEquals(InsightDuplicateClusterCategory.REVIEW_REQUIRED, response.clusters().getFirst().category());
        assertEquals(InsightDuplicateRecommendation.REVIEW_MANUALLY, response.clusters().getFirst().recommendation());
    }

    @Test
    void shouldKeepProjectsIsolated() {
        UUID projectId = UUID.randomUUID();
        when(insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)).thenReturn(List.of());

        InsightDuplicateAuditResponse response = new TrustedKnowledgeDuplicateAuditService(insightRepository).audit(projectId);

        assertEquals(projectId, response.projectId());
        assertEquals(0, response.clusterCount());
    }

    private Insight insight(
            Project project,
            String title,
            String content,
            String rationale,
            BigDecimal confidence,
            String sourceType,
            Instant createdAt
    ) {
        return Insight.builder()
                .id(UUID.randomUUID())
                .project(project)
                .analysis(new Analysis())
                .proposal(ValidatableProposal.builder().id(UUID.randomUUID()).build())
                .validation(com.hopeful117.devlogai.validation.entity.Validation.builder().id(UUID.randomUUID()).build())
                .type(InsightType.ARCHITECTURAL)
                .severity(InsightSeverity.INFO)
                .title(title)
                .content(content)
                .rationale(rationale)
                .confidence(confidence)
                .sourceType(sourceType)
                .createdAt(createdAt)
                .build();
    }
}
