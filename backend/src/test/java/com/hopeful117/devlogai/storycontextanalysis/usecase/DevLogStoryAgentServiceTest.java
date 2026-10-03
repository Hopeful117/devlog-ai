package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DevLogStoryAgentServiceTest {

    @Mock
    private AnalyzeStoryContextUseCase analyzeStoryContextUseCase;
    @Mock
    private AiTaskService aiTaskService;
    @Mock
    private StoryContextAnalysisQueryService analysisQueryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void submitsOnceAndReturnsProcessingExecutionWithoutResult() {
        UUID taskId = UUID.randomUUID();
        DevLogStoryAgentRequest request = request();
        when(analyzeStoryContextUseCase.prepare("project", null,
                "engineering-story-context-analysis", List.of("README.md"), "question"))
                .thenReturn(mock(PreparedStoryContext.class));
        when(analyzeStoryContextUseCase.executePrepared(any(),
                eq(Map.of("focus", "question")), eq("key")))
                .thenReturn(taskId);
        when(aiTaskService.getById(taskId)).thenReturn(task(taskId, AiTaskStatus.PROCESSING));

        DevLogStoryAgentExecution execution = service().execute(request);

        assertEquals(AiTaskStatus.PROCESSING, execution.status());
        assertEquals(Map.of(), execution.result());
        assertEquals(Map.of("status", "PROCESSING"), execution.diagnostics());
        verify(analyzeStoryContextUseCase).prepare("project", null,
                "engineering-story-context-analysis", List.of("README.md"), "question");
        verify(analyzeStoryContextUseCase).executePrepared(any(),
                eq(Map.of("focus", "question")), eq("key"));
        verifyNoInteractions(analysisQueryService);
    }

    @Test
    void readsCompletedAnalysisAndPreservesDigests() {
        UUID taskId = UUID.randomUUID();
        AiTaskResponse task = task(taskId, AiTaskStatus.COMPLETED);
        when(analyzeStoryContextUseCase.prepare(any(), any(),
                eq("engineering-story-context-analysis"), any(), eq("question")))
                .thenReturn(mock(PreparedStoryContext.class));
        when(analyzeStoryContextUseCase.executePrepared(any(),
                eq(Map.of("focus", "question")), any())).thenReturn(taskId);
        when(aiTaskService.getById(taskId)).thenReturn(task);
        when(analysisQueryService.findResponseByAiTaskId(taskId))
                .thenReturn(java.util.Optional.of(new StoryContextAnalysisResponse(null, Map.of())));

        DevLogStoryAgentExecution execution = service().execute(request());

        assertTrue(execution.result().containsKey("analysis"));
        assertEquals("context", execution.result().get("contextDigest"));
        assertEquals("projection", execution.result().get("projectionDigest"));
    }

    private DevLogStoryAgentService service() {
        return new DevLogStoryAgentService(analyzeStoryContextUseCase, aiTaskService,
                analysisQueryService, objectMapper);
    }

    private static DevLogStoryAgentRequest request() {
        return new DevLogStoryAgentRequest("project", null, "engineering-story-context-analysis", "question", List.of("README.md"), Map.of(), "key");
    }

    private static AiTaskResponse task(UUID id, AiTaskStatus status) {
        return new AiTaskResponse(id, null, null, null, null, null, Map.of(), Map.of(),
                null, null, null, null, null, "context", "projection", Map.of(), null,
                null, status, Map.of(), null, 0, null, null, null, null, null, null);
    }
}
