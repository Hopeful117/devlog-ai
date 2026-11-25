package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Story 0240: refresh the project baseline (Project Understanding execution)
 * and follow it through MCP, so the live evaluation flow needs no UI step.
 * Core stays authoritative; this is a thin authenticated adapter.
 */
@Component
public class ProjectBaselineTool {

    private static final List<String> ANALYSIS_FIELDS = List.of(
            "id", "projectId", "type", "intentId", "intentVersion", "status",
            "startedAt", "completedAt", "createdAt", "updatedAt");

    private final DevlogAdminClient adminClient;
    private final ResourceSupport support;
    private final McpAuthenticatedPrincipalProvider principalProvider;
    private final ObjectMapper objectMapper;

    public ProjectBaselineTool(DevlogAdminClient adminClient, ResourceSupport support,
                               McpAuthenticatedPrincipalProvider principalProvider, ObjectMapper objectMapper) {
        this.adminClient = adminClient;
        this.support = support;
        this.principalProvider = principalProvider;
        this.objectMapper = objectMapper;
    }

    @McpTool(
            name = "refresh_project_baseline",
            description = "Starts (or reuses) a Core Project Understanding execution for the project's active "
                    + "repository source, refreshing the knowledge baseline used by project freshness. "
                    + "Returns the analysis id, status and outcome (STARTED or REUSED). Follow it with "
                    + "get_project_baseline_status. Resulting proposals still require human review."
    )
    public String refreshProjectBaseline(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,

            @McpToolParam(description = "Git revision to analyze. Defaults to the source's current revision.",
                    required = false)
            @Nullable String targetRevision,

            McpSyncRequestContext requestContext
    ) {
        String authorization = bearer(requestContext, "refresh the project baseline");
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        UUID sourceId = support.requireActiveSourceId(projectId, projectSlug);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sourceId", sourceId.toString());
        if (targetRevision != null && !targetRevision.isBlank()) {
            request.put("targetRevision", targetRevision.strip());
        }
        try {
            return adminClient.executeProjectUnderstandingAuthenticated(authorization, projectId, request);
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Project '%s' not found or access denied".formatted(projectSlug), false,
                    "refreshing the project baseline");
        }
    }

    @McpTool(
            name = "get_project_baseline_status",
            description = "Reads the status of a Project Understanding (baseline) analysis started by "
                    + "refresh_project_baseline, together with the Core project freshness summary."
    )
    public String getProjectBaselineStatus(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,

            @McpToolParam(description = "Analysis id returned by refresh_project_baseline", required = true)
            String analysisId,

            McpSyncRequestContext requestContext
    ) {
        String authorization = bearer(requestContext, "read the project baseline status");
        UUID projectId = support.requireProjectId(projectSlug, requestContext);
        UUID analysis;
        try {
            analysis = UUID.fromString(analysisId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw CoreCallPolicy.invalidParams("analysisId must be a UUID");
        }
        try {
            JsonNode node = objectMapper.readTree(adminClient.getAnalysisAuthenticated(authorization, analysis));
            if (!projectId.toString().equals(node.path("projectId").asText())) {
                throw CoreCallPolicy.notFound("Analysis '%s' not found for project '%s'"
                        .formatted(analysisId, projectSlug));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> projection = new LinkedHashMap<>();
            for (String field : ANALYSIS_FIELDS) {
                JsonNode value = node.get(field);
                projection.put(field, value == null || value.isNull() ? null : value.asText());
            }
            JsonNode snapshot = node.path("selectedSourceSnapshot");
            projection.put("sourceRevision", textOrNull(snapshot, "resolvedRevision", "currentRevision", "revision"));
            result.put("analysis", projection);
            result.put("freshness", objectMapper.readTree(
                    adminClient.getProjectFreshnessSummaryAuthenticated(authorization, projectId)));
            return objectMapper.writeValueAsString(result);
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Analysis '%s' not found or access denied".formatted(analysisId), false,
                    "reading the project baseline status");
        }
    }

    private String bearer(McpSyncRequestContext requestContext, String operation) {
        var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
        if (principal == null || principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authentication is required to " + operation);
        }
        return principal.bearerToken();
    }

    private static String textOrNull(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }
}
