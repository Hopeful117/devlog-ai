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
        AiTaskStatus status,
        Map<String, Object> contextSnapshot,
        String externalJobId,
        int attemptCount,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt
) {
    public AiTaskResponse(UUID id, UUID analysisId, UUID correlationId, AiTaskType taskType,
                          AiTaskStatus status, Map<String, Object> contextSnapshot,
                          String externalJobId, int attemptCount, String failureCode,
                          String failureMessage, Instant createdAt, Instant submittedAt,
                          Instant startedAt, Instant completedAt) {
        this(id, analysisId, correlationId, taskType, null, null, null, null,
                null, null, null, null, null, null, status,
                contextSnapshot, externalJobId, attemptCount, failureCode, failureMessage,
                createdAt, submittedAt, startedAt, completedAt);
    }
}
