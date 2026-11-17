package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool that registers a new project in DevLog and optionally links a Git
 * repository source to it. Wraps the existing {@code POST /api/v1/projects}
 * and {@code POST /api/v1/sources} backend endpoints.
 */
@Component
public class RegisterProjectTool {

    private final DevlogAdminClient adminClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public RegisterProjectTool(DevlogAdminClient adminClient, ObjectMapper objectMapper) {
        this(adminClient, objectMapper, null);
    }

    @Autowired
    public RegisterProjectTool(
            DevlogAdminClient adminClient,
            ObjectMapper objectMapper,
            McpAuthenticatedPrincipalProvider principalProvider) {
        this.adminClient = adminClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    public String registerProject(
            @McpArg(description = "Human-readable project name (max 100 chars)", required = true)
            String name,

            @McpArg(description = "Project description (optional)", required = false)
            @Nullable String description,

            @McpArg(description = "Git repository URL to register as a source (e.g. https://github.com/owner/repo)", required = false)
            @Nullable String repositoryUrl,

            @McpArg(description = "Default branch name (defaults to 'main' when not provided)", required = false)
            @Nullable String defaultBranch
    ) {
        if (principalProvider == null) {
            return doRegisterProject(name, description, repositoryUrl, defaultBranch, null);
        }
        return registerProject(name, description, repositoryUrl, defaultBranch, null);
    }

    @McpTool(
            name = "register_project",
            description = "Creates a new project in DevLog and optionally registers a Git repository as its source. Returns the created project slug and source details."
    )
    public String registerProject(
            String name,
            @Nullable String description,
            @Nullable String repositoryUrl,
            @Nullable String defaultBranch,
            McpSyncRequestContext requestContext
    ) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        return doRegisterProject(name, description, repositoryUrl, defaultBranch,
                principal.bearerToken());
    }

    private String doRegisterProject(
            String name,
            @Nullable String description,
            @Nullable String repositoryUrl,
            @Nullable String defaultBranch,
            @Nullable String bearerToken
    ) {
        Map<String, Object> projectRequest = new LinkedHashMap<>();
        projectRequest.put("name", name);
        if (description != null && !description.isBlank()) {
            projectRequest.put("description", description);
        }

        String projectJson = bearerToken != null
                ? adminClient.createProjectAuthenticated(bearerToken, projectRequest)
                : adminClient.createProject(projectRequest);

        JsonNode project = objectMapper.readTree(projectJson);
        String projectId = project.path("id").asText();
        String projectSlug = project.path("slug").asText();

        ObjectNode result = objectMapper.createObjectNode();
        result.set("project", project);

        if (repositoryUrl != null && !repositoryUrl.isBlank()) {
            String branch = (defaultBranch != null && !defaultBranch.isBlank())
                    ? defaultBranch : "main";
            String sourceName = deriveSourceName(repositoryUrl);
            String provider = detectGitProvider(repositoryUrl);

            Map<String, Object> sourceRequest = new LinkedHashMap<>();
            sourceRequest.put("projectId", projectId);
            sourceRequest.put("type", "GIT_REPOSITORY");
            sourceRequest.put("name", sourceName);
            sourceRequest.put("repositoryUrl", repositoryUrl);
            sourceRequest.put("defaultBranch", branch);
            sourceRequest.put("provider", provider);

            String sourceJson = bearerToken != null
                    ? adminClient.createSourceAuthenticated(bearerToken, sourceRequest)
                    : adminClient.createSource(sourceRequest);

            result.set("source", objectMapper.readTree(sourceJson));
        }

        return objectMapper.writeValueAsString(result);
    }

    private static String detectGitProvider(String repositoryUrl) {
        if (repositoryUrl.contains("github.com")) return "GITHUB";
        if (repositoryUrl.contains("gitlab.com")) return "GITLAB";
        if (repositoryUrl.contains("bitbucket.org")) return "BITBUCKET";
        if (repositoryUrl.contains("dev.azure.com") || repositoryUrl.contains("visualstudio.com")) {
            return "AZURE_DEVOPS";
        }
        return "GENERIC_GIT";
    }

    private static String deriveSourceName(String repositoryUrl) {
        String stripped = repositoryUrl.replaceAll("\\.git$", "");
        int lastSlash = stripped.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < stripped.length() - 1
                ? stripped.substring(lastSlash + 1)
                : repositoryUrl;
    }
}
