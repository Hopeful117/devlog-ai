package com.hopeful117.devlogai.contextmaintenance;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceAssessmentService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import org.junit.jupiter.api.Test;
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
class MaintenanceAssessmentPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MaintenanceAssessmentService assessmentService;

    @Autowired
    private MaintenanceFindingService findingService;

    @Test
    void shouldPersistAssessmentLinkedToExistingFinding() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Assessment project", "assessment-project");

        MaintenanceFindingResponse finding = findingService.create(projectId, new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceSuggestedActionCategory.REVIEW,
                true,
                "Project understanding is stale",
                "Latest analysis predates current evidence."
        ));

        CreateMaintenanceAssessmentRequest request = new CreateMaintenanceAssessmentRequest(
                finding.id(),
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "The finding appears to represent a genuine duplicate based on content comparison.",
                null
        );

        MaintenanceAssessmentResponse response = assessmentService.create(projectId, request);

        assertNotNull(response.id());
        assertEquals(projectId, response.projectId());
        assertEquals(finding.id(), response.findingId());
        assertEquals(MaintenanceAssessmentConfidenceLevel.HIGH, response.confidenceLevel());
        assertEquals(MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE, response.semanticClassification());
        assertEquals(MaintenanceAssessmentRecommendedAction.ESCALATE, response.recommendedAction());
        assertTrue(response.rationale().contains("genuine duplicate"));
        assertNotNull(response.createdAt());
        assertEquals(1, count("maintenance_assessments", response.id()));
    }

    @Test
    void shouldRetrieveAssessmentsByProject() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Project A", "project-a");

        MaintenanceFindingResponse finding = findingService.create(projectId, new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                MaintenanceFindingSeverity.MEDIUM,
                MaintenanceSuggestedActionCategory.MONITOR,
                false,
                "Stale understanding",
                null
        ));

        assessmentService.create(projectId, new CreateMaintenanceAssessmentRequest(
                finding.id(),
                MaintenanceAssessmentConfidenceLevel.MEDIUM,
                MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                MaintenanceAssessmentRecommendedAction.MONITOR,
                "Uncertain classification.",
                null
        ));

        List<MaintenanceAssessmentResponse> results = assessmentService.getByProject(projectId);

        assertEquals(1, results.size());
        assertEquals(projectId, results.getFirst().projectId());
    }

    @Test
    void shouldRetrieveAssessmentsByFinding() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Project B", "project-b");

        MaintenanceFindingResponse finding = findingService.create(projectId, new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH,
                MaintenanceFindingSeverity.LOW,
                MaintenanceSuggestedActionCategory.REFRESH,
                false,
                "Missing projection refresh",
                null
        ));

        assessmentService.create(projectId, new CreateMaintenanceAssessmentRequest(
                finding.id(),
                MaintenanceAssessmentConfidenceLevel.LOW,
                MaintenanceAssessmentSemanticClassification.ISOLATED_SIGNAL,
                MaintenanceAssessmentRecommendedAction.NO_ACTION,
                "Isolated signal without cross-surface correlation.",
                null
        ));

        List<MaintenanceAssessmentResponse> results = assessmentService.getByFinding(projectId, finding.id());

        assertEquals(1, results.size());
        assertEquals(finding.id(), results.getFirst().findingId());
    }

    private void insertProject(UUID id, String name, String slug) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, name, slug, OffsetDateTime.now(), OffsetDateTime.now());
    }

    private int count(String table, UUID id) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where id = ?",
                Integer.class, id);
    }
}
