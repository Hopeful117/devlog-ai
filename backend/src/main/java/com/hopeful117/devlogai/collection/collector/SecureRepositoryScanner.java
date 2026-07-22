package com.hopeful117.devlogai.collection.collector;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Component
public class SecureRepositoryScanner {

    private static final int MAX_DIRECTORY_DEPTH = 64;
    private final CollectorLimits limits;

    public SecureRepositoryScanner(CollectorLimits limits) {
        this.limits = limits;
    }

    public RepositoryScan scan(CollectionContext context, Predicate<String> includeContent) {
        Path root = context.workspacePath().toAbsolutePath().normalize();
        ScanState state = new ScanState(
                root,
                Instant.now().plus(limits.getCollectorTimeout()),
                includeContent
        );
        scanDirectory(root, 0, state);
        return new RepositoryScan(
                state.files,
                state.visitedFiles,
                state.directories,
                state.warnings
        );
    }

    private void scanDirectory(Path directory, int depth, ScanState state) {
        if (state.terminated || expired(state)) return;
        if (depth > MAX_DIRECTORY_DEPTH) {
            warningOnce(state.warnings, "MAX_DIRECTORY_DEPTH_REACHED",
                    "Maximum repository directory depth reached");
            return;
        }
        if (state.directories >= limits.getMaxFiles()) {
            warningOnce(state.warnings, "MAX_DIRECTORIES_REACHED",
                    "Maximum inspected directory count reached");
            state.terminated = true;
            return;
        }
        if (depth > 0 && excluded(directory.getFileName().toString())) return;
        state.directories++;

        List<Path> children;
        try (var paths = Files.list(directory)) {
            children = paths.limit((long) limits.getMaxFiles() + 1).toList();
            if (children.size() > limits.getMaxFiles()) {
                warningOnce(state.warnings, "MAX_DIRECTORY_ENTRIES_REACHED",
                        "Maximum entries in one directory reached");
                state.terminated = true;
                return;
            }
            children = children.stream()
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException exception) {
            if (directory.equals(state.root)) {
                throw new UncheckedIOException("Unable to inspect synchronized workspace", exception);
            }
            state.warnings.add(new CollectionWarning(
                    "DIRECTORY_READ_FAILED", "Unable to inspect directory: " + safeRelative(state.root, directory)));
            return;
        }

        for (Path child : children) {
            if (state.terminated || expired(state)) return;
            if (Files.isSymbolicLink(child)) continue;
            if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                scanDirectory(child, depth + 1, state);
            } else if (Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)) {
                scanFile(child, state);
            }
        }
    }

    private void scanFile(Path path, ScanState state) {
        if (++state.visitedFiles > limits.getMaxFiles()) {
            warningOnce(state.warnings, "MAX_FILES_REACHED", "Maximum inspected file count reached");
            state.terminated = true;
            return;
        }
        String relative = safeRelative(state.root, path);
        long size;
        try {
            size = Files.size(path);
        } catch (IOException exception) {
            state.warnings.add(new CollectionWarning("FILE_READ_FAILED", "Unable to inspect file: " + relative));
            return;
        }
        if (!state.includeContent.test(relative)) {
            state.files.add(new RepositoryFile(relative, size, null));
            return;
        }
        if (size > limits.getMaxFileSize()) {
            warningOnce(state.warnings, "MAX_FILE_SIZE_REACHED", "Skipped oversized file: " + relative);
            return;
        }
        if (state.bytes + size > limits.getMaxTotalBytes()) {
            warningOnce(state.warnings, "MAX_TOTAL_BYTES_REACHED", "Maximum bytes read reached");
            state.terminated = true;
            return;
        }
        try {
            state.files.add(new RepositoryFile(
                    relative, size, Files.readString(path, StandardCharsets.UTF_8)));
            state.bytes += size;
        } catch (MalformedInputException exception) {
            state.warnings.add(new CollectionWarning("NON_UTF8_FILE", "Skipped non UTF-8 file: " + relative));
        } catch (IOException exception) {
            state.warnings.add(new CollectionWarning("FILE_READ_FAILED", "Unable to read file: " + relative));
        }
    }

    private String safeRelative(Path root, Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) throw new SecurityException("Repository path escaped workspace root");
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private boolean excluded(String directory) {
        return limits.getExcludedDirectories().contains(directory);
    }

    private boolean expired(ScanState state) {
        if (Instant.now().isBefore(state.deadline)) return false;
        warningOnce(state.warnings, "COLLECTOR_TIMEOUT", "Collector inspection timeout reached");
        state.terminated = true;
        return true;
    }

    private void warningOnce(List<CollectionWarning> warnings, String code, String message) {
        if (warnings.stream().noneMatch(warning -> warning.code().equals(code))) {
            warnings.add(new CollectionWarning(code, message));
        }
    }

    private static final class ScanState {
        private final Path root;
        private final Instant deadline;
        private final Predicate<String> includeContent;
        private final List<RepositoryFile> files = new ArrayList<>();
        private final List<CollectionWarning> warnings = new ArrayList<>();
        private int visitedFiles;
        private int directories = -1;
        private long bytes;
        private boolean terminated;

        private ScanState(Path root, Instant deadline, Predicate<String> includeContent) {
            this.root = root;
            this.deadline = deadline;
            this.includeContent = includeContent;
        }
    }
}
