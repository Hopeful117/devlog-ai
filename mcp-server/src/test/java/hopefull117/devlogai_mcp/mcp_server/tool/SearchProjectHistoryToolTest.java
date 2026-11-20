package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchProjectHistoryToolTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final SearchProjectHistoryTool tool = new SearchProjectHistoryTool(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient,
                    new ObjectMapper()));

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE",
                        List.of()));
        when(projectContextClient.getProjectContext("unknown")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnSearchResultsJson() {
        String payload = """
                {"query":"markdown rendering","totalMatches":1,"truncated":false,
                 "results":[{"commitSha":"%s","subject":"fix project note markdown preview",
                   "relevance":25,"matches":[{"matchedOn":"COMMIT_MESSAGE",
                   "matchedValue":"fix project note markdown preview"}],
                   "resource":"devlog://projects/devlog-ai/commits/%s"}]}"""
                .formatted("4c4180000f226acb40e88f484e0585ff2a5568bd",
                        "4c4180000f226acb40e88f484e0585ff2a5568bd");
        when(resourceClient.searchProjectHistory(PROJECT_ID, "Markdown Rendering", null))
                .thenReturn(payload);

        String result = tool.searchProjectHistory(SLUG, "  Markdown Rendering  ", null, null);

        assertThat(result)
                .contains("\"totalMatches\":1")
                .contains("fix project note markdown preview")
                .contains("devlog://projects/devlog-ai/commits/");
        verify(resourceClient).searchProjectHistory(PROJECT_ID, "Markdown Rendering", null);
    }

    @Test
    void shouldForwardExplicitLimit() {
        when(resourceClient.searchProjectHistory(PROJECT_ID, "engine", 5))
                .thenReturn("{\"results\":[]}");

        tool.searchProjectHistory(SLUG, "engine", 5, null);

        verify(resourceClient).searchProjectHistory(PROJECT_ID, "engine", 5);
    }

    @Test
    void shouldForwardRequestScopedBearerAndDelegation() {
        McpAuthenticatedPrincipalProvider.McpPrincipal principal =
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "request-principal",
                        McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "request",
                        "Bearer request-token",
                        "delegation-token");
        McpAuthenticatedPrincipalProvider principalProvider = new McpAuthenticatedPrincipalProvider() {
            @Override
            public Optional<McpPrincipal> currentPrincipal() {
                return Optional.empty();
            }

            @Override
            public Optional<McpPrincipal> currentPrincipal(
                    org.springframework.ai.mcp.annotation.context.McpSyncRequestContext requestContext) {
                return Optional.of(principal);
            }
        };
        ResourceSupport authenticatedSupport = new ResourceSupport(
                projectContextClient, resourceClient, new ObjectMapper(), principalProvider);
        SearchProjectHistoryTool authenticatedTool = new SearchProjectHistoryTool(
                resourceClient, authenticatedSupport, principalProvider);
        org.springframework.ai.mcp.annotation.context.McpSyncRequestContext requestContext =
                mock(org.springframework.ai.mcp.annotation.context.McpSyncRequestContext.class);
        when(projectContextClient.getProjectContextAuthenticated(
                SLUG, "Bearer request-token", "delegation-token"))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(resourceClient.searchProjectHistoryAuthenticated(
                PROJECT_ID, "engine", null, "Bearer request-token", "delegation-token"))
                .thenReturn("{\"results\":[]}");

        authenticatedTool.searchProjectHistory(SLUG, "engine", null, requestContext);

        verify(projectContextClient).getProjectContextAuthenticated(
                SLUG, "Bearer request-token", "delegation-token");
        verify(resourceClient).searchProjectHistoryAuthenticated(
                PROJECT_ID, "engine", null, "Bearer request-token", "delegation-token");
    }

    @Test
    void shouldRejectBlankQuery() {
        assertThatThrownBy(() -> tool.searchProjectHistory(SLUG, "   ", null, null))
                .isInstanceOf(McpError.class);
    }

    @Test
    void shouldRejectInvalidLimit() {
        assertThatThrownBy(() -> tool.searchProjectHistory(SLUG, "engine", 0, null))
                .isInstanceOf(McpError.class);
        assertThatThrownBy(() -> tool.searchProjectHistory(SLUG, "engine", 101, null))
                .isInstanceOf(McpError.class);
    }

    @Test
    void shouldRejectUnknownProject() {
        assertThatThrownBy(() -> tool.searchProjectHistory("unknown", "engine", null, null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Project 'unknown' not found");
    }
}
