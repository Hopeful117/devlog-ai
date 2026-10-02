package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.Nullable;
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
            @McpArg(description = "Optional Engineering Story UUID", required = false) @Nullable UUID storyId,
            @McpArg(description = "Natural-language investigation objective", required = true) String intent,
            @McpArg(description = "Optional repository file paths", required = false) @Nullable List<String> files,
            @McpArg(description = "Optional human guidance", required = false) @Nullable Map<String, Object> guidance,
            @McpArg(description = "Optional idempotency key", required = false) @Nullable String idempotencyKey) {
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
