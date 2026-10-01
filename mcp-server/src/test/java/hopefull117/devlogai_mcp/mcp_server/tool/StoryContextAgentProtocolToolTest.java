package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.service.StoryContextAgentCallbackSigner;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import io.modelcontextprotocol.spec.McpError;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StoryContextAgentProtocolToolTest {
    @Test
    void snapshotAdapterForwardsAuthenticatedPrincipalWithoutApplyingPolicy() {
        DevlogProjectContextClient client = mock(DevlogProjectContextClient.class);
        UUID snapshotId = UUID.randomUUID();
        when(client.getStoryContextAgentSnapshot(snapshotId, "human-1", "HUMAN", "test"))
                .thenReturn(Map.of("id", snapshotId.toString()));
        McpAuthenticatedPrincipalProvider principalProvider = () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal("human-1",
                        McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN, "test"));
        ResourceSupport resourceSupport = mock(ResourceSupport.class);
        when(resourceSupport.getAuthorizedSnapshot(any(), any())).thenAnswer(invocation ->
                ((Supplier<String>) invocation.getArgument(0)).get());
        StoryContextAgentProtocolTool tool = new StoryContextAgentProtocolTool(
                client, mock(StoryContextAgentCallbackSigner.class), new ObjectMapper(), principalProvider,
                resourceSupport);

        String result = tool.getSnapshot(snapshotId);

        assertTrue(result.contains(snapshotId.toString()));
        verify(client).getStoryContextAgentSnapshot(snapshotId, "human-1", "HUMAN", "test");
    }

    @Test
    void snapshotAdapterFailsWithMcpAuthenticationErrorWhenPrincipalIsAbsent() {
        StoryContextAgentProtocolTool tool = new StoryContextAgentProtocolTool(
                mock(DevlogProjectContextClient.class), mock(StoryContextAgentCallbackSigner.class),
                new ObjectMapper(), () -> Optional.empty(), mock(ResourceSupport.class));

        assertThrows(McpError.class, () -> tool.getSnapshot(UUID.randomUUID()));
    }
}
