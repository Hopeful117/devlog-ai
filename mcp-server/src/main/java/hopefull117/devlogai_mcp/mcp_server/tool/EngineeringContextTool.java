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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Component
public class EngineeringContextTool {
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
            return objectMapper.writeValueAsString(engineeringContext);
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

        return objectMapper.writeValueAsString(engineeringContext);
    }
}
