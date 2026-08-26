package com.hopeful117.devlogai.contextmaintenance;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceEvaluationService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateAuditResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateMemberResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateRecommendation;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.service.TrustedKnowledgeDuplicateAuditService;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class MaintenanceEvaluationIntegrationTest {

    private static final UUID SYSTEM_AUTOMATION_ACTOR_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MaintenanceEvaluationService evaluationService;

    @Autowired
    private MaintenanceFindingService findingService;

    @Autowired
    private ProjectFreshnessService freshnessService;

    @Autowired
    private TrustedKnowledgeDuplicateAuditService duplicateAuditService;

    @BeforeEach
    void resetMocks() {
        reset(freshnessService, duplicateAuditService);
    }

    @Test
    void evaluatesMultipleMaintenanceSurfacesAndPreservesAutomationBoundaries() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Cross-surface project", "cross-surface-project");

        insertHumanContextInput(projectId, "Fresh goal", ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ACTIVE, Instant.now().minusSeconds(5 * 24 * 3600L));
        insertHumanContextInput(projectId, "Older goal", ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ACTIVE, Instant.now().minusSeconds(60L * 24 * 3600L));

        MaintenanceFindingResponse autoResolvable = findingService.create(projectId,
                new CreateMaintenanceFindingRequest(
                        MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.HIGH,
                        MaintenanceSuggestedActionCategory.REFRESH,
                        false,
                        "Project understanding is stale for source 'legacy'.",
                        """
                                Freshness check status is STALE with guidance REFRESH_RECOMMENDED.
                                Source requested revision: origin/main
                                Source current revision: %s
                                Baseline analyzed revision: %s
                                Baseline completed at: 2026-08-14T08:00:00Z
                                Checked at: 2026-08-14T09:00:00Z
                                """.formatted("c".repeat(40), "d".repeat(40)).trim()
                ));

        MaintenanceFindingResponse duplicateDebt = findingService.create(projectId,
                new CreateMaintenanceFindingRequest(
                        MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW,
                        com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity.MEDIUM,
                        MaintenanceSuggestedActionCategory.REVIEW,
                        true,
                        "Trusted knowledge overlap requires review for cluster 'existing-overlap'.",
                        "Existing duplicate debt that must remain untouched by automation."
                ));

        UUID activeSourceId = UUID.randomUUID();
        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(staleFreshness(projectId, activeSourceId, "repo",
                        "a".repeat(40), "b".repeat(40))),
                1,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId,
                2,
                1,
                List.of(exactDuplicateCluster())
        ));

        MaintenanceEvaluationResponse result = evaluationService.evaluate(projectId);

        assertEquals(4, result.createdCount());
        assertEquals(0, result.skippedCount());
        assertEquals(List.of(
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH,
                        MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE
                ),
                result.createdFindings().stream()
                        .map(MaintenanceFindingResponse::issueType)
                        .toList());

        List<MaintenanceFindingResponse> findings = findingService.getByProject(projectId);
        assertEquals(6, findings.size());

        MaintenanceFindingResponse resolvedFinding = findings.stream()
                .filter(value -> value.id().equals(autoResolvable.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(MaintenanceFindingStatus.RESOLVED, resolvedFinding.status());
        assertEquals("AUTO_RESOLVE",
                resolvedFinding.actionHistory().getFirst().actionType().name());
        assertEquals(SYSTEM_AUTOMATION_ACTOR_ID,
                resolvedFinding.actionHistory().getFirst().actedBy());

        MaintenanceFindingResponse preservedDuplicateDebt = findings.stream()
                .filter(value -> value.id().equals(duplicateDebt.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(MaintenanceFindingStatus.OPEN, preservedDuplicateDebt.status());
        assertTrue(preservedDuplicateDebt.actionHistory().isEmpty());

        assertEquals("ACTIVE", jdbc.queryForObject(
                """
                        select status
                        from project_human_context_inputs
                        where project_id = ? and title = ?
                        """,
                String.class, projectId, "Older goal"));

        long autoResolvedCount = findings.stream()
                .filter(value -> !value.actionHistory().isEmpty())
                .filter(value -> value.actionHistory().getFirst().actionType().name().equals("AUTO_RESOLVE"))
                .count();
        assertEquals(1, autoResolvedCount);
    }

    @Test
    void resistsFalsePositivesWhenNoMaintenanceConditionApplies() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Quiet project", "quiet-project");
        insertHumanContextInput(projectId, "Only goal", ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ACTIVE, Instant.now().minusSeconds(5 * 24 * 3600L));

        when(freshnessService.summary(projectId)).thenReturn(new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId,
                List.of(currentFreshness(projectId, UUID.randomUUID(), "repo", "a".repeat(40))),
                0,
                false
        ));
        when(duplicateAuditService.audit(projectId)).thenReturn(new InsightDuplicateAuditResponse(
                projectId, 0, 0, List.of()
        ));

        MaintenanceEvaluationResponse result = evaluationService.evaluate(projectId);

        assertEquals(0, result.createdCount());
        assertEquals(0, result.skippedCount());
        assertTrue(result.createdFindings().isEmpty());
        assertTrue(findingService.getByProject(projectId).isEmpty());
        assertEquals("ACTIVE", jdbc.queryForObject(
                """
                        select status
                        from project_human_context_inputs
                        where project_id = ? and title = ?
                        """,
                String.class, projectId, "Only goal"));
    }

    private ProjectFreshnessResponse staleFreshness(
            UUID projectId,
            UUID sourceId,
            String sourceName,
            String currentRevision,
            String baselineRevision
    ) {
        return new ProjectFreshnessResponse(
                ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(),
                projectId,
                new ProjectFreshnessResponse.Source(
                        sourceId, sourceName, "main", "origin/main", currentRevision, null
                ),
                Instant.parse("2026-08-14T10:00:00Z"),
                ProjectFreshnessStatus.STALE,
                ProjectRefreshGuidance.REFRESH_RECOMMENDED,
                new ProjectFreshnessResponse.Baseline(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-14T09:00:00Z"),
                        baselineRevision
                ),
                new ProjectFreshnessResponse.ReviewCounts(1, 0, 1, 0)
        );
    }

    private ProjectFreshnessResponse currentFreshness(
            UUID projectId,
            UUID sourceId,
            String sourceName,
            String revision
    ) {
        return new ProjectFreshnessResponse(
                ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(),
                projectId,
                new ProjectFreshnessResponse.Source(
                        sourceId, sourceName, "main", "origin/main", revision, null
                ),
                Instant.parse("2026-08-14T10:00:00Z"),
                ProjectFreshnessStatus.CURRENT,
                ProjectRefreshGuidance.REFRESH_NOT_NEEDED,
                new ProjectFreshnessResponse.Baseline(
                        UUID.randomUUID(),
                        Instant.parse("2026-08-14T10:00:00Z"),
                        revision
                ),
                new ProjectFreshnessResponse.ReviewCounts(1, 0, 1, 0)
        );
    }

    private InsightDuplicateClusterResponse exactDuplicateCluster() {
        return new InsightDuplicateClusterResponse(
                "ARCHITECTURE_DESCRIPTION::adr",
                InsightDuplicateClusterCategory.EXACT_DUPLICATE,
                InsightDuplicateRecommendation.KEEP_NEWEST_AS_CANONICAL,
                "Members share the same normalized trusted fingerprint.",
                List.of(
                        member("Architecture Decision Records (ADR) Documentation",
                                Instant.parse("2026-08-14T10:00:00Z")),
                        member("Architecture Decision Records (ADR) Documentation",
                                Instant.parse("2026-08-14T09:00:00Z"))
                )
        );
    }

    private InsightDuplicateMemberResponse member(String title, Instant createdAt) {
        return new InsightDuplicateMemberResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InsightType.ARCHITECTURAL,
                InsightSeverity.INFO,
                "ARCHITECTURE_DESCRIPTION",
                title,
                "content",
                "rationale",
                BigDecimal.ONE,
                1,
                createdAt
        );
    }

    private void insertProject(UUID id, String name, String slug) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, name, slug, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private void insertHumanContextInput(
            UUID projectId,
            String title,
            ProjectHumanContextInputType type,
            ProjectHumanContextInputStatus status,
            Instant updatedAt
    ) {
        jdbc.update("""
                insert into project_human_context_inputs
                    (id, project_id, title, content_markdown, input_type, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                projectId,
                title,
                "content for " + title,
                type.name(),
                status.name(),
                OffsetDateTime.now().minusDays(90),
                OffsetDateTime.ofInstant(updatedAt, java.time.ZoneOffset.UTC));
    }

    @TestConfiguration
    static class MockSignalsConfiguration {
        @Bean
        @Primary
        ProjectFreshnessService projectFreshnessService() {
            return mock(ProjectFreshnessService.class);
        }

        @Bean
        @Primary
        TrustedKnowledgeDuplicateAuditService trustedKnowledgeDuplicateAuditService() {
            return mock(TrustedKnowledgeDuplicateAuditService.class);
        }
    }
}
