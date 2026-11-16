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

    @Query(value = """
            SELECT correlation_id FROM mcp_tool_events
            WHERE project_id = :projectId
              AND instance_id = :instanceId
              AND operation_id = :operationId
              AND correlation_id IS NOT NULL
            LIMIT 1
            """, nativeQuery = true)
    java.util.UUID findCorrelationId(
            @Param("projectId") UUID projectId,
            @Param("instanceId") String instanceId,
            @Param("operationId") String operationId);

    @Query(value = """
            SELECT instance_id, operation_id FROM mcp_tool_events
            WHERE project_id = :projectId
              AND correlation_id = :correlationId
            LIMIT 1
            """, nativeQuery = true)
    CorrelationLookupProjection findOpByCorrelationId(
            @Param("projectId") UUID projectId,
            @Param("correlationId") UUID correlationId);

    interface CorrelationLookupProjection {
        String getInstanceId();
        String getOperationId();
    }

    @Modifying
    @Query(value = """
            INSERT INTO mcp_tool_events (id, project_id, instance_id, operation_id, tool_name,
                event_type, status, generation, duration_ms, reason_code, schema_version,
                occurred_at, received_at, agent_session_id, agent_provider, correlation_id)
            VALUES (:id, :projectId, :instanceId, :operationId, :toolName,
                :eventType, :status, :generation, :durationMs, :reasonCode, :schemaVersion,
                :occurredAt, :receivedAt, :agentSessionId, :agentProvider, :correlationId)
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
            @Param("agentProvider") String agentProvider,
            @Param("correlationId") UUID correlationId);

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
                COALESCE(s.agent_provider,       t.agent_provider)     AS agent_provider,
                (SELECT correlation_id FROM mcp_tool_events e2
                 WHERE e2.instance_id = COALESCE(s.instance_id, t.instance_id)
                   AND e2.operation_id = COALESCE(s.operation_id, t.operation_id)
                   AND e2.correlation_id IS NOT NULL
                 LIMIT 1)                                              AS correlation_id
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
                COALESCE(s.agent_provider,       t.agent_provider)     AS agent_provider,
                (SELECT correlation_id FROM mcp_tool_events e2
                 WHERE e2.instance_id = COALESCE(s.instance_id, t.instance_id)
                   AND e2.operation_id = COALESCE(s.operation_id, t.operation_id)
                   AND e2.correlation_id IS NOT NULL
                 LIMIT 1)                                              AS correlation_id
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
        UUID getCorrelationId();
    }

    @Query(value = """
            WITH terminal_alerts AS (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id, operation_id, tool_name, status,
                    reason_code, occurred_at, duration_ms,
                    agent_session_id, agent_provider
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                  AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
                ORDER BY instance_id, operation_id, occurred_at DESC
            )
            SELECT *
            FROM terminal_alerts
            ORDER BY occurred_at DESC NULLS LAST,
                     instance_id ASC,
                     operation_id ASC
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ActivityAlertProjection> findAlertsByProject(
            @Param("projectId") UUID projectId,
            @Param("offset") int offset,
            @Param("size") int size);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT instance_id, operation_id
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                  AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
            ) sub
            """, nativeQuery = true)
    long countAlertsByProject(@Param("projectId") UUID projectId);

    @Query(value = """
            SELECT COUNT(*) FROM (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id, operation_id, status, reason_code
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                  AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
                  AND occurred_at >= :windowStart
                ORDER BY instance_id, operation_id, occurred_at DESC
            ) sub
            WHERE status = 'FAILED' OR reason_code = 'MCP_PROCESS_FAILED'
            """, nativeQuery = true)
    long countErrorAlertsByProjectInWindow(
            @Param("projectId") UUID projectId,
            @Param("windowStart") Instant windowStart);

        interface ActivityAlertProjection {
        String getInstanceId();
        String getOperationId();
        String getToolName();
        String getStatus();
        String getReasonCode();
        Instant getOccurredAt();
        Integer getDurationMs();
        String getAgentSessionId();
        String getAgentProvider();
    }

    @Query(value = """
            WITH recent_alerts AS (
                SELECT DISTINCT ON (instance_id, operation_id)
                    instance_id,
                    operation_id,
                    tool_name,
                    status,
                    reason_code,
                    occurred_at
                FROM mcp_tool_events
                WHERE project_id = :projectId
                  AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                  AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
                  AND occurred_at >= :windowStart
                ORDER BY instance_id, operation_id, occurred_at DESC
            ),
            grouped AS (
                SELECT
                    tool_name,
                    reason_code,
                    COUNT(*)                                                                       AS occurrence_count,
                    MIN(occurred_at)                                                               AS first_occurred_at,
                    MAX(occurred_at)                                                               AS last_occurred_at,
                    BOOL_OR(status = 'FAILED' OR reason_code = 'MCP_PROCESS_FAILED')              AS has_error,
                    string_agg(instance_id || '/' || operation_id, ',' ORDER BY occurred_at DESC) AS recent_alert_ids_raw
                FROM recent_alerts
                GROUP BY tool_name, reason_code
            )
            SELECT *
            FROM grouped
            ORDER BY last_occurred_at DESC NULLS LAST, has_error DESC, tool_name NULLS LAST, reason_code NULLS LAST
            LIMIT :size OFFSET :offset
            """, nativeQuery = true)
    List<ActivityIncidentProjection> findIncidentsByProject(
            @Param("projectId") UUID projectId,
            @Param("windowStart") Instant windowStart,
            @Param("offset") int offset,
            @Param("size") int size);

    @Query(value = """
            SELECT
                COUNT(*)                                  AS total,
                COUNT(*) FILTER (WHERE has_error)         AS error_count,
                COUNT(*) FILTER (WHERE NOT has_error)     AS warn_count
            FROM (
                SELECT tool_name, reason_code,
                       BOOL_OR(status = 'FAILED' OR reason_code = 'MCP_PROCESS_FAILED') AS has_error
                FROM (
                    SELECT DISTINCT ON (instance_id, operation_id)
                        tool_name, reason_code, status
                    FROM mcp_tool_events
                    WHERE project_id = :projectId
                      AND event_type IN ('MCP_TOOL_FAILED', 'MCP_TOOL_INTERRUPTED')
                      AND (reason_code IS NULL OR reason_code != 'MCP_INSTANCE_STOPPING')
                      AND occurred_at >= :windowStart
                    ORDER BY instance_id, operation_id, occurred_at DESC
                ) deduped
                GROUP BY tool_name, reason_code
            ) sub
            """, nativeQuery = true)
    IncidentCountsProjection summarizeIncidentsByProject(
            @Param("projectId") UUID projectId,
            @Param("windowStart") Instant windowStart);

    interface IncidentCountsProjection {
        long getTotal();
        long getErrorCount();
        long getWarnCount();
    }

    interface ActivityIncidentProjection {
        String getToolName();
        String getReasonCode();
        long getOccurrenceCount();
        Instant getFirstOccurredAt();
        Instant getLastOccurredAt();
        boolean isHasError();
        String getRecentAlertIdsRaw();
    }
}
