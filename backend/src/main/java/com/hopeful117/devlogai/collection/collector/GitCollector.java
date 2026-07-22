package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GitCollector implements KnowledgeCollector {

    private final GitCommandExecutor git;

    @Override
    public String name() {
        return "git-collector-v1";
    }

    @Override
    public boolean supports(SourceType sourceType) {
        return sourceType == SourceType.GIT_REPOSITORY;
    }

    @Override
    public List<CollectedFact> collect(
            Source source,
            SynchronizedWorkspace workspace
    ) {
        String revision = git.execute(
                workspace.path(),
                List.of("rev-parse", "HEAD")
        );
        String authoredAt = git.execute(
                workspace.path(),
                List.of("show", "-s", "--format=%aI", "HEAD")
        );
        String subject = git.execute(
                workspace.path(),
                List.of("show", "-s", "--format=%s", "HEAD")
        );

        String content = "revision=%s%nauthoredAt=%s%nsubject=%s".formatted(
                revision,
                authoredAt,
                subject
        );
        return List.of(new CollectedFact(
                FactType.COMMIT,
                content,
                name() + ":" + source.getId(),
                List.of("git:" + revision)
        ));
    }
}
