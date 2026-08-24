package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Direct read of a known validated insight. Insights are resolved through the
 * ACTIVE-only project knowledge list, so superseded or archived insights can
 * never be returned as trusted knowledge.
 */
@Component
public class InsightResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public InsightResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/insights/{insightId}",
            name = "project-insight",
            description = "Reads a validated (ACTIVE) DevLog insight of a project by its identifier",
            mimeType = "application/json"
    )
    public String getInsight(String projectSlug, String insightId) {
        var projectId = support.requireProjectId(projectSlug);
        var id = support.requireUuid(insightId, "insight");
        return support.findActiveInsight(projectId, id.toString());
    }
}
