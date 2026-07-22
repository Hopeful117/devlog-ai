package com.hopeful117.devlogai.profile.dto;

import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ProjectProfileResponse(
        UUID id, UUID projectId, UUID analysisId, String profileVersion,
        String rendererVersion, Instant generatedAt, String requestedRevision,
        Map<String, Object> resolvedRevisions, Completeness completeness,
        List<Map<String, Object>> sections, String deterministicSummary,
        List<Map<String, Object>> sourceObservations, int characteristicCount
) {
    public ProjectProfileResponse {
        resolvedRevisions = Map.copyOf(resolvedRevisions);
        sections = List.copyOf(sections);
        sourceObservations = List.copyOf(sourceObservations);
    }

    public record Completeness(ProfileCompletenessStatus status, boolean collectionComplete,
                               boolean truncated, int warningCount, int errorCount,
                               int successfulCollectorCount, int collectorsWithWarningsCount,
                               int failedCollectorCount) { }
}
