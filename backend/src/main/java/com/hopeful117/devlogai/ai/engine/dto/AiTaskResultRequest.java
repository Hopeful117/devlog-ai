package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiTaskResultRequest(
        @NotNull UUID correlationId,
        String externalJobId,
        @NotNull AiTaskResultStatus status,
        @NotNull Instant completedAt,
        @NotNull List<@Valid AiProposalResult> proposals,
        @Valid AiTaskResultError error,
        @Valid PromptExecutionMetadata promptExecution
) {
    public AiTaskResultRequest(UUID correlationId, String externalJobId,
                               AiTaskResultStatus status, Instant completedAt,
                               List<AiProposalResult> proposals, AiTaskResultError error) {
        this(correlationId, externalJobId, status, completedAt, proposals, error, null);
    }
}
