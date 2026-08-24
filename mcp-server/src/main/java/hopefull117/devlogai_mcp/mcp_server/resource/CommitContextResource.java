package hopefull117.devlogai_mcp.mcp_server.resource;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Direct read of the DevLog context of a known commit (bounded deterministic
 * diff view with classification and candidate ADR/roadmap references). This is
 * not a Git search: the consumer already knows the SHA.
 */
@Component
public class CommitContextResource {

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    public CommitContextResource(
            DevlogResourceClient resourceClient,
            ResourceSupport support
    ) {
        this.resourceClient = resourceClient;
        this.support = support;
    }

    @McpResource(
            uri = "devlog://projects/{projectSlug}/commits/{commitSha}",
            name = "project-commit-context",
            description = "Reads the DevLog deterministic context of a known commit by its SHA",
            mimeType = "application/json"
    )
    public String getCommitContext(String projectSlug, String commitSha) {
        var projectId = support.requireProjectId(projectSlug);
        var sha = support.requireCommitSha(commitSha);
        var sourceId = support.requireActiveSourceId(projectId, projectSlug);
        return support.getScoped(
                () -> resourceClient.getCommitContext(sourceId, sha),
                "Commit '%s' not found in project '%s'".formatted(sha, projectSlug));
    }
}
