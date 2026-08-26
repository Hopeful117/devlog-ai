package com.hopeful117.devlogai.projectfreshness;

import org.springframework.stereotype.Component;

/**
 * Classifies freshness from the three per-source checkpoints:
 *
 * <pre>
 * observed (currentRevision) = revision seen from the repository
 * ingestedRevision           = highest revision whose deterministic SYNC completed
 * baselineRevision           = revision represented by Understanding knowledge
 * </pre>
 *
 * STALE           = knowledge behind AND deterministic ingestion also behind
 * PARTIALLY_FRESH = knowledge behind BUT deterministic repository state synchronized
 * CURRENT         = knowledge at the observed revision
 */
@Component
class ProjectFreshnessClassifier {
    Classification classify(boolean baselineExists, String currentRevision,
            String baselineRevision) {
        return classify(baselineExists, currentRevision, baselineRevision, null);
    }

    Classification classify(boolean baselineExists, String currentRevision,
            String baselineRevision, String ingestedRevision) {
        if (!baselineExists) return new Classification(
                ProjectFreshnessStatus.NO_BASELINE,
                ProjectRefreshGuidance.ESTABLISH_BASELINE);
        var current = GitCommitIdentity.normalize(currentRevision);
        var baseline = GitCommitIdentity.normalize(baselineRevision);
        if (current.isEmpty() || baseline.isEmpty()) return new Classification(
                ProjectFreshnessStatus.UNKNOWN,
                ProjectRefreshGuidance.VERIFY_BASELINE);
        boolean equal = current.get().equals(baseline.get());
        if (equal) return new Classification(
                ProjectFreshnessStatus.CURRENT,
                ProjectRefreshGuidance.REFRESH_NOT_NEEDED);
        var ingested = GitCommitIdentity.normalize(ingestedRevision);
        boolean ingestedCaughtUp = ingested.isPresent()
                && ingested.get().equals(current.get());
        return new Classification(
                ingestedCaughtUp ? ProjectFreshnessStatus.PARTIALLY_FRESH
                        : ProjectFreshnessStatus.STALE,
                ProjectRefreshGuidance.REFRESH_RECOMMENDED);
    }

    record Classification(ProjectFreshnessStatus status, ProjectRefreshGuidance guidance) { }
}
