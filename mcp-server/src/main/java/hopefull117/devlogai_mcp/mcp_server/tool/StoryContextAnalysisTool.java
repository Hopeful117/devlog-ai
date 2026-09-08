package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryContextAnalysisTool {

    private static final int TIMEOUT_SECONDS = 120;
    private static final int POLL_INTERVAL_MS = 2000;

    private final DevlogProjectContextClient devlogProjectContextClient;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "analyze_story_context",
            description = "Analyzes an Engineering Story context and produces a structured, grounded analysis for Discuss/Plan preparation. Submits analysis, waits for completion, and returns the canonical persisted result."
    )
    public String analyzeStoryContext(
            @McpArg(description = "Slug identifying the DevLog project", required = true) String projectSlug,
            @McpArg(description = "Engineering Story UUID", required = true) UUID storyId,
            @McpArg(description = "Optional file paths to narrow technical evidence scope", required = false) List<String> files,
            @McpArg(description = "Optional human guidance for the analysis", required = false) Map<String, Object> guidance
    ) {
        DevlogProjectContextClient.AnalyzeContextRequest request = null;
        if (files != null || guidance != null) {
            request = new DevlogProjectContextClient.AnalyzeContextRequest(files, guidance);
        }

        DevlogProjectContextClient.AnalyzeContextResponse submitResponse =
                devlogProjectContextClient.analyzeStoryContext(projectSlug, storyId, request);

        UUID aiTaskId = submitResponse.aiTaskId();
        log.info("Story context analysis submitted: aiTaskId={}", aiTaskId);

        long startTime = System.currentTimeMillis();
        long timeoutMs = TIMEOUT_SECONDS * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return buildErrorResponse("Interrupted while waiting for analysis", aiTaskId);
            }

            StoryContextAnalysisResponse response = pollForAnalysis(aiTaskId);
            if (response != null) {
                log.info("Story context analysis completed: aiTaskId={}", aiTaskId);
                return objectMapper.writeValueAsString(response);
            }

            try {
                DevlogProjectContextClient.AiTaskStatusResponse taskStatus =
                        devlogProjectContextClient.getAiTaskStatus(aiTaskId);
                if ("FAILED".equals(taskStatus.status())) {
                    log.warn("Story context analysis task failed: aiTaskId={}, failureCode={}",
                            aiTaskId, taskStatus.failureCode());
                    return buildTerminalFailureResponse(aiTaskId, taskStatus);
                }
            } catch (Exception e) {
                log.debug("Could not check AiTask status aiTaskId={}: {}", aiTaskId, e.getMessage());
            }
        }

        log.warn("Story context analysis timed out: aiTaskId={}", aiTaskId);
        return buildErrorResponse(
                "Analysis did not complete within " + TIMEOUT_SECONDS + " seconds. " +
                "The analysis may still be processing. AI Task ID: " + aiTaskId,
                aiTaskId
        );
    }

    private StoryContextAnalysisResponse pollForAnalysis(UUID aiTaskId) {
        try {
            return devlogProjectContextClient.getStoryContextAnalysis(aiTaskId);
        } catch (Exception e) {
            log.debug("Poll for analysis aiTaskId={} returned error: {}", aiTaskId, e.getMessage());
            return null;
        }
    }

    private String buildTerminalFailureResponse(UUID aiTaskId,
            DevlogProjectContextClient.AiTaskStatusResponse taskStatus) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "error", "Analysis task failed: " + (taskStatus.failureMessage() != null ? taskStatus.failureMessage() : "unknown error"),
                    "aiTaskId", aiTaskId.toString(),
                    "status", "TASK_FAILED",
                    "failureCode", taskStatus.failureCode() != null ? taskStatus.failureCode() : "UNKNOWN"
            ));
        } catch (Exception e) {
            return "{\"error\":\"Analysis task failed\",\"aiTaskId\":\"" + aiTaskId + "\",\"status\":\"TASK_FAILED\"}";
        }
    }

    private String buildErrorResponse(String message, UUID aiTaskId) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "error", message,
                    "aiTaskId", aiTaskId.toString(),
                    "status", "TIMEOUT_OR_NOT_READY"
            ));
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\",\"aiTaskId\":\"" + aiTaskId + "\"}";
        }
    }
}