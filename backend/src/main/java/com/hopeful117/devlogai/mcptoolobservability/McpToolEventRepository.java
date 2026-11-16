package com.hopeful117.devlogai.mcptoolobservability;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface McpToolEventRepository extends JpaRepository<McpToolEvent, UUID> {
    Page<McpToolEvent> findByProjectIdOrderByReceivedAtDesc(UUID projectId, Pageable pageable);
    boolean existsByInstanceIdAndOperationIdAndEventType(String instanceId, String operationId, String eventType);

    @Modifying
    @Query(value = """
            INSERT INTO mcp_tool_events (id, project_id, instance_id, operation_id, tool_name,
                event_type, status, generation, duration_ms, reason_code, schema_version,
                occurred_at, received_at, agent_session_id, agent_provider)
            VALUES (:id, :projectId, :instanceId, :operationId, :toolName,
                :eventType, :status, :generation, :durationMs, :reasonCode, :schemaVersion,
                :occurredAt, :receivedAt, :agentSessionId, :agentProvider)
            ON CONFLICT (instance_id, operation_id, event_type) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id") UUID id,
            @Param("projectId") UUID projectId,
            @Param("instanceId") String instanceId,
            @Param("operationId") String operationId,
            @Param("toolName") String toolName,
            @Param("eventType") String eventType,
            @Param("status") String status,
            @Param("generation") int generation,
            @Param("durationMs") Integer durationMs,
            @Param("reasonCode") String reasonCode,
            @Param("schemaVersion") String schemaVersion,
            @Param("occurredAt") Instant occurredAt,
            @Param("receivedAt") Instant receivedAt,
            @Param("agentSessionId") String agentSessionId,
            @Param("agentProvider") String agentProvider);

    @Query(value = """
            WITH terminals AS (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id,
                    operation_id,
                    status,
                    occurred_at   AS completed_at,
                    duration_ms,
                    reason_code,
                    tool_name     AS terminal_tool_name,
                    agent_session_id,
                    agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_RESPONDED', 'MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                ORDER BY instance_id, operation_id, occurred_at DESC, event_type ASC
            ),
            starts AS (
                SELECT instance_id, operation_id, tool_name, occurred_at AS started_at, generation,
                       agent_session_id, agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type = 'MCP_TOOL_STARTED'
            )
            SELECT
                COALESCE(s.instance_id,          t.instance_id)        AS instance_id,
                COALESCE(s.operation_id,         t.operation_id)       AS operation_id,
                COALESCE(s.tool_name,            t.terminal_tool_name) AS tool_name,
                COALESCE(t.status,               'ACTIVE')             AS status,
                COALESCE(s.generation,           0)                    AS generation,
                s.started_at,
                t.completed_at,
                t.duration_ms,
                t.reason_code,
                COALESCE(s.agent_session_id,     t.agent_session_id)   AS agent_session_id,
                COALESCE(s.agent_provider,       t.agent_provider)     AS agent_provider
            FROM starts s
            FULL OUTER JOIN terminals t
                ON s.instance_id = t.instance_id AND s.operation_id = t.operation_id
            ORDER BY s.started_at DESC NULLS LAST,
                     COALESCE(s.operation_id, t.operation_id) ASC,
                     COALESCE(s.instance_id,  t.instance_id)  ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<McpOperationProjection> findOperationsByProject(
            @Param("projectId") UUID projectId,
            @Param("offset") int offset,
            @Param("size") int size);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT instance_id, operation_id FROM mcp_tool_events WHERE project_id = :projectId
            ) sub
            """, nativeQuery = true)
    long countOperationsByProject(@Param("projectId") UUID projectId);


    @Query(value = """
            WITH terminals AS (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id, operation_id, status, occurred_at AS completed_at,
                    agent_session_id, agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_RESPONDED', 'MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                ORDER BY instance_id, operation_id, occurred_at DESC, event_type ASC
            ),
            starts AS (
                SELECT instance_id, operation_id, occurred_at AS started_at,
                       agent_session_id, agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type = 'MCP_TOOL_STARTED'
            ),
            op_level AS (
                SELECT
                    COALESCE(s.agent_session_id, t.agent_session_id) AS agent_session_id,
                    COALESCE(s.agent_provider,   t.agent_provider)   AS agent_provider,
                    COALESCE(t.status, 'ACTIVE')                     AS status,
                    COALESCE(s.started_at,  t.completed_at)          AS time_min,
                    COALESCE(t.completed_at, s.started_at)           AS time_max
                FROM starts s
                FULL OUTER JOIN terminals t
                    ON s.instance_id = t.instance_id AND s.operation_id = t.operation_id
                WHERE COALESCE(s.agent_session_id, t.agent_session_id) IS NOT NULL
            )
            SELECT
                agent_session_id                                       AS agent_session_id,
                MIN(agent_provider)                                    AS agent_provider,
                MIN(time_min)                                          AS first_observed_at,
                MAX(time_max)                                          AS last_observed_at,
                COUNT(*)                                               AS operation_count,
                COUNT(*) FILTER (WHERE status = 'RESPONDED')          AS responded_count,
                COUNT(*) FILTER (WHERE status = 'FAILED')             AS failed_count,
                COUNT(*) FILTER (WHERE status = 'INTERRUPTED')        AS interrupted_count,
                COUNT(*) FILTER (WHERE status = 'ACTIVE')             AS active_operation_count
            FROM op_level
            GROUP BY agent_session_id
            ORDER BY MAX(time_max) DESC NULLS LAST, agent_session_id ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<AgentSessionProjection> findSessionsByProject(
            @Param("projectId") UUID projectId,
            @Param("offset") int offset,
            @Param("size") int size);

    @Query(value = """
            SELECT COUNT(DISTINCT agent_session_id)
            FROM mcp_tool_events
            WHERE project_id = :projectId
              AND agent_session_id IS NOT NULL
            """, nativeQuery = true)
    long countSessionsByProject(@Param("projectId") UUID projectId);

    @Query(value = """
            WITH terminals AS (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id,
                    operation_id,
                    status,
                    occurred_at   AS completed_at,
                    duration_ms,
                    reason_code,
                    tool_name     AS terminal_tool_name,
                    agent_session_id,
                    agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND agent_session_id = :sessionId
                  AND event_type IN ('MCP_TOOL_RESPONDED', 'MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                ORDER BY instance_id, operation_id, occurred_at DESC, event_type ASC
            ),
            starts AS (
                SELECT instance_id, operation_id, tool_name, occurred_at AS started_at, generation,
                       agent_session_id, agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND agent_session_id = :sessionId
                  AND event_type = 'MCP_TOOL_STARTED'
            )
            SELECT
                COALESCE(s.instance_id,          t.instance_id)        AS instance_id,
                COALESCE(s.operation_id,         t.operation_id)       AS operation_id,
                COALESCE(s.tool_name,            t.terminal_tool_name) AS tool_name,
                COALESCE(t.status,               'ACTIVE')             AS status,
                COALESCE(s.generation,           0)                    AS generation,
                s.started_at,
                t.completed_at,
                t.duration_ms,
                t.reason_code,
                COALESCE(s.agent_session_id,     t.agent_session_id)   AS agent_session_id,
                COALESCE(s.agent_provider,       t.agent_provider)     AS agent_provider
            FROM starts s
            FULL OUTER JOIN terminals t
                ON s.instance_id = t.instance_id AND s.operation_id = t.operation_id
            ORDER BY s.started_at DESC NULLS LAST,
                     COALESCE(s.operation_id, t.operation_id) ASC,
                     COALESCE(s.instance_id,  t.instance_id)  ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<McpOperationProjection> findOperationsByProjectAndSession(
            @Param("projectId") UUID projectId,
            @Param("sessionId") String sessionId,
            @Param("offset") int offset,
            @Param("size") int size);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT instance_id, operation_id
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND agent_session_id = :sessionId
            ) sub
            """, nativeQuery = true)
    long countOperationsByProjectAndSession(
            @Param("projectId") UUID projectId,
            @Param("sessionId") String sessionId);

    interface AgentSessionProjection {
        String getAgentSessionId();
        String getAgentProvider();
        Instant getFirstObservedAt();
        Instant getLastObservedAt();
        long getOperationCount();
        long getRespondedCount();
        long getFailedCount();
        long getInterruptedCount();
        long getActiveOperationCount();
    }
    interface McpOperationProjection {
        String getInstanceId();
        String getOperationId();
        String getToolName();
        String getStatus();
        Integer getGeneration();
        Instant getStartedAt();
        Instant getCompletedAt();
        Integer getDurationMs();
        String getReasonCode();
        String getAgentSessionId();
        String getAgentProvider();
    }
}
