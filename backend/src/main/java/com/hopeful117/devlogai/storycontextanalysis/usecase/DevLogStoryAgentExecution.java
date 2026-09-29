package com.hopeful117.devlogai.storycontextanalysis.usecase;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;

public record DevLogStoryAgentExecution(

        AiTaskStatus status,
        UUID aiTaskId,
        UUID snapshotId,
        Map<String, Object> result,
        Map<String, Object> diagnostics) {
    public DevLogStoryAgentExecution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(aiTaskId, "aiTaskId");
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (!aiTaskId.equals(snapshotId)) {
            throw new IllegalArgumentException("snapshotId must equal aiTaskId");
        }
        result = Map.copyOf(result);
        diagnostics = Map.copyOf(diagnostics);
    }
}
