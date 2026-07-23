package com.hopeful117.devlogai.history.provider;

import com.hopeful117.devlogai.collection.workspace.ProcessGitCommandExecutor;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.model.GitCommitData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineGitHistoryProviderTest {
    private final ProcessGitCommandExecutor git = new ProcessGitCommandExecutor();
    private final CommandLineGitHistoryProvider provider =
            new CommandLineGitHistoryProvider(git);

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsRootAndNormalCommitsWithFileTypesStatisticsAndBinaryEvidence() throws IOException {
        Path repository = initializeRepository();
        write(repository, "kept.txt", "one\n");
        write(repository, "deleted.txt", "delete me\n");
        write(repository, "renamed.txt", "rename me\n");
        commitAll(repository, "root");

        write(repository, "kept.txt", "one\ntwo\n");
        Files.delete(repository.resolve("deleted.txt"));
        git.execute(repository, List.of("mv", "renamed.txt", "moved.txt"));
        write(repository, "added.java", "class Added {}\n");
        Files.write(repository.resolve("binary.bin"), new byte[]{0, 1, 2, 0, 4});
        commitAll(repository, "mixed change");

        List<GitCommitData> history = provider.readHistory(repository, "HEAD");

        assertEquals(2, history.size());
        assertTrue(history.getFirst().rootCommit());
        assertFalse(history.getFirst().mergeCommit());
        assertEquals(3, history.getFirst().statistics().filesChanged());
        GitCommitData second = history.get(1);
        assertEquals(history.getFirst().commitHash(), second.firstParentHash());
        assertTrue(second.changedFiles().stream().anyMatch(
                file -> file.changeType() == FileChangeType.ADDED
                        && "added.java".equals(file.newPath())));
        assertTrue(second.changedFiles().stream().anyMatch(
                file -> file.changeType() == FileChangeType.MODIFIED
                        && "kept.txt".equals(file.newPath())));
        assertTrue(second.changedFiles().stream().anyMatch(
                file -> file.changeType() == FileChangeType.DELETED
                        && "deleted.txt".equals(file.oldPath())));
        assertTrue(second.changedFiles().stream().anyMatch(
                file -> file.changeType() == FileChangeType.RENAMED
                        && "renamed.txt".equals(file.oldPath())
                        && "moved.txt".equals(file.newPath())));
        assertTrue(second.changedFiles().stream().anyMatch(
                file -> file.binary() && "binary.bin".equals(file.newPath())));
        assertTrue(second.statistics().insertions() >= 2);
        assertTrue(second.statistics().deletions() >= 1);
        assertEquals(1, second.statistics().binaryFiles());
    }

    @Test
    void identifiesMergeAndUsesFirstParentDiff() throws IOException {
        Path repository = initializeRepository();
        write(repository, "base.txt", "base\n");
        commitAll(repository, "root");
        git.execute(repository, List.of("checkout", "-b", "feature"));
        write(repository, "feature.txt", "feature\n");
        commitAll(repository, "feature");
        git.execute(repository, List.of("checkout", "main"));
        write(repository, "main.txt", "main\n");
        commitAll(repository, "main");
        git.execute(repository, List.of("merge", "--no-ff", "feature", "-m", "merge feature"));

        GitCommitData merge = provider.readHistory(repository, "HEAD").getLast();

        assertTrue(merge.mergeCommit());
        assertEquals(2, merge.parentHashes().size());
        assertEquals(1, merge.changedFiles().size());
        assertEquals("feature.txt", merge.changedFiles().getFirst().newPath());
    }

    @Test
    void wrapsFailuresWhenHistoryCannotBeRead() throws IOException {
        Path directory = temporaryDirectory.resolve("not-a-repository");
        Files.createDirectories(directory);

        assertThrows(GitHistoryReadException.class,
                () -> provider.readHistory(directory, "HEAD"));
    }

    private Path initializeRepository() throws IOException {
        Path repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository);
        git.execute(repository, List.of("init", "--initial-branch=main"));
        git.execute(repository, List.of("config", "user.name", "History Test"));
        git.execute(repository, List.of("config", "user.email", "history@example.test"));
        return repository;
    }

    private void write(Path repository, String path, String content) throws IOException {
        Path target = repository.resolve(path);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private void commitAll(Path repository, String message) {
        git.execute(repository, List.of("add", "-A"));
        git.execute(repository, List.of("commit", "-m", message));
    }
}
