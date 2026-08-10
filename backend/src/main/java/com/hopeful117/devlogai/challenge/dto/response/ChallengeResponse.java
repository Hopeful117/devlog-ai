package com.hopeful117.devlogai.challenge.dto.response;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;

import java.time.Instant;
import java.util.UUID;

public record ChallengeResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        String impact,
        ChallengeStatus status,
        String resolution,
        Instant createdAt,
        Instant updatedAt
) {
}
