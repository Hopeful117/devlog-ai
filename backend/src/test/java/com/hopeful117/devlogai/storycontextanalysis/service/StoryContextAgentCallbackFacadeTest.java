package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.engine.dto.AiInteractionTraceRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultError;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class StoryContextAgentCallbackFacadeTest {
    @Test
    void malformedInteractionTracesAreRejectedBeforeRepositoryOrPersistenceInteraction() {
        AiTaskRepository aiTaskRepository = mock(AiTaskRepository.class);
        AiTaskResultService aiTaskResultService = mock(AiTaskResultService.class);
        StoryContextAgentMetrics metrics = mock(StoryContextAgentMetrics.class);
        StoryContextAgentCallbackFacade facade = new StoryContextAgentCallbackFacade(
                aiTaskRepository, aiTaskResultService, metrics);
        AiTaskResultRequest request = new AiTaskResultRequest(
                UUID.randomUUID(), "job-42", AiTaskResultStatus.FAILED, Instant.now(), List.of(),
                new AiTaskResultError("MODEL_ERROR", "Provider failed"), null, null, null,
                Collections.singletonList((AiInteractionTraceRequest) null));

        assertThrows(InvalidAiTaskResultException.class,
                () -> facade.callback(UUID.randomUUID(), request));

        verifyNoInteractions(aiTaskRepository, aiTaskResultService, metrics);
    }
}
