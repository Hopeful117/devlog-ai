package com.hopeful117.devlogai.projectfreshness;

import java.util.List;
import java.util.UUID;

public record ProjectFreshnessSummary(
        String version,
        UUID projectId,
        List<ProjectFreshnessResponse> checkedSources,
        int uncheckedSourceCount,
        boolean truncated
) {
    public static final String PROJECTION_VERSION = "project-freshness-summary-v1";

    public ProjectFreshnessSummary {
        checkedSources = List.copyOf(checkedSources);
    }
}
