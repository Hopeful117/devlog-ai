package com.hopeful117.devlogai.projectfreshness;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProjectFreshnessClassifierTest {
    private static final String A = "a".repeat(40);
    private static final String B = "b".repeat(40);
    private final ProjectFreshnessClassifier classifier = new ProjectFreshnessClassifier();

    @Test
    void shouldClassifyEveryFreshnessOutcome() {
        assertEquals(ProjectFreshnessStatus.NO_BASELINE,
                classifier.classify(false, A, null).status());
        assertEquals(ProjectFreshnessStatus.CURRENT,
                classifier.classify(true, A.toUpperCase(), A).status());
        assertEquals(ProjectFreshnessStatus.STALE,
                classifier.classify(true, A, B).status());
        assertEquals(ProjectFreshnessStatus.UNKNOWN,
                classifier.classify(true, A, "short").status());
        assertEquals(ProjectRefreshGuidance.REFRESH_RECOMMENDED,
                classifier.classify(true, A, B).guidance());
    }

    @Test
    void shouldClassifyPartiallyFreshWhenIngestionCaughtUpButKnowledgeBehind() {
        var classification = classifier.classify(true, A, B, A);
        assertEquals(ProjectFreshnessStatus.PARTIALLY_FRESH, classification.status());
        assertEquals(ProjectRefreshGuidance.REFRESH_RECOMMENDED, classification.guidance());
    }

    @Test
    void shouldRemainStaleWhenIngestionHasNotCaughtUpWithObservation() {
        assertEquals(ProjectFreshnessStatus.STALE,
                classifier.classify(true, A, B, B).status());
        assertEquals(ProjectFreshnessStatus.STALE,
                classifier.classify(true, A, B, null).status());
    }

    @Test
    void shouldStayCurrentWhenKnowledgeMatchesHeadRegardlessOfIngestion() {
        assertEquals(ProjectFreshnessStatus.CURRENT,
                classifier.classify(true, A, A, null).status());
    }

    @Test
    void shouldAcceptCompleteSha256ButRejectAbbreviations() {
        assertTrue(GitCommitIdentity.normalize("C".repeat(64)).isPresent());
        assertTrue(GitCommitIdentity.normalize("abc123").isEmpty());
        assertTrue(GitCommitIdentity.normalize(null).isEmpty());
    }
}
