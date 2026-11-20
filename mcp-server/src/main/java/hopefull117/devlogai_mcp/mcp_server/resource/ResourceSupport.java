package hopefull117.devlogai_mcp.mcp_server.resource;

import io.modelcontextprotocol.spec.McpError;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Shared resolution, validation and error-mapping behavior for DevLog MCP
 * Resources. Enforces project membership on every artifact read so no global
 * ambiguous lookup exists.
 */
@Component
public class ResourceSupport {

    private final DevlogProjectContextClient projectContextClient;
    private final DevlogResourceClient resourceClient;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public ResourceSupport(
            DevlogProjectContextClient projectContextClient,
            DevlogResourceClient resourceClient,
            ObjectMapper objectMapper
    ) {
        this(projectContextClient, resourceClient, objectMapper, null);
    }

    @Autowired
    public ResourceSupport(
            DevlogProjectContextClient projectContextClient,
            DevlogResourceClient resourceClient,
            ObjectMapper objectMapper,
            McpAuthenticatedPrincipalProvider principalProvider
    ) {
        this.projectContextClient = projectContextClient;
        this.resourceClient = resourceClient;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    /**
     * Resolves a project slug to its stable internal id. Unknown slugs surface
     * as a clean MCP error, never as a transport failure.
     */
    public UUID requireProjectId(String projectSlug) {
        return requireProjectId(projectSlug, null);
    }

    /**
     * Resolves a project slug to its stable internal id using the per-request
     * transport context when available, falling back to SecurityContextHolder.
     * This overload must be used by MCP Tools to avoid 401 errors in stdio transport.
     */
    public UUID requireProjectId(String projectSlug, @Nullable McpSyncRequestContext requestContext) {
        try {
            var project = fetchProjectContext(projectSlug, requestContext);
            if (project == null || project.id() == null) {
                throw notFound("Project '%s' not found".formatted(projectSlug));
            }
            return project.id();
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Project '%s' not found".formatted(projectSlug), true,
                    "resolving the project");
        }
    }

    private com.hopeful117.devlogai.contracts.projectcontext.ProjectContext fetchProjectContext(
            String projectSlug, @Nullable McpSyncRequestContext requestContext) {
        if (principalProvider != null) {
            var principal = requestContext != null
                    ? principalProvider.currentPrincipal(requestContext)
                    : principalProvider.currentPrincipal();
            if (principal.isPresent()) {
                String token = principal.get().bearerToken();
                String delegation = principal.get().delegationToken();
                if (token != null && !token.isBlank()) {
                    return projectContextClient.getProjectContextAuthenticated(
                            projectSlug, token,
                            delegation != null ? delegation : "");
                }
            }
        }
        return projectContextClient.getProjectContext(projectSlug);
    }

    /**
     * Reads an artifact through a backend endpoint already scoped by project.
     */
    public String getScoped(Supplier<String> call, String notFoundMessage) {
        return get(call, notFoundMessage);
    }

    /** Maps the non-disclosing outcomes of an authorized snapshot read. */
    public String getAuthorizedSnapshot(Supplier<String> call, String notFoundMessage) {
        try {
            return call.get();
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception, notFoundMessage, true,
                    "reading the Story Context snapshot");
        }
    }

    /** Maps follow-up submission errors without exposing backend diagnostics. */
    public String submitAuthorizedSnapshot(Supplier<String> call, String notFoundMessage) {
        try {
            return call.get();
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception, notFoundMessage, true,
                    "submitting the Story Context follow-up");
        }
    }

    /**
     * Reads an artifact exposed by a global identifier and enforces that it
     * belongs to the resolved project before returning it.
     */
    public String getWithProjectOwnership(
            Supplier<String> call,
            UUID projectId,
            String artifactKind,
            String artifactId
    ) {
        String payload = get(call, "%s '%s' not found"
                .formatted(artifactKind, artifactId));
        JsonNode node = objectMapper.readTree(payload);
        String owner = node.path("projectId").asText(null);
        if (owner == null || owner.isBlank()) {
            throw notFound("%s '%s' does not declare a project"
                    .formatted(artifactKind, artifactId));
        }
        UUID ownerId;
        try {
            ownerId = UUID.fromString(owner);
        } catch (IllegalArgumentException exception) {
            throw invalidParams("%s '%s' declares an invalid project"
                    .formatted(artifactKind, artifactId));
        }
        if (!ownerId.equals(projectId)) {
            throw notFound("%s '%s' does not belong to project '%s'"
                    .formatted(artifactKind, artifactId, projectId));
        }
        return payload;
    }

    /**
     * Resolves an insight inside the ACTIVE-only project knowledge list, so
     * superseded or archived insights can never be served as trusted
     * knowledge.
     */
    public String findActiveInsight(UUID projectId, String insightId) {
        String insights = get(
                () -> resourceClient.listProjectInsights(projectId),
                "Insights for project '%s' not found".formatted(projectId));
        JsonNode array = objectMapper.readTree(insights);
        if (!array.isArray()) {
            throw notFound("Insight \'%s\' not found in project \'%s\'"
                    .formatted(insightId, projectId));
        }
        for (JsonNode candidate : array) {
            if (insightId.equalsIgnoreCase(candidate.path("id").asText(""))) {
                return candidate.toString();
            }
        }
        throw notFound("Insight \'%s\' not found in project \'%s\'"
                .formatted(insightId, projectId));
    }

    /**
     * Selects the only admissible active Git source for a project.
     * Ordering is never used to resolve ambiguity.
     */
    public UUID requireActiveSourceId(UUID projectId, String projectSlug) {
        String sources = get(
                () -> resourceClient.listProjectSources(projectId),
                "Sources of project '%s' not found".formatted(projectSlug));
        JsonNode array = objectMapper.readTree(sources);
        if (!array.isArray()) {
            throw sourceUnavailable("No active repository source for project '%s'"
                    .formatted(projectSlug));
        }
        UUID selected = null;
        int activeCount = 0;
        for (JsonNode candidate : array) {
            if (!candidate.path("active").asBoolean(false)) continue;
            activeCount++;
            selected = UUID.fromString(candidate.path("id").asText());
        }
        if (activeCount == 0) {
            throw sourceUnavailable("No active repository source for project '%s'"
                    .formatted(projectSlug));
        }
        if (activeCount > 1) {
            throw ambiguousSource("Ambiguous active repository source for project '%s'"
                    .formatted(projectSlug));
        }
        return selected;
    }

    private static McpError sourceUnavailable(String message) {
        return notFound("SOURCE_UNAVAILABLE: " + message);
    }

    private static McpError ambiguousSource(String message) {
        return invalidParams("AMBIGUOUS_SOURCE: " + message);
    }

    public UUID requireUuid(String raw, String kind) {
        if (raw == null || raw.isBlank()) {
            throw invalidParams("Invalid %s identifier: blank value".formatted(kind));
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalidParams("Invalid %s identifier: '%s'".formatted(kind, raw));
        }
    }

    public String requireCommitSha(String raw) {
        if (raw != null && raw.matches("[0-9a-fA-F]{40}|[0-9a-fA-F]{64}")) {
            return raw.toLowerCase();
        }
        throw invalidParams(
                "Invalid commit SHA: '%s' (expected a 40 or 64 character hexadecimal SHA)"
                        .formatted(raw));
    }

    private String get(Supplier<String> call, String notFoundMessage) {
        try {
            return call.get();
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception, notFoundMessage, false,
                    "reading the resource");
        }
    }

    public static io.modelcontextprotocol.spec.McpError notFound(String message) {
        return CoreCallPolicy.notFound(message);
    }

    public static io.modelcontextprotocol.spec.McpError invalidParams(String message) {
        return CoreCallPolicy.invalidParams(message);
    }

    public static io.modelcontextprotocol.spec.McpError unauthenticated(String message) {
        return CoreCallPolicy.unauthenticated(message);
    }

    public static io.modelcontextprotocol.spec.McpError coreAuthenticationFailed(String message) {
        return CoreCallPolicy.coreAuthenticationFailed(message);
    }

}
