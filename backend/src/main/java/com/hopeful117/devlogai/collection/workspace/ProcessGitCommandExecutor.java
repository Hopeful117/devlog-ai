package com.hopeful117.devlogai.collection.workspace;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ProcessGitCommandExecutor implements GitCommandExecutor {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(2);

    @Override
    public String execute(Path workingDirectory, List<String> arguments) {
        List<String> command = new ArrayList<>(arguments.size() + 1);
        command.add("git");
        command.addAll(arguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
        processBuilder.environment().put("LC_ALL", "C");

        try {
            Process process = processBuilder.start();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                Future<String> output = executor.submit(() ->
                        new String(
                                process.getInputStream().readAllBytes(),
                                StandardCharsets.UTF_8
                        )
                );
                boolean completed = process.waitFor(
                        COMMAND_TIMEOUT.toMillis(),
                        TimeUnit.MILLISECONDS
                );
                if (!completed) {
                    process.destroyForcibly();
                    throw new GitCommandException(
                            "Git command timed out: " + String.join(" ", command)
                    );
                }
                String captured = output.get(5, TimeUnit.SECONDS).trim();
                if (process.exitValue() != 0) {
                    throw new GitCommandException(
                            "Git command failed (%d): %s%n%s".formatted(
                                    process.exitValue(),
                                    String.join(" ", command),
                                    captured
                            )
                    );
                }
                return captured;
            }
        } catch (IOException exception) {
            throw new GitCommandException("Unable to execute Git command", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("Git command was interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new GitCommandException("Unable to read Git command output", exception);
        }
    }
}
