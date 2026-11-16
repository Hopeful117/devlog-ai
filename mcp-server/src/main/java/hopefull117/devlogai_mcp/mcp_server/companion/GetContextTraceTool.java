package hopefull117.devlogai_mcp.mcp_server.companion;

import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * MCP tool that returns a Context Trace for a specific DevLog MCP operation.
 * Provides business observability: sources, context assembly, accounting, diagnostics.
 */
@Component
public class GetContextTraceTool {

    private final DevlogCompanionClient companionClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public GetContextTraceTool(DevlogCompanionClient companionClient,
                               ObjectMapper objectMapper,
                               McpAuthenticatedPrincipalProvider principalProvider) {
        this.companionClient = companionClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "get_devlog_context_trace",
            description = "Returns the DevLog Context Trace for a specific MCP operation: "
                    + "the sources selected, context assembled, accounting, freshness, and diagnostics. "
                    + "Use instanceId and operationId from a previous DevLog MCP operation to inspect what DevLog prepared.")
    public String getContextTrace(
            @McpArg(description = "DevLog project UUID", required = true) UUID projectId,
            @McpArg(description = "MCP instance ID from the operation", required = true) String instanceId,
            @McpArg(description = "MCP operation ID from the operation", required = true) String operationId,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        try {
            String raw = companionClient.getContextTrace(
                    projectId, instanceId, operationId,
                    "Bearer " + principal.bearerToken(),
                    principal.delegationToken());
            // Return pretty-printed JSON
            Object parsed = objectMapper.readValue(raw, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(java.util.Map.of(
                        "error", "CONTEXT_TRACE_UNAVAILABLE",
                        "message", e.getMessage() != null ? e.getMessage() : "Context trace not found"));
            } catch (Exception ignored) {
                return "{\"error\":\"CONTEXT_TRACE_UNAVAILABLE\"}";
            }
        }
    }
}
