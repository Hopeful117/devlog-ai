package com.hopeful117.devlogai.collection.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
        List<String> arguments = List.of("status", "--nonexistent-flag");

        assertThrows(GitCommandException.class,
                () -> executor.execute(tempDir, arguments));
    }

    @Test
    void shouldTerminateAndReapCommandThatExceedsTimeout() {
        var executor = new ProcessGitCommandExecutor(Duration.ofMillis(100));

        GitCommandException failure = assertThrows(GitCommandException.class,
                () -> executor.execute(tempDir, List.of("hash-object", "--stdin")));

        assertTrue(failure.getMessage().contains("timed out"));
    }

    @Test
    void shouldCloseStreamsBeforeReapingAndWaitForReaderWhenExecuteTimesOut() throws Exception {
        Path fakeGit = tempDir.resolve("fake-git");
        Path releaseMarker = tempDir.resolve("stdin-closed-before-reap");
        Files.writeString(fakeGit, "#!/bin/sh\n" + "trap '' TERM INT\n" + "IFS= read -r ignored || true\n" + "printf released > '" + releaseMarker + "'\n" + "printf 'git version test\\n'\n");
        Files.setPosixFilePermissions(fakeGit, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        var executor = new ProcessGitCommandExecutor(Duration.ofMillis(50), fakeGit.toString());
        long startedAt = System.nanoTime();
        GitCommandException failure = assertThrows(GitCommandException.class, () -> executor.execute(tempDir, List.of("--version")));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        assertTrue(failure.getMessage().contains("timed out"));
        assertEquals("released", Files.readString(releaseMarker));
        assertTrue(elapsedMillis < 2_000, "closing stdin must release child before reap timeout: " + elapsedMillis + "ms");
    }
    @Test
    void shouldCloseAllProcessPipesBeforeReaderExecutorShutdown() throws Exception {
        AtomicReference<Boolean> inputClosed = new AtomicReference<>(false);
        AtomicReference<Boolean> errorClosed = new AtomicReference<>(false);
        AtomicReference<Boolean> outputClosed = new AtomicReference<>(false);
        Process process = new Process() {
            @Override public OutputStream getOutputStream() {
                return new OutputStream() {
                    @Override public void write(int value) { }
                    @Override public void close() { outputClosed.set(true); }
                };
            }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    @Override public int read() { return -1; }
                    @Override public void close() { inputClosed.set(true); }
                };
            }
            @Override public InputStream getErrorStream() {
                return new InputStream() {
                    @Override public int read() { return -1; }
                    @Override public void close() { errorClosed.set(true); }
                };
            }
            @Override public int waitFor() { return 0; }
            @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public Process destroyForcibly() { return this; }
            @Override public boolean isAlive() { return false; }
        };

        Method closeStreams = ProcessGitCommandExecutor.class
                .getDeclaredMethod("closeProcessStreams", Process.class);
        closeStreams.setAccessible(true);
        closeStreams.invoke(null, process);

        assertTrue(inputClosed.get());
        assertTrue(errorClosed.get());
        assertTrue(outputClosed.get());
    }

    @Test
    void shouldReapProcessWhenWaitingThreadIsInterruptedAndRestoreFlag() throws Exception {
        var executor = new ProcessGitCommandExecutor(Duration.ofSeconds(10));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<ProcessHandle> child = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                executor.execute(tempDir, List.of("hash-object", "--stdin"));
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (child.get() == null && System.nanoTime() < deadline) {
            ProcessHandle.current().children()
                    .filter(handle -> handle.info().command().orElse("").endsWith("/git"))
                    .findFirst()
                    .ifPresent(child::set);
            Thread.sleep(10);
        }
        assertNotNull(child.get(), "git child should have started");

        worker.interrupt();
        worker.join(10_000);

        assertFalse(worker.isAlive());
        assertInstanceOf(GitCommandException.class, failure.get());
        assertTrue(interrupted.get());
        assertFalse(child.get().isAlive(), "interrupted command must be terminated and reaped");
    }

    @Test
    void shouldPropagateInterruptionDuringTimedReap() throws Exception {
        Process process = new Process() {
            private boolean destroyed;
            @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
            @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
            @Override public int waitFor() { return 0; }
            @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException { throw new InterruptedException("test interruption"); }
            @Override public int exitValue() { return 0; }
            @Override public void destroy() { }
            @Override public Process destroyForcibly() { destroyed = true; return this; }
            @Override public boolean isAlive() { return !destroyed; }
        };
        Method waitFor = ProcessGitCommandExecutor.class.getDeclaredMethod("waitForUninterruptibly", Process.class, Duration.class);
        waitFor.setAccessible(true);
        AtomicReference<Object> result = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { result.set(waitFor.invoke(null, process, Duration.ofSeconds(1))); }
            catch (Throwable exception) { result.set(exception); }
            finally { interrupted.set(Thread.currentThread().isInterrupted()); }
        });
        worker.start();
        worker.interrupt();
        worker.join(2_000);
        assertFalse(worker.isAlive());
        assertEquals(Boolean.TRUE, result.get());
        assertTrue(interrupted.get());
    }
}
