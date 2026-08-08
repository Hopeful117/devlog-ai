package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.collection.collector.CollectionContext;
import com.hopeful117.devlogai.collection.collector.CollectorLimits;
import com.hopeful117.devlogai.collection.collector.RepositoryFile;
import com.hopeful117.devlogai.collection.collector.RepositoryScan;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryScanner;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryStructureCollectorTest {

    @Mock
    private SecureRepositoryScanner scanner;

    @Mock
    private CollectorLimits limits;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private WorkspaceManager workspaceManager;

    private EvidenceFactory evidenceFactory;

    private RepositoryStructureCollector collector;

    private UUID projectId;
    private UUID analysisId;
    private UUID sourceId;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        evidenceFactory = new EvidenceFactory();
        collector = new RepositoryStructureCollector(
                scanner, limits, sourceRepository, workspaceManager, evidenceFactory);

        projectId = UUID.randomUUID();
        analysisId = UUID.randomUUID();
        sourceId = UUID.randomUUID();
    }

    @Test
    void producesRelatedSourceCodeLayer() {
        Source source = Source.builder()
                .id(sourceId)
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));

        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                sourceId, tempDir, "abc123");
        when(workspaceManager.synchronize(eq(source), eq(null))).thenReturn(workspace);

        RepositoryScan scan = new RepositoryScan(
                List.of(
                        new RepositoryFile("src/main/java/com/App.java", 500, null),
                        new RepositoryFile("pom.xml", 200, null)),
                2, 1, List.of());
        when(scanner.scan(any(CollectionContext.class), any())).thenReturn(scan);

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertFalse(evidence.isEmpty());
        for (RepositoryEvidence item : evidence) {
            assertEquals(RepositoryContextLayer.RELATED_SOURCE_CODE, item.layer());
        }
    }

    @Test
    void producesModuleSummaryEvidence() {
        Source source = Source.builder()
                .id(sourceId)
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));

        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                sourceId, tempDir, "abc123");
        when(workspaceManager.synchronize(eq(source), eq(null))).thenReturn(workspace);

        RepositoryScan scan = new RepositoryScan(
                List.of(
                        new RepositoryFile("pom.xml", 200, null),
                        new RepositoryFile("module-a/pom.xml", 200, null),
                        new RepositoryFile("module-b/pom.xml", 200, null),
                        new RepositoryFile("src/main/java/App.java", 500, null)),
                4, 1, List.of());
        when(scanner.scan(any(CollectionContext.class), any())).thenReturn(scan);

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.stream().anyMatch(e -> "MODULE_SUMMARY".equals(e.kind())));
        RepositoryEvidence moduleEvidence = evidence.stream()
                .filter(e -> "MODULE_SUMMARY".equals(e.kind()))
                .findFirst().orElseThrow();
        assertTrue(moduleEvidence.summary().contains("Multi-module"));
        assertTrue(moduleEvidence.summary().contains("2 modules"));
    }

    @Test
    void producesSourceDirectoryEvidence() {
        Source source = Source.builder()
                .id(sourceId)
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));

        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                sourceId, tempDir, "abc123");
        when(workspaceManager.synchronize(eq(source), eq(null))).thenReturn(workspace);

        RepositoryScan scan = new RepositoryScan(
                List.of(
                        new RepositoryFile("src/main/java/com/App.java", 500, null),
                        new RepositoryFile("src/main/java/com/Service.java", 300, null),
                        new RepositoryFile("src/main/python/main.py", 200, null),
                        new RepositoryFile("README.md", 100, null)),
                4, 1, List.of());
        when(scanner.scan(any(CollectionContext.class), any())).thenReturn(scan);

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.stream().anyMatch(e -> "SOURCE_DIRECTORIES".equals(e.kind())));
        RepositoryEvidence dirEvidence = evidence.stream()
                .filter(e -> "SOURCE_DIRECTORIES".equals(e.kind()))
                .findFirst().orElseThrow();
        assertTrue(dirEvidence.summary().contains("src/main/java"));
        assertTrue(dirEvidence.summary().contains("src/main/python"));
    }

    @Test
    void returnsEmptyListWhenNoSource() {
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of());

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.isEmpty());
    }

    @Test
    void returnsEmptyListWhenWorkspaceUnavailable() {
        Source source = Source.builder()
                .id(sourceId)
                .type(SourceType.GIT_REPOSITORY)
                .active(true)
                .build();
        when(sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId))
                .thenReturn(List.of(source));
        when(workspaceManager.synchronize(eq(source), eq(null)))
                .thenThrow(new RuntimeException("Workspace unavailable"));

        List<RepositoryEvidence> evidence = collector.collect(createRequest());

        assertTrue(evidence.isEmpty());
    }

    private ContextRequest createRequest() {
        AnalysisContext analysisContext = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "TestProject",
                        "test-project", "A test project", ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId,
                        AnalysisType.ARCHITECTURE_REVIEW, "test-intent", "v1",
                        AnalysisStatus.IN_PROGRESS, Instant.EPOCH, null, Instant.EPOCH),
                null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of());

        com.hopeful117.devlogai.intent.model.IntentDefinition intent =
                new com.hopeful117.devlogai.intent.model.IntentDefinition(
                        "test-intent", "v1", "Test objective",
                        List.of(com.hopeful117.devlogai.intent.model.InsightType
                                .ARCHITECTURE_DESCRIPTION),
                        List.of("grounded"), Map.of("type", "object"),
                        "test-prompt", List.of("architecture-v1"));

        return new ContextRequest(
                analysisContext,
                intent,
                null,
                List.of(),
                mockContextPlan(),
                new RepositoryContext.ContextBudget(10, 100, 5, 1000));
    }

    private ContextPlan mockContextPlan() {
        return new ContextPlan(
                "test-v1",
                List.of(),
                Map.of(),
                List.of(),
                1,
                List.of());
    }
}
