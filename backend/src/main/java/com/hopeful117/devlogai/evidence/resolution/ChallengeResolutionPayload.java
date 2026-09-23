package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import java.time.Instant;
import java.util.UUID;

public record ChallengeResolutionPayload(
        UUID id, UUID projectId, String title, String description, String impact,
        ChallengeStatus status, String resolution, Instant createdAt, Instant updatedAt
) implements EvidenceResolutionPayload {
}
