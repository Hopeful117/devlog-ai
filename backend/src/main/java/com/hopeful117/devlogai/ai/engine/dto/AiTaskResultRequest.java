package com.hopeful117.devlogai.ai.engine.dto;

import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;

import java.time.Instant;
import java.util.List;
import java.util.Comparator;
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
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    public AiTaskResultRequest {
        interactionTraces = interactionTraces == null ? List.of() : List.copyOf(interactionTraces);
    }

    /** Validates payloads decoded after raw-body HMAC authentication. */
    public void validateCallbackContract() {
        if (correlationId == null || status == null || completedAt == null || proposals == null) {
            throw new InvalidAiTaskResultException("AI task result callback is missing required fields");
        }
        for (AiProposalResult proposal : proposals) {
            if (proposal == null || proposal.type() == null || proposal.payload() == null
                    || proposal.confidence() == null) {
                throw new InvalidAiTaskResultException("AI task result callback contains an incomplete proposal");
            }
        }
        validateInteractionTraces(interactionTraces);
    }

    /**
     * Validates traces at the domain callback boundary as well as at REST
     * deserialization. Direct callers and the isolated trace transaction must
     * enforce the same Bean Validation contract before any mutation.
     */
    public static void validateInteractionTraces(List<AiInteractionTraceRequest> traces) {
        if (traces == null) {
            return;
        }
        for (int index = 0; index < traces.size(); index++) {
            AiInteractionTraceRequest trace = traces.get(index);
            if (trace == null) {
                throw new InvalidAiTaskResultException(
                        "AI task result callback contains a null interaction trace at index " + index);
            }
            List<String> violations = VALIDATOR.validate(trace).stream()
                    .sorted(Comparator.comparing((ConstraintViolation<AiInteractionTraceRequest> violation) ->
                            violation.getPropertyPath().toString())
                            .thenComparing(ConstraintViolation::getMessage))
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .toList();
            if (!violations.isEmpty()) {
                throw new InvalidAiTaskResultException(
                        "Invalid interaction trace at index " + index + ": " + String.join(", ", violations));
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
