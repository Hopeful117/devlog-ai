package hopefull117.devlogai_mcp.mcp_server.companion;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanionErrorSanitizationTest {

    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String SECRET = "backend payload with secret details";

    @Test
    void companionAppDoesNotReturnBackendExceptionMessage() {
        DevlogProjectContextClient projectClient = mock(DevlogProjectContextClient.class);
        DevlogCompanionClient companionClient = mock(DevlogCompanionClient.class);
        when(projectClient.getProjectContextAuthenticated("devlog-ai", "Bearer token", "delegation"))
                .thenReturn(new ProjectContext(PROJECT_ID, "devlog-ai", "DevLog", "", "ACTIVE", List.of()));
        when(companionClient.listMcpOperationsAuthenticated(PROJECT_ID, 0, 15,
                "Bearer token", "delegation")).thenThrow(new RuntimeException(SECRET));

        CompanionAppTool tool = new CompanionAppTool(projectClient, companionClient, new ObjectMapper(), principal());

        String result = tool.openDevlogCompanion("devlog-ai", null);

        assertThat(result).isEqualTo("{\"error\":\"COMPANION_UNAVAILABLE\"}");
        assertThat(result).doesNotContain(SECRET);
    }

    @Test
    void contextTraceDoesNotReturnBackendExceptionMessage() {
        DevlogCompanionClient companionClient = mock(DevlogCompanionClient.class);
        when(companionClient.getContextTrace(PROJECT_ID, "instance", "operation",
                "Bearer token", "delegation")).thenThrow(new RuntimeException(SECRET));

        GetContextTraceTool tool = new GetContextTraceTool(companionClient, new ObjectMapper(), principal());

        String result = tool.getContextTrace(PROJECT_ID, "instance", "operation", null);

        assertThat(result).isEqualTo("{\"error\":\"CONTEXT_TRACE_UNAVAILABLE\"}");
        assertThat(result).doesNotContain(SECRET);
    }

    @Test
    void contextTracePassesTheBearerValueWithoutDuplicatingItsScheme() {
        DevlogCompanionClient companionClient = mock(DevlogCompanionClient.class);
        when(companionClient.getContextTrace(PROJECT_ID, "instance", "operation",
                "Bearer token", "delegation")).thenReturn("{\"trace\":true}");

        GetContextTraceTool tool = new GetContextTraceTool(companionClient, new ObjectMapper(), principal());

        String result = tool.getContextTrace(PROJECT_ID, "instance", "operation", null);

        assertThat(result).contains("\"trace\" : true");
    }

    private static McpAuthenticatedPrincipalProvider principal() {
        return () -> Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", "Bearer token", "delegation"));
    }
}
