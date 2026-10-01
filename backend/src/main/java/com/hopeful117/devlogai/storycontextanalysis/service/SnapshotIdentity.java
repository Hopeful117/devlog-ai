package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;

import java.time.Instant;
import java.util.UUID;

public record SnapshotIdentity(UUID taskId, UUID projectId,
                               AiTaskType taskType, Instant createdAt,
                               UUID parentSnapshotId) {
    public SnapshotIdentity(UUID taskId, UUID projectId, AiTaskType taskType, Instant createdAt) {
        this(taskId, projectId, taskType, createdAt, null);
    }
}
