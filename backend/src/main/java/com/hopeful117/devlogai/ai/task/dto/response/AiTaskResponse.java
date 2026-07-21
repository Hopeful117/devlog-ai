package com.hopeful117.devlogai.ai.task.dto.response;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record AiTaskResponse(
        UUID id,
        UUID analysisId,
        UUID correlationId,
        AiTaskType taskType,
        AiTaskStatus status,
        JsonNode contextSnapshot,
        String externalJobId,
        int attemptCount,
        String failureCode,
        String failureMessage,
        Instant createdAt,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt
) {
}
