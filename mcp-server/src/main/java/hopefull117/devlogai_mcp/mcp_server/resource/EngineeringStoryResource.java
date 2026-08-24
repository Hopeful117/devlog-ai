package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Direct read of a known Engineering Story. Ownership is enforced server-side
 * by the existing story service (storyId + projectId lookup).
 */
@Component
public class EngineeringStoryResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public EngineeringStoryResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/stories/{storyId}",
            name = "project-story",
            description = "Reads a DevLog Engineering Story of a project by its identifier",
            mimeType = "application/json"
    )
    public String getStory(String projectSlug, String storyId) {
        var projectId = support.requireProjectId(projectSlug);
        var id = support.requireUuid(storyId, "story");
        return support.getScoped(
                () -> resourceClient.getStory(projectId, id),
                "Story '%s' not found in project '%s'".formatted(storyId, projectSlug));
    }
}
