package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;

import java.time.Instant;

public record AiTaskConflictResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        AiTaskStatus currentStatus,
        String message,
        String path
) {
}
