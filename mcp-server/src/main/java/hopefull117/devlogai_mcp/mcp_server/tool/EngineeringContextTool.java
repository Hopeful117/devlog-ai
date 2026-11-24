package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

@Component
public class EngineeringContextTool {
    static final int DEFAULT_MAX_CHARS = 25_000;
    private static final String TRUNCATION_WARNING = "RESPONSE_SIZE_TRUNCATED";

    private final DevlogProjectContextClient devlogProjectContextClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public EngineeringContextTool(
            DevlogProjectContextClient devlogProjectContextClient,
            ObjectMapper objectMapper) {
        this(devlogProjectContextClient, objectMapper, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EngineeringContextTool(
            DevlogProjectContextClient devlogProjectContextClient,
            ObjectMapper objectMapper,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this.devlogProjectContextClient = devlogProjectContextClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }
    public String getEngineeringContext(
            @McpArg(
                    description = "Slug identifying the DevLog project",
                    required = true
            )
            String projectSlug,

            @McpArg(
                    description = "Engineering task or investigation for which relevant context is requested",
                    required = true
            )
            String intent,

            @McpArg(
                    description = "Optional file paths to narrow evidence scope",
                    required = false
            )
            List<String> files,

            @McpArg(
                    description = "Optional Engineering Story UUID to scope context to story commit window",
                    required = false
            )
             @Nullable UUID storyId
     ) {
        if (principalProvider == null) {
            EngineeringContext engineeringContext = devlogProjectContextClient.getEngineeringContext(
                    projectSlug, intent, files, storyId);
            return boundedJson(objectMapper.writeValueAsString(engineeringContext));
        }
        return getEngineeringContext(projectSlug, intent, files, storyId, null);
    }

    @McpTool(
            name = "get_engineering_context",
            description = "Returns relevant engineering context for a project and engineering intent"
    )
    public String getEngineeringContext(
            String projectSlug,
            String intent,
            List<String> files,
            @Nullable UUID storyId,
            McpSyncRequestContext requestContext
    ) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        EngineeringContext engineeringContext =
                devlogProjectContextClient.getEngineeringContextAuthenticated(
                        projectSlug,
                        intent,
                        files,
                        storyId,
                        principal.bearerToken(),
                        principal.delegationToken()
                );

        return boundedJson(objectMapper.writeValueAsString(engineeringContext));
    }

    String boundedJson(String json) {
        if (json.length() <= DEFAULT_MAX_CHARS) {
            return json;
        }
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(json);
            trimArray(root, "evidence");
            trimArray(root, "sections");
            json = objectMapper.writeValueAsString(root);
            JsonNode metadataNode = root.path("metadata");
            if (!metadataNode.isMissingNode() && metadataNode.isObject()) {
                ObjectNode metadata = (ObjectNode) metadataNode;
                JsonNode warningsNode = metadata.path("warnings");
                ArrayNode warnings = warningsNode.isArray()
                        ? (ArrayNode) warningsNode
                        : metadata.putArray("warnings");
                warnings.add(TRUNCATION_WARNING);
                json = objectMapper.writeValueAsString(root);
            }
        } catch (Exception e) {
            return json.substring(0, DEFAULT_MAX_CHARS) + "\n...[response truncated]";
        }
        return json;
    }

    private void trimArray(ObjectNode root, String field) throws Exception {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return;
        }
        ArrayNode array = (ArrayNode) node;
        while (objectMapper.writeValueAsString(root).length() > DEFAULT_MAX_CHARS && array.size() > 0) {
            array.remove(array.size() - 1);
        }
    }
}
