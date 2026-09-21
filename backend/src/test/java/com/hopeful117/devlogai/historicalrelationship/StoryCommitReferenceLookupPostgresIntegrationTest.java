package com.hopeful117.devlogai.historicalrelationship;

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
class StoryCommitReferenceLookupPostgresIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StoryCommitReferenceLookup lookup;

    @Test
    void matchesBaseTargetBothRolesAndProjectScope() {
        UUID projectId = insertProject();
        UUID otherProjectId = insertProject();
        String hash = "a".repeat(40);
        OffsetDateTime now = OffsetDateTime.now();

        insertStory(projectId, UUID.randomUUID(), 1, "   " + hash.toUpperCase() + "  ",
                null, now.minusMinutes(2));
        insertStory(projectId, UUID.randomUUID(), 2, null, hash, now.minusMinutes(1));
        insertStory(projectId, UUID.randomUUID(), 3, hash, hash, now);
        insertStory(otherProjectId, UUID.randomUUID(), 4, hash, null, now.plusMinutes(1));

        StoryCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertFalse(result.truncated());
        assertEquals(4, result.references().size());
        assertEquals(List.of(
                        CommitReferenceRole.REFERENCES_AS_BASE,
                        CommitReferenceRole.REFERENCES_AS_TARGET,
                        CommitReferenceRole.REFERENCES_AS_TARGET,
                        CommitReferenceRole.REFERENCES_AS_BASE),
                result.references().stream().map(StoryCommitReference::role).toList());
        assertEquals(3, result.references().get(0).storyNumber());
        assertEquals(3, result.references().get(1).storyNumber());
    }

    @Test
    void ignoresNullMalformedAndAbbreviatedPersistedValues() {
        UUID projectId = insertProject();
        String hash = "b".repeat(40);
        OffsetDateTime now = OffsetDateTime.now();

        insertStory(projectId, UUID.randomUUID(), 1, null, null, now);
        insertStory(projectId, UUID.randomUUID(), 2, "b".repeat(7), null, now);
        insertStory(projectId, UUID.randomUUID(), 3, "not-a-commit", null, now);

        StoryCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertTrue(result.references().isEmpty());
        assertFalse(result.truncated());
    }

    @Test
    void ordersByCreatedAtDescendingThenIdAscending() {
        UUID projectId = insertProject();
        String hash = "c".repeat(40);
        OffsetDateTime base = OffsetDateTime.now();
        UUID lowId = new UUID(projectId.getMostSignificantBits(), 1);
        UUID highId = new UUID(projectId.getMostSignificantBits(), 2);

        insertStory(projectId, highId, 1, hash, null, base);
        insertStory(projectId, lowId, 2, hash, null, base);
        insertStory(projectId, UUID.randomUUID(), 3, hash, null, base.plusMinutes(1));

        StoryCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertEquals(List.of(3, 2, 1), result.references().stream()
                .map(StoryCommitReference::storyNumber).toList());
        assertEquals(lowId, result.references().get(1).storyId());
        assertEquals(highId, result.references().get(2).storyId());
    }

    @Test
    void exposesAtMostOneHundredAndDetectsTheExtraRow() {
        UUID projectId = insertProject();
        String hash = "d".repeat(40);
        OffsetDateTime createdAt = OffsetDateTime.now();

        for (int i = 0; i < 101; i++) {
            insertStory(projectId,
                    new UUID(projectId.getMostSignificantBits(), i + 1),
                    i + 1, hash, null, createdAt);
        }

        StoryCommitLookupResult result = lookup.findReferences(
                new HistoricalCommitLookup(projectId, new CommitHash(hash)));

        assertTrue(result.truncated());
        assertEquals(100, result.references().size());
        assertEquals(1, result.references().getFirst().storyNumber());
        assertEquals(100, result.references().getLast().storyNumber());
    }

    private UUID insertProject() {
        UUID projectId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into projects (id, name, slug, description, status, created_at, updated_at)
                values (?, ?, ?, '', 'ACTIVE', ?, ?)
                """, projectId, "Story lookup " + projectId,
                "story-lookup-" + projectId, now, now);
        return projectId;
    }

    private void insertStory(
            UUID projectId,
            UUID storyId,
            int storyNumber,
            String baseCommit,
            String targetCommit,
            OffsetDateTime createdAt) {
        jdbc.update("""
                insert into engineering_stories
                    (id, project_id, story_number, title, status, story_path,
                     base_commit, target_commit, created_at, updated_at, completed_at)
                values (?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?)
                """, storyId, projectId, storyNumber, "Story " + storyNumber,
                "docs/stories/" + storyNumber + ".md", baseCommit, targetCommit,
                createdAt, createdAt, createdAt);
    }
}
