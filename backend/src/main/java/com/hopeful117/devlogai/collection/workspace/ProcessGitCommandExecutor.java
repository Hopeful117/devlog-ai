package com.hopeful117.devlogai.collection.workspace;

import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProcessGitCommandExecutor implements GitCommandExecutor {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration REAP_TIMEOUT = Duration.ofSeconds(5);

    private final Duration commandTimeout;
    private final String gitExecutable;

    public ProcessGitCommandExecutor() {
        this(COMMAND_TIMEOUT, "git");
    }

    ProcessGitCommandExecutor(Duration commandTimeout) {
        this(commandTimeout, "git");
    }

    ProcessGitCommandExecutor(Duration commandTimeout, String gitExecutable) {
        this.commandTimeout = commandTimeout;
        this.gitExecutable = gitExecutable;
    }

    @Override
    public String execute(Path workingDirectory, List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add(gitExecutable);
        command.addAll(arguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("LC_ALL", "C");

        Process process = null;
        ExecutorService executor = null;
        Future<String> output = null;
        boolean interrupted = false;
        try {
            Process started = processBuilder.start();
            process = started;
            executor = Executors.newVirtualThreadPerTaskExecutor();
            output = executor.submit(() ->
                    new String(
                            started.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8
                    )
            );
            boolean completed = started.waitFor(
                    commandTimeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!completed) {
                throw new GitCommandException(
                        "Git command timed out: " + String.join(" ", command)
                );
            }
            String captured = output.get(5, TimeUnit.SECONDS).trim();
            if (started.exitValue() != 0) {
                throw new GitCommandException(
                        "Git command failed (%d): %s%n%s".formatted(
                                started.exitValue(),
                                String.join(" ", command),
                                captured
                        ),
                        started.exitValue()
                );
            }
            return captured;
        } catch (IOException exception) {
            throw new GitCommandException("Unable to execute Git command", exception);
        } catch (InterruptedException exception) {
            interrupted = true;
            throw new GitCommandException("Git command was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new GitCommandException("Unable to read Git command output", exception);
        } finally {
            // Closing the process pipes is required to unblock readAllBytes().
            // destroy() alone is asynchronous and shutting down the executor
            // first can therefore leave its reader task alive.
            closeProcessStreams(process);
            interrupted |= reap(process);
            if (output != null) {
                output.cancel(true);
            }
            if (executor != null) {
                executor.shutdownNow();
                interrupted |= awaitTerminationUninterruptibly(executor);
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void closeProcessStreams(Process process) {
        if (process == null) {
            return;
        }
        for (Closeable stream : List.of(
                process.getInputStream(), process.getErrorStream(), process.getOutputStream())) {
            try {
                stream.close();
            } catch (IOException | RuntimeException exception) {
                // Cleanup must not hide the original command failure.
            }
        }
    }

    private static boolean reap(Process process) {
        if (process == null) {
            return false;
        }
        boolean interrupted = false;
        try {
            if (process.isAlive()) {
                process.destroy();
                if (!waitForUninterruptibly(process, REAP_TIMEOUT)) {
                    process.destroyForcibly();
                }
            }
            // destroy and destroyForcibly are asynchronous; this wait is the reap.
            interrupted |= waitForUninterruptibly(process);
        } catch (SecurityException exception) {
            // Cleanup must not hide the original command failure.
        }
        return interrupted;
    }

    private static boolean waitForUninterruptibly(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            // Cleanup must still reap the process, while preserving the
            // interruption for execute's finally block.
            waitForUninterruptibly(process);
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static boolean waitForUninterruptibly(Process process) {
        boolean interrupted = false;
        for (;;) {
            try {
                process.waitFor();
                return interrupted;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
    }

    private static boolean awaitTerminationUninterruptibly(ExecutorService executor) {
        boolean interrupted = false;
        for (;;) {
            try {
                if (executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
                    return interrupted;
                }
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
    }
}
