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
class AgentSessionLifecyclePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg17");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AgentSessionService sessionService;

    @Autowired
    private McpSessionLifecycleEventService lifecycleService;

    @Test
    void endedEventSetsLifecycleStatusEnded() {
        UUID projectId = insertProject();
        String sessionId = "session-" + UUID.randomUUID();
        insertToolEvent(projectId, sessionId, Instant.now().minusSeconds(300));
        insertLifecycleEvent(projectId, sessionId, "SESSION_LIFECYCLE_ENDED",
                Instant.now().minusSeconds(60));

        AgentSessionPageResponse page = sessionService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().lifecycleStatus()).isEqualTo("ENDED");
    }

    @Test
    void missingEndedEventDefaultsToUnknown() {
        UUID projectId = insertProject();
        String sessionId = "session-" + UUID.randomUUID();
        // Recent activity but no active operation and no lifecycle event
        insertToolEventWithTerminal(projectId, sessionId,
                Instant.now().minusSeconds(60), Instant.now().minusSeconds(50));

        AgentSessionPageResponse page = sessionService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().lifecycleStatus()).isEqualTo("UNKNOWN");
    }

    @Test
    void idleThresholdSetsLifecycleStatusIdle() {
        UUID projectId = insertProject();
        String sessionId = "session-" + UUID.randomUUID();
        // Last activity more than 10 minutes ago, no active operations
        insertToolEventWithTerminal(projectId, sessionId,
                Instant.now().minusSeconds(800), Instant.now().minusSeconds(790));

        AgentSessionPageResponse page = sessionService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().lifecycleStatus()).isEqualTo("IDLE");
    }

    @Test
    void activeToolWithRecentActivitySetsStatus() {
        UUID projectId = insertProject();
        String sessionId = "session-" + UUID.randomUUID();
        // Active operation started 20 seconds ago
        insertToolEvent(projectId, sessionId, Instant.now().minusSeconds(20));

        AgentSessionPageResponse page = sessionService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().lifecycleStatus()).isEqualTo("ACTIVE_TOOL");
    }

    @Test
    void lifecycleEventIsIdempotent() {
        UUID projectId = insertProject();
        String sessionId = "session-" + UUID.randomUUID();
        insertToolEvent(projectId, sessionId, Instant.now().minusSeconds(60));
        // Insert the same lifecycle event twice — unique constraint absorbs the duplicate
        insertLifecycleEvent(projectId, sessionId, "SESSION_LIFECYCLE_ENDED", Instant.now().minusSeconds(30));
        insertLifecycleEvent(projectId, sessionId, "SESSION_LIFECYCLE_ENDED", Instant.now().minusSeconds(30));

        AgentSessionPageResponse page = sessionService.listByProject(projectId, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().lifecycleStatus()).isEqualTo("ENDED");
        long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mcp_session_lifecycle_events WHERE agent_session_id = ?",
                Long.class, sessionId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void lifecycleIsolatedByProject() {
        UUID projectA = insertProject();
        UUID projectB = insertProject();
        String sessionId = "shared-session-" + UUID.randomUUID();
        insertToolEvent(projectA, sessionId, Instant.now().minusSeconds(60));
        insertToolEvent(projectB, sessionId, Instant.now().minusSeconds(60));
        // Only project A has ENDED
        insertLifecycleEvent(projectA, sessionId, "SESSION_LIFECYCLE_ENDED", Instant.now().minusSeconds(10));

        AgentSessionPageResponse pageA = sessionService.listByProject(projectA, 0, 20);
        AgentSessionPageResponse pageB = sessionService.listByProject(projectB, 0, 20);

        assertThat(pageA.content().getFirst().lifecycleStatus()).isEqualTo("ENDED");
        assertThat(pageB.content().getFirst().lifecycleStatus()).isNotEqualTo("ENDED");
    }

    // --- helpers ---

    private UUID insertProject() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                INSERT INTO projects (id, name, slug, description, status, created_at, updated_at)
                VALUES (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, id, "Lifecycle Test " + id, "lifecycle-test-" + id, now, now);
        return id;
    }

    private void insertToolEvent(UUID projectId, String sessionId, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), projectId, "inst-" + projectId,
                "op-" + UUID.randomUUID(), "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                "mcp-tool-observability-v1",
                Timestamp.from(occurredAt), Timestamp.from(Instant.now()),
                sessionId, "claude-code");
    }

    private void insertToolEventWithTerminal(UUID projectId, String sessionId,
                                              Instant startedAt, Instant completedAt) {
        String opId = "op-" + UUID.randomUUID();
        String instId = "inst-" + projectId;
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), projectId, instId, opId, "get_engineering_context",
                "MCP_TOOL_STARTED", "ACTIVE", 1, null, null,
                "mcp-tool-observability-v1",
                Timestamp.from(startedAt), Timestamp.from(Instant.now()),
                sessionId, "claude-code");
        jdbc.update("""
                INSERT INTO mcp_tool_events
                    (id, project_id, instance_id, operation_id, tool_name, event_type, status,
                     generation, duration_ms, reason_code, schema_version, occurred_at, received_at,
                     agent_session_id, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), projectId, instId, opId, "get_engineering_context",
                "MCP_TOOL_RESPONDED", "RESPONDED", 1, 100, null,
                "mcp-tool-observability-v1",
                Timestamp.from(completedAt), Timestamp.from(Instant.now()),
                sessionId, "claude-code");
    }

    private void insertLifecycleEvent(UUID projectId, String sessionId,
                                       String eventType, Instant occurredAt) {
        jdbc.update("""
                INSERT INTO mcp_session_lifecycle_events
                    (id, project_id, agent_session_id, event_type, occurred_at, received_at, agent_provider)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (project_id, agent_session_id, event_type) DO NOTHING
                """,
                UUID.randomUUID(), projectId, sessionId, eventType,
                Timestamp.from(occurredAt), Timestamp.from(Instant.now()), "claude-code");
    }
}
