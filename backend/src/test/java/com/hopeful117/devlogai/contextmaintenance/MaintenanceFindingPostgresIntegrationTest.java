package com.hopeful117.devlogai.contextmaintenance;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceFindingRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.request.MaintenanceFindingActionRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.*;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Testcontainers
class MaintenanceFindingPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MaintenanceFindingService service;

    @Test
    void shouldPersistFindingAndSupportBasicLifecycle() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Maintenance project", "maintenance-project");

        MaintenanceFindingResponse created = service.create(projectId, new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceSuggestedActionCategory.REFRESH,
                true,
                "Project understanding is stale",
                "Latest analysis baseline no longer reflects current repository evidence."
        ));

        assertNotNull(created.id());
        assertEquals(MaintenanceFindingStatus.OPEN, created.status());
        assertEquals(projectId, created.projectId());
        assertEquals(1, count("maintenance_findings", created.id()));
        assertEquals("PROJECT_UNDERSTANDING", jdbc.queryForObject(
                "select context_surface from maintenance_findings where id = ?",
                String.class, created.id()));
        assertEquals("STALE_PROJECT_UNDERSTANDING", jdbc.queryForObject(
                "select issue_type from maintenance_findings where id = ?",
                String.class, created.id()));
        assertEquals("REFRESH", jdbc.queryForObject(
                "select suggested_action from maintenance_findings where id = ?",
                String.class, created.id()));
        assertEquals(0, created.actionHistory().size());

        MaintenanceFindingResponse updated = service.updateStatus(
                projectId, created.id(), MaintenanceFindingStatus.RESOLVED
        );

        assertEquals(MaintenanceFindingStatus.RESOLVED, updated.status());
        assertEquals("RESOLVED", jdbc.queryForObject(
                "select status from maintenance_findings where id = ?",
                String.class, created.id()));
        assertEquals(1, service.getByProject(projectId).size());
    }

    @Test
    void shouldPersistAcknowledgementAuditActionForDuplicateDebtFinding() {
        UUID projectId = UUID.randomUUID();
        insertProject(projectId, "Duplicate debt project", "duplicate-debt-project");

        MaintenanceFindingResponse created = service.create(projectId, new CreateMaintenanceFindingRequest(
                MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceSuggestedActionCategory.REVIEW,
                true,
                "Trusted knowledge exact duplicate debt detected for cluster 'adr'.",
                "Duplicate cluster key: adr"
        ));

        MaintenanceFindingResponse acknowledged = service.acknowledge(
                projectId,
                created.id(),
                new MaintenanceFindingActionRequest(
                        UUID.fromString("00000000-0000-0000-0000-000000000321"),
                        "Reviewed and acknowledged"
                )
        );

        assertEquals(MaintenanceFindingStatus.ACKNOWLEDGED, acknowledged.status());
        assertEquals(1, acknowledged.actionHistory().size());
        assertEquals("ACKNOWLEDGE", jdbc.queryForObject(
                "select action_type from maintenance_finding_actions where finding_id = ?",
                String.class, created.id()));
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
