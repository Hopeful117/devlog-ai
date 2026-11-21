package hopefull117.devlogai_mcp.mcp_server.client;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

/**
 * Read-only access to existing DevLog backend endpoints backing the MCP
 * Resources. This facade adds no business logic: every method proxies an
 * existing application capability, mirroring the tool-side client.
 */
@HttpExchange("/api/v1")
public interface DevlogResourceClient {

    @GetExchange("/projects")
    String listProjects();

    @GetExchange("/decisions/{decisionId}")
    String getDecision(@PathVariable("decisionId") UUID decisionId);

    @GetExchange("/insights/project/{projectId}")
    String listProjectInsights(@PathVariable("projectId") UUID projectId);

    @GetExchange("/engineering-events/{eventId}")
    String getEngineeringEvent(@PathVariable("eventId") UUID eventId);

    @GetExchange("/projects/{projectId}/stories/{storyId}")
    String getStory(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("storyId") UUID storyId);

    @GetExchange("/projects/{projectId}/stories/resolve")
    String resolveStoryByNumber(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("storyNumber") Integer storyNumber);

    @GetExchange("/projects/{projectId}/stories/resolve")
    String resolveStoryByNumberAuthenticated(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("storyNumber") Integer storyNumber,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);

    @GetExchange("/projects/{projectSlug}/stories/{storyId}/change-briefing")
    String getStoryChangeBriefing(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("storyId") UUID storyId);

    @GetExchange("/sources/project/{projectId}")
    String listProjectSources(@PathVariable("projectId") UUID projectId);

    @GetExchange("/project-history/repositories/{repositoryId}/commits/{commitHash}/context")
    String getCommitContext(
            @PathVariable("repositoryId") UUID repositoryId,
            @PathVariable("commitHash") String commitHash);

    @GetExchange("/projects/{projectId}/freshness-checks/summary")
    String getFreshnessSummary(@PathVariable("projectId") UUID projectId);

    @GetExchange("/project-history/projects/{projectId}/commits/search")
    String searchProjectHistory(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", required = false) Integer limit);

    @GetExchange("/project-history/projects/{projectId}/commits/search")
    String searchProjectHistoryAuthenticated(
            @PathVariable("projectId") UUID projectId,
            @RequestParam("query") String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);

    @GetExchange("/evidence/resolve")
    String resolveEvidence(
            @RequestParam("reference") String reference,
            @RequestParam("mode") String mode,
            @RequestParam(value = "analysisId", required = false) UUID analysisId);
}
