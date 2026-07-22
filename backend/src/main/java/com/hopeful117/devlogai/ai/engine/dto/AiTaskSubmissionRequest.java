package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.UUID;

public record AiTaskSubmissionRequest(
        UUID correlationId,
        AiTaskType taskType,
        UUID analysisId,
        IntentDefinition intent,
        UserGuidance userGuidance,
        AnalysisContext context
) {
    public AiTaskSubmissionRequest {
        java.util.Objects.requireNonNull(correlationId, "correlationId");
        java.util.Objects.requireNonNull(taskType, "taskType");
        java.util.Objects.requireNonNull(analysisId, "analysisId");
        java.util.Objects.requireNonNull(intent, "intent");
        java.util.Objects.requireNonNull(context, "context");
    }

    public AiTaskSubmissionRequest(UUID correlationId, AiTaskType taskType, UUID analysisId,
                                   IntentDefinition intent, AnalysisContext context) {
        this(correlationId, taskType, analysisId, intent, null, context);
    }
}
