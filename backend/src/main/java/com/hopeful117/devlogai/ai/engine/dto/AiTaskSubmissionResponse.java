package com.hopeful117.devlogai.ai.engine.dto;

import java.time.Instant;
import java.util.UUID;

public record AiTaskSubmissionResponse(
        UUID correlationId,
        boolean accepted,
        String externalJobId,
        Instant acceptedAt
) {
}
