package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AiInteractionTraceRequest(
        @NotNull UUID id,
        @Min(1) int attempt,
        @NotBlank @Size(max = 40) String interactionType,
        @NotBlank @Size(max = 20) String traceLevel,
        @NotBlank @Size(max = 100) String provider,
        @NotBlank @Size(max = 255) String modelIdentifier,
        @NotBlank @Size(max = 80) String intent,
        @NotBlank @Size(max = 20) String intentVersion,
        @NotBlank @Size(max = 100) String promptVersion,
        @NotNull Instant startedAt,
        @NotNull Instant completedAt,
        @Min(0) long durationMs,
        @NotBlank @Size(max = 40) String validationStatus,
        @Size(max = 80) String failureCategory,
        @Size(max = 5000) String retryReason,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String selectedKnowledgeFingerprint,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String promptFingerprint,
        @Min(0) int selectedFactCount,
        @Min(0) int selectedObservationCount,
        @Min(0) int selectedInsightCount,
        @Min(0) int selectedEngineeringEventCount,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String groundingFingerprint,
        @Min(0) Integer inputTokens,
        @Min(0) Integer outputTokens,
        @Min(0) Integer totalTokens,
        @Size(max = 100) String traceId,
        @Size(max = 100) String spanId,
        String systemPrompt,
        String userPrompt,
        String rawModelResponse,
        Object parsedModelResponse,
        @Size(max = 5000) String validationDiagnostics
) {
}
