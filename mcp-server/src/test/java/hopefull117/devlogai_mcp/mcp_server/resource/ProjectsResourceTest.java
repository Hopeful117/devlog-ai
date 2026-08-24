package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectsResourceTest {

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final ProjectsResource resource = new ProjectsResource(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient,
                    new ObjectMapper()));

    @Test
    void shouldListProjectsAsJson() {
        String projects = """
                [{"id":"f3d56247-aada-4a76-982b-e6802c0b309c",
                  "name":"devlog-ai","slug":"devlog-ai","status":"ACTIVE"}]""";
        when(resourceClient.listProjects()).thenReturn(projects);

        String result = resource.listProjects();

        assertThat(result).contains("devlog-ai").contains("\"slug\"");
        verify(resourceClient).listProjects();
    }

    @Test
    void shouldMapBackendNotFoundToCleanMcpError() {
        when(resourceClient.listProjects()).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(resource::listProjects)
                .isInstanceOf(McpError.class);
    }
}
