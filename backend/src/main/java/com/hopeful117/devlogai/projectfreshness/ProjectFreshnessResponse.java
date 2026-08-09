package com.hopeful117.devlogai.projectfreshness;

import java.time.Instant;
import java.util.UUID;

public record ProjectFreshnessResponse(
        String version,
        UUID id,
        UUID projectId,
        Source source,
        Instant checkedAt,
        ProjectFreshnessStatus status,
        ProjectRefreshGuidance guidance,
        Baseline baseline,
        ReviewCounts review
) {
    public static final String PROJECTION_VERSION = "project-freshness-v1";

    public record Source(UUID id, String name, String defaultBranch,
                         String requestedRevision, String currentRevision) { }
    public record Baseline(UUID analysisId, Instant completedAt, String analyzedRevision) { }
    public record ReviewCounts(long total, long pending, long accepted, long rejected) { }
}
