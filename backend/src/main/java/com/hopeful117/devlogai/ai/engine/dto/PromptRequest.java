package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record PromptRequest(
        UUID requestId,
        UUID correlationId,
        UUID analysisId,
        UUID aiTaskId,
        AiTaskType taskType,
        IntentDefinition intent,
        UserGuidance userGuidance,
        AnalysisContext context,
        Map<String, Object> expectedOutputContract,
        Map<String, Object> metadata
) {
    public PromptRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(aiTaskId, "aiTaskId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(context, "context");
        expectedOutputContract = Map.copyOf(expectedOutputContract);
        metadata = Map.copyOf(metadata);
    }
}
