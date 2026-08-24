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
import static org.mockito.Mockito.when;

class InsightResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID INSIGHT_ID =
            UUID.fromString("aaaa1111-2222-3333-4444-555555555555");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final InsightResource resource = new InsightResource(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient,
                    new ObjectMapper()));

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(projectContextClient.getProjectContext("unknown")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnActiveInsightOfTheProject() {
        String insights = """
                [{"id":"%s","projectId":"%s","title":"Layered architecture",
                  "type":"ARCHITECTURAL","severity":"INFO"}]"""
                .formatted(INSIGHT_ID, PROJECT_ID);
        when(resourceClient.listProjectInsights(PROJECT_ID)).thenReturn(insights);

        String result = resource.getInsight(SLUG, INSIGHT_ID.toString());

        assertThat(result)
                .contains("\"title\":\"Layered architecture\"")
                .contains("\"severity\":\"INFO\"");
    }

    @Test
    void shouldNeverReturnInsightAbsentFromActiveKnowledge() {
        // The project-scoped ACTIVE-only list is the source of truth: a
        // superseded or archived insight (absent from the list) behaves as an
        // unknown artifact and can never be served.
        when(resourceClient.listProjectInsights(PROJECT_ID)).thenReturn("[]");

        assertThatThrownBy(() -> resource.getInsight(SLUG, INSIGHT_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found in project");
    }

    @Test
    void shouldRejectInvalidInsightIdentifier() {
        assertThatThrownBy(() -> resource.getInsight(SLUG, "git:abc123"))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Invalid insight identifier");
    }
}
