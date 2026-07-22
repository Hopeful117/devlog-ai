package com.hopeful117.devlogai.collection.workspace;

import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitWorkspaceManagerTest {

    private final ProcessGitCommandExecutor git = new ProcessGitCommandExecutor();

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldReuseCleanAndSynchronizePersistentWorkspace() throws IOException {
        Path repository = createRepository();
        String firstRevision = git.execute(repository, List.of("rev-parse", "HEAD"));
        Source source = source(repository);
        Path root = temporaryDirectory.resolve("workspaces");
        GitWorkspaceManager manager = new GitWorkspaceManager(root.toString(), git);

        SynchronizedWorkspace first = manager.synchronize(source, null);
        Files.writeString(first.path().resolve("untracked.tmp"), "dirty");
        Files.writeString(first.path().resolve("tracked.txt"), "locally modified");
        commit(repository, "second", "second revision");
        String secondRevision = git.execute(repository, List.of("rev-parse", "HEAD"));

        SynchronizedWorkspace second = manager.synchronize(source, null);

        assertEquals(first.path(), second.path());
        assertNotEquals(firstRevision, secondRevision);
        assertEquals(secondRevision, second.resolvedRevision());
        assertEquals("second revision", Files.readString(second.path().resolve("tracked.txt")));
        assertFalse(Files.exists(second.path().resolve("untracked.tmp")));

        SynchronizedWorkspace explicitBranch = manager.synchronize(source, "main");
        assertEquals(secondRevision, explicitBranch.resolvedRevision());

        SynchronizedWorkspace historical = manager.synchronize(source, firstRevision);
        assertEquals(firstRevision, historical.resolvedRevision());
        assertEquals("first revision", Files.readString(historical.path().resolve("tracked.txt")));
    }

    @Test
    void shouldRecreateCorruptedWorkspaceAndRejectInactiveSource() throws IOException {
        Path repository = createRepository();
        Source source = source(repository);
        GitWorkspaceManager manager = new GitWorkspaceManager(
                temporaryDirectory.resolve("workspaces").toString(),
                git
        );
        SynchronizedWorkspace initial = manager.synchronize(source, null);
        deleteRecursively(initial.path().resolve(".git"));

        SynchronizedWorkspace recreated = manager.synchronize(source, null);

        assertTrue(Files.isDirectory(recreated.path().resolve(".git")));
        assertEquals(
                git.execute(repository, List.of("rev-parse", "HEAD")),
                recreated.resolvedRevision()
        );

        source.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> manager.synchronize(source, null));
    }

    private Path createRepository() throws IOException {
        Path repository = temporaryDirectory.resolve("origin");
        Files.createDirectories(repository);
        git.execute(repository, List.of("init", "--initial-branch=main"));
        git.execute(repository, List.of("config", "user.name", "DevLog Test"));
        git.execute(repository, List.of("config", "user.email", "devlog@example.test"));
        commit(repository, "first", "first revision");
        return repository;
    }

    private void commit(Path repository, String message, String content) throws IOException {
        Files.writeString(repository.resolve("tracked.txt"), content);
        git.execute(repository, List.of("add", "tracked.txt"));
        git.execute(repository, List.of("commit", "-m", message));
    }

    private Source source(Path repository) {
        return Source.builder()
                .id(UUID.randomUUID())
                .type(SourceType.GIT_REPOSITORY)
                .name("test repository")
                .repositoryUrl(repository.toString())
                .defaultBranch("main")
                .active(true)
                .build();
    }

    private void deleteRecursively(Path target) throws IOException {
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
