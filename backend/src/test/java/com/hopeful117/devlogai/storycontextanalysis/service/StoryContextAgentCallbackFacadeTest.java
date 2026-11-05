package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.engine.dto.AiInteractionTraceRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultError;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoryContextAgentCallbackFacadeTest {
    @Test
    void malformedInteractionTracesAreRejectedBeforeRepositoryOrPersistenceInteraction() {
        AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
        AiTaskResultService aiTaskResultService = mock(AiTaskResultService.class);
        StoryContextAgentMetrics metrics = mock(StoryContextAgentMetrics.class);
        StoryContextAgentCallbackFacade facade = new StoryContextAgentCallbackFacade(
                aiTaskRepository, aiTaskResultService, metrics, mock(ObjectMapper.class));
        AiTaskResultRequest request = new AiTaskResultRequest(
                UUID.randomUUID(), "job-42", AiTaskResultStatus.FAILED, Instant.now(), List.of(),
                new AiTaskResultError("MODEL_ERROR", "Provider failed"), null, null, null,
                Collections.singletonList((AiInteractionTraceRequest) null));

        assertThrows(InvalidAiTaskResultException.class,
                () -> facade.callback(UUID.randomUUID(), request));

        verifyNoInteractions(aiTaskRepository, aiTaskResultService, metrics);
    }

    @Test
    void finalProviderOutputAndElapsedAreChargedBeforeCompletedCallback() {
        AiTaskRepository repository = mock(AiTaskRepository.class);
        AiTaskResultService resultService = mock(AiTaskResultService.class);
        AiTask task = new AiTask();
        UUID taskId = UUID.randomUUID();
        task.setId(taskId);
        task.setCorrelationId(UUID.randomUUID());
        task.setStatus(AiTaskStatus.PROCESSING);
        task.setContextSnapshot(new java.util.LinkedHashMap<>(Map.of(
                "investigation", Map.of("initialized", true,
                        "usage", Map.of("steps", 1, "retrievalCalls", 1, "evidenceItems", 0,
                                "bytes", 0, "tokens", 0, "elapsedMillis", 9,
                                "providerOutputTokens", 4),
                        "budget", Map.of("maximumProviderOutputTokens", 5,
                                "maximumElapsedMillis", 10)))));
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        AiInteractionTraceRequest trace = new AiInteractionTraceRequest(
                UUID.randomUUID(), 1, "FINAL_GENERATION", "NORMAL", "openai", "model",
                "intent", "v1", "prompt", Instant.now(), Instant.now(), 2, "SUCCEEDED",
                null, null, null, "0".repeat(64), "1".repeat(64), 0, 0, 0, 0,
                "2".repeat(64), 1, 2, 3, null, null, null, null, null, null, null,
                "PROVIDER", "COMPLETED", null, "tokens", "EXACT");
        AiTaskResultRequest request = new AiTaskResultRequest(
                task.getCorrelationId(), "job", AiTaskResultStatus.COMPLETED, Instant.now(),
                List.of(), null, null, null, null, List.of(trace));

        StoryContextAgentCallbackFacade facade = new StoryContextAgentCallbackFacade(
                repository, resultService, mock(StoryContextAgentMetrics.class), mock(ObjectMapper.class));

        assertThrows(com.hopeful117.devlogai.shared.exception.ConflictException.class,
                () -> facade.callback(taskId, request));
        verifyNoInteractions(resultService);
    }
}
