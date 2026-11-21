package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import io.modelcontextprotocol.spec.McpError;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StoryContextAgentProtocolToolTest {
    @Test
    void reservesTaskBindingWithoutDispatchingProviderWork() throws Exception {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        UUID storyId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        when(client.reserveStoryContextAgentTaskBinding(
                eq("devlog-ai"), eq(storyId), eq("Bearer test"), eq("q1-prepare-1"), eq(""),
                eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("story.md"), Map.of()))))
                .thenReturn(Map.of("aiTaskId", taskId, "provider", "chatgpt", "status", "CREATED"));
        StoryContextAgentProtocolTool tool = new StoryContextAgentProtocolTool(
                client, new ObjectMapper(), () -> Optional.of(
                        new McpAuthenticatedPrincipalProvider.McpPrincipal(
                                "agent-1", McpAuthenticatedPrincipalProvider.PrincipalKind.AI_AGENT,
                                "test", "Bearer test", "")), mock(ResourceSupport.class));

        String result = tool.prepareTaskBinding("devlog-ai", storyId, List.of("story.md"),
                "engineering-story-context-analysis", "q1-prepare-1", null);

        assertTrue(result.contains(taskId.toString()));
        verify(client).reserveStoryContextAgentTaskBinding(
                eq("devlog-ai"), eq(storyId), eq("Bearer test"), eq("q1-prepare-1"), eq(""),
                eq(new DevlogProjectContextClient.SubmitRequest(
                        "engineering-story-context-analysis", List.of("story.md"), Map.of())));
        verify(client, never()).submitStoryContextAgentTask(any(), any(), any(), any(), any(), any());
    }

    @Test
    void snapshotAdapterForwardsAuthenticatedPrincipalWithoutApplyingPolicy() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        UUID snapshotId = UUID.randomUUID();
        when(client.getStoryContextAgentSnapshot(snapshotId, "human-1", "HUMAN", "test", "Bearer test", ""))
                .thenReturn(Map.of("id", snapshotId.toString()));
        McpAuthenticatedPrincipalProvider principalProvider = () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal("human-1",
                        McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN, "test", "Bearer test", ""));
        ResourceSupport resourceSupport = mock(ResourceSupport.class);
        when(resourceSupport.getAuthorizedSnapshot(any(), any())).thenAnswer(invocation ->
                ((Supplier<String>) invocation.getArgument(0)).get());
        StoryContextAgentProtocolTool tool = new StoryContextAgentProtocolTool(
                client, new ObjectMapper(), principalProvider,
                resourceSupport);

        String result = tool.getSnapshot(snapshotId);

        assertTrue(result.contains(snapshotId.toString()));
        verify(client).getStoryContextAgentSnapshot(snapshotId, "human-1", "HUMAN", "test", "Bearer test", "");
    }

    @Test
    void snapshotAdapterFailsWithMcpAuthenticationErrorWhenPrincipalIsAbsent() {
        StoryContextAgentProtocolTool tool = new StoryContextAgentProtocolTool(
                mock(DevlogProjectContextClient.class), new ObjectMapper(),
                () -> Optional.empty(), mock(ResourceSupport.class));

        assertThrows(McpError.class, () -> tool.getSnapshot(UUID.randomUUID()));
    }
}
