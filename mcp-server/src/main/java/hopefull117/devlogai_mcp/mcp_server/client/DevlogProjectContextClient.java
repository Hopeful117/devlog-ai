package hopefull117.devlogai_mcp.mcp_server.client;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@HttpExchange("/api/v1")
public interface DevlogProjectContextClient {
    @GetExchange("/projects/{projectSlug}/context")
    ProjectContext getProjectContext(@PathVariable String projectSlug);

    @GetExchange("/projects/{projectSlug}/engineering-context")
    EngineeringContext getEngineeringContext(
            @PathVariable String projectSlug,
            @RequestParam("intent") String intent,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "storyId", required = false) UUID storyId
    );

    @PostExchange("/projects/{projectSlug}/stories/{storyId}/analyze-context")
    AnalyzeContextResponse analyzeStoryContext(
            @PathVariable String projectSlug,
            @PathVariable UUID storyId,
            @RequestBody(required = false) AnalyzeContextRequest request
    );

    @GetExchange("/ai/tasks/{aiTaskId}/story-context-analysis")
    StoryContextAnalysisResponse getStoryContextAnalysis(
            @PathVariable UUID aiTaskId
    );

    @GetExchange("/ai-tasks/{aiTaskId}")
    AiTaskStatusResponse getAiTaskStatus(
            @PathVariable UUID aiTaskId
    );

    @GetExchange("/story-context-agent/projects/{projectSlug}/context")
    Map<String, Object> getStoryContextAgentProjection(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam("intent") String intent
    );

    @PostExchange("/story-context-agent/projects/{projectSlug}/tasks")
    Map<String, Object> submitStoryContextAgentTask(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestBody SubmitRequest request
    );

    @PostExchange("/story-context-agent/tasks/{aiTaskId}/callback")
    Map<String, Object> callbackStoryContextAgentTask(
            @PathVariable UUID aiTaskId,
            @RequestHeader("X-SCA-Signature") String signature,
            @RequestBody Map<String, Object> request
    );

    @GetExchange("/story-context-agent/tasks/{aiTaskId}/snapshot")
    Map<String, Object> getStoryContextAgentSnapshot(
            @PathVariable UUID aiTaskId,
            @RequestHeader("X-DevLog-Principal-Id") String principalId,
            @RequestHeader("X-DevLog-Principal-Kind") String principalKind,
            @RequestHeader("X-DevLog-Authentication-Source") String authenticationSource);

    @PostExchange("/story-context-agent/snapshots/{snapshotId}/follow-up")
    Map<String, Object> submitStoryContextAgentFollowUp(
            @PathVariable UUID snapshotId,
            @RequestHeader("X-DevLog-Principal-Id") String principalId,
            @RequestHeader("X-DevLog-Principal-Kind") String principalKind,
            @RequestHeader("X-DevLog-Authentication-Source") String authenticationSource,
            @RequestBody FollowUpRequest request);

    @PostExchange("/projects/{projectSlug}/story-agent")
    Map<String, Object> executeStoryAgent(
            @PathVariable String projectSlug,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody StoryAgentRequest request
    );

    record AnalyzeContextRequest(List<String> files, Map<String, Object> guidance) {}

    record SubmitRequest(String intent, List<String> files, Map<String, Object> guidance) {}

    record FollowUpRequest(String question, Map<String, Object> guidance) {}

    record StoryAgentRequest(UUID storyId, String intent, List<String> files, Map<String, Object> guidance) {}

    record AnalyzeContextResponse(UUID aiTaskId) {}

    record AiTaskStatusResponse(
            UUID id,
            String status,
            String failureCode,
            String failureMessage
    ) {}
}
