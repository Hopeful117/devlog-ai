package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thin MCP adapter for the bounded, read-only DevLog Story Agent capability. */
@Component
@RequiredArgsConstructor
public class DevLogStoryAgentTool {
    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "devlog_story_agent",
            description = "Runs one bounded, read-only DevLog Story Agent execution and returns its structured result or execution diagnostics.")
    public String execute(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Optional Engineering Story UUID", required = false) UUID storyId,
            @McpArg(description = "Intent identifier", required = true) String intent,
            @McpArg(description = "Optional repository file paths", required = false) List<String> files,
            @McpArg(description = "Optional human guidance", required = false) Map<String, Object> guidance,
            @McpArg(description = "Optional idempotency key", required = false) String idempotencyKey) {
        Map<String, Object> execution = client.executeStoryAgent(
                projectSlug,
                idempotencyKey,
                new DevlogProjectContextClient.StoryAgentRequest(storyId, intent, files, guidance));
        try {
            return objectMapper.writeValueAsString(execution);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize DevLog Story Agent response", exception);
        }
    }
}
