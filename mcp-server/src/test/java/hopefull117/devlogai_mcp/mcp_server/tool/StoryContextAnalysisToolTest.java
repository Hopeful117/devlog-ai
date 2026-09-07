package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StoryContextAnalysisToolTest {

    private DevlogProjectContextClient devlogProjectContextClient;
    private StoryContextAnalysisTool storyContextAnalysisTool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        devlogProjectContextClient = mock(DevlogProjectContextClient.class);
        storyContextAnalysisTool = new StoryContextAnalysisTool(
                devlogProjectContextClient,
                objectMapper
        );
    }

    @Test
    void shouldSubmitAndRetrieveAnalysisOnFirstPoll() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        var analysisResult = new StoryContextAnalysisResult(
                new StoryContextAnalysisResult.ObjectiveUnderstanding("Test summary"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                StoryContextAnalysisResult.Confidence.HIGH,
                new StoryContextAnalysisResult.Provenance("digest", "v1", null),
                new StoryContextAnalysisResult.OutputClassification(List.of())
        );
        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenReturn(analysisResult);

        String result = storyContextAnalysisTool.analyzeStoryContext(
                "test-project", storyId, null, null);

        assertTrue(result.contains("Test summary"));
        assertTrue(result.contains("HIGH"));

        Mockito.verify(devlogProjectContextClient).analyzeStoryContext(
                eq("test-project"), eq(storyId), any());
        Mockito.verify(devlogProjectContextClient).getStoryContextAnalysis(aiTaskId);
    }

    @Test
    void shouldStopPollingImmediatelyOnTerminalFailure() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not found"));

        var failedStatus = new DevlogProjectContextClient.AiTaskStatusResponse(
                aiTaskId, "FAILED", "AI_ENGINE_ERROR", "Python engine returned error");
        when(devlogProjectContextClient.getAiTaskStatus(aiTaskId))
                .thenReturn(failedStatus);

        long startTime = System.currentTimeMillis();
        String result = storyContextAnalysisTool.analyzeStoryContext(
                "test-project", storyId, null, null);
        long elapsed = System.currentTimeMillis() - startTime;

        assertTrue(result.contains("TASK_FAILED"), "Expected TASK_FAILED status in response");
        assertTrue(result.contains("Python engine returned error"), "Expected failure message");
        assertTrue(result.contains(aiTaskId.toString()), "Expected aiTaskId in response");
        assertTrue(elapsed < 10000, "Expected fast return on FAILED, took " + elapsed + "ms");

        verify(devlogProjectContextClient, atLeastOnce()).getStoryContextAnalysis(aiTaskId);
        verify(devlogProjectContextClient, atLeastOnce()).getAiTaskStatus(aiTaskId);
    }

    @Test
    void shouldContinuePollingWhenTaskIsNonTerminal() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        var processingStatus = new DevlogProjectContextClient.AiTaskStatusResponse(
                aiTaskId, "PROCESSING", null, null);
        when(devlogProjectContextClient.getAiTaskStatus(aiTaskId))
                .thenReturn(processingStatus);

        var analysisResult = new StoryContextAnalysisResult(
                new StoryContextAnalysisResult.ObjectiveUnderstanding("Completed"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                StoryContextAnalysisResult.Confidence.HIGH,
                new StoryContextAnalysisResult.Provenance("digest", "v1", null),
                new StoryContextAnalysisResult.OutputClassification(List.of())
        );

        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not found"))
                .thenReturn(analysisResult);

        String result = storyContextAnalysisTool.analyzeStoryContext(
                "test-project", storyId, null, null);

        assertTrue(result.contains("Completed"));
        verify(devlogProjectContextClient, atLeast(2)).getStoryContextAnalysis(aiTaskId);
        verify(devlogProjectContextClient, atLeastOnce()).getAiTaskStatus(aiTaskId);
    }

    @Test
    void shouldReturnTimeoutErrorWhenAnalysisDoesNotComplete() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenThrow(new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Not found"));

        when(devlogProjectContextClient.getAiTaskStatus(aiTaskId))
                .thenReturn(new DevlogProjectContextClient.AiTaskStatusResponse(
                        aiTaskId, "SUBMITTED", null, null));

        String result = storyContextAnalysisTool.analyzeStoryContext(
                "test-project", storyId, null, null);

        assertTrue(result.contains("TIMEOUT_OR_NOT_READY"));
        assertTrue(result.contains(aiTaskId.toString()));
    }

    @Test
    void shouldCorrelateWithAiTaskIdFromSubmitResponse() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        var analysisResult = new StoryContextAnalysisResult(
                new StoryContextAnalysisResult.ObjectiveUnderstanding("Correlated result"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                StoryContextAnalysisResult.Confidence.MEDIUM,
                new StoryContextAnalysisResult.Provenance("digest", "v1", null),
                new StoryContextAnalysisResult.OutputClassification(List.of())
        );
        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenReturn(analysisResult);

        storyContextAnalysisTool.analyzeStoryContext("test-project", storyId, null, null);

        verify(devlogProjectContextClient).getStoryContextAnalysis(aiTaskId);
    }

    @Test
    void shouldPassFilesAndGuidanceToSubmit() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        List<String> files = List.of("src/main.java");
        Map<String, Object> guidance = Map.of("key", "value");

        var submitResponse = new DevlogProjectContextClient.AnalyzeContextResponse(aiTaskId);
        when(devlogProjectContextClient.analyzeStoryContext(
                eq("test-project"), eq(storyId), any()))
                .thenReturn(submitResponse);

        var analysisResult = new StoryContextAnalysisResult(
                new StoryContextAnalysisResult.ObjectiveUnderstanding("Test"),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                StoryContextAnalysisResult.Confidence.LOW,
                new StoryContextAnalysisResult.Provenance("digest", "v1", null),
                new StoryContextAnalysisResult.OutputClassification(List.of())
        );
        when(devlogProjectContextClient.getStoryContextAnalysis(aiTaskId))
                .thenReturn(analysisResult);

        storyContextAnalysisTool.analyzeStoryContext("test-project", storyId, files, guidance);

        verify(devlogProjectContextClient).analyzeStoryContext(
                eq("test-project"), eq(storyId),
                argThat(req -> req.files().equals(files) && req.guidance().equals(guidance)));
    }
}
