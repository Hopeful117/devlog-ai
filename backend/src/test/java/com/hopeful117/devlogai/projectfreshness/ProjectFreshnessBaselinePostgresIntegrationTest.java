package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the baseline-comparability drift introduced by story
 * 0085 (ADR-062): freshness baselines are revision-pinned describe-project
 * snapshots — {@code targetRevision IS NULL} is no longer the comparability
 * criterion, AI completion must not gate deterministic baselines, and
 * compensated runs must never anchor freshness.
 */
@SpringBootTest
@Testcontainers
class ProjectFreshnessBaselinePostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectProfileSnapshotRepository profiles;

    @Test
    void post0085CompletedRevisionPinnedSnapshotBecomesTheFreshnessBaseline() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = insertProjectWithSource(projectId);

        UUID analysisId = UUID.randomUUID();
        insertUnderstandingAnalysis(analysisId, projectId, sourceId,
                "COMPLETED", "c".repeat(40), OffsetDateTime.now());
        insertSnapshot(analysisId, projectId, sourceId, "c".repeat(40));

        var baseline = profiles.findLatestComparable(projectId, sourceId,
                PageRequest.of(0, 1));

        assertEquals(1, baseline.size(),
                "a post-0085 revision-pinned completed analysis must be selectable "
                        + "as freshness baseline");
        assertEquals("c".repeat(40),
                baseline.get(0).getResolvedRevisions().get(sourceId.toString()));
    }

    @Test
    void inProgressSnapshotWithDeterministicBaselineIsEligible() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = insertProjectWithSource(projectId);

        UUID analysisId = UUID.randomUUID();
        insertUnderstandingAnalysis(analysisId, projectId, sourceId,
                "IN_PROGRESS", "b".repeat(40), OffsetDateTime.now());
        insertSnapshot(analysisId, projectId, sourceId, "b".repeat(40));

        var baseline = profiles.findLatestComparable(projectId, sourceId,
                PageRequest.of(0, 1));

        assertEquals(1, baseline.size(),
                "an in-progress analysis whose deterministic snapshot already exists "
                        + "must anchor freshness before the async AI tail completes");
    }

    @Test
    void newestComparableSnapshotWinsAndLegacyRowsRemainEligible() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = insertProjectWithSource(projectId);
        OffsetDateTime base = OffsetDateTime.now();

        UUID legacyId = UUID.randomUUID();
        insertUnderstandingAnalysis(legacyId, projectId, sourceId,
                "COMPLETED", null, base.minusHours(3));
        insertSnapshot(legacyId, projectId, sourceId, "a".repeat(40));

        UUID pinnedInProgressId = UUID.randomUUID();
        insertUnderstandingAnalysis(pinnedInProgressId, projectId, sourceId,
                "IN_PROGRESS", "b".repeat(40), base.minusHours(2));
        insertSnapshot(pinnedInProgressId, projectId, sourceId, "b".repeat(40));

        UUID pinnedCompletedId = UUID.randomUUID();
        insertUnderstandingAnalysis(pinnedCompletedId, projectId, sourceId,
                "COMPLETED", "c".repeat(40), base.minusHours(1));
        insertSnapshot(pinnedCompletedId, projectId, sourceId, "c".repeat(40));

        var latest = profiles.findLatestComparable(projectId, sourceId,
                PageRequest.of(0, 1));
        assertEquals(1, latest.size());
        assertEquals(pinnedCompletedId, latest.get(0).getAnalysis().getId(),
                "the newest comparable observation wins regardless of AI completion");

        var all = profiles.findLatestComparable(projectId, sourceId, PageRequest.of(0, 10));
        assertEquals(List.of(pinnedCompletedId, pinnedInProgressId, legacyId),
                all.stream().map(snapshot -> snapshot.getAnalysis().getId()).toList(),
                "ordering follows creation time, newest observation first");
    }

    @Test
    void failedRunsNeverAnchorFreshness() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = insertProjectWithSource(projectId);
        OffsetDateTime base = OffsetDateTime.now();

        UUID failedId = UUID.randomUUID();
        insertUnderstandingAnalysis(failedId, projectId, sourceId,
                "FAILED", "d".repeat(40), base);
        insertSnapshot(failedId, projectId, sourceId, "d".repeat(40));

        assertTrue(profiles.findLatestComparable(projectId, sourceId,
                        PageRequest.of(0, 1)).isEmpty(),
                "a compensated run must not become the knowledge baseline");
    }

    private UUID insertProjectWithSource(UUID projectId) {
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, projectId, "Freshness project " + projectId,
                "freshness-" + projectId,
                OffsetDateTime.now(), OffsetDateTime.now());
        UUID sourceId = UUID.randomUUID();
        jdbc.update("""
                insert into sources
                    (id, project_id, type, name, repository_url, default_branch, provider,
                     active, created_at, updated_at)
                values (?, ?, 'GIT', 'repo', 'https://example.test/repository.git',
                        'main', 'GITHUB', true, ?, ?)
                """, sourceId, projectId, OffsetDateTime.now(), OffsetDateTime.now());
        return sourceId;
    }

    private void insertUnderstandingAnalysis(UUID id, UUID projectId, UUID sourceId,
            String status, String targetRevision, OffsetDateTime createdAt) {
        jdbc.update("""
                insert into analyses
                    (id, project_id, selected_source_id, selected_source_snapshot,
                     type, status, intent_id, intent_version, target_revision,
                     created_at, updated_at)
                values (?, ?, ?, cast(? as jsonb), 'ARCHITECTURE_REVIEW', ?,
                        'describe-project', 'v1', ?, ?, ?)
                """, id, projectId, sourceId,
                "{\"id\":\"" + sourceId + "\"}", status, targetRevision,
                createdAt, OffsetDateTime.now());
    }

    private void insertSnapshot(UUID analysisId, UUID projectId, UUID sourceId,
            String resolvedRevision) {
        jdbc.update("""
                insert into project_profile_snapshots
                    (id, project_id, analysis_id, profile_version, renderer_version,
                     generated_at, resolved_revisions, completeness_status,
                     collection_complete, truncated, warning_count, error_count,
                     successful_collector_count, collectors_with_warnings_count,
                     failed_collector_count, sections, deterministic_summary,
                     source_observations, characteristic_count)
                values (?, ?, ?, 'v1', 'v1', ?, jsonb_build_object(?, ?),
                        'COMPLETE', true, false, 0, 0, 7, 0, 0, '[]'::jsonb,
                        'summary', '[]'::jsonb, 0)
                """, UUID.randomUUID(), projectId, analysisId,
                OffsetDateTime.now(), sourceId.toString(), resolvedRevision);
    }
}
