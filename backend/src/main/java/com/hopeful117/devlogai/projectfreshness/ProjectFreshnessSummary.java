package com.hopeful117.devlogai.projectfreshness;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Project freshness across active sources.
 *
 * <p>{@code status} and {@code guidance} are always the Core aggregation of
 * {@code checkedSources} (Story 0238). A supplied value that differs from the
 * aggregation is rejected, so no caller can publish a status Core did not derive.
 */
public record ProjectFreshnessSummary(
        String version,
        UUID projectId,
        List<ProjectFreshnessResponse> checkedSources,
        int uncheckedSourceCount,
        boolean truncated,
        ProjectFreshnessStatus status,
        ProjectRefreshGuidance guidance
) {
    public static final String PROJECTION_VERSION = "project-freshness-summary-v1";

    public ProjectFreshnessSummary {
        checkedSources = List.copyOf(checkedSources);
        ProjectFreshnessAggregation.Result aggregate =
                ProjectFreshnessAggregation.aggregate(checkedSources, uncheckedSourceCount, truncated);
        if ((status != null && status != aggregate.status())
                || (guidance != null && guidance != aggregate.guidance())) {
            throw new IllegalArgumentException("project freshness status must be the Core aggregation");
        }
        status = aggregate.status();
        guidance = aggregate.guidance();
    }

    public ProjectFreshnessSummary(String version, UUID projectId, List<ProjectFreshnessResponse> checkedSources,
                                   int uncheckedSourceCount, boolean truncated) {
        this(version, projectId, Objects.requireNonNull(checkedSources, "checkedSources"),
                uncheckedSourceCount, truncated, null, null);
    }
}
