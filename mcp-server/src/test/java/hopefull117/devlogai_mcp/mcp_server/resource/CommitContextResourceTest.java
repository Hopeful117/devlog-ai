package hopefull117.devlogai_mcp.mcp_server.resource;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
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

class CommitContextResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID SOURCE_ID =
            UUID.fromString("dddd1111-2222-3333-4444-555555555555");
    private static final String SHA =
            "3cd3723206eae38d518eb696a1dd50c0476264d0";

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final CommitContextResource resource = new CommitContextResource(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient,
                    new ObjectMapper()));

    @BeforeEach
    void resolveProjectAndActiveSource() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(resourceClient.listProjectSources(PROJECT_ID)).thenReturn("""
                [{"id":"%s","projectId":"%s","active":true,
                  "createdAt":"2026-06-01T00:00:00Z"},
                 {"id":"dddd2222-2222-3333-4444-555555555555","projectId":"%s",
                  "active":false,"createdAt":"2026-05-01T00:00:00Z"}]"""
                .formatted(SOURCE_ID, PROJECT_ID, PROJECT_ID));
        when(resourceClient.getCommitContext(SOURCE_ID, SHA)).thenReturn(
                """
                {"commitHash":"%s","language":"java",
                 "candidateAdrReferences":["docs/decisions/ADR-038.md"],
                 "warnings":[]}""".formatted(SHA));
    }

    @Test
    void shouldReturnCommitContextThroughTheActiveSource() {
        String result = resource.getCommitContext(SLUG, SHA);

        assertThat(result)
                .contains("\"commitHash\":\"" + SHA + "\"")
                .contains("candidateAdrReferences");
        verify(resourceClient).getCommitContext(SOURCE_ID, SHA);
    }

    @Test
    void shouldNormalizeShaCase() {
        String result = resource.getCommitContext(SLUG, SHA.toUpperCase());

        assertThat(result).contains(SHA);
        verify(resourceClient).getCommitContext(SOURCE_ID, SHA);
    }

    @Test
    void shouldRejectInvalidCommitSha() {
        assertThatThrownBy(() -> resource.getCommitContext(SLUG, "abc123"))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Invalid commit SHA");
    }

    @Test
    void shouldRejectUnknownCommitAsCleanNotFound() {
        when(resourceClient.getCommitContext(SOURCE_ID, SHA)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resource.getCommitContext(SLUG, SHA))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found in project");
    }

    @Test
    void shouldRejectProjectWithoutActiveSource() {
        when(resourceClient.listProjectSources(PROJECT_ID)).thenReturn("""
                [{"id":"dddd2222-2222-3333-4444-555555555555","projectId":"%s",
                  "active":false,"createdAt":"2026-05-01T00:00:00Z"}]"""
                .formatted(PROJECT_ID));

        assertThatThrownBy(() -> resource.getCommitContext(SLUG, SHA))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("No active repository source");
    }
}
