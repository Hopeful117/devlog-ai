package hopefull117.devlogai_mcp.mcp_server.resource;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
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

    public ResourceSupport(
            DevlogProjectContextClient projectContextClient,
            DevlogResourceClient resourceClient,
            ObjectMapper objectMapper
    ) {
        this.projectContextClient = projectContextClient;
        this.resourceClient = resourceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolves a project slug to its stable internal id. Unknown slugs surface
     * as a clean MCP error, never as a transport failure.
     */
    public UUID requireProjectId(String projectSlug) {
        try {
            var project = projectContextClient.getProjectContext(projectSlug);
            if (project == null || project.id() == null) {
                throw notFound("Project '%s' not found".formatted(projectSlug));
            }
            return project.id();
        } catch (HttpClientErrorException.NotFound exception) {
            throw notFound("Project '%s' not found".formatted(projectSlug));
        } catch (RestClientResponseException exception) {
            throw internal("DevLog backend failed while resolving project '%s' (%s)"
                    .formatted(projectSlug, exception.getStatusCode()));
        }
    }

    /**
     * Reads an artifact through a backend endpoint already scoped by project.
     */
    public String getScoped(Supplier<String> call, String notFoundMessage) {
        return get(call, notFoundMessage);
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
     * Selects the active Git source of the project using the same ordering
     * rule as RepositoryStructureCollector (createdAt asc, id asc).
     */
    public UUID requireActiveSourceId(UUID projectId, String projectSlug) {
        String sources = get(
                () -> resourceClient.listProjectSources(projectId),
                "Sources of project '%s' not found".formatted(projectSlug));
        JsonNode array = objectMapper.readTree(sources);
        if (!array.isArray()) {
            throw notFound("No active repository source for project '%s'"
                    .formatted(projectSlug));
        }
        JsonNode best = null;
        for (JsonNode candidate : array) {
            if (!candidate.path("active").asBoolean(false)) continue;
            if (best == null || compareSources(candidate, best) < 0) best = candidate;
        }
        if (best == null) {
            throw notFound("No active repository source for project '%s'"
                    .formatted(projectSlug));
        }
        return UUID.fromString(best.path("id").asText());
    }

    private int compareSources(JsonNode left, JsonNode right) {
        int byCreatedAt = left.path("createdAt").asText("")
                .compareTo(right.path("createdAt").asText(""));
        if (byCreatedAt != 0) return byCreatedAt;
        return left.path("id").asText("").compareTo(right.path("id").asText(""));
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
        } catch (HttpClientErrorException.NotFound exception) {
            throw notFound(notFoundMessage);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized exception) {
            throw internal("DevLog backend refused access (%s)"
                    .formatted(exception.getStatusCode()));
        } catch (RestClientResponseException exception) {
            HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
            if (status != null && status.is5xxServerError()) {
                throw internal("DevLog backend failed (%s)".formatted(exception.getStatusCode()));
            }
            throw internal("DevLog backend rejected the request (%s): %s"
                    .formatted(exception.getStatusCode(), notFoundMessage));
        }
    }

    public static McpError notFound(String message) {
        return McpError.builder(ErrorCodes.RESOURCE_NOT_FOUND)
                .message(message)
                .build();
    }

    public static McpError invalidParams(String message) {
        return McpError.builder(ErrorCodes.INVALID_PARAMS)
                .message(message)
                .build();
    }

    private static McpError internal(String message) {
        return McpError.builder(ErrorCodes.INTERNAL_ERROR)
                .message(message)
                .build();
    }
}
