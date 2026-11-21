package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncProjectStoriesToolTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID = UUID.randomUUID();

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);
    private final ResourceSupport support = new ResourceSupport(
            projectContextClient, resourceClient, new ObjectMapper());

    @Test
    void synchronizesStoriesThroughAuthenticatedCore() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "digest", "ACTIVE", List.of()));
        String report = "{\"createdCount\":1,\"revision\":\"abc123\"}";
        when(adminClient.synchronizeProjectStoriesAuthenticated("Bearer test", PROJECT_ID, "main"))
                .thenReturn(report);

        String result = new SyncProjectStoriesTool(adminClient, support, principalProvider())
                .syncProjectStories(SLUG, "main", null);

        assertThat(result).contains("createdCount");
        verify(adminClient).synchronizeProjectStoriesAuthenticated("Bearer test", PROJECT_ID, "main");
    }

    @Test
    void requiresAuthentication() {
        assertThatThrownBy(() -> new SyncProjectStoriesTool(adminClient, support)
                .syncProjectStories(SLUG, null, null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Authentication is required");
    }

    private static McpAuthenticatedPrincipalProvider principalProvider() {
        return () -> java.util.Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "test-principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", "Bearer test", ""));
    }
}
