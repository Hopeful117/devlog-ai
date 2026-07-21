package com.hopeful117.devlogai.observation.dto.response;

import com.hopeful117.devlogai.observation.entity.ObservationType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ObservationResponse(
        UUID id,
        UUID analysisId,
        ObservationType type,
        String content,
        Set<UUID> supportingFactIds,
        Instant createdAt
) {
}
