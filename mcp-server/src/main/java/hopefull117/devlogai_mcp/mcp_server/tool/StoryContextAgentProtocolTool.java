package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thin MCP adapters for the asynchronous Story Context Agent protocol. */
@Component
@RequiredArgsConstructor
public class StoryContextAgentProtocolTool {
    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final ResourceSupport resourceSupport;

    public String getProjection(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) UUID storyId,
            @McpArg(description = "Optional repository file paths", required = false) List<String> files,
            @McpArg(description = "Intent identifier", required = true) String intent) {
        return getProjection(projectSlug, storyId, files, intent, null);
    }

    @McpTool(name = "story_context_agent_get_projection",
            description = "Read the Core-owned versioned Story Context Agent projection without creating a task")
    public String getProjection(
            String projectSlug,
            UUID storyId,
            List<String> files,
            String intent,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        requireBearer(principal);
        return write(client.getStoryContextAgentProjectionAuthenticated(projectSlug, storyId, files, intent,
                principal.bearerToken(), principal.delegationToken()));
    }

    @McpTool(name = "story_context_agent_submit_task",
            description = "Submit an asynchronous Story Context Agent task and return aiTaskId/snapshotId")
    public String submitTask(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) UUID storyId,
            @McpArg(description = "Optional repository file paths", required = false) List<String> files,
            @McpArg(description = "Intent identifier", required = true) String intent,
            @McpArg(description = "Optional human guidance", required = false) Map<String, Object> guidance,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        requireBearer(principal);
        return write(client.submitStoryContextAgentTask(projectSlug, storyId,
                principal.bearerToken(),
                null,
                principal.delegationToken(),
                new DevlogProjectContextClient.SubmitRequest(intent, files, guidance)));
    }

    public String getSnapshot(
            @McpArg(description = "AI task and snapshot UUID", required = true) UUID aiTaskId) {
        return getSnapshot(aiTaskId, null);
    }

    @McpTool(name = "story_context_agent_get_snapshot_authenticated",
            description = "Read an authorized Story Context Agent snapshot using the MCP bearer token")
    public String getSnapshot(
            @McpArg(description = "AI task and snapshot UUID", required = true) UUID aiTaskId,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        requireBearer(principal);
        return resourceSupport.getAuthorizedSnapshot(
                () -> write(client.getStoryContextAgentSnapshot(aiTaskId, principal.principalId(),
                        principal.kind().name(), principal.authenticationSource(), principal.bearerToken(),
                        principal.delegationToken())),
                "Story Context snapshot '%s' not found".formatted(aiTaskId));
    }

    public String followUp(
            @McpArg(description = "Initial Story Context snapshot UUID", required = true) UUID snapshotId,
            @McpArg(description = "Bounded follow-up question", required = true) String question,
            @McpArg(description = "Optional bounded guidance", required = false) Map<String, Object> guidance) {
        return followUp(snapshotId, question, guidance, null);
    }

    @McpTool(name = "story_context_agent_follow_up_authenticated",
            description = "Submit an authorized follow-up using the MCP bearer token")
    public String followUp(
            @McpArg(description = "Initial Story Context snapshot UUID", required = true) UUID snapshotId,
            @McpArg(description = "Bounded follow-up question", required = true) String question,
            @McpArg(description = "Optional bounded guidance", required = false) Map<String, Object> guidance,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        requireBearer(principal);
        return resourceSupport.submitAuthorizedSnapshot(
                () -> write(client.submitStoryContextAgentFollowUp(snapshotId, principal.principalId(),
                        principal.kind().name(), principal.authenticationSource(), principal.bearerToken(),
                        principal.delegationToken(),
                        new DevlogProjectContextClient.FollowUpRequest(question, guidance))),
                "Story Context snapshot '%s' not found".formatted(snapshotId));
    }

    private void requireBearer(McpAuthenticatedPrincipalProvider.McpPrincipal principal) {
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize Story Context Agent protocol response", exception);
        }
    }
}
