package com.hopeful117.devlogai.collection.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessGitCommandExecutorAdditionalTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExecuteGitVersion() {
        var executor = new ProcessGitCommandExecutor();
        String result = executor.execute(tempDir, List.of("--version"));

        assertNotNull(result);
        assertTrue(result.contains("git version"));
    }

    @Test
    void shouldThrowOnInvalidCommand() {
        var executor = new ProcessGitCommandExecutor();

        assertThrows(GitCommandException.class,
                () -> executor.execute(tempDir, List.of("status", "--nonexistent-flag")));
    }
}
