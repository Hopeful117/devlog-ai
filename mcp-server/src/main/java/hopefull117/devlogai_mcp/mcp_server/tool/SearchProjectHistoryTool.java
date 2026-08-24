package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Deterministic search over the project history already imported by DevLog:
 * commit messages and changed paths. Answers "where does this appear in the
 * project's history?" — discovery only; detailed inspection of a result
 * belongs to the commit-context resource referenced in each match.
 */
@Component
@RequiredArgsConstructor
public class SearchProjectHistoryTool {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    private final DevlogResourceClient resourceClient;
    private final ResourceSupport support;

    @McpTool(
            name = "search_project_history",
            description = "Searches the imported history of a DevLog project for commits "
                    + "matching the query terms (commit message and changed file paths). "
                    + "Deterministic keyword search, not semantic search. Each result "
                    + "carries a commit-context resource URI for detailed inspection."
    )
    public String searchProjectHistory(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,

            @McpToolParam(description = "Space-separated keywords to search for in commit "
                    + "messages and changed file paths (e.g. \"markdown rendering\" "
                    + "or \"RepositoryContextEngine\")", required = true)
            String query,

            @McpToolParam(description = "Maximum number of results returned (default 20, "
                    + "maximum 100)", required = false)
            Integer limit
    ) {
        var projectId = support.requireProjectId(projectSlug);
        if (query == null || query.isBlank()) {
            throw ResourceSupport.invalidParams(
                    "query must not be blank");
        }
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            throw ResourceSupport.invalidParams(
                    "limit must be between 1 and %d".formatted(MAX_LIMIT));
        }
        return support.getScoped(
                () -> resourceClient.searchProjectHistory(projectId, query.strip(), limit),
                "Project '%s' not found".formatted(projectSlug));
    }
}
