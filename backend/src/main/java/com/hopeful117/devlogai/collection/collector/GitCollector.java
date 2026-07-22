package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.fact.entity.FactType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GitCollector implements KnowledgeCollector {

    private static final String VERSION = "git-collector-v1";
    private final GitCommandExecutor git;

    public GitCollector(GitCommandExecutor git) {
        this.git = git;
    }

    @Override public CollectorType type() { return CollectorType.GIT; }
    @Override public String version() { return VERSION; }

    @Override
    public CollectionResult collect(CollectionContext context) {
        String revision = git.execute(context.workspacePath(), List.of("rev-parse", "HEAD"));
        if (!revision.equals(context.resolvedRevision())) {
            throw new IllegalStateException("Workspace HEAD differs from resolved revision");
        }
        String authoredAt = git.execute(context.workspacePath(),
                List.of("show", "-s", "--format=%aI", "HEAD"));
        String subject = git.execute(context.workspacePath(),
                List.of("show", "-s", "--format=%s", "HEAD"));
        CollectedFact fact = CollectedFact.create(
                VERSION,
                FactType.COMMIT,
                "revision=%s%nauthoredAt=%s%nsubject=%s".formatted(revision, authoredAt, subject),
                List.of("git:" + revision, "source:" + context.sourceId()),
                revision
        );
        return CollectionResult.of(type(), version(), List.of(fact), List.of());
    }
}
