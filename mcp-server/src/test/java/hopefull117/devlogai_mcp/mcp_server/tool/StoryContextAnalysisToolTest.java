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
