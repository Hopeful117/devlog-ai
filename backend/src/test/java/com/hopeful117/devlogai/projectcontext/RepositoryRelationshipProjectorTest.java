package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.history.entity.ChangedFile;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryRelationshipProjectorTest {

    private final RepositoryRelationshipProjector projector = new RepositoryRelationshipProjector();

    @Test
    void projectsChangedFileUsingCanonicalCommitAndRevisionScopedFileIdentity() {
        UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ProjectCommit commit = commit(projectId, sourceId, "abc123");
        commit.addChangedFile(ChangedFile.builder().changeType(FileChangeType.MODIFIED)
                .newPath("backend\\src\\main\\App.java").oldPath("backend/src/main/App.java")
                .binary(false).insertions(2).deletions(1).build());

        var relationship = projector.project(commit).getFirst();

        assertEquals("CHANGES", relationship.relationType());
        assertEquals(EngineeringRelationship.Origin.REPOSITORY_DERIVED, relationship.origin());
        assertEquals("abc123", relationship.revision());
        assertEquals("commit:" + projectId + ":" + sourceId + ":abc123",
                relationship.source().canonicalIdentity());
        assertEquals("file:" + projectId + ":" + sourceId
                        + ":abc123:backend/src/main/App.java",
                relationship.target().canonicalIdentity());
        assertTrue(relationship.evidenceReferences().contains("diff:abc123:backend\\src\\main\\App.java"));
    }

    @Test
    void ignoresChangedFileWithoutRepositoryPath() {
        ProjectCommit commit = commit(UUID.randomUUID(), UUID.randomUUID(), "abc123");
        commit.addChangedFile(ChangedFile.builder().changeType(FileChangeType.DELETED)
                .binary(false).insertions(0).deletions(1).build());

        assertTrue(projector.project(commit).isEmpty());
    }

    private ProjectCommit commit(UUID projectId, UUID sourceId, String hash) {
        Project project = Project.builder().id(projectId).name("Project").slug("project")
                .status(ProjectStatus.ACTIVE).build();
        Source source = Source.builder().id(sourceId).project(project).name("origin")
                .repositoryUrl("https://example.test/repository").active(true).build();
        return ProjectCommit.builder().id(UUID.randomUUID()).project(project).source(source)
                .commitHash(hash).committedAt(Instant.EPOCH).subject("subject")
                .fullMessage("full message").rootCommit(false).mergeCommit(false)
                .filesChanged(1).insertions(2).deletions(1).binaryFiles(0)
                .importedAt(Instant.EPOCH).build();
    }
}
