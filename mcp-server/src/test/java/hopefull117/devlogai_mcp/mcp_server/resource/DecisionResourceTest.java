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

class DecisionResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID DECISION_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final DecisionResource resource = new DecisionResource(
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
    void shouldReturnDecisionBelongingToTheProject() {
        String decision = """
                {"id":"%s","projectId":"%s","title":"Use PostgreSQL",
                 "choice":"PostgreSQL","rationale":"ACID","consequences":"Migration"}"""
                .formatted(DECISION_ID, PROJECT_ID);
        when(resourceClient.getDecision(DECISION_ID)).thenReturn(decision);

        String result = resource.getDecision(SLUG, DECISION_ID.toString());

        assertThat(result).contains("\"title\":\"Use PostgreSQL\"");
    }

    @Test
    void shouldRejectUnknownDecision() {
        when(resourceClient.getDecision(DECISION_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resource.getDecision(SLUG, DECISION_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found")
                .hasMessageContaining(DECISION_ID.toString());
    }

    @Test
    void shouldRejectDecisionFromAnotherProject() {
        String foreign = """
                {"id":"%s","projectId":"%s","title":"Foreign decision"}"""
                .formatted(DECISION_ID, "00000000-0000-0000-0000-000000000001");
        when(resourceClient.getDecision(DECISION_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> resource.getDecision(SLUG, DECISION_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("does not belong to project");
    }

    @Test
    void shouldRejectInvalidDecisionIdentifier() {
        assertThatThrownBy(() -> resource.getDecision(SLUG, "commit-hash"))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Invalid decision identifier");
    }

    @Test
    void shouldRejectUnknownProject() {
        assertThatThrownBy(() -> resource.getDecision("unknown", DECISION_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Project 'unknown' not found");
    }
}
