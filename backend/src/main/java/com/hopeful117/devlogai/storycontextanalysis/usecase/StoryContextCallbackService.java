package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.engine.dto.PromptExecutionMetadata;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

/** Owns callback state transitions and persistence after contract validation. */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryContextCallbackService {
    private final AiTaskRepository aiTaskRepository;
    private final EngineeringStoryRepository storyRepository;
    private final StoryContextAnalysisRepository storyContextAnalysisRepository;
    private final ObjectMapper objectMapper;
    private final StoryContextCallbackIdentityValidator callbackIdentityValidator;

    @Transactional
    public void handle(UUID correlationId, AiTaskResultRequest request) {
        request.validateCallbackContract();
        AiTask task = aiTaskRepository.findByCorrelationIdForUpdate(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("AI task correlation", correlationId));

        if (task.getStatus().isTerminal()) {
            log.info("Duplicate callback for completed task correlationId={}", correlationId);
            return;
        }

        if (request.status() == AiTaskResultStatus.FAILED) {
            callbackIdentityValidator.validate(task, request.promptExecution());
            task.setStatus(AiTaskStatus.FAILED);
            task.setFailureCode(request.error().code());
            task.setFailureMessage(request.error().message());
            task.setCompletedAt(request.completedAt());
            aiTaskRepository.save(task);
            return;
        }

        callbackIdentityValidator.validate(task, request.promptExecution());
        StoryContextAnalysisResult analysisResult = request.analysisResult();
        if (analysisResult == null) {
            throw new IllegalStateException("Story Context Analysis callback must include analysisResult");
        }
        analysisResult = StoryContextResultValidator.validate(analysisResult, task);

        UUID storyId = callbackStoryId(task);
        @SuppressWarnings("unchecked")
        Map<String, Object> contextFreshness = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("contextFreshness")
                : null;

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .story(storyId == null ? null : storyRepository.findById(storyId).orElseThrow())
                .aiTask(task)
                .analysisSnapshot(objectMapper.convertValue(analysisResult, Map.class))
                .contextDigest(task.getContextDigest())
                .promptExecutionMetadata(objectMapper.convertValue(request.promptExecution(), Map.class))
                .contextFreshness(contextFreshness)
                .build();
        storyContextAnalysisRepository.save(analysis);

        task.setStatus(AiTaskStatus.COMPLETED);
        task.setCompletedAt(request.completedAt());
        PromptExecutionMetadata metadata = request.promptExecution();
        task.setPromptVersion(metadata.promptVersion());
        task.setProvider(metadata.provider());
        task.setModelIdentifier(metadata.modelIdentifier());
        task.setPromptContentDigest(metadata.promptContentDigest());
        aiTaskRepository.save(task);
    }

    private static UUID callbackStoryId(AiTask task) {
        Map<String, Object> snapshot = task.getContextSnapshot();
        Object rawStoryId = null;
        if (snapshot != null && snapshot.get("scope") instanceof Map<?, ?> scope) {
            rawStoryId = scope.get("storyId");
        }
        if (rawStoryId == null && snapshot != null) rawStoryId = snapshot.get("storyId");
        return rawStoryId == null ? null : UUID.fromString(rawStoryId.toString());
    }
}
