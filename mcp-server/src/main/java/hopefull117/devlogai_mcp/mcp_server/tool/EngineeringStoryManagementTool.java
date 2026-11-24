package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for Engineering Story lifecycle management.
 *
 * <p>Covers the four workflow steps that were previously only available through
 * the DevLog web UI: register, list, start implementation, and complete
 * implementation. Every mutating operation requires an authenticated human
 * principal so the tool call approval in the MCP client serves as the
 * confirmation gate.
 */
@Component
public class EngineeringStoryManagementTool {

    private final DevlogAdminClient adminClient;
    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final ObjectMapper objectMapper;

    @Autowired
    public EngineeringStoryManagementTool(
            DevlogAdminClient adminClient,
            DevlogResourceClient resourceClient,
            ResourceSupport support,
            McpAuthenticatedPrincipalProvider principalProvider,
            ObjectMapper objectMapper
    ) {
        this.adminClient = adminClient;
        this.resourceClient = resourceClient;
        this.support = support;
        this.principalProvider = principalProvider;
        this.objectMapper = objectMapper;
    }

    @McpTool(
            name = "register_engineering_story",
            description = "Registers a new Engineering Story in a DevLog project. "
                    + "Requires human approval (tool-call confirmation). "
                    + "The storyPath must be the docs/stories/NNNN-… path relative to the repository root."
    )
    public String registerEngineeringStory(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "Human-readable story title, e.g. 'Borner la réponse get_engineering_context'", required = true)
            String title,
            @McpToolParam(description = "Repository story number, e.g. 230", required = true)
            Integer storyNumber,
            @McpToolParam(description = "Path to the story document relative to the repository root, e.g. docs/stories/0230-my-story/story.md", required = true)
            String storyPath,
            McpSyncRequestContext requestContext
    ) {
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        var principal = requirePrincipal(requestContext);
        try {
            return adminClient.registerStoryAuthenticated(
                    principal.bearerToken(),
                    projectId,
                    Map.of(
                            "projectId", projectId.toString(),
                            "title", title,
                            "storyNumber", storyNumber,
                            "storyPath", storyPath
                    )
            );
        } catch (io.modelcontextprotocol.spec.McpError e) {
            throw e;
        } catch (Exception e) {
            throw CoreCallPolicy.map(
                    e, "Failed to register story %d in project '%s'".formatted(storyNumber, projectSlug),
                    false, "registering engineering story");
        }
    }

    @McpTool(
            name = "list_project_stories",
            description = "Returns all Engineering Stories for a DevLog project"
    )
    public String listProjectStories(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            McpSyncRequestContext requestContext
    ) {
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        var principal = requirePrincipal(requestContext);
        try {
            return adminClient.listProjectStoriesAuthenticated(principal.bearerToken(), projectId);
        } catch (io.modelcontextprotocol.spec.McpError e) {
            throw e;
        } catch (Exception e) {
            throw CoreCallPolicy.map(
                    e, "Failed to list stories for project '%s'".formatted(projectSlug),
                    false, "listing project stories");
        }
    }

    @McpTool(
            name = "start_story_implementation",
            description = "Transitions an Engineering Story to IN_PROGRESS, recording the base commit. "
                    + "Requires human approval (tool-call confirmation). "
                    + "baseCommit must be a 40- or 64-character hexadecimal Git SHA."
    )
    public String startStoryImplementation(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "Repository story number", required = true)
            Integer storyNumber,
            @McpToolParam(description = "Git commit SHA at which implementation begins (40 or 64 hex chars)", required = true)
            String baseCommit,
            McpSyncRequestContext requestContext
    ) {
        String validatedCommit = support.requireCommitSha(baseCommit);
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        var principal = requirePrincipal(requestContext);
        UUID storyId = resolveStoryId(projectId, storyNumber, principal.bearerToken(),
                principal.delegationToken(), projectSlug);
        try {
            return adminClient.startStoryImplementationAuthenticated(
                    principal.bearerToken(),
                    projectId,
                    storyId,
                    Map.of("baseCommit", validatedCommit)
            );
        } catch (io.modelcontextprotocol.spec.McpError e) {
            throw e;
        } catch (Exception e) {
            throw CoreCallPolicy.map(
                    e, "Failed to start story %d in project '%s'".formatted(storyNumber, projectSlug),
                    false, "starting story implementation");
        }
    }

    @McpTool(
            name = "complete_story_implementation",
            description = "Transitions an Engineering Story to COMPLETED, recording the target commit. "
                    + "Requires human approval (tool-call confirmation). "
                    + "targetCommit must be a 40- or 64-character hexadecimal Git SHA."
    )
    public String completeStoryImplementation(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "Repository story number", required = true)
            Integer storyNumber,
            @McpToolParam(description = "Git commit SHA at which implementation ends (40 or 64 hex chars)", required = true)
            String targetCommit,
            McpSyncRequestContext requestContext
    ) {
        String validatedCommit = support.requireCommitSha(targetCommit);
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        var principal = requirePrincipal(requestContext);
        UUID storyId = resolveStoryId(projectId, storyNumber, principal.bearerToken(),
                principal.delegationToken(), projectSlug);
        try {
            return adminClient.completeStoryImplementationAuthenticated(
                    principal.bearerToken(),
                    projectId,
                    storyId,
                    Map.of("targetCommit", validatedCommit)
            );
        } catch (io.modelcontextprotocol.spec.McpError e) {
            throw e;
        } catch (Exception e) {
            throw CoreCallPolicy.map(
                    e, "Failed to complete story %d in project '%s'".formatted(storyNumber, projectSlug),
                    false, "completing story implementation");
        }
    }

    private McpAuthenticatedPrincipalProvider.McpPrincipal requirePrincipal(
            McpSyncRequestContext requestContext) {
        return principalProvider.currentPrincipal(requestContext)
                .filter(p -> p.bearerToken() != null && !p.bearerToken().isBlank())
                .orElseThrow(() -> ResourceSupport.unauthenticated(
                        "Authenticated bearer token is required"));
    }

    private UUID resolveStoryId(UUID projectId, Integer storyNumber,
            String token, String delegation, String projectSlug) {
        String storyJson;
        try {
            storyJson = resourceClient.resolveStoryByNumberAuthenticated(
                    projectId, storyNumber, token,
                    delegation != null ? delegation : "");
        } catch (io.modelcontextprotocol.spec.McpError e) {
            throw e;
        } catch (Exception e) {
            throw ResourceSupport.notFound(
                    "Story %d not found in project '%s'".formatted(storyNumber, projectSlug));
        }
        JsonNode node = objectMapper.readTree(storyJson);
        String id = node.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw ResourceSupport.notFound(
                    "Story %d not found in project '%s'".formatted(storyNumber, projectSlug));
        }
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ResourceSupport.invalidParams("Invalid story id: '%s'".formatted(id));
        }
    }
}
