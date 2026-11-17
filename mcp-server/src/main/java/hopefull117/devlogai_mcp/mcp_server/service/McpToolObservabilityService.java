package hopefull117.devlogai_mcp.mcp_server.service;

import hopefull117.devlogai_mcp.mcp_server.client.McpToolEventIngestClient;
import hopefull117.devlogai_mcp.mcp_server.client.McpToolEventIngestPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Records MCP tool call completions to the backend observability table.
 * Enables the DevLog Companion's context-trace feature by linking aiTaskId
 * to a stable (instanceId, operationId) pair in mcp_tool_events.
 */
@Service
@Slf4j
public class McpToolObservabilityService {

    /** Stable per-process identifier — regenerated on restart, stable within one run. */
    private final String instanceId = UUID.randomUUID().toString();

    private final McpToolEventIngestClient ingestClient;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final ObjectMapper objectMapper;

    public McpToolObservabilityService(
            McpToolEventIngestClient ingestClient,
            McpAuthenticatedPrincipalProvider principalProvider,
            ObjectMapper objectMapper) {
        this.ingestClient = ingestClient;
        this.principalProvider = principalProvider;
        this.objectMapper = objectMapper;
    }

    /** Generates a fresh operation ID for the start of a tool call. */
    public String newOperationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Records a COMPLETED event for a tool call.
     * Called after the tool returns its result. Fire-and-forget — never throws.
     *
     * @param projectSlug  DevLog project slug from tool input
     * @param operationId  ID generated at the start of this call via {@link #newOperationId()}
     * @param toolName     MCP tool name
     * @param startedAt    when the call started (for duration)
     * @param resultJson   raw JSON string returned by the tool (may contain "aiTaskId")
     */
    public void recordCompletion(
            String projectSlug,
            String operationId,
            String toolName,
            Instant startedAt,
            String resultJson) {
        record("COMPLETED", projectSlug, operationId, toolName, startedAt, resultJson, null);
    }

    /**
     * Records a FAILED event for a tool call.
     * Called when the tool throws. Fire-and-forget — never throws.
     */
    public void recordFailure(
            String projectSlug,
            String operationId,
            String toolName,
            Instant startedAt,
            String reasonCode) {
        record("FAILED", projectSlug, operationId, toolName, startedAt, null, reasonCode);
    }

    private void record(
            String eventType,
            String projectSlug,
            String operationId,
            String toolName,
            Instant startedAt,
            String resultJson,
            String reasonCode) {
        try {
            var principal = principalProvider.currentPrincipal();
            if (principal.isEmpty()) {
                log.debug("MCP observability: no configured principal — skipping ingest for {}/{}", toolName, operationId);
                return;
            }
            String bearerToken = principal.get().bearerToken();
            if (bearerToken == null || bearerToken.isBlank()) {
                log.debug("MCP observability: no bearer token — skipping ingest for {}/{}", toolName, operationId);
                return;
            }

            String correlationId = extractAiTaskId(resultJson);
            int durationMs = (int) Math.min(Integer.MAX_VALUE,
                    java.time.Duration.between(startedAt, Instant.now()).toMillis());
            String delegation = principal.get().delegationToken();

            ingestClient.ingest(
                    new McpToolEventIngestPayload(
                            projectSlug,
                            instanceId,
                            operationId,
                            toolName,
                            eventType,
                            eventType,   // status mirrors eventType
                            0,
                            "1.0",
                            durationMs,
                            reasonCode,
                            startedAt.toString(),
                            null,
                            "devlog-mcp-server",
                            correlationId),
                    bearerToken,
                    delegation != null ? delegation : "");
            log.debug("MCP observability: ingested {} for tool={} operationId={} correlationId={}",
                    eventType, toolName, operationId, correlationId);
        } catch (Exception e) {
            log.debug("MCP observability: ingest failed for {}/{}: {}", toolName, operationId, e.getMessage());
        }
    }

    private String extractAiTaskId(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            JsonNode node = root.get("aiTaskId");
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
