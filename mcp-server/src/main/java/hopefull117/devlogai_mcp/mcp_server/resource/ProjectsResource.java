package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Discovers the DevLog projects available to MCP clients. Exposes only the
 * information needed to identify and select a project; the resulting project
 * slug is the entry point of every other resource URI.
 */
@Component
public class ProjectsResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public ProjectsResource(DevlogResourceClient resourceClient, ResourceSupport support) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects",
            name = "projects",
            description = "Lists the DevLog projects available through MCP",
            mimeType = "application/json"
    )
    public String listProjects() {
        return support.getScoped(resourceClient::listProjects,
                "Projects not available");
    }
}
