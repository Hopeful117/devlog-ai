package com.hopeful117.devlogai.ai.task.dto.request;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAiTaskRequest(
        @NotNull UUID analysisId,
        @NotNull AiTaskType taskType
) {
}
