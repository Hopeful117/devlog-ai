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
class AgentSessionPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgentSessionService service;

    @Test
    void singleSessionYieldsOneRow() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        String sessionId = UUID.randomUUID().toString();
        String provider = "claude-code_2-1-325_agent";
        insertEvent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:00Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 500, null,
                Instant.parse("2026-01-01T12:00:01Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-2", "search_project_history",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:02Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-2", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 300, null,
                Instant.parse("2026-01-01T12:00:03Z"), sessionId, provider);

        AgentSessionPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalSessions()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        AgentSessionView s = result.content().getFirst();
        assertThat(s.agentSessionId()).isEqualTo(sessionId);
        assertThat(s.agentProvider()).isEqualTo(provider);
        assertThat(s.operationCount()).isEqualTo(2);
    }

    @Test
    void nullSessionExcluded() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        // event with no agent identity
        insertEvent(projectId, inst, "op-1", "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:00Z"), null, null);

        AgentSessionPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalSessions()).isEqualTo(0);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void countersAreAccurate() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        String sessionId = UUID.randomUUID().toString();
        String provider = "claude-code_2-1-325_agent";
        insertEvent(projectId, inst, "op-1", "tool",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null,
                Instant.parse("2026-01-01T12:00:01Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-2", "tool",
                "MCP_TOOL_FAILED", "FAILED", 1, 200, "err",
                Instant.parse("2026-01-01T12:00:02Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-3", "tool",
                "MCP_TOOL_INTERRUPTED", "INTERRUPTED", 1, 50, null,
                Instant.parse("2026-01-01T12:00:03Z"), sessionId, provider);
        insertEvent(projectId, inst, "op-4", "tool",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:04Z"), sessionId, provider);

        AgentSessionView s = service.listByProject(projectId, 0, 20).content().getFirst();

        assertThat(s.operationCount()).isEqualTo(4);
        assertThat(s.respondedCount()).isEqualTo(1);
        assertThat(s.failedCount()).isEqualTo(1);
        assertThat(s.interruptedCount()).isEqualTo(1);
        assertThat(s.activeOperationCount()).isEqualTo(1);
    }

    @Test
    void projectIsolationPreventsSessionCrossVisibility() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        String sessionA = UUID.randomUUID().toString();
        String sessionB = UUID.randomUUID().toString();
        insertEvent(projectA, "inst-" + projectA, "op-1", "tool",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:00Z"), sessionA, "claude-code");
        insertEvent(projectB, "inst-" + projectB, "op-1", "tool",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:00Z"), sessionB, "claude-code");

        AgentSessionPageResponse resultA = service.listByProject(projectA, 0, 20);

        assertThat(resultA.totalSessions()).isEqualTo(1);
        assertThat(resultA.content().getFirst().agentSessionId()).isEqualTo(sessionA);
    }

    @Test
    void multipleInstancesSameSession() {
        UUID projectId = insertProject();
        String sessionId = UUID.randomUUID().toString();
        String provider = "claude-code_2-1-325_agent";
        // instance 1
        insertEvent(projectId, "inst-a-" + projectId, "op-1", "tool",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null,
                Instant.parse("2026-01-01T12:00:01Z"), sessionId, provider);
        // instance 2 (same session, different MCP process)
        insertEvent(projectId, "inst-b-" + projectId, "op-1", "tool",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 200, null,
                Instant.parse("2026-01-01T12:00:02Z"), sessionId, provider);

        AgentSessionPageResponse result = service.listByProject(projectId, 0, 20);

        assertThat(result.totalSessions()).isEqualTo(1);
        assertThat(result.content().getFirst().operationCount()).isEqualTo(2);
    }

    @Test
    void firstAndLastObservedAtCorrect() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        String sessionId = UUID.randomUUID().toString();
        Instant first = Instant.parse("2026-01-01T10:00:00Z");
        Instant last  = Instant.parse("2026-01-01T11:30:00Z");
        insertEvent(projectId, inst, "op-1", "tool",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                first, sessionId, "claude-code");
        insertEvent(projectId, inst, "op-1", null,
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null,
                last, sessionId, "claude-code");

        AgentSessionView s = service.listByProject(projectId, 0, 20).content().getFirst();

        assertThat(s.firstObservedAt()).isEqualTo(first);
        assertThat(s.lastObservedAt()).isEqualTo(last);
    }

    @Test
    void sessionOperationsEndpointFiltersCorrectly() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        String sessionA = UUID.randomUUID().toString();
        String sessionB = UUID.randomUUID().toString();
        insertEvent(projectId, inst, "op-1", "tool-a",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:00Z"), sessionA, "claude-code");
        insertEvent(projectId, inst, "op-2", "tool-b",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                Instant.parse("2026-01-01T12:00:01Z"), sessionB, "claude-code");

        McpOperationPageResponse result = service.listOperationsBySession(projectId, sessionA, 0, 20);

        assertThat(result.totalOperations()).isEqualTo(1);
        assertThat(result.content().getFirst().toolName()).isEqualTo("tool-a");
    }

    @Test
    void sessionPaginationIsDeterministicWhenSameLastObservedAt() {
        UUID projectId = insertProject();
        String inst = "inst-" + projectId;
        Instant sameTime = Instant.parse("2026-01-01T12:00:00Z");
        // Three sessions all with the same last-observed timestamp
        String sessionA = "aaa-" + UUID.randomUUID();
        String sessionB = "bbb-" + UUID.randomUUID();
        String sessionC = "ccc-" + UUID.randomUUID();
        insertEvent(projectId, inst, "op-a", "tool", "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, sameTime, sessionA, "cc");
        insertEvent(projectId, inst, "op-b", "tool", "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, sameTime, sessionB, "cc");
        insertEvent(projectId, inst, "op-c", "tool", "MCP_TOOL_STARTED", "ACTIVE", 1, null, null, sameTime, sessionC, "cc");

        // Page 1 of size 2 and page 2 of size 2 must together yield all 3 sessions without duplicates
        AgentSessionPageResponse page1 = service.listByProject(projectId, 0, 2);
        AgentSessionPageResponse page2 = service.listByProject(projectId, 1, 2);

        assertThat(page1.content()).hasSize(2);
        assertThat(page2.content()).hasSize(1);
        java.util.Set<String> ids = new java.util.HashSet<>();
        page1.content().forEach(s -> ids.add(s.agentSessionId()));
        page2.content().forEach(s -> ids.add(s.agentSessionId()));
        assertThat(ids).containsExactlyInAnyOrder(sessionA, sessionB, sessionC);
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Agent Session Test " + id, "agent-session-test-" + id, now, now);
        return id;
    }

    private void insertEvent(
            UUID projectId, String instanceId, String operationId, String toolName,
            String eventType, String status, int generation,
            Integer durationMs, String reasonCode, Instant occurredAt,
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
                Timestamp.from(occurredAt), Timestamp.from(Instant.now()),
                agentSessionId, agentProvider);
    }
}
