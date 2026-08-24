package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Direct read of a known validated Engineering Event, including its Git
 * commit references and provenance (analysis/proposal/validation identifiers).
 */
@Component
public class EngineeringEventResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public EngineeringEventResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/engineering-events/{eventId}",
            name = "project-engineering-event",
            description = "Reads a validated DevLog Engineering Event of a project by its identifier",
            mimeType = "application/json"
    )
    public String getEngineeringEvent(String projectSlug, String eventId) {
        var projectId = support.requireProjectId(projectSlug);
        var id = support.requireUuid(eventId, "engineering event");
        return support.getWithProjectOwnership(
                () -> resourceClient.getEngineeringEvent(id),
                projectId,
                "Engineering event",
                eventId);
    }
}
