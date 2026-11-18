package hopefull117.devlogai_mcp.mcp_server;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectNote;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ProjectContextResource;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectContextResourceTest {
    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProjectContextResource resource =
            new ProjectContextResource(projectContextClient, objectMapper);

    private ProjectContext sampleContext(UUID projectId) {
        return new ProjectContext(projectId, "devlog-ai", "devlog-ai",
                "An AI-powered documentation assistant.", "ACTIVE", List.of());
    }

    @Test
    void shouldReturnProjectContextForSlug_withNotes() {
        UUID projectId =
                UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

        ProjectNote note = new ProjectNote(
                UUID.randomUUID(), "GOAL", "Medium-term goal",
                "Improve semantic usefulness for humans and agents.", "ACTIVE", Instant.now());

        ProjectContext context = new ProjectContext(
                projectId,
                "devlog-ai",
                "devlog-ai",
                "An AI-powered documentation assistant.",
                "ACTIVE",
                List.of(note)
        );

        when(projectContextClient.getProjectContext("devlog-ai"))
                .thenReturn(context);

        String result = resource.getProjectContext("devlog-ai");

        assertThat(result)
                .contains("\"id\":\"f3d56247-aada-4a76-982b-e6802c0b309c\"")
                .contains("\"name\":\"devlog-ai\"")
                .contains("\"slug\":\"devlog-ai\"")
                .contains("\"description\":\"An AI-powered documentation assistant.\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"type\":\"GOAL\"")
                .contains("\"title\":\"Medium-term goal\"")
                .contains("\"contentMarkdown\":\"Improve semantic usefulness for humans and agents.\"")
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"updatedAt\"");

        verify(projectContextClient)
                .getProjectContext("devlog-ai");
    }

    @Test
    void shouldReturnProjectContextForSlug_withoutNotes() {
        UUID projectId =
                UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

        ProjectContext context = new ProjectContext(
                projectId,
                "devlog-ai",
                "devlog-ai",
                "An AI-powered documentation assistant.",
                "ACTIVE",
                List.of()

        );

        when(projectContextClient.getProjectContext("devlog-ai"))
                .thenReturn(context);

        String result = resource.getProjectContext("devlog-ai");

        assertThat(result)
                .contains("\"id\":\"f3d56247-aada-4a76-982b-e6802c0b309c\"")
                .contains("\"name\":\"devlog-ai\"")
                .contains("\"slug\":\"devlog-ai\"")
                .contains("\"description\":\"An AI-powered documentation assistant.\"")
                .contains("\"status\":\"ACTIVE\"");

        verify(projectContextClient)
                .getProjectContext("devlog-ai");
    }

    @Test
    void shouldUseAuthenticatedEndpointWhenPrincipalProviderIsConfigured() {
        UUID projectId = UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
        ProjectContext context = sampleContext(projectId);

        McpAuthenticatedPrincipalProvider principalProvider = mock(McpAuthenticatedPrincipalProvider.class);
        McpAuthenticatedPrincipalProvider.McpPrincipal principal =
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "agent-id", McpAuthenticatedPrincipalProvider.PrincipalKind.AI_AGENT,
                        "http://issuer", "Bearer my-token", null);
        when(principalProvider.currentPrincipal()).thenReturn(Optional.of(principal));
        when(projectContextClient.getProjectContextAuthenticated(eq("devlog-ai"), eq("Bearer my-token"), eq("")))
                .thenReturn(context);

        ProjectContextResource authenticatedResource =
                new ProjectContextResource(projectContextClient, objectMapper, principalProvider);

        String result = authenticatedResource.getProjectContext("devlog-ai");

        assertThat(result).contains("\"id\":\"f3d56247-aada-4a76-982b-e6802c0b309c\"");
        verify(projectContextClient).getProjectContextAuthenticated("devlog-ai", "Bearer my-token", "");
        verify(projectContextClient, never()).getProjectContext(any());
    }

    @Test
    void shouldFallBackToUnauthenticatedWhenPrincipalHasNoToken() {
        UUID projectId = UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
        ProjectContext context = sampleContext(projectId);

        McpAuthenticatedPrincipalProvider principalProvider = mock(McpAuthenticatedPrincipalProvider.class);
        when(principalProvider.currentPrincipal()).thenReturn(Optional.empty());
        when(projectContextClient.getProjectContext("devlog-ai")).thenReturn(context);

        ProjectContextResource noTokenResource =
                new ProjectContextResource(projectContextClient, objectMapper, principalProvider);

        String result = noTokenResource.getProjectContext("devlog-ai");

        assertThat(result).contains("\"id\":\"f3d56247-aada-4a76-982b-e6802c0b309c\"");
        verify(projectContextClient).getProjectContext("devlog-ai");
        verify(projectContextClient, never()).getProjectContextAuthenticated(any(), any(), any());
    }
}