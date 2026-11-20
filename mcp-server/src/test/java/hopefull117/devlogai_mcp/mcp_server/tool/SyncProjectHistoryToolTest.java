package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncProjectHistoryToolTest {

    private static final String SLUG = "claude-context-optimizer";
    private static final UUID PROJECT_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID SOURCE_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static final String IMPORT_RESULT =
            "{\"commitsImported\":142,\"revision\":\"main\",\"repositoryId\":\""
                    + SOURCE_ID + "\"}";

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);

    private final ResourceSupport support = new ResourceSupport(
            projectContextClient, resourceClient, new ObjectMapper());

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, "Claude Context Optimizer",
                        "Optimizes context for Claude AI", "ACTIVE", List.of()));
        when(resourceClient.listProjectSources(PROJECT_ID)).thenReturn(
                "[{\"id\":\"" + SOURCE_ID + "\",\"active\":true}]");
        when(projectContextClient.getProjectContext("unknown")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));
    }

    @Test
    void shouldImportHistoryForClaudeContextOptimizer() {
        when(adminClient.importProjectHistoryAuthenticated(
                "Bearer test", SOURCE_ID, null)).thenReturn(IMPORT_RESULT);

        SyncProjectHistoryTool tool = new SyncProjectHistoryTool(
                adminClient, support, principalProvider());

        String result = tool.syncProjectHistory(SLUG, null, null);

        assertThat(result).contains("commitsImported").contains("142");
        verify(adminClient).importProjectHistoryAuthenticated("Bearer test", SOURCE_ID, null);
    }

    @Test
    void shouldForwardRevisionParameter() {
        when(adminClient.importProjectHistoryAuthenticated(
                "Bearer test", SOURCE_ID, "feature/my-branch")).thenReturn(
                "{\"commitsImported\":5,\"revision\":\"feature/my-branch\"}");

        SyncProjectHistoryTool tool = new SyncProjectHistoryTool(
                adminClient, support, principalProvider());

        String result = tool.syncProjectHistory(SLUG, "feature/my-branch", null);

        assertThat(result).contains("feature/my-branch");
        verify(adminClient).importProjectHistoryAuthenticated(
                "Bearer test", SOURCE_ID, "feature/my-branch");
    }

    @Test
    void shouldRejectWhenNoPrincipalProvider() {
        SyncProjectHistoryTool tool = new SyncProjectHistoryTool(adminClient, support);

        assertThatThrownBy(() -> tool.syncProjectHistory(SLUG, null, null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    void shouldRejectUnknownProject() {
        SyncProjectHistoryTool tool = new SyncProjectHistoryTool(
                adminClient, support, principalProvider());

        assertThatThrownBy(() -> tool.syncProjectHistory("unknown", null, null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Project 'unknown' not found");
    }

    @Test
    void mapsBackendUnauthorizedAsCoreAuthentication() {
        when(adminClient.importProjectHistoryAuthenticated("Bearer test", SOURCE_ID, null))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        null, null, StandardCharsets.UTF_8));

        SyncProjectHistoryTool tool = new SyncProjectHistoryTool(
                adminClient, support, principalProvider());

        assertThatThrownBy(() -> tool.syncProjectHistory(SLUG, null, null))
                .isInstanceOf(McpError.class)
                .satisfies(exception -> {
                    McpError error = (McpError) exception;
                    assertThat(error.getJsonRpcError().data())
                            .isEqualTo(java.util.Map.of("source", "CORE", "category", "CORE_AUTHENTICATION"));
                });
    }

    private static McpAuthenticatedPrincipalProvider principalProvider() {
        return () -> java.util.Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "test-principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", "Bearer test", ""));
    }
}
