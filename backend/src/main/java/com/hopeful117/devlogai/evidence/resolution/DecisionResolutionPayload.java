package com.hopeful117.devlogai.evidence.resolution;

import java.time.Instant;
import java.util.UUID;

public record DecisionResolutionPayload(
        UUID id, UUID projectId, UUID proposalId, String title, String context,
        String choice, String rationale, String consequences, Instant createdAt, Instant updatedAt
) implements EvidenceResolutionPayload {
}
