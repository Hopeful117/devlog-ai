package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record AiTaskResultRequest(
        @NotNull UUID correlationId,
        String externalJobId,
        @NotNull AiTaskResultStatus status,
        @NotNull Instant completedAt,
        @NotNull List<@Valid AiProposalResult> proposals,
        @Valid AiTaskResultError error,
        @Valid PromptExecutionMetadata promptExecution,
        @Valid AnalysisSynthesisResult synthesis,
        @Valid StoryContextAnalysisResult analysisResult,
        @Valid List<AiInteractionTraceRequest> interactionTraces
) {
    public AiTaskResultRequest {
        interactionTraces = interactionTraces == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(interactionTraces));
    }

    /** Validates payloads decoded after raw-body HMAC authentication. */
    public void validateCallbackContract() {
        if (correlationId == null || status == null || completedAt == null || proposals == null) {
            throw new InvalidAiTaskResultException("AI task result callback is missing required fields");
        }
        AiTaskResultCallbackContractValidator.validateInteractionTraces(interactionTraces);
        for (AiProposalResult proposal : proposals) {
            if (proposal == null || proposal.type() == null || proposal.payload() == null
                    || proposal.confidence() == null) {
                throw new InvalidAiTaskResultException("AI task result callback contains an incomplete proposal");
            }
        }
    }

    /** Validates payloads decoded after raw-body HMAC authentication. */
    public void validateCallbackContract() {
        if (correlationId == null || status == null || completedAt == null || proposals == null) {
            throw new InvalidAiTaskResultException("AI task result callback is missing required fields");
        }
        AiTaskResultCallbackContractValidator.validateInteractionTraces(interactionTraces);
        for (AiProposalResult proposal : proposals) {
            if (proposal == null || proposal.type() == null || proposal.payload() == null
                    || proposal.confidence() == null) {
                throw new InvalidAiTaskResultException("AI task result callback contains an incomplete proposal");
            }
        }
    }

    public AiTaskResultRequest(UUID correlationId, String externalJobId,
                               AiTaskResultStatus status, Instant completedAt,
                               List<AiProposalResult> proposals, AiTaskResultError error) {
        this(correlationId, externalJobId, status, completedAt, proposals, error,
                null, null, null, List.of());
    }

    public AiTaskResultRequest(UUID correlationId, String externalJobId,
                               AiTaskResultStatus status, Instant completedAt,
                               List<AiProposalResult> proposals, AiTaskResultError error,
                               PromptExecutionMetadata promptExecution, AnalysisSynthesisResult synthesis) {
        this(correlationId, externalJobId, status, completedAt, proposals, error,
                promptExecution, synthesis, null, List.of());
    }

    public AiTaskResultRequest(UUID correlationId, String externalJobId,
                               AiTaskResultStatus status, Instant completedAt,
                               List<AiProposalResult> proposals, AiTaskResultError error,
                               PromptExecutionMetadata promptExecution,
                               AnalysisSynthesisResult synthesis,
                               StoryContextAnalysisResult analysisResult) {
        this(correlationId, externalJobId, status, completedAt, proposals, error,
                promptExecution, synthesis, analysisResult, List.of());
    }
}
