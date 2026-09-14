package com.hopeful117.devlogai.ai.interactiontrace.dto;

import com.hopeful117.devlogai.ai.interactiontrace.entity.AiInteractionTrace;

import java.time.Instant;
import java.util.UUID;

public record AiInteractionTraceResponse(
        UUID id, UUID aiTaskId, UUID analysisId, UUID correlationId,
        int attempt, String interactionType, String traceLevel,
        String provider, String modelIdentifier, String intent, String intentVersion,
        String promptVersion, Instant startedAt, Instant completedAt, long durationMs,
        String validationStatus, String failureCategory, String retryReason,
        String selectedKnowledgeFingerprint, String promptFingerprint,
        int selectedFactCount, int selectedObservationCount, int selectedInsightCount,
        int selectedEngineeringEventCount, String groundingFingerprint,
        Integer inputTokens, Integer outputTokens, Integer totalTokens,
        String traceId, String spanId, String systemPrompt, String userPrompt,
        String rawModelResponse, Object parsedModelResponse,
        String validationDiagnostics, Instant expiresAt
) {
    public static AiInteractionTraceResponse from(AiInteractionTrace trace) {
        return new AiInteractionTraceResponse(
                trace.getId(), trace.getAiTask().getId(), trace.getAnalysis().getId(),
                trace.getCorrelationId(), trace.getAttempt(), trace.getInteractionType(),
                trace.getTraceLevel(), trace.getProvider(), trace.getModelIdentifier(),
                trace.getIntent(), trace.getIntentVersion(), trace.getPromptVersion(),
                trace.getStartedAt(), trace.getCompletedAt(), trace.getDurationMs(),
                trace.getValidationStatus(), trace.getFailureCategory(), trace.getRetryReason(),
                trace.getSelectedKnowledgeFingerprint(), trace.getPromptFingerprint(),
                trace.getSelectedFactCount(), trace.getSelectedObservationCount(),
                trace.getSelectedInsightCount(), trace.getSelectedEngineeringEventCount(),
                trace.getGroundingFingerprint(), trace.getInputTokens(), trace.getOutputTokens(),
                trace.getTotalTokens(), trace.getTraceId(), trace.getSpanId(),
                trace.getSystemPrompt(), trace.getUserPrompt(), trace.getRawModelResponse(),
                trace.getParsedModelResponse(), trace.getValidationDiagnostics(), trace.getExpiresAt()
        );
    }
}
