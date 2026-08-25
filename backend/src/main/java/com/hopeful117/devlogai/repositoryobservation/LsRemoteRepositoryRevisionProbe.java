package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.projectfreshness.GitCommitIdentity;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cheap remote HEAD observation through plain Git plumbing:
 *
 * <pre>git ls-remote &lt;repositoryUrl&gt; refs/heads/&lt;defaultBranch&gt; | HEAD</pre>
 *
 * One round-trip, no clone, no fetch, no working tree. Local and file
 * transports resolve through the same command without network access,
 * keeping the detector provider-independent (ADR-041). The repository URL is
 * deliberately never logged: it may carry credentials for private remotes.
 */
@Component
public class LsRemoteRepositoryRevisionProbe implements RepositoryRevisionProbe {

    private final GitCommandExecutor git;
    private final Path workingDirectory;

    public LsRemoteRepositoryRevisionProbe(
            GitCommandExecutor git,
            @Value("${collection.workspace-root}") String workspaceRoot
    ) {
        this.git = git;
        this.workingDirectory = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public String probeHead(Source source) {
        requireSupported(source);
        List<String> arguments = List.of(
                "ls-remote",
                source.getRepositoryUrl(),
                trackedRef(source)
        );
        try {
            Files.createDirectories(workingDirectory);
            String output = git.execute(workingDirectory, arguments);
            return parseHead(source, output);
        } catch (GitCommandException exception) {
            throw exception;
        } catch (java.io.IOException exception) {
            throw new GitCommandException(
                    "Repository HEAD observation failed for source " + source.getId(),
                    exception);
        } catch (RuntimeException exception) {
            throw new GitCommandException(
                    "Repository HEAD observation failed for source " + source.getId(),
                    exception);
        }
    }

    private void requireSupported(Source source) {
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException(
                    "Repository observation requires a GIT_REPOSITORY source");
        }
    }

    private String trackedRef(Source source) {
        String branch = source.getDefaultBranch();
        return branch == null || branch.isBlank()
                ? "HEAD"
                : "refs/heads/" + branch.strip();
    }

    private String parseHead(Source source, String output) {
        String firstLine = output.lines().findFirst().orElse("");
        int separator = firstLine.indexOf('\t');
        String sha = separator >= 0 ? firstLine.substring(0, separator) : "";
        return GitCommitIdentity.normalize(sha)
                .orElseThrow(() -> new GitCommandException(String.format(
                        "Repository reported no revision for source %s tracked ref '%s'",
                        source.getId(), trackedRef(source))));
    }
}
