package hopefull117.devlogai_mcp.mcp_server.resource;


import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectContextResource {

    private final DevlogProjectContextClient projectContextClient;
    private final ObjectMapper objectMapper;

    public ProjectContextResource(
            DevlogProjectContextClient projectContextClient,
            ObjectMapper objectMapper
    ) {
        this.projectContextClient = projectContextClient;
        this.objectMapper = objectMapper;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/context",
            name = "project-context",
            description = "Provides DevLog project context for a project identified by its slug",
            mimeType = "application/json"
    )
    public String getProjectContext(String projectSlug) {
        ProjectContext context =
                projectContextClient.getProjectContext(projectSlug);

        return objectMapper.writeValueAsString(context);
    }
}
