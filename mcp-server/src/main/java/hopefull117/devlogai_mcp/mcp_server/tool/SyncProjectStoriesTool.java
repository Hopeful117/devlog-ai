package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/** Explicit MCP fallback for repository Story catalog synchronization. */
@Component
public class SyncProjectStoriesTool {

    private final DevlogAdminClient adminClient;
    private final ResourceSupport support;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public SyncProjectStoriesTool(DevlogAdminClient adminClient, ResourceSupport support) {
        this(adminClient, support, null);
    }

    @Autowired
    public SyncProjectStoriesTool(
            DevlogAdminClient adminClient,
            ResourceSupport support,
            McpAuthenticatedPrincipalProvider principalProvider
    ) {
        this.adminClient = adminClient;
        this.support = support;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "sync_project_stories",
            description = "Synchronizes the Engineering Story catalog from the active repository. "
                    + "Use as a bounded MCP fallback when automatic repository synchronization has not run."
    )
    public String syncProjectStories(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "Git revision to synchronize up to; defaults to the source branch",
                    required = false)
            @Nullable String revision,
            McpSyncRequestContext requestContext
    ) {
        if (principalProvider == null) {
            throw ResourceSupport.unauthenticated("Authentication is required to sync project Stories");
        }
        var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
        if (principal == null || principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authentication is required to sync project Stories");
        }
        var projectId = support.requireProjectId(projectSlug, requestContext);
        try {
            return adminClient.synchronizeProjectStoriesAuthenticated(
                    principal.bearerToken(), projectId, revision);
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Project '%s' not found or access denied".formatted(projectSlug), false,
                    "synchronizing project Stories");
        }
    }
}
