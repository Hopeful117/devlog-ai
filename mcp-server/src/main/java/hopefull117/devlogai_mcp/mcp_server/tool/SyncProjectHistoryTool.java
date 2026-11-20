package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import io.modelcontextprotocol.spec.McpError;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Triggers a deterministic history import for the active Git source of a
 * registered DevLog project. Required before {@code search_project_history},
 * {@code devlog_story_agent}, or {@code get_engineering_context} can return
 * results for a project that was just registered via {@code register_project}.
 */
@Component
public class SyncProjectHistoryTool {

    private final DevlogAdminClient adminClient;
    private final ResourceSupport support;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public SyncProjectHistoryTool(DevlogAdminClient adminClient, ResourceSupport support) {
        this(adminClient, support, null);
    }

    @Autowired
    public SyncProjectHistoryTool(
            DevlogAdminClient adminClient,
            ResourceSupport support,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this.adminClient = adminClient;
        this.support = support;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "sync_project_history",
            description = "Triggers a Git history import for a DevLog project's active repository source. "
                    + "Must be called after register_project before search_project_history, "
                    + "devlog_story_agent, or get_engineering_context can return results. "
                    + "Returns the import result including the number of commits imported."
    )
    public String syncProjectHistory(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,

            @McpToolParam(description = "Git revision (branch name, tag, or SHA) to import up to. "
                    + "Defaults to the source's configured default branch when not provided.",
                    required = false)
            @Nullable String revision,

            McpSyncRequestContext requestContext
    ) {
        if (principalProvider != null) {
            var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
            if (principal != null && principal.bearerToken() != null && !principal.bearerToken().isBlank()) {
                return doSync(projectSlug, revision, principal.bearerToken(), requestContext);
            }
        }
        throw ResourceSupport.unauthenticated("Authentication is required to sync project history");
    }

    private String doSync(String projectSlug, @Nullable String revision, String authorization,
            McpSyncRequestContext requestContext) {
        var projectId = support.requireProjectId(projectSlug, requestContext);
        var repositoryId = support.requireActiveSourceId(projectId, projectSlug);
        try {
            return adminClient.importProjectHistoryAuthenticated(authorization, repositoryId, revision);
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Project '%s' not found or access denied".formatted(projectSlug), false,
                    "syncing project history");
        }
    }
}
