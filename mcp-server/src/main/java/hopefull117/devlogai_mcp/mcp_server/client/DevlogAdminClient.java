package hopefull117.devlogai_mcp.mcp_server.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Map;
import java.util.UUID;

/**
 * Mutation-capable client for DevLog backend endpoints not covered by the
 * read-only {@link DevlogResourceClient}. Used by tools that create or update
 * domain objects (projects, sources).
 */
@HttpExchange("/api/v1")
public interface DevlogAdminClient {

    @PostExchange("/projects")
    String createProject(@RequestBody Map<String, Object> request);

    @PostExchange("/projects")
    String createProjectAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request
    );

    @GetExchange("/projects/{slug}")
    String getProjectBySlug(@PathVariable("slug") String slug);

    @GetExchange("/projects/{slug}")
    String getProjectBySlugAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("slug") String slug
    );

    @PostExchange("/sources")
    String createSource(@RequestBody Map<String, Object> request);

    @PostExchange("/sources")
    String createSourceAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request
    );

    @PostExchange("/project-history/repositories/{repositoryId}/imports")
    String importProjectHistoryAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("repositoryId") UUID repositoryId,
            @RequestParam(value = "revision", required = false) String revision
    );

    @PostExchange("/projects/{projectId}/understanding-executions")
    String executeProjectUnderstandingAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId,
            @RequestBody Map<String, Object> request
    );

    @GetExchange("/analyses/{analysisId}")
    String getAnalysisAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("analysisId") UUID analysisId
    );

    @GetExchange("/projects/{projectId}/freshness-checks/summary")
    String getProjectFreshnessSummaryAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId
    );

    @PostExchange("/projects/{projectId}/stories/synchronize")
    String synchronizeProjectStoriesAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId,
            @RequestParam(value = "revision", required = false) String revision
    );

    @PostExchange("/projects/{projectId}/stories")
    String registerStoryAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId,
            @RequestBody Map<String, Object> request
    );

    @PostExchange("/projects/{projectId}/stories/{storyId}/start")
    String startStoryImplementationAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId,
            @PathVariable("storyId") UUID storyId,
            @RequestBody Map<String, Object> request
    );

    @PostExchange("/projects/{projectId}/stories/{storyId}/complete")
    String completeStoryImplementationAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId,
            @PathVariable("storyId") UUID storyId,
            @RequestBody Map<String, Object> request
    );

    @GetExchange("/projects/{projectId}/stories")
    String listProjectStoriesAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @PathVariable("projectId") UUID projectId
    );

    @PostExchange("/validations")
    String validateProposalAuthenticated(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> request
    );
}
