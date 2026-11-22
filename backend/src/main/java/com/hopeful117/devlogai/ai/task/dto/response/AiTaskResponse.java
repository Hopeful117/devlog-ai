package com.hopeful117.devlogai.ai.task.dto.response;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiTaskResponse(
        UUID id,
        UUID analysisId,
        UUID correlationId,
        AiTaskType taskType,
        String intentId,
        String intentVersion,
        Map<String, Object> intentSnapshot,
        Map<String, Object> userGuidanceSnapshot,
        UUID promptRequestId,
        String promptVersion,
        String provider,
        String modelIdentifier,
        String promptContentDigest,
        String contextDigest,
        String projectionDigest,
        Map<String, Object> selectedKnowledgeSnapshot,
        String selectionVersion,
        String selectionDigest,
        AiTaskStatus status,
        Map<String, Object> contextSnapshot,
        String externalJobId,
        Instant aiEngineAcceptedAt,
        Long aiEngineSubmissionDurationMs,
        String aiEngineSubmissionError,
        int attemptCount,
        String failureCode,
        String failureMessage,
        String lastCallbackFailureCode,
        String lastCallbackFailureStatus,
        String lastCallbackFailureMessage,
        Instant lastCallbackFailureAt,
        Instant createdAt,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        Instant deadlineAt,
        String terminalReason,
        String recoveryAction
) {
    public AiTaskResponse(UUID id, UUID analysisId, UUID correlationId, AiTaskType taskType,
                           AiTaskStatus status, Map<String, Object> contextSnapshot,
                          String externalJobId, int attemptCount, String failureCode,
                          String failureMessage, Instant createdAt, Instant submittedAt,
                          Instant startedAt, Instant completedAt) {
        this(id, analysisId, correlationId, taskType, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, status,
                contextSnapshot, externalJobId, null, null, null, attemptCount, failureCode, failureMessage,
                null, null, null, null,
                 createdAt, submittedAt, startedAt, completedAt, null, null, null);
    }

    public AiTaskResponse(UUID id, UUID analysisId, UUID correlationId, AiTaskType taskType,
                          String intentId, String intentVersion, Map<String, Object> intentSnapshot,
                          Map<String, Object> userGuidanceSnapshot, UUID promptRequestId,
                          String promptVersion, String provider, String modelIdentifier,
                          String promptContentDigest, String contextDigest, String projectionDigest,
                          Map<String, Object> selectedKnowledgeSnapshot, String selectionVersion,
                          String selectionDigest, AiTaskStatus status, Map<String, Object> contextSnapshot,
                           String externalJobId, int attemptCount, String failureCode, String failureMessage,
                           Instant createdAt, Instant submittedAt, Instant startedAt, Instant completedAt) {
        this(id, analysisId, correlationId, taskType, intentId, intentVersion, intentSnapshot,
                userGuidanceSnapshot, promptRequestId, promptVersion, provider, modelIdentifier,
                promptContentDigest, contextDigest, projectionDigest, selectedKnowledgeSnapshot,
                selectionVersion, selectionDigest, status, contextSnapshot, externalJobId, null, null, null, attemptCount,
                failureCode, failureMessage, null, null, null, null,
                 createdAt, submittedAt, startedAt, completedAt, null, null, null);
    }
}
