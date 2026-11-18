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
class McpOperationPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private McpOperationService service;

    @Test
    void singleStartedAndTerminalYieldsOneRow() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        insertEvent(projectId, inst, "op-1", "search_project_history",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 1050, null, completed);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalOperations()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        McpOperationView op = result.content().getFirst();
        assertThat(op.instanceId()).isEqualTo(inst);
        assertThat(op.operationId()).isEqualTo("op-1");
        assertThat(op.toolName()).isEqualTo("search_project_history");
        assertThat(op.status()).isEqualTo("RESPONDED");
        assertThat(op.durationMs()).isEqualTo(1050);
        assertThat(op.startedAt()).isEqualTo(started);
        assertThat(op.completedAt()).isEqualTo(completed);
    }

    @Test
    void terminalConflictSelectsMostRecentByOccurredAt() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant responded = Instant.parse("2026-01-01T12:00:01Z");
        Instant failed = Instant.parse("2026-01-01T12:00:02Z");
        insertEvent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 500, null, responded);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_FAILED", "FAILED", 1, 2000, "timeout", failed);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalOperations()).isEqualTo(1);
        McpOperationView op = result.content().getFirst();
        assertThat(op.status()).isEqualTo("FAILED");
        assertThat(op.reasonCode()).isEqualTo("timeout");
        assertThat(op.durationMs()).isEqualTo(2000);
    }

    @Test
    void terminalConflictUsesEventTypeAsAlphabeticTiebreak() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant sameTime = Instant.parse("2026-01-01T12:00:01Z");
        insertEvent(projectId, inst, "op-1", "search_project_history",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null, sameTime);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_FAILED", "FAILED", 1, 100, "err", sameTime);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        // MCP_TOOL_FAILED < MCP_TOOL_RESPONDED alphabetically → FAILED wins tiebreak
        assertThat(result.content().getFirst().status()).isEqualTo("FAILED");
    }

    @Test
    void activeOperationWithNoTerminalHasNullCompletedAt() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        insertEvent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        McpOperationView op = result.content().getFirst();
        assertThat(op.status()).isEqualTo("ACTIVE");
        assertThat(op.completedAt()).isNull();
        assertThat(op.durationMs()).isNull();
    }

    @Test
    void orphanTerminalWithoutStartedRowIsIncluded() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        insertEvent(projectId, inst, "op-1", "search_project_history",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 500, null, completed);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalOperations()).isEqualTo(1);
        McpOperationView op = result.content().getFirst();
        assertThat(op.startedAt()).isNull();
        assertThat(op.toolName()).isEqualTo("search_project_history");
        assertThat(op.status()).isEqualTo("RESPONDED");
    }

    @Test
    void outOfOrderArrivalProducesCorrectTimestamps() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        // terminal ingested first (earlier receivedAt), started ingested second
        insertEventWithReceivedAt(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 1000, null, completed,
                Instant.parse("2026-01-01T11:59:58Z"));
        insertEventWithReceivedAt(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started,
                Instant.parse("2026-01-01T11:59:59Z"));

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        McpOperationView op = result.content().getFirst();
        assertThat(op.startedAt()).isEqualTo(started);
        assertThat(op.completedAt()).isEqualTo(completed);
    }

    @Test
    void paginationAppliedAfterAggregation() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        for (int i = 1; i <= 5; i++) {
            Instant ts = Instant.parse("2026-01-01T12:00:0" + i + "Z");
            insertEvent(projectId, inst, "op-" + i, "tool-" + i,
                    "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, ts);
            insertEvent(projectId, inst, "op-" + i, null,
                    "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null, ts.plusMillis(100));
        }

        McpOperationPageResponse page0 = service.listByProject(projectId, 0, 2);
        McpOperationPageResponse page1 = service.listByProject(projectId, 1, 2);

        assertThat(page0.totalOperations()).isEqualTo(5);
        assertThat(page0.content()).hasSize(2);
        assertThat(page1.totalOperations()).isEqualTo(5);
        assertThat(page1.content()).hasSize(2);
        // ensure no overlap between pages
        assertThat(page0.content().stream().map(McpOperationView::operationId).toList())
                .doesNotContainAnyElementsOf(
                        page1.content().stream().map(McpOperationView::operationId).toList());
    }

    @Test
    void projectIsolationPreventsCrossProjectVisibility() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        Instant ts = Instant.parse("2026-01-01T12:00:00Z");
        insertEvent(projectA, "inst-" + projectA, "op-1", "tool-a", "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, ts);
        insertEvent(projectB, "inst-" + projectB, "op-1", "tool-b", "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, ts);

        McpOperationPageResponse resultA = service.listByProject(projectA, 0, 20);

        assertThat(resultA.totalOperations()).isEqualTo(1);
        assertThat(resultA.content().getFirst().toolName()).isEqualTo("tool-a");
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "MCP Op Test " + id, "mcp-op-test-" + id, now, now);
        return id;
    }

    private void insertEvent(
            UUID projectId, String instanceId, String operationId, String toolName,
            String eventType, String status, int generation,
            Integer durationMs, String reasonCode, Instant occurredAt) {
        insertEventWithReceivedAt(projectId, instanceId, operationId, toolName,
                eventType, status, generation, durationMs, reasonCode, occurredAt, Instant.now());
    }

    private void insertEventWithReceivedAt(
            UUID projectId, String instanceId, String operationId, String toolName,
            String eventType, String status, int generation,
            Integer durationMs, String reasonCode, Instant occurredAt, Instant receivedAt) {
        insertEventWithAgent(projectId, instanceId, operationId, toolName,
                eventType, status, generation, durationMs, reasonCode,
                occurredAt, receivedAt, null, null);
    }

    private void insertEventWithAgent(
            UUID projectId, String instanceId, String operationId, String toolName,
            String eventType, String status, int generation,
            Integer durationMs, String reasonCode,
            Instant occurredAt, Instant receivedAt,
            String agentSessionId, String agentProvider) {
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'mcp-tool-observability-v1', ?, ?, ?, ?)
                """,
                UUID.randomUUID(), projectId, instanceId, operationId, toolName,
                eventType, status, generation, durationMs, reasonCode,
                Timestamp.from(occurredAt), Timestamp.from(receivedAt),
                agentSessionId, agentProvider);
    }

    @Test
    void sessionIdentityPropagatedFromStartedEvent() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        String sessionId = "cc7d159d-a138-4b73-b14e-1e7279d013ab";
        String provider = "claude-code_2-1-325_agent";
        insertEventWithAgent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started, Instant.now(),
                sessionId, provider);
        insertEventWithAgent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 500, null, completed, Instant.now(),
                sessionId, provider);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        McpOperationView op = result.content().getFirst();
        assertThat(op.agentSessionId()).isEqualTo(sessionId);
        assertThat(op.agentProvider()).isEqualTo(provider);
    }

    @Test
    void sessionIdentityNullWhenAbsent() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant started = Instant.parse("2026-01-01T12:00:00Z");
        insertEvent(projectId, inst, "op-1", "search_project_history",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, started);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        McpOperationView op = result.content().getFirst();
        assertThat(op.agentSessionId()).isNull();
        assertThat(op.agentProvider()).isNull();
    }

    @Test
    void sessionIdentityFallsBackToTerminalWhenStartedAbsent() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant completed = Instant.parse("2026-01-01T12:00:01Z");
        String sessionId = "aa11bb22-ccdd-eeff-0011-223344556677";
        String provider = "claude-code_2-1-325_agent";
        insertEventWithAgent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 300, null, completed, Instant.now(),
                sessionId, provider);

        McpOperationPageResponse result = service.listByProject(projectId, 0, 20);

        McpOperationView op = result.content().getFirst();
        assertThat(op.agentSessionId()).isEqualTo(sessionId);
        assertThat(op.agentProvider()).isEqualTo(provider);
    }
}
