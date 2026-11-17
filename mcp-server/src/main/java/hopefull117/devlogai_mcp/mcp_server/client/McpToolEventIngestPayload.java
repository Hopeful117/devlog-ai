package hopefull117.devlogai_mcp.mcp_server.client;

/** Wire DTO matching the backend's McpToolEventIngestRequest record. */
public record McpToolEventIngestPayload(
        String projectSlug,
        String instanceId,
        String operationId,
        String toolName,
        String eventType,
        String status,
        int generation,
        String schemaVersion,
        Integer durationMs,
        String reasonCode,
        String occurredAt,
        String agentSessionId,
        String agentProvider,
        String correlationId
) {}
