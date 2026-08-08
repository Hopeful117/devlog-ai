package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecureRepositoryContentReaderTest {
    @TempDir
    Path workspacePath;

    @Test
    void readsAndTruncatesUtf8Content() throws IOException {
        Path source = workspacePath.resolve("backend/src/main/java/App.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class Application {}\n");
        SecureRepositoryContentReader reader = reader(new CollectorLimits());

        var complete = reader.read(workspace(),
                "backend/src/main/java/App.java", 100);
        var truncated = reader.read(workspace(),
                "backend/src/main/java/App.java", 5);

        assertEquals(SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                complete.status());
        assertEquals("class Application {}\n", complete.text());
        assertEquals(SecureRepositoryContentReader.ReadResult.Status.TRUNCATED,
                truncated.status());
        assertEquals("class", truncated.text());
    }

    @Test
    void rejectsTraversalSymlinkExcludedBinaryAndInvalidUtf8() throws IOException {
        Path outside = workspacePath.resolve("outside-secret.txt");
        Files.writeString(outside, "secret");
        Path link = workspacePath.resolve("src/link.java");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, outside);
        Path excluded = workspacePath.resolve("target/Generated.java");
        Files.createDirectories(excluded.getParent());
        Files.writeString(excluded, "generated");
        Files.write(workspacePath.resolve("binary.java"), new byte[]{1, 0, 2});
        Files.write(workspacePath.resolve("invalid.java"), new byte[]{(byte) 0xC3, 0x28});
        SecureRepositoryContentReader reader = reader(new CollectorLimits());

        assertSkipped(reader.read(workspace(), "../outside-secret.txt", 100),
                "UNSAFE_OR_EXCLUDED_PATH");
        assertSkipped(reader.read(workspace(), "src/link.java", 100),
                "SYMLINK_OR_PATH_ESCAPE");
        assertSkipped(reader.read(workspace(), "target/Generated.java", 100),
                "UNSAFE_OR_EXCLUDED_PATH");
        assertSkipped(reader.read(workspace(), "binary.java", 100),
                "BINARY_CONTENT");
        assertSkipped(reader.read(workspace(), "invalid.java", 100),
                "UNSUPPORTED_ENCODING");
    }

    @Test
    void reportsOversizedAndMissingWithoutContent() throws IOException {
        CollectorLimits limits = new CollectorLimits();
        limits.setMaxFileSize(3);
        Files.writeString(workspacePath.resolve("large.java"), "large");
        SecureRepositoryContentReader reader = reader(limits);

        assertSkipped(reader.read(workspace(), "large.java", 100), "FILE_TOO_LARGE");
        var missing = reader.read(workspace(), "missing.java", 100);
        assertEquals(SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                missing.status());
        assertEquals("FILE_UNAVAILABLE", missing.reason());
        assertNull(missing.text());
    }

    @Test
    void boundsReadDuration() throws IOException {
        CollectorLimits limits = new CollectorLimits();
        limits.setCollectorTimeout(Duration.ofNanos(1));
        Files.writeString(workspacePath.resolve("App.java"), "class App {}");

        var result = reader(limits).read(workspace(), "App.java", 100);

        assertEquals(SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                result.status());
        assertEquals("READ_TIMEOUT", result.reason());
        assertNull(result.text());
    }

    private void assertSkipped(
            SecureRepositoryContentReader.ReadResult result,
            String reason
    ) {
        assertEquals(SecureRepositoryContentReader.ReadResult.Status.SKIPPED,
                result.status());
        assertEquals(reason, result.reason());
        assertNull(result.text());
    }

    private SecureRepositoryContentReader reader(CollectorLimits limits) {
        return new SecureRepositoryContentReader(limits);
    }

    private SynchronizedWorkspace workspace() {
        return new SynchronizedWorkspace(UUID.randomUUID(), workspacePath, "abc123");
    }
}
