package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevLogStoryAgentToolTest {
    @Test
    void forwardsTheHighLevelRequestAndReturnsCoreExecution() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        ObjectMapper objectMapper = new ObjectMapper();
        DevLogStoryAgentTool tool = new DevLogStoryAgentTool(client, objectMapper);
        UUID storyId = UUID.randomUUID();
        Map<String, Object> guidance = Map.of("focus", "grounding");
        Map<String, Object> response = Map.of(
                "status", "PROCESSING",
                "aiTaskId", storyId,
                "snapshotId", storyId,
                "result", Map.of(),
                "diagnostics", Map.of("status", "PROCESSING"));

        when(client.executeStoryAgent(
                eq("devlog-ai"), eq("request-1"),
                eq(new DevlogProjectContextClient.StoryAgentRequest(
                        storyId, "engineering-story-context-analysis", List.of("README.md"), guidance))))
                .thenReturn(response);

        String result = tool.execute(
                "devlog-ai", storyId, "engineering-story-context-analysis",
                List.of("README.md"), guidance, "request-1");

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("status").asString()).isEqualTo("PROCESSING");
        assertThat(json.path("aiTaskId").asString()).isEqualTo(storyId.toString());
        assertThat(json.path("snapshotId").asString()).isEqualTo(storyId.toString());
        assertThat(json.path("result").isEmpty()).isTrue();
        assertThat(json.path("diagnostics").path("status").asString()).isEqualTo("PROCESSING");
        verify(client).executeStoryAgent(
                eq("devlog-ai"), eq("request-1"),
                eq(new DevlogProjectContextClient.StoryAgentRequest(
                        storyId, "engineering-story-context-analysis", List.of("README.md"), guidance)));
    }
}
