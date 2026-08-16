package hopefull117.devlogai_mcp.mcp_server;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ProjectContextResource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ProjectContextResourceTest {
    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProjectContextResource resource =
            new ProjectContextResource(projectContextClient, objectMapper);

    @Test
    void shouldReturnProjectContextForSlug() {
        UUID projectId =
                UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

        ProjectContext context = new ProjectContext(
                projectId,
                "devlog-ai",
                "devlog-ai",
                "An AI-powered documentation assistant.",
                "ACTIVE"
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
}
