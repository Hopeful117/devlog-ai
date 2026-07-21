package com.hopeful117.devlogai.validation.dto.request;

import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateValidationRequest(
        @NotNull
        UUID proposalId,

        @NotNull
        ValidationDecision decision,

        @Size(max = 2000)
        String comment,

        @NotNull
        UUID validatedBy
) {
}
