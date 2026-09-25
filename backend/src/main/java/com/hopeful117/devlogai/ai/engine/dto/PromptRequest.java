package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;

import java.util.Collections;
import java.util.LinkedHashMap;
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
        Map<String, Object> selectedKnowledge,
        Map<String, Object> expectedOutputContract,
        Map<String, Object> groundingContract,
        Map<String, Object> metadata,
        String contextDigest,
        String selectionDigest,
        String projectionDigest
) {
    public PromptRequest(UUID requestId, UUID correlationId, UUID analysisId, UUID aiTaskId,
                         AiTaskType taskType, IntentDefinition intent, UserGuidance userGuidance,
                         Map<String, Object> selectedKnowledge, Map<String, Object> expectedOutputContract,
                         Map<String, Object> groundingContract, Map<String, Object> metadata) {
        this(requestId, correlationId, analysisId, aiTaskId, taskType, intent, userGuidance,
                selectedKnowledge, expectedOutputContract, groundingContract, metadata,
                string(metadata, "contextDigest"), string(metadata, "selectionDigest"),
                string(metadata, "projectionDigest"));
    }
    private static String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : value.toString();
    }
    public PromptRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(aiTaskId, "aiTaskId");
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(selectedKnowledge, "selectedKnowledge");
        selectedKnowledge = Collections.unmodifiableMap(
                new LinkedHashMap<>(selectedKnowledge)
        );
        expectedOutputContract = Map.copyOf(expectedOutputContract);
        groundingContract = groundingContract == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(groundingContract));
        metadata = Map.copyOf(metadata);
    }
}
