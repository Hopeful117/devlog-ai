package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;

import java.util.UUID;

public record AiTaskSubmissionRequest(
        UUID correlationId,
        AiTaskType taskType,
        UUID analysisId,
        AnalysisContext context
) {
}
