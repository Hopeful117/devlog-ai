package com.hopeful117.devlogai.historicalrelationship;

import com.hopeful117.devlogai.engineeringevent.EngineeringEventCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class EventCommitReferenceLookupPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EventCommitReferenceLookup lookup;

    @Test
    void matchesBaseAndTargetRolesAndReturnsPersistedSource() {
        UUID projectId = insertProject();
        UUID sourceId = insertSource(projectId);
        String hash = "a".repeat(40);
        OffsetDateTime now = OffsetDateTime.now();
        UUID baseEventId = UUID.randomUUID();
        UUID targetEventId = UUID.randomUUID();

        insertEvent(projectId, sourceId, baseEventId, hash, "b".repeat(40),
                EngineeringEventCategory.FEATURE_INTRODUCTION, "Base event", now.minusMinutes(1));
        insertEvent(projectId, sourceId, targetEventId, "c".repeat(40), hash,
                EngineeringEventCategory.BUG_RESOLUTION, "Target event", now);

        EventCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertFalse(result.truncated());
        assertEquals(2, result.references().size());
        assertEquals(List.of(
                        CommitReferenceRole.REFERENCES_AS_TARGET,
                        CommitReferenceRole.REFERENCES_AS_BASE),
                result.references().stream().map(EventCommitReference::role).toList());
        assertEquals(targetEventId, result.references().get(0).eventId());
        assertEquals(sourceId, result.references().get(0).sourceId());
        assertEquals(EngineeringEventCategory.BUG_RESOLUTION,
                result.references().get(0).category());
    }

    @Test
    void isolatesProjectAndDoesNotUsePrefixMatching() {
        UUID projectId = insertProject();
        UUID otherProjectId = insertProject();
        UUID sourceId = insertSource(projectId);
        UUID otherSourceId = insertSource(otherProjectId);
        String hash = "d".repeat(40);

        insertEvent(projectId, sourceId, UUID.randomUUID(), "e".repeat(40),
                "d".repeat(39) + "e", EngineeringEventCategory.ARCHITECTURE_CHANGE,
                "Different full hash", OffsetDateTime.now());
        insertEvent(otherProjectId, otherSourceId, UUID.randomUUID(), hash,
                "f".repeat(40), EngineeringEventCategory.TECHNOLOGY_CHANGE,
                "Other project", OffsetDateTime.now());

        EventCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertTrue(result.references().isEmpty());
        assertFalse(result.truncated());
    }

    @Test
    void ordersByOccurredAtDescendingThenIdAscending() {
        UUID projectId = insertProject();
        UUID sourceId = insertSource(projectId);
        String hash = "1".repeat(40);
        OffsetDateTime occurredAt = OffsetDateTime.now();
        UUID lowId = new UUID(projectId.getMostSignificantBits(), 1);
        UUID highId = new UUID(projectId.getMostSignificantBits(), 2);

        insertEvent(projectId, sourceId, highId, hash, "2".repeat(40),
                EngineeringEventCategory.FEATURE_INTRODUCTION, "High id", occurredAt);
        insertEvent(projectId, sourceId, lowId, hash, "3".repeat(40),
                EngineeringEventCategory.FEATURE_INTRODUCTION, "Low id", occurredAt);
        UUID latestId = UUID.randomUUID();
        insertEvent(projectId, sourceId, latestId, hash, "4".repeat(40),
                EngineeringEventCategory.FEATURE_INTRODUCTION, "Latest", occurredAt.plusMinutes(1));

        EventCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertEquals(List.of(latestId, lowId, highId), result.references().stream()
                .map(EventCommitReference::eventId).toList());
    }

    @Test
    void exposesAtMostOneHundredAndDetectsTheExtraRow() {
        UUID projectId = insertProject();
        UUID sourceId = insertSource(projectId);
        String hash = "5".repeat(40);
        OffsetDateTime occurredAt = OffsetDateTime.now();

        for (int i = 0; i < 101; i++) {
            insertEvent(projectId, sourceId,
                    new UUID(projectId.getMostSignificantBits(), i + 1),
                    hash, String.format("%040x", i + 1000),
                    EngineeringEventCategory.ENGINEERING_IMPROVEMENT,
                    "Event " + (i + 1), occurredAt);
        }

        EventCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertTrue(result.truncated());
        assertEquals(100, result.references().size());
        assertEquals(1L, result.references().getFirst().eventId().getLeastSignificantBits());
        assertEquals(100L, result.references().getLast().eventId().getLeastSignificantBits());
    }

    private UUID insertProject() {
        UUID projectId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, projectId, "Event lookup " + projectId,
                "event-lookup-" + projectId, now, now);
        return projectId;
    }

    private UUID insertSource(UUID projectId) {
        UUID sourceId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into sources
                    (id, project_id, type, name, repository_url, active, created_at, updated_at)
                values (?, ?, 'GIT_REPOSITORY', ?, 'https://example.test/repository.git', true, ?, ?)
                """, sourceId, projectId, "Source " + sourceId, now, now);
        return sourceId;
    }

    private void insertEvent(
            UUID projectId,
            UUID sourceId,
            UUID eventId,
            String baseCommit,
            String targetCommit,
            EngineeringEventCategory category,
            String title,
            OffsetDateTime occurredAt) {
        UUID analysisId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID validationId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        jdbc.update("""
                insert into analyses
                    (id, project_id, type, status, started_at, created_at, updated_at)
                values (?, ?, 'ARCHITECTURE_OVERVIEW', 'COMPLETED', ?, ?, ?)
                """, analysisId, projectId, now, now, now);
        jdbc.update("""
                insert into validatable_proposals
                    (id, project_id, analysis_id, type, status, payload, created_at)
                values (?, ?, ?, 'ENGINEERING_EVENT', 'ACCEPTED', '{}'::jsonb, ?)
                """, proposalId, projectId, analysisId, now);
        jdbc.update("""
                insert into validations
                    (id, proposal_id, decision, validated_at, validated_by)
                values (?, ?, 'ACCEPTED', ?, ?)
                """, validationId, proposalId, now, UUID.randomUUID());
        jdbc.update("""
                insert into engineering_events
                    (id, project_id, analysis_id, proposal_id, validation_id, source_id,
                     category, title, summary, significance, base_commit, target_commit,
                     occurred_at, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 'Summary', 'Significance', ?, ?, ?, ?)
                """, eventId, projectId, analysisId, proposalId, validationId, sourceId,
                category.name(), title, baseCommit, targetCommit, occurredAt, now);
    }
}
