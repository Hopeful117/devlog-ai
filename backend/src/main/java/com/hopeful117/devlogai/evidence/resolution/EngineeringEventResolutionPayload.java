package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import java.time.Instant;
import java.util.UUID;

public record EngineeringEventResolutionPayload(
        UUID id, UUID projectId, UUID analysisId, UUID proposalId, UUID validationId, UUID sourceId,
        EngineeringEventCategory category, String title, String summary, String significance,
        String baseCommit, String targetCommit, Instant occurredAt, Instant createdAt
) implements EvidenceResolutionPayload {
}
