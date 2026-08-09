package com.hopeful117.devlogai.engineeringevent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EngineeringEventResponse(
        String version, UUID id, UUID projectId, UUID analysisId, UUID proposalId,
        UUID validationId, UUID sourceId, EngineeringEventCategory category, String title,
        String summary, String significance, String baseCommit, String targetCommit,
        EvolutionComparisonPolicy comparisonPolicy, boolean mergeCommit, Instant occurredAt,
        Instant createdAt, BigDecimal confidence, List<UUID> supportingFactIds,
        List<UUID> supportingObservationIds, List<String> evidenceReferences) {
    public static final String PROJECTION_VERSION = "engineering-event-v1";
}
