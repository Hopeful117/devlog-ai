package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitCollectorTest {

    @Test
    void shouldProduceTheSameCommitFactForTheSameWorkspaceState() {
        UUID sourceId = UUID.randomUUID();
        String revision = "0123456789abcdef";
        GitCommandExecutor git = (path, arguments) -> switch (arguments.get(0)) {
            case "rev-parse" -> revision;
            case "show" -> arguments.get(2).equals("--format=%aI")
                    ? "2026-07-22T10:15:30+02:00"
                    : "Implement deterministic collection";
            default -> throw new AssertionError("Unexpected command: " + arguments);
        };
        GitCollector collector = new GitCollector(git);
        Source source = Source.builder()
                .id(sourceId)
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                sourceId,
                Path.of("workspace"),
                revision
        );

        List<CollectedFact> first = collector.collect(source, workspace);
        List<CollectedFact> second = collector.collect(source, workspace);

        assertEquals(first, second);
        assertEquals(FactType.COMMIT, first.getFirst().type());
        assertEquals("git-collector-v1:" + sourceId, first.getFirst().source());
        assertEquals(List.of("git:" + revision), first.getFirst().evidenceReferences());
        assertTrue(first.getFirst().content().contains("revision=" + revision));
        assertTrue(collector.supports(SourceType.GIT_REPOSITORY));
    }
}
