package com.hopeful117.devlogai.mcptoolobservability;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface McpToolEventRepository extends JpaRepository<McpToolEvent, UUID> {
    Page<McpToolEvent> findByProjectIdOrderByReceivedAtDesc(UUID projectId, Pageable pageable);
    boolean existsByInstanceIdAndOperationIdAndEventType(String instanceId, String operationId, String eventType);

    @Modifying
    @Query(value = """
            INSERT INTO mcp_tool_events (id, project_id, instance_id, operation_id, tool_name,
                event_type, status, generation, duration_ms, reason_code, schema_version,
                occurred_at, received_at)
            VALUES (:id, :projectId, :instanceId, :operationId, :toolName,
                :eventType, :status, :generation, :durationMs, :reasonCode, :schemaVersion,
                :occurredAt, :receivedAt)
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
            @Param("receivedAt") Instant receivedAt);
}
