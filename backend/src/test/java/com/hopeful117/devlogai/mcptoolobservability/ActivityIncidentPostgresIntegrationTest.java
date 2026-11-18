package com.hopeful117.devlogai.mcptoolobservability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class ActivityIncidentPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ActivityIncidentService incidentService;

    @Test
    void respondedOperationProducesNoIncident() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_RESPONDED", "RESPONDED", null, "my_tool",
                Instant.now().minusSeconds(60));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(0);
    }

    @Test
    void failedOperationInWindowProducesErrorIncident() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "tool_x",
                Instant.now().minusSeconds(60));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(1);
        assertThat(page.content().getFirst().severity()).isEqualTo("ERROR");
        assertThat(page.content().getFirst().toolName()).isEqualTo("tool_x");
        assertThat(page.errorCount()).isEqualTo(1);
    }

    @Test
    void failedOperationOutsideWindowProducesNoIncident() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "tool_x",
                Instant.now().minusSeconds(7200));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(0);
    }

    @Test
    void mcpInstanceStoppingExcluded() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_INTERRUPTED", "INTERRUPTED", "MCP_INSTANCE_STOPPING", null,
                Instant.now().minusSeconds(60));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(0);
    }

    @Test
    void fiveSameToolReasonGroupedInOneIncident() {
        UUID projectId = insertProject();
        for (int i = 0; i < 5; i++) {
            insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "repeated_tool",
                    Instant.now().minusSeconds(60 + i));
        }

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(1);
        assertThat(page.content().getFirst().occurrenceCount()).isEqualTo(5);
        assertThat(page.content().getFirst().toolName()).isEqualTo("repeated_tool");
    }

    @Test
    void twoDifferentToolsProduceTwoIncidents() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "tool_a",
                Instant.now().minusSeconds(60));
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "tool_b",
                Instant.now().minusSeconds(120));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(2);
    }

    @Test
    void nullToolNameGroupedSeparately() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, null,
                Instant.now().minusSeconds(60));
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "explicit_tool",
                Instant.now().minusSeconds(90));

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.totalIncidents()).isEqualTo(2);
    }

    @Test
    void incidentsIsolatedByProject() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        insertOperation(projectA, "MCP_TOOL_FAILED", "FAILED", null, "tool",
                Instant.now().minusSeconds(60));

        assertThat(incidentService.listByProject(projectA, 60, 0, 20).totalIncidents()).isEqualTo(1);
        assertThat(incidentService.listByProject(projectB, 60, 0, 20).totalIncidents()).isEqualTo(0);
    }

    @Test
    void incidentsSortedByLastOccurredAtDesc() {
        UUID projectId = insertProject();
        Instant older = Instant.now().minusSeconds(300);
        Instant newer = Instant.now().minusSeconds(60);
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, "tool_old", older);
        insertOperation(projectId, "MCP_TOOL_INTERRUPTED", "INTERRUPTED", "MCP_REAUTH_REQUIRED", "tool_new", newer);

        ActivityIncidentPageResponse page = incidentService.listByProject(projectId, 60, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().getFirst().lastOccurredAt())
                .isAfter(page.content().getLast().lastOccurredAt());
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Incident Test " + id, "incident-test-" + id, now, now);
        return id;
    }

    private void insertOperation(UUID projectId, String eventType, String status,
                                  String reasonCode, String toolName, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (instance_id, operation_id, event_type) DO NOTHING
                """,
                UUID.randomUUID(), projectId,
                "inst-" + UUID.randomUUID(), "op-" + UUID.randomUUID(),
                toolName, eventType, status, 1, null, reasonCode,
                "mcp-tool-observability-v1",
                Timestamp.from(occurredAt), Timestamp.from(Instant.now()),
                null, null);
    }
}
