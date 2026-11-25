package com.hopeful117.devlogai.projectfreshness;

import java.util.List;
import java.util.Map;

/**
 * Core-owned aggregation of per-source freshness into one project status (Story 0238).
 *
 * <p>The worst checked status wins, by severity
 * {@code UNKNOWN > NO_BASELINE > STALE > PARTIALLY_FRESH > CURRENT}. When some
 * active sources were not checked, or the check list was truncated, the project
 * can never be {@code CURRENT}: it is capped at {@code PARTIALLY_FRESH}. With no
 * checked source at all the project is {@code UNKNOWN}. The guidance is the one
 * of the source that decided the status.
 */
final class ProjectFreshnessAggregation {

    private static final Map<ProjectFreshnessStatus, Integer> SEVERITY = Map.of(
            ProjectFreshnessStatus.CURRENT, 0,
            ProjectFreshnessStatus.PARTIALLY_FRESH, 1,
            ProjectFreshnessStatus.STALE, 2,
            ProjectFreshnessStatus.NO_BASELINE, 3,
            ProjectFreshnessStatus.UNKNOWN, 4);

    private ProjectFreshnessAggregation() {
    }

    static Result aggregate(List<ProjectFreshnessResponse> checkedSources, int uncheckedSourceCount,
                            boolean truncated) {
        if (checkedSources == null || checkedSources.isEmpty()) {
            return new Result(ProjectFreshnessStatus.UNKNOWN, ProjectRefreshGuidance.VERIFY_BASELINE);
        }
        ProjectFreshnessStatus worst = null;
        ProjectRefreshGuidance guidance = null;
        for (ProjectFreshnessResponse source : checkedSources) {
            ProjectFreshnessStatus status = source == null || source.status() == null
                    ? ProjectFreshnessStatus.UNKNOWN : source.status();
            if (worst == null || SEVERITY.get(status) > SEVERITY.get(worst)) {
                worst = status;
                guidance = source == null || source.guidance() == null
                        ? defaultGuidance(status) : source.guidance();
            }
        }
        if (worst == ProjectFreshnessStatus.CURRENT && (uncheckedSourceCount > 0 || truncated)) {
            return new Result(ProjectFreshnessStatus.PARTIALLY_FRESH, ProjectRefreshGuidance.VERIFY_BASELINE);
        }
        return new Result(worst, guidance);
    }

    private static ProjectRefreshGuidance defaultGuidance(ProjectFreshnessStatus status) {
        return switch (status) {
            case CURRENT -> ProjectRefreshGuidance.REFRESH_NOT_NEEDED;
            case PARTIALLY_FRESH, STALE -> ProjectRefreshGuidance.REFRESH_RECOMMENDED;
            case NO_BASELINE -> ProjectRefreshGuidance.ESTABLISH_BASELINE;
            case UNKNOWN -> ProjectRefreshGuidance.VERIFY_BASELINE;
        };
    }

    record Result(ProjectFreshnessStatus status, ProjectRefreshGuidance guidance) {
    }
}
