package com.hopeful117.devlogai.collection.workspace;

import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class GitWorkspaceManager implements WorkspaceManager {

    private final Path workspaceRoot;
    private final GitCommandExecutor git;
    private final ConcurrentHashMap<UUID, ReentrantLock> sourceLocks =
            new ConcurrentHashMap<>();

    public GitWorkspaceManager(
            @Value("${collection.workspace-root}") String workspaceRoot,
            GitCommandExecutor git
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
        this.git = git;
    }

    @Override
    public SynchronizedWorkspace synchronize(
            Source source,
            String targetRevision
    ) {
        requireSupportedSource(source);
        ReentrantLock lock = sourceLocks.computeIfAbsent(
                source.getId(),
                ignored -> new ReentrantLock()
        );
        lock.lock();
        try {
            Files.createDirectories(workspaceRoot);
            Path workspace = resolveWorkspace(source.getId());
            if (!isGitWorkspace(workspace)) {
                deleteWorkspace(workspace);
                cloneWorkspace(source, workspace);
            }
            try {
                return synchronizeExisting(source, workspace, targetRevision);
            } catch (GitCommandException firstFailure) {
                deleteWorkspace(workspace);
                cloneWorkspace(source, workspace);
                try {
                    return synchronizeExisting(source, workspace, targetRevision);
                } catch (GitCommandException retryFailure) {
                    retryFailure.addSuppressed(firstFailure);
                    throw retryFailure;
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Unable to prepare workspace for source " + source.getId(),
                    exception
            );
        } finally {
            lock.unlock();
        }
    }

    private SynchronizedWorkspace synchronizeExisting(
            Source source,
            Path workspace,
            String targetRevision
    ) {
        git.execute(workspace, List.of("remote", "set-url", "origin", source.getRepositoryUrl()));
        git.execute(workspace, List.of("fetch", "--prune", "origin"));
        git.execute(workspace, List.of("clean", "-fdx"));

        String requested = requestedRevision(source, targetRevision);
        String resolved = resolveRevision(workspace, requested, targetRevision);
        git.execute(workspace, List.of("checkout", "--force", "--detach", resolved));
        git.execute(workspace, List.of("reset", "--hard", resolved));
        String head = git.execute(workspace, List.of("rev-parse", "HEAD"));
        return new SynchronizedWorkspace(source.getId(), workspace, head);
    }

    private String requestedRevision(Source source, String targetRevision) {
        if (targetRevision != null && !targetRevision.isBlank()) {
            return targetRevision.trim();
        }
        if (source.getDefaultBranch() != null && !source.getDefaultBranch().isBlank()) {
            return "origin/" + source.getDefaultBranch().trim();
        }
        return "origin/HEAD";
    }

    private String resolveRevision(
            Path workspace,
            String requested,
            String explicitRevision
    ) {
        if (explicitRevision == null || explicitRevision.isBlank()) {
            return git.execute(
                    workspace,
                    List.of("rev-parse", "--verify", requested + "^{commit}")
            );
        }

        try {
            return git.execute(
                    workspace,
                    List.of(
                            "rev-parse", "--verify",
                            "refs/remotes/origin/" + requested + "^{commit}"
                    )
            );
        } catch (GitCommandException notRemoteBranch) {
            return git.execute(
                    workspace,
                    List.of("rev-parse", "--verify", requested + "^{commit}")
            );
        }
    }

    private void cloneWorkspace(Source source, Path workspace) {
        git.execute(
                workspaceRoot,
                List.of(
                        "clone", "--no-checkout", "--origin", "origin",
                        source.getRepositoryUrl(), workspace.toString()
                )
        );
    }

    private boolean isGitWorkspace(Path workspace) {
        return Files.isDirectory(workspace.resolve(".git"));
    }

    private Path resolveWorkspace(UUID sourceId) {
        Path workspace = workspaceRoot.resolve(sourceId.toString()).normalize();
        if (!workspace.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Workspace escaped configured root");
        }
        return workspace;
    }

    private void deleteWorkspace(Path workspace) {
        if (!Files.exists(workspace)) {
            return;
        }
        if (!workspace.startsWith(workspaceRoot) || workspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("Refusing to delete unsafe workspace path");
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void requireSupportedSource(Source source) {
        if (source.getId() == null) {
            throw new IllegalArgumentException("Source must be persisted before synchronization");
        }
        if (!source.isActive()) {
            throw new IllegalArgumentException("Inactive source cannot be synchronized");
        }
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException("Unsupported source type: " + source.getType());
        }
    }
}
