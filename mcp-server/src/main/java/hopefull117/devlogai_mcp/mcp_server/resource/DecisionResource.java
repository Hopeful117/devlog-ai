package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Direct read of a known architectural decision. Answers
 * "give me this known decision" — never an AI-generated interpretation.
 */
@Component
public class DecisionResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public DecisionResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/decisions/{decisionId}",
            name = "project-decision",
            description = "Reads a DevLog architectural decision of a project by its identifier",
            mimeType = "application/json"
    )
    public String getDecision(String projectSlug, String decisionId) {
        var projectId = support.requireProjectId(projectSlug);
        var id = support.requireUuid(decisionId, "decision");
        return support.getWithProjectOwnership(
                () -> resourceClient.getDecision(id),
                projectId,
                "Decision",
                decisionId);
    }
}
