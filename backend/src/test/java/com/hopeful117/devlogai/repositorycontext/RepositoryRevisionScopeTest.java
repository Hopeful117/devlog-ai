package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryRevisionScopeTest {

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();

    @Test
    void createsImmutableScope() {
        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                PROJECT_ID,
                SOURCE_ID,
                "abc123",
                Path.of("/workspace/path"),
                "STORY_TARGET_COMMIT"
        );

        assertEquals(PROJECT_ID, scope.projectId());
        assertEquals(SOURCE_ID, scope.sourceId());
        assertEquals("abc123", scope.resolvedRevision());
        assertNotNull(scope.workspacePath());
        assertEquals("STORY_TARGET_COMMIT", scope.revisionSource());
    }

    @Test
    void normalizesWorkspacePath() {
        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                PROJECT_ID,
                SOURCE_ID,
                "abc123",
                Path.of("/workspace/path/../other"),
                "STORY_TARGET_COMMIT"
        );

        assertEquals("/workspace/other", scope.workspacePath().toString());
    }

    @Test
    void allowsNullWorkspacePath() {
        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                PROJECT_ID,
                SOURCE_ID,
                "abc123",
                null,
                "STORY_TARGET_COMMIT"
        );

        assertNull(scope.workspacePath());
    }

    @Test
    void hasCorrectSourceConstants() {
        assertEquals("STORY_TARGET_COMMIT", RepositoryRevisionScope.SOURCE_STORY_TARGET);
        assertEquals("ANALYSIS_TARGET_REVISION", RepositoryRevisionScope.SOURCE_ANALYSIS_TARGET);
        assertEquals("SOURCE_CURRENT_REVISION", RepositoryRevisionScope.SOURCE_CURRENT_REVISION);
        assertEquals("HEAD", RepositoryRevisionScope.SOURCE_HEAD);
    }

    @Test
    void equalsAndHashCodeByAllFields() {
        RepositoryRevisionScope scope1 = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, "abc123", Path.of("/path"), "STORY_TARGET_COMMIT");
        RepositoryRevisionScope scope2 = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, "abc123", Path.of("/path"), "STORY_TARGET_COMMIT");

        assertEquals(scope1, scope2);
        assertEquals(scope1.hashCode(), scope2.hashCode());
    }

    @Test
    void notEqualWithDifferentRevision() {
        RepositoryRevisionScope scope1 = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, "abc123", Path.of("/path"), "STORY_TARGET_COMMIT");
        RepositoryRevisionScope scope2 = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, "xyz789", Path.of("/path"), "STORY_TARGET_COMMIT");

        assertNotEquals(scope1, scope2);
    }

    @Test
    void rejectsNullProjectId() {
        assertThrows(NullPointerException.class,
                () -> new RepositoryRevisionScope(null, SOURCE_ID, "abc123", null, "STORY_TARGET_COMMIT"));
    }

    @Test
    void rejectsBlankRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new RepositoryRevisionScope(PROJECT_ID, SOURCE_ID, "", null, "STORY_TARGET_COMMIT"));
    }

    @Test
    void rejectsBlankRevisionSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new RepositoryRevisionScope(PROJECT_ID, SOURCE_ID, "abc123", null, ""));
    }
}
