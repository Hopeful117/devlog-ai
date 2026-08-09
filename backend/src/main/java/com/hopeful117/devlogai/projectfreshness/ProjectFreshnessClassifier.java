package com.hopeful117.devlogai.projectfreshness;

import org.springframework.stereotype.Component;

@Component
class ProjectFreshnessClassifier {
    Classification classify(boolean baselineExists, String currentRevision,
            String baselineRevision) {
        if (!baselineExists) return new Classification(
                ProjectFreshnessStatus.NO_BASELINE,
                ProjectRefreshGuidance.ESTABLISH_BASELINE);
        var current = GitCommitIdentity.normalize(currentRevision);
        var baseline = GitCommitIdentity.normalize(baselineRevision);
        if (current.isEmpty() || baseline.isEmpty()) return new Classification(
                ProjectFreshnessStatus.UNKNOWN,
                ProjectRefreshGuidance.VERIFY_BASELINE);
        boolean equal = current.get().equals(baseline.get());
        return new Classification(
                equal ? ProjectFreshnessStatus.CURRENT : ProjectFreshnessStatus.STALE,
                equal ? ProjectRefreshGuidance.REFRESH_NOT_NEEDED
                        : ProjectRefreshGuidance.REFRESH_RECOMMENDED);
    }

    record Classification(ProjectFreshnessStatus status, ProjectRefreshGuidance guidance) { }
}
