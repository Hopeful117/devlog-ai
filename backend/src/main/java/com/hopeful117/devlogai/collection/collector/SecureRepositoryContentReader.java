package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class SecureRepositoryContentReader {
    private final CollectorLimits limits;

    public SecureRepositoryContentReader(CollectorLimits limits) {
        this.limits = limits;
    }

    public ReadResult read(
            SynchronizedWorkspace workspace,
            String relativePath,
            int maximumCharacters
    ) {
        if (relativePath == null || relativePath.isBlank()) {
            return ReadResult.skipped("INVALID_PATH");
        }
        Path root = workspace.path().toAbsolutePath().normalize();
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root) || excluded(relativePath)) {
            return ReadResult.skipped("UNSAFE_OR_EXCLUDED_PATH");
        }
        try {
            if (!Files.exists(candidate) || !Files.isRegularFile(candidate)) {
                return ReadResult.unavailable("FILE_UNAVAILABLE");
            }
            Path realRoot = root.toRealPath();
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(realRoot) || containsSymbolicLink(root, candidate)) {
                return ReadResult.skipped("SYMLINK_OR_PATH_ESCAPE");
            }
            long size = Files.size(realCandidate);
            if (size > limits.getMaxFileSize()) {
                return ReadResult.skipped("FILE_TOO_LARGE");
            }
            byte[] bytes = readWithinDeadline(realCandidate);
            if (containsNullByte(bytes)) {
                return ReadResult.skipped("BINARY_CONTENT");
            }
            String text = decodeUtf8(bytes);
            if (text.length() <= maximumCharacters) {
                return ReadResult.complete(text);
            }
            return ReadResult.truncated(safePrefix(text, maximumCharacters));
        } catch (CharacterCodingException exception) {
            return ReadResult.skipped("UNSUPPORTED_ENCODING");
        } catch (TimeoutException exception) {
            return ReadResult.unavailable("READ_TIMEOUT");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ReadResult.unavailable("READ_INTERRUPTED");
        } catch (IOException | SecurityException exception) {
            return ReadResult.unavailable("FILE_UNREADABLE");
        }
    }

    private byte[] readWithinDeadline(Path path)
            throws IOException, InterruptedException, TimeoutException {
        CompletableFuture<byte[]> read = new CompletableFuture<>();
        Thread worker = Thread.startVirtualThread(() -> {
            try {
                read.complete(Files.readAllBytes(path));
            } catch (IOException exception) {
                read.completeExceptionally(exception);
            }
        });
        try {
            return read.get(limits.getCollectorTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            throw new IOException("Repository content read failed", cause);
        } finally {
            read.cancel(true);
            worker.interrupt();
        }
    }

    private boolean excluded(String relativePath) {
        Set<String> excluded = limits.getExcludedDirectories();
        for (String segment : relativePath.replace('\\', '/').split("/")) {
            if (excluded.contains(segment)) return true;
        }
        return false;
    }

    private boolean containsSymbolicLink(Path root, Path candidate) {
        Path current = root;
        Path relative = root.relativize(candidate);
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) return true;
        }
        return false;
    }

    private boolean containsNullByte(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) return true;
        }
        return false;
    }

    private String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
    }

    private String safePrefix(String value, int maximumCharacters) {
        int end = Math.min(maximumCharacters, value.length());
        if (end > 0 && end < value.length()
                && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    public record ReadResult(Status status, String text, String reason) {
        public enum Status { COMPLETE, TRUNCATED, SKIPPED, UNAVAILABLE }

        static ReadResult complete(String text) {
            return new ReadResult(Status.COMPLETE, text, null);
        }

        static ReadResult truncated(String text) {
            return new ReadResult(Status.TRUNCATED, text, "CONTENT_TRUNCATED");
        }

        static ReadResult skipped(String reason) {
            return new ReadResult(Status.SKIPPED, null, reason);
        }

        static ReadResult unavailable(String reason) {
            return new ReadResult(Status.UNAVAILABLE, null, reason);
        }
    }
}
