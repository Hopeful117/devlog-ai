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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FreshnessResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final FreshnessResource resource = new FreshnessResource(
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
    void shouldReturnFreshnessProjectionOfTheProject() {
        String payload = """
                {"version":"project-freshness-summary-v1","projectId":"%s",
                 "checkedSources":[{"source":{"id":"11111111-2222-3333-4444-555555555555",
                   "name":"devlog-ai","currentRevision":"%s"},
                  "status":"STALE","guidance":"REFRESH_RECOMMENDED",
                  "baseline":{"analyzedRevision":"%s"}}],
                 "uncheckedSourceCount":0,"truncated":false}"""
                .formatted(PROJECT_ID, "c".repeat(40), "b".repeat(40));
        when(resourceClient.getFreshnessSummary(PROJECT_ID)).thenReturn(payload);

        String result = resource.getFreshness(SLUG);

        assertThat(result).contains("\"status\":\"STALE\"");
        assertThat(result).contains("REFRESH_RECOMMENDED");
    }

    @Test
    void shouldReturnBackendPayloadUnchanged() {
        String payload = """
                {"version":"project-freshness-summary-v1","projectId":"%s",
                 "checkedSources":[],"uncheckedSourceCount":0,"truncated":false}"""
                .formatted(PROJECT_ID);
        when(resourceClient.getFreshnessSummary(PROJECT_ID)).thenReturn(payload);

        assertThat(resource.getFreshness(SLUG)).isEqualTo(payload);
    }

    @Test
    void shouldOnlyQueryTheResolvedProjectScopedFreshnessEndpoint() {
        when(resourceClient.getFreshnessSummary(PROJECT_ID)).thenReturn("{}");

        resource.getFreshness(SLUG);

        org.mockito.Mockito.verify(resourceClient).getFreshnessSummary(PROJECT_ID);
        org.mockito.Mockito.verifyNoMoreInteractions(resourceClient);
    }

    @Test
    void shouldRejectUnknownProjectAsCleanNotFound() {
        assertThatThrownBy(() -> resource.getFreshness("unknown"))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Project 'unknown' not found");
    }

    @Test
    void shouldMapBackendAbsenceToCleanNotFound() {
        when(resourceClient.getFreshnessSummary(PROJECT_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resource.getFreshness(SLUG))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found");
    }
}
