package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.StoryContextAgentCallbackSigner;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
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
    private final StoryContextAgentCallbackSigner callbackSigner;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final ResourceSupport resourceSupport;

    @McpTool(name = "story_context_agent_get_projection",
            description = "Read the Core-owned versioned Story Context Agent projection without creating a task")
    public String getProjection(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) UUID storyId,
            @McpArg(description = "Optional repository file paths", required = false) List<String> files,
            @McpArg(description = "Intent identifier", required = true) String intent) {
        return write(client.getStoryContextAgentProjection(projectSlug, storyId, files, intent));
    }

    @McpTool(name = "story_context_agent_submit_task",
            description = "Submit an asynchronous Story Context Agent task and return aiTaskId/snapshotId")
    public String submitTask(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) UUID storyId,
            @McpArg(description = "Optional repository file paths", required = false) List<String> files,
            @McpArg(description = "Intent identifier", required = true) String intent,
            @McpArg(description = "Optional human guidance", required = false) Map<String, Object> guidance) {
        return write(client.submitStoryContextAgentTask(projectSlug, storyId,
                new DevlogProjectContextClient.SubmitRequest(intent, files, guidance)));
    }

    @McpTool(name = "story_context_agent_callback",
            description = "Forward a validated Story Context Agent callback to Core")
    public String callback(
            @McpArg(description = "AI task and snapshot UUID", required = true) UUID aiTaskId,
            @McpArg(description = "Versioned callback payload", required = true) Map<String, Object> request) {
        String signature = callbackSigner.sign(aiTaskId, request);
        return write(client.callbackStoryContextAgentTask(aiTaskId, signature, request));
    }

    @McpTool(name = "story_context_agent_get_snapshot",
            description = "Read the immutable Story Context Agent task snapshot and status")
    public String getSnapshot(
            @McpArg(description = "AI task and snapshot UUID", required = true) UUID aiTaskId) {
        var principal = principalProvider.currentPrincipal()
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        return resourceSupport.getAuthorizedSnapshot(
                () -> write(client.getStoryContextAgentSnapshot(aiTaskId, principal.principalId(),
                        principal.kind().name(), principal.authenticationSource())),
                "Story Context snapshot '%s' not found".formatted(aiTaskId));
    }

    @McpTool(name = "story_context_agent_follow_up",
            description = "Submit one bounded follow-up question against an authorized Story Context snapshot")
    public String followUp(
            @McpArg(description = "Initial Story Context snapshot UUID", required = true) UUID snapshotId,
            @McpArg(description = "Bounded follow-up question", required = true) String question,
            @McpArg(description = "Optional bounded guidance", required = false) Map<String, Object> guidance) {
        var principal = principalProvider.currentPrincipal()
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        return resourceSupport.submitAuthorizedSnapshot(
                () -> write(client.submitStoryContextAgentFollowUp(snapshotId, principal.principalId(),
                        principal.kind().name(), principal.authenticationSource(),
                        new DevlogProjectContextClient.FollowUpRequest(question, guidance))),
                "Story Context snapshot '%s' not found".formatted(snapshotId));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize Story Context Agent protocol response", exception);
        }
    }
}
