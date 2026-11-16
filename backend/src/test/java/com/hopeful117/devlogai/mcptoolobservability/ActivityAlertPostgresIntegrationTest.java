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
class ActivityAlertPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ActivityAlertService alertService;

    @Test
    void failedOperationAppearsAsAlert() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_FAILED", "FAILED", null, null);

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.totalAlerts()).isEqualTo(1);
        assertThat(page.content().getFirst().severity()).isEqualTo("ERROR");
        assertThat(page.content().getFirst().eventType()).isEqualTo("MCP_TOOL_FAILED");
    }

    @Test
    void respondedOperationDoesNotAppear() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_RESPONDED", "RESPONDED", null, null);

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.totalAlerts()).isEqualTo(0);
        assertThat(page.content()).isEmpty();
    }

    @Test
    void mcpInstanceStoppingExcluded() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_INTERRUPTED", "INTERRUPTED", "MCP_INSTANCE_STOPPING", null);

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.totalAlerts()).isEqualTo(0);
    }

    @Test
    void reauthRequiredAppearsAsWarnAlert() {
        UUID projectId = insertProject();
        insertOperation(projectId, "MCP_TOOL_INTERRUPTED", "INTERRUPTED", "MCP_REAUTH_REQUIRED", "my_tool");

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.totalAlerts()).isEqualTo(1);
        ActivityAlertView a = page.content().getFirst();
        assertThat(a.severity()).isEqualTo("WARN");
        assertThat(a.toolName()).isEqualTo("my_tool");
        assertThat(a.reasonCode()).isEqualTo("MCP_REAUTH_REQUIRED");
    }

    @Test
    void alertsIsolatedByProject() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        insertOperation(projectA, "MCP_TOOL_FAILED", "FAILED", null, null);

        assertThat(alertService.listByProject(projectA, 0, 20).totalAlerts()).isEqualTo(1);
        assertThat(alertService.listByProject(projectB, 0, 20).totalAlerts()).isEqualTo(0);
    }

    @Test
    void deduplicationPreservesOneAlertPerOperation() {
        UUID projectId = insertProject();
        String instId = "inst-dedup-" + UUID.randomUUID();
        String opId = "op-dedup-" + UUID.randomUUID();
        // Insert both STARTED and FAILED for same operation
        insertRawEvent(projectId, instId, opId, "MCP_TOOL_STARTED", "ACTIVE", null, null,
                Instant.now().minusSeconds(60));
        insertRawEvent(projectId, instId, opId, "MCP_TOOL_FAILED", "FAILED", null, null,
                Instant.now().minusSeconds(50));

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.totalAlerts()).isEqualTo(1);
        assertThat(page.content().getFirst().alertId()).isEqualTo(instId + "/" + opId);
    }

    @Test
    void alertsSortedByOccurredAtDesc() {
        UUID projectId = insertProject();
        Instant older = Instant.now().minusSeconds(300);
        Instant newer = Instant.now().minusSeconds(60);
        String instA = "inst-sort-a";
        String instB = "inst-sort-b";
        insertRawEvent(projectId, instA, "op-old", "MCP_TOOL_FAILED", "FAILED", null, null, older);
        insertRawEvent(projectId, instB, "op-new", "MCP_TOOL_FAILED", "FAILED", null, null, newer);

        ActivityAlertPageResponse page = alertService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(2);
        assertThat(page.content().getFirst().occurredAt()).isAfter(page.content().getLast().occurredAt());
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Alert Test " + id, "alert-test-" + id, now, now);
        return id;
    }

    private void insertOperation(UUID projectId, String eventType, String status,
                                  String reasonCode, String toolName) {
        String instId = "inst-" + UUID.randomUUID();
        String opId = "op-" + UUID.randomUUID();
        insertRawEvent(projectId, instId, opId, eventType, status, reasonCode, toolName,
                Instant.now().minusSeconds(60));
    }

    private void insertRawEvent(UUID projectId, String instId, String opId,
                                 String eventType, String status, String reasonCode,
                                 String toolName, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (instance_id, operation_id, event_type) DO NOTHING
                """,
                UUID.randomUUID(), projectId, instId, opId, toolName,
                eventType, status, 1, null, reasonCode,
                "mcp-tool-observability-v1",
                Timestamp.from(occurredAt), Timestamp.from(Instant.now()),
                null, null);
    }
}
