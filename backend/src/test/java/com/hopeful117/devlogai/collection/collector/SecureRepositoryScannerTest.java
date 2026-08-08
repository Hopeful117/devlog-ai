package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SecureRepositoryScannerTest {

    @TempDir
    Path tempDir;

    private CollectionContext createContext(Path path) {
        return new CollectionContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                path, "abc123", SourceType.GIT_REPOSITORY, Instant.now());
    }

    private CollectorLimits createLimits() {
        CollectorLimits limits = new CollectorLimits();
        limits.setCollectorTimeout(java.time.Duration.ofSeconds(30));
        return limits;
    }

    @Test
    void shouldScanEmptyDirectory() {
        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertEquals(0, result.files().size());
        assertEquals(0, result.warnings().size());
    }

    @Test
    void shouldScanRegularFiles() throws IOException {
        Files.writeString(tempDir.resolve("file1.txt"), "hello", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("file2.java"), "public class Test {}", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertEquals(2, result.files().size());
        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("file1.txt")));
        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("file2.java")));
    }

    @Test
    void shouldReadFileContentWhenIncluded() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content here", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertEquals(1, result.files().size());
        assertEquals("content here", result.files().getFirst().content());
    }

    @Test
    void shouldNotReadContentWhenExcluded() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content here", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> false);

        assertEquals(1, result.files().size());
        assertNull(result.files().getFirst().content());
    }

    @Test
    void shouldSkipExcludedDirectories() throws IOException {
        Path gitDir = tempDir.resolve(".git");
        Files.createDirectories(gitDir);
        Files.writeString(gitDir.resolve("config"), "git config", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("keep.txt"), "keep", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertFalse(result.files().stream().anyMatch(f -> f.relativePath().contains(".git")));
        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("keep.txt")));
    }

    @Test
    void shouldSkipSymlinks() throws IOException {
        Path real = tempDir.resolve("real.txt");
        Files.writeString(real, "real", StandardCharsets.UTF_8);
        Path link = tempDir.resolve("link.txt");
        Files.createSymbolicLink(link, real);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertEquals(1, result.files().size());
        assertEquals("real.txt", result.files().getFirst().relativePath());
    }

    @Test
    void shouldSkipDirectoriesInExcludedList() throws IOException {
        Path nodeModules = tempDir.resolve("node_modules");
        Files.createDirectories(nodeModules);
        Files.writeString(nodeModules.resolve("pkg.js"), "package", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("index.js"), "main", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertFalse(result.files().stream().anyMatch(f -> f.relativePath().contains("node_modules")));
        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("index.js")));
    }

    @Test
    void shouldTerminateWhenMaxFilesExceeded() throws IOException {
        CollectorLimits limits = createLimits();
        limits.setMaxFiles(2);
        limits.setMaxTotalBytes(1_000_000);

        for (int i = 0; i < 10; i++) {
            Files.writeString(tempDir.resolve("file" + i + ".txt"), "data" + i, StandardCharsets.UTF_8);
        }

        var scanner = new SecureRepositoryScanner(limits);
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.visitedFileCount() <= 3);
    }

    @Test
    void shouldSkipOversizedFiles() throws IOException {
        CollectorLimits limits = createLimits();
        limits.setMaxFileSize(5);

        Files.writeString(tempDir.resolve("small.txt"), "ok", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("big.txt"), "this is definitely more than 5 bytes", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(limits);
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("small.txt")));
        assertFalse(result.files().stream().anyMatch(f -> f.relativePath().equals("big.txt")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("MAX_FILE_SIZE_REACHED")));
    }

    @Test
    void shouldTerminateWhenMaxTotalBytesExceeded() throws IOException {
        CollectorLimits limits = createLimits();
        limits.setMaxTotalBytes(10);

        Files.writeString(tempDir.resolve("a.txt"), "1234567890", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("b.txt"), "exceeds", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(limits);
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("MAX_TOTAL_BYTES_REACHED")));
    }

    @Test
    void shouldSkipSubdirectoriesInExcludedDirectories() throws IOException {
        Path buildDir = tempDir.resolve("build");
        Files.createDirectories(buildDir);
        Files.writeString(buildDir.resolve("output.class"), "bytes", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertFalse(result.files().stream().anyMatch(f -> f.relativePath().contains("build")));
    }

    @Test
    void shouldHandleDeepNestedDirectories() throws IOException {
        Path deep = tempDir.resolve("a").resolve("b").resolve("c");
        Files.createDirectories(deep);
        Files.writeString(deep.resolve("deep.txt"), "deep", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("a/b/c/deep.txt")));
    }

    @Test
    void shouldSkipUnreadableFiles() throws IOException {
        Files.writeString(tempDir.resolve("good.txt"), "good", StandardCharsets.UTF_8);
        Path broken = tempDir.resolve("broken.txt");
        Files.writeString(broken, "data", StandardCharsets.UTF_8);
        Files.delete(broken);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.files().stream().anyMatch(f -> f.relativePath().equals("good.txt")));
    }

    @Test
    void shouldSetDirectoriesCount() throws IOException {
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/file.txt"), "data", StandardCharsets.UTF_8);

        var scanner = new SecureRepositoryScanner(createLimits());
        var context = createContext(tempDir);
        var result = scanner.scan(context, p -> true);

        assertTrue(result.directoryCount() > 0);
    }
}
