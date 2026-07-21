package com.hopeful117.devlogai.analysis.workflow.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import jakarta.validation.constraints.NotNull;

public record StartAnalysisWorkflowRequest(
        @NotNull AiTaskType taskType
) {
}
