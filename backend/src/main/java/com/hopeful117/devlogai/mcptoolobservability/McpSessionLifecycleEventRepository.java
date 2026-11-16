package com.hopeful117.devlogai.mcptoolobservability;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface McpSessionLifecycleEventRepository
        extends JpaRepository<McpSessionLifecycleEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO mcp_session_lifecycle_events
                (id, project_id, agent_session_id, event_type, occurred_at, received_at, agent_provider)
            VALUES (:id, :projectId, :agentSessionId, :eventType, :occurredAt, :receivedAt, :agentProvider)
            ON CONFLICT (project_id, agent_session_id, event_type) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("id")             UUID id,
            @Param("projectId")      UUID projectId,
            @Param("agentSessionId") String agentSessionId,
            @Param("eventType")      String eventType,
            @Param("occurredAt")     Instant occurredAt,
            @Param("receivedAt")     Instant receivedAt,
            @Param("agentProvider")  String agentProvider);

    @Query(value = """
            SELECT COUNT(*) > 0
            FROM mcp_session_lifecycle_events
            WHERE project_id      = :projectId
              AND agent_session_id = :agentSessionId
              AND event_type       = 'SESSION_LIFECYCLE_ENDED'
            """, nativeQuery = true)
    boolean hasEndedEvent(
            @Param("projectId")      UUID projectId,
            @Param("agentSessionId") String agentSessionId);
}
