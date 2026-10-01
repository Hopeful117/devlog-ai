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
import java.util.List;
import java.util.Set;
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
        if (task.getParentSnapshotId() != null) {
            handleFollowUp(task, request.followUpResult());
            task.setStatus(AiTaskStatus.COMPLETED);
            task.setCompletedAt(request.completedAt());
            PromptExecutionMetadata metadata = request.promptExecution();
            task.setPromptVersion(metadata.promptVersion());
            task.setProvider(metadata.provider());
            task.setModelIdentifier(metadata.modelIdentifier());
            task.setPromptContentDigest(metadata.promptContentDigest());
            aiTaskRepository.save(task);
            return;
        }
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

    private void handleFollowUp(AiTask task, Map<String, Object> result) {
        if (result == null) throw new IllegalStateException("followUpResult is required");
        if (!task.getId().toString().equals(String.valueOf(result.get("followUpId")))) {
            throw new IllegalStateException("followUpId does not match the task");
        }
        if (!task.getParentSnapshotId().toString().equals(String.valueOf(result.get("parentSnapshotId")))) {
            throw new IllegalStateException("parentSnapshotId does not match the task");
        }
        if (!task.getId().toString().equals(String.valueOf(result.get("snapshotId")))) {
            throw new IllegalStateException("snapshotId does not match the follow-up task");
        }
        if (!task.getContextDigest().equals(result.get("contextDigest"))
                || !task.getProjectionDigest().equals(result.get("projectionDigest"))) {
            throw new IllegalStateException("Follow-up digest does not match the authorized snapshot");
        }
        Object status = result.get("status");
        if (!(status instanceof String) || !Set.of("COMPLETED", "NOT_ESTABLISHED", "FAILED", "TIMED_OUT").contains(status)) {
            throw new IllegalStateException("Invalid follow-up status");
        }
        Object nextStep = result.get("nextStep");
        if (!(nextStep instanceof Map<?, ?> next) || !Set.of("RECOMMENDED", "NEEDS_CLARIFICATION", "NO_SAFE_NEXT_STEP").contains(next.get("status"))) {
            throw new IllegalStateException("Follow-up nextStep is required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) task.getContextSnapshot().get("groundingContract");
        Set<String> allowed = AnalyzeStoryContextUseCase.allowedGroundingReferences(contract, task);
        validateReferences(result.get("evidenceReferences"), allowed);
        validateReferences(next.get("evidenceReferences"), allowed);
        task.setSynthesisSnapshot(objectMapper.convertValue(result, Map.class));
    }

    private void validateReferences(Object raw, Set<String> allowed) {
        if (!(raw instanceof List<?> references)) return;
        for (Object value : references) {
            if (!(value instanceof Map<?, ?> reference) || !(reference.get("reference") instanceof String ref)
                    || !allowed.contains(ref)) {
                throw new IllegalStateException("Follow-up contains an unauthorized grounding reference");
            }
        }
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
