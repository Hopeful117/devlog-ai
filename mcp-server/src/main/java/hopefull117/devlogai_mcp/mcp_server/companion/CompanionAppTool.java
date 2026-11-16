package hopefull117.devlogai_mcp.mcp_server.companion;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.UUID;

/**
 * MCP App tool — opens the DevLog Companion View inline in Claude Desktop.
 * Returns recent MCP operations as JSON; the View renders them as a live feed.
 */
@Component
public class CompanionAppTool {

    private final DevlogProjectContextClient projectClient;
    private final DevlogCompanionClient companionClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public CompanionAppTool(
            DevlogProjectContextClient projectClient,
            DevlogCompanionClient companionClient,
            ObjectMapper objectMapper) {
        this(projectClient, companionClient, objectMapper, null);
    }

    @Autowired
    public CompanionAppTool(
            DevlogProjectContextClient projectClient,
            DevlogCompanionClient companionClient,
            ObjectMapper objectMapper,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this.projectClient = projectClient;
        this.companionClient = companionClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "open_devlog_companion",
            description = "Opens the DevLog Companion interface showing recent MCP tool activity for the devlog-ai project.",
            metaProvider = DevlogCompanionMetaProvider.class
    )
    public String openDevlogCompanion(McpSyncRequestContext requestContext) {
        if (principalProvider == null) {
            return openDevlogCompanionUnauthenticated();
        }
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        try {
            var project = projectClient.getProjectContextAuthenticated(
                    "devlog-ai",
                    principal.bearerToken(),
                    principal.delegationToken() != null ? principal.delegationToken() : "");
            UUID projectId = project.id();

            String raw = companionClient.listMcpOperationsAuthenticated(
                    projectId, 0, 15,
                    principal.bearerToken(),
                    principal.delegationToken() != null ? principal.delegationToken() : "");

            return buildPayload(raw);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String openDevlogCompanionUnauthenticated() {
        try {
            var project = projectClient.getProjectContext("devlog-ai");
            UUID projectId = project.id();
            String raw = companionClient.listMcpOperations(projectId, 0, 15);
            return buildPayload(raw);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String buildPayload(String raw) throws Exception {
        JsonNode page = objectMapper.readTree(raw);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("projectSlug", "devlog-ai");
        payload.put("totalOperations", page.path("totalOperations").asLong());
        payload.put("asOf", Instant.now().toString());
        ArrayNode ops = objectMapper.createArrayNode();
        JsonNode content = page.path("content");
        if (content.isArray()) {
            for (JsonNode op : content) {
                ObjectNode item = objectMapper.createObjectNode();
                item.put("toolName", op.path("toolName").asText(null));
                item.put("status", op.path("status").asText("UNKNOWN"));
                JsonNode dur = op.path("durationMs");
                if (!dur.isNull() && dur.isNumber()) {
                    item.put("durationMs", dur.asInt());
                } else {
                    item.putNull("durationMs");
                }
                JsonNode started = op.path("startedAt");
                if (!started.isNull() && started.isTextual()) {
                    item.put("startedAt", started.asText());
                } else {
                    item.putNull("startedAt");
                }
                ops.add(item);
            }
        }
        payload.set("operations", ops);
        return objectMapper.writeValueAsString(payload);
    }
}
