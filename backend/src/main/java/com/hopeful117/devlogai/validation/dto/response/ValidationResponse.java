package com.hopeful117.devlogai.validation.dto.response;

import com.hopeful117.devlogai.validation.entity.ValidationDecision;

import java.time.Instant;
import java.util.UUID;

public record ValidationResponse(
        UUID id,

        UUID proposalId,

        ValidationDecision decision,

        Instant validatedAt,

        UUID validatedBy,

        String comment
) {
}
