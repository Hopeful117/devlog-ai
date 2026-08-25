package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read-only view of the project freshness projection (ADR-062): per-source
 * observed vs baseline repository revisions and their status. Serves the last
 * recorded observations; it never probes Git, refreshes, or mutates anything.
 */
@Component
public class FreshnessResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public FreshnessResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/freshness",
            name = "project-freshness",
            description = "Reads the freshness projection of a project: per-source "
                    + "observed repository revision, knowledge baseline revision and "
                    + "freshness status (CURRENT, STALE, NO_BASELINE, UNKNOWN)",
            mimeType = "application/json"
    )
    public String getFreshness(String projectSlug) {
        UUID projectId = support.requireProjectId(projectSlug);
        return support.getScoped(
                () -> resourceClient.getFreshnessSummary(projectId),
                "Freshness for project '%s' not found".formatted(projectSlug));
    }
}
