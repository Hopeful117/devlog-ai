package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
public class StoryChangeBriefingResource {
    private final DevlogResourceClient client;
    private final ResourceSupport support;

    public StoryChangeBriefingResource(DevlogResourceClient client, ResourceSupport support) {
        this.client = client;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/stories/{storyId}/change-briefing",
            name = "story-change-briefing",
            description = "Descriptive, non-causal change briefing grounded in the canonical story context",
            mimeType = "application/json")
    public String get(String projectSlug, String storyId) {
        support.requireProjectId(projectSlug);
        return client.getStoryChangeBriefing(projectSlug, support.requireUuid(storyId, "story"));
    }
}
