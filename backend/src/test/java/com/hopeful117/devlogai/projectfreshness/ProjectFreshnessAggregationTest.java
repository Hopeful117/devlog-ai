package com.hopeful117.devlogai.projectfreshness;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectFreshnessAggregationTest {

    private static ProjectFreshnessResponse source(ProjectFreshnessStatus status, ProjectRefreshGuidance guidance) {
        return new ProjectFreshnessResponse(ProjectFreshnessResponse.PROJECTION_VERSION, UUID.randomUUID(),
                UUID.randomUUID(), null, null, status, guidance, null, null);
    }

    private static ProjectFreshnessSummary summary(List<ProjectFreshnessResponse> sources, int unchecked,
                                                   boolean truncated) {
        return new ProjectFreshnessSummary(ProjectFreshnessSummary.PROJECTION_VERSION, UUID.randomUUID(),
                sources, unchecked, truncated);
    }

    @ParameterizedTest
    @EnumSource(ProjectFreshnessStatus.class)
    void singleSourceStatusIsTheProjectStatus(ProjectFreshnessStatus status) {
        ProjectFreshnessSummary result = summary(List.of(source(status, ProjectRefreshGuidance.REFRESH_RECOMMENDED)),
                0, false);

        assertEquals(status, result.status());
        assertEquals(ProjectRefreshGuidance.REFRESH_RECOMMENDED, result.guidance());
    }

    @Test
    void worstStatusWinsWithItsGuidance() {
        ProjectFreshnessSummary result = summary(List.of(
                source(ProjectFreshnessStatus.CURRENT, ProjectRefreshGuidance.REFRESH_NOT_NEEDED),
                source(ProjectFreshnessStatus.STALE, ProjectRefreshGuidance.REFRESH_RECOMMENDED),
                source(ProjectFreshnessStatus.PARTIALLY_FRESH, ProjectRefreshGuidance.REFRESH_RECOMMENDED)), 0, false);

        assertEquals(ProjectFreshnessStatus.STALE, result.status());
        assertEquals(ProjectRefreshGuidance.REFRESH_RECOMMENDED, result.guidance());
    }

    @Test
    void severityOrderPlacesUnknownAboveNoBaseline() {
        ProjectFreshnessSummary result = summary(List.of(
                source(ProjectFreshnessStatus.NO_BASELINE, ProjectRefreshGuidance.ESTABLISH_BASELINE),
                source(ProjectFreshnessStatus.UNKNOWN, ProjectRefreshGuidance.VERIFY_BASELINE)), 0, false);

        assertEquals(ProjectFreshnessStatus.UNKNOWN, result.status());
        assertEquals(ProjectRefreshGuidance.VERIFY_BASELINE, result.guidance());
    }

    @Test
    void noCheckedSourceIsUnknown() {
        ProjectFreshnessSummary result = summary(List.of(), 2, false);

        assertEquals(ProjectFreshnessStatus.UNKNOWN, result.status());
        assertEquals(ProjectRefreshGuidance.VERIFY_BASELINE, result.guidance());
    }

    @Test
    void uncheckedOrTruncatedSourcesNeverYieldCurrent() {
        List<ProjectFreshnessResponse> current =
                List.of(source(ProjectFreshnessStatus.CURRENT, ProjectRefreshGuidance.REFRESH_NOT_NEEDED));

        assertEquals(ProjectFreshnessStatus.CURRENT, summary(current, 0, false).status());
        assertEquals(ProjectFreshnessStatus.PARTIALLY_FRESH, summary(current, 1, false).status());
        assertEquals(ProjectFreshnessStatus.PARTIALLY_FRESH, summary(current, 0, true).status());
        assertEquals(ProjectRefreshGuidance.VERIFY_BASELINE, summary(current, 1, false).guidance());
    }

    @Test
    void missingSourceStatusCountsAsUnknown() {
        ProjectFreshnessSummary result = summary(List.of(
                source(ProjectFreshnessStatus.CURRENT, ProjectRefreshGuidance.REFRESH_NOT_NEEDED),
                source(null, null)), 0, false);

        assertEquals(ProjectFreshnessStatus.UNKNOWN, result.status());
        assertEquals(ProjectRefreshGuidance.VERIFY_BASELINE, result.guidance());
    }

    @Test
    void suppliedStatusMustEqualTheCoreAggregation() {
        List<ProjectFreshnessResponse> stale =
                List.of(source(ProjectFreshnessStatus.STALE, ProjectRefreshGuidance.REFRESH_RECOMMENDED));

        assertThrows(IllegalArgumentException.class, () -> new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, UUID.randomUUID(), stale, 0, false,
                ProjectFreshnessStatus.CURRENT, null));
        assertEquals(ProjectFreshnessStatus.STALE, new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, UUID.randomUUID(), stale, 0, false,
                ProjectFreshnessStatus.STALE, ProjectRefreshGuidance.REFRESH_RECOMMENDED).status());
    }
}
