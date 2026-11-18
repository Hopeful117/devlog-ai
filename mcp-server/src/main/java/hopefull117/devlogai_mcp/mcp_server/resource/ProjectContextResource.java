package hopefull117.devlogai_mcp.mcp_server.resource;


import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ProjectContextResource {

    private final DevlogProjectContextClient projectContextClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public ProjectContextResource(
            DevlogProjectContextClient projectContextClient,
            ObjectMapper objectMapper
    ) {
        this(projectContextClient, objectMapper, null);
    }

    @Autowired
    public ProjectContextResource(
            DevlogProjectContextClient projectContextClient,
            ObjectMapper objectMapper,
            McpAuthenticatedPrincipalProvider principalProvider
    ) {
        this.projectContextClient = projectContextClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/context",
            name = "project-context",
            description = "Provides DevLog project context for a project identified by its slug",
            mimeType = "application/json"
    )
    public String getProjectContext(String projectSlug) {
        ProjectContext context = fetchProjectContext(projectSlug);
        return objectMapper.writeValueAsString(context);
    }

    private ProjectContext fetchProjectContext(String projectSlug) {
        if (principalProvider != null) {
            var principal = principalProvider.currentPrincipal();
            if (principal.isPresent()) {
                String token = principal.get().bearerToken();
                String delegation = principal.get().delegationToken();
                if (token != null && !token.isBlank()) {
                    return projectContextClient.getProjectContextAuthenticated(
                            projectSlug, token,
                            delegation != null ? delegation : "");
                }
            }
        }
        return projectContextClient.getProjectContext(projectSlug);
    }
}
