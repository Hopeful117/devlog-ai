package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves the external project/story-number reference to Core's Story UUID. */
@Component
public class ResolveEngineeringStoryTool {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public ResolveEngineeringStoryTool(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this(resourceClient, support, null);
    }

    @Autowired
    public ResolveEngineeringStoryTool(
            DevlogResourceClient resourceClient,
            ResourceSupport support,
            McpAuthenticatedPrincipalProvider principalProvider
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "resolve_engineering_story",
            description = "Resolves a DevLog Engineering Story by project slug and story number"
    )
    public String resolveEngineeringStory(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "Repository Story number, for example 223", required = true)
            Integer storyNumber,
            McpSyncRequestContext requestContext
    ) {
        if (storyNumber == null || storyNumber < 1) {
            throw ResourceSupport.invalidParams("storyNumber must be a positive integer");
        }

        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        String notFound = "Story %d not found in project '%s'".formatted(storyNumber, projectSlug);
        if (principalProvider != null) {
            var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
            if (principal != null && principal.bearerToken() != null
                    && !principal.bearerToken().isBlank()) {
                return support.getScoped(
                        () -> resourceClient.resolveStoryByNumberAuthenticated(
                                projectId,
                                storyNumber,
                                principal.bearerToken(),
                                principal.delegationToken() == null ? "" : principal.delegationToken()),
                        notFound);
            }
        }
        return support.getScoped(
                () -> resourceClient.resolveStoryByNumber(projectId, storyNumber),
                notFound);
    }
}
