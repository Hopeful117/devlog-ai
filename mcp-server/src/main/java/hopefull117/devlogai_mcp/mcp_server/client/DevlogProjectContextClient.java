package hopefull117.devlogai_mcp.mcp_server.client;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    record AnalyzeContextRequest(List<String> files, Map<String, Object> guidance) {}

    record AnalyzeContextResponse(UUID aiTaskId) {}

    record AiTaskStatusResponse(
            UUID id,
            String status,
            String failureCode,
            String failureMessage
    ) {}
}