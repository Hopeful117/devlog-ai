package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.collection.collector.CollectorLimits;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SelectedJavaSymbolEnricherTest {
    @TempDir
    Path workspace;

    @Test
    void enrichesOnlySelectedJavaAndPreservesBudgetAndProvenance() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/App.java"),
                "@Deprecated public class App { public App() {} void run(int count) {} }");
        Files.writeString(workspace.resolve("src/config.yml"), "secret: no");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = Source.builder().id(sourceId).active(true)
                .project(Project.builder().id(projectId).build()).build();
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));
        SelectedJavaSymbolEnricher enricher = enricher(policy, sources, manager);
        RepositoryEvidence java = evidence(sourceId, "SOURCE_FILE", "src/App.java", 200);
        RepositoryEvidence config = evidence(sourceId, "CONFIG_FILE", "src/config.yml", 100);

        var result = enricher.enrich(request(projectId, 1000), selection(List.of(java, config)));

        RepositoryEvidence enriched = result.selection().selected().getFirst();
        assertEquals(RepositoryEvidenceSymbols.Status.EXTRACTED,
                enriched.symbols().status());
        assertEquals("abc123", enriched.symbols().revision());
        assertTrue(enriched.symbols().declarations().stream().anyMatch(value ->
                value.kind() == RepositoryEvidenceSymbols.Kind.METHOD
                        && value.name().equals("run")));
        assertNull(result.selection().selected().get(1).symbols());
        assertTrue(result.selection().usedTokens() <= 1000);
        assertEquals(result.selection().selected().stream()
                        .mapToInt(RepositoryEvidence::estimatedTokens).sum(),
                result.selection().usedTokens());
    }

    @Test
    void reportsMalformedAndStopsConsideringFilesAtTheFileLimit()
            throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("Broken.java"), "class Broken { void x( }");
        Files.writeString(workspace.resolve("Other.java"), "class Other {}");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        policy.setMaxInspectedFiles(1);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = Source.builder().id(sourceId).active(true)
                .project(Project.builder().id(projectId).build()).build();
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));
        RepositoryEvidence broken = evidence(sourceId, "SOURCE_FILE", "Broken.java", 200);
        RepositoryEvidence other = evidence(sourceId, "SOURCE_FILE", "Other.java", 100);

        var result = enricher(policy, sources, manager).enrich(request(projectId, 1000),
                selection(List.of(broken, other)));

        assertEquals(RepositoryEvidenceSymbols.Status.UNSUPPORTED,
                result.selection().selected().getFirst().symbols().status());
        assertNull(result.selection().selected().get(1).symbols());
        assertEquals(other.reference(), result.selection().selected().get(1).reference());
        assertTrue(result.warnings().contains("SYMBOL_ENRICHMENT_UNSUPPORTED"));
        assertTrue(result.warnings().contains("SYMBOL_ENRICHMENT_LIMIT_APPLIED"));
    }

    @Test
    void stopsBeforeConsideringEvidenceWhoseOutcomeMetadataCannotFit() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        policy.setMaxInspectedFiles(20);
        policy.setMaxTokens(100);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));

        List<RepositoryEvidence> evidence = new java.util.ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String path = "Type" + index + ".java";
            Files.writeString(workspace.resolve(path), "class Type" + index + " {}");
            evidence.add(evidence(sourceId, "SOURCE_FILE", path, 200 - index));
        }

        var result = enricher(policy, sources, manager).enrich(request(projectId, 1000),
                selection(evidence));

        long considered = result.selection().selected().stream()
                .filter(value -> value.symbols() != null).count();
        assertTrue(considered > 0 && considered < evidence.size());
        assertTrue(result.selection().selected().stream().filter(value -> value.symbols() != null)
                .allMatch(value -> value.symbols().status() != null));
        assertTrue(result.warnings().contains(
                "SYMBOL_ENRICHMENT_METADATA_BUDGET_EXHAUSTED"));
        assertTrue(result.selection().usedTokens() <= 1000);
    }

    @Test
    void isolatesUnexpectedExtractorFailure() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("App.java"), "class App {}");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        JavaDeclarationExtractor extractor = mock(JavaDeclarationExtractor.class);
        when(extractor.extract(any(), any())).thenThrow(new IllegalStateException("boom"));
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));

        var result = enricher(policy, extractor, sources, manager).enrich(
                request(projectId, 1000), selection(List.of(
                        evidence(sourceId, "SOURCE_FILE", "App.java", 200))));

        assertEquals(RepositoryEvidenceSymbols.Status.FAILED,
                result.selection().selected().getFirst().symbols().status());
        assertEquals("EXTRACTION_FAILURE",
                result.selection().selected().getFirst().symbols().reason());
        assertTrue(result.warnings().contains("SYMBOL_ENRICHMENT_FAILED"));
    }

    @Test
    void preservesARealOutcomeThatIsSmallerThanReservedMetadata() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("Package.java"), "package sample;");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));

        var result = enricher(policy, sources, manager).enrich(request(projectId, 1000),
                selection(List.of(evidence(sourceId, "SOURCE_FILE", "Package.java", 200))));

        assertEquals(RepositoryEvidenceSymbols.Status.NO_SUPPORTED_SYMBOLS,
                result.selection().selected().getFirst().symbols().status());
        assertTrue(result.selection().usedTokens() <= 1000);
    }

    @Test
    void reportsParseAndAggregateDurationLimits() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("App.java"), "class App {}");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        policy.setMaxParseDurationPerFile(java.time.Duration.ofMillis(1));
        policy.setMaxTotalDuration(java.time.Duration.ofMillis(5));
        JavaDeclarationExtractor extractor = mock(JavaDeclarationExtractor.class);
        when(extractor.extract(any(), any())).thenAnswer(invocation -> {
            long until = System.nanoTime() + java.time.Duration.ofMillis(50).toNanos();
            while (System.nanoTime() < until) Thread.onSpinWait();
            return new JavaDeclarationExtractor().extract(invocation.getArgument(0), policy);
        });
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));

        var result = enricher(policy, extractor, sources, manager).enrich(
                request(projectId, 1000), selection(List.of(
                        evidence(sourceId, "SOURCE_FILE", "App.java", 200))));

        assertEquals(RepositoryEvidenceSymbols.Status.FAILED,
                result.selection().selected().getFirst().symbols().status());
        assertEquals("PARSE_TIMEOUT",
                result.selection().selected().getFirst().symbols().reason());
    }

    @Test
    void appliesAggregateDurationBeforeInspectingTheNextConsideredFile() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("App.java"), "class App {}");
        RepositorySymbolPolicy policy = new RepositorySymbolPolicy();
        policy.setMaxTotalDuration(java.time.Duration.ofNanos(1));
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);

        var result = enricher(policy, sources, manager).enrich(request(projectId, 1000),
                selection(List.of(evidence(sourceId, "SOURCE_FILE", "App.java", 200))));

        assertEquals(RepositoryEvidenceSymbols.Status.SKIPPED,
                result.selection().selected().getFirst().symbols().status());
        assertEquals("SYMBOL_TOTAL_DURATION_LIMIT",
                result.selection().selected().getFirst().symbols().reason());
        assertTrue(result.warnings().contains(
                "SYMBOL_ENRICHMENT_DURATION_LIMIT_APPLIED"));
    }

    @Test
    void appliesAggregateSymbolAndTokenLimits() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.writeString(workspace.resolve("First.java"),
                "class First { void consumeBudget() {} }");
        Files.writeString(workspace.resolve("Second.java"), "class Second {}");
        Files.writeString(workspace.resolve("Large.java"), "class Large { "
                + java.util.stream.IntStream.range(0, 30)
                        .mapToObj(index -> "void method" + index + "() {}")
                        .collect(java.util.stream.Collectors.joining(" ")) + " }");
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager manager = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(manager.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspace, "abc123"));

        RepositorySymbolPolicy symbolBound = new RepositorySymbolPolicy();
        symbolBound.setMaxTotalSymbols(1);
        var symbolResult = enricher(symbolBound, sources, manager).enrich(
                request(projectId, 5_000), selection(List.of(
                        evidence(sourceId, "SOURCE_FILE", "First.java", 300),
                        evidence(sourceId, "SOURCE_FILE", "Second.java", 200))));
        assertEquals(RepositoryEvidenceSymbols.Status.SKIPPED,
                symbolResult.selection().selected().get(1).symbols().status());
        assertEquals("SYMBOL_COUNT_LIMIT",
                symbolResult.selection().selected().get(1).symbols().reason());

        RepositorySymbolPolicy tokenBound = new RepositorySymbolPolicy();
        tokenBound.setMaxTokens(100);
        var tokenResult = enricher(tokenBound, sources, manager).enrich(
                request(projectId, 1000), selection(List.of(
                        evidence(sourceId, "SOURCE_FILE", "Large.java", 300))));
        assertEquals(RepositoryEvidenceSymbols.Status.SKIPPED,
                tokenResult.selection().selected().getFirst().symbols().status());
        assertEquals("SYMBOL_TOKEN_LIMIT",
                tokenResult.selection().selected().getFirst().symbols().reason());
        assertTrue(tokenResult.warnings().contains(
                "SYMBOL_ENRICHMENT_TOKEN_LIMIT_APPLIED"));
    }

    private SelectedJavaSymbolEnricher enricher(
            RepositorySymbolPolicy policy,
            SourceRepository sources,
            WorkspaceManager manager
    ) {
        return new SelectedJavaSymbolEnricher(policy,
                new SelectedSymbolAllocationPolicy(), new JavaDeclarationExtractor(),
                new SecureRepositoryContentReader(new CollectorLimits()), sources, manager);
    }

    private SelectedJavaSymbolEnricher enricher(
            RepositorySymbolPolicy policy,
            JavaDeclarationExtractor extractor,
            SourceRepository sources,
            WorkspaceManager manager
    ) {
        return new SelectedJavaSymbolEnricher(policy,
                new SelectedSymbolAllocationPolicy(), extractor,
                new SecureRepositoryContentReader(new CollectorLimits()), sources, manager);
    }

    private Source source(UUID projectId, UUID sourceId) {
        return Source.builder().id(sourceId).active(true)
                .project(Project.builder().id(projectId).build()).build();
    }

    private ContextRequest request(UUID projectId, int tokens) {
        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "DevLog", "devlog", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(),
                        AnalysisType.ARCHITECTURE_REVIEW, "story", "v1",
                        AnalysisStatus.IN_PROGRESS, Instant.EPOCH, null, Instant.EPOCH),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        return new ContextRequest(context, null, null, List.of(), null,
                new RepositoryContext.ContextBudget(60, 500, 20, tokens));
    }

    private EvidenceSelector.SelectionResult selection(List<RepositoryEvidence> selected) {
        List<RepositoryContext.SelectionDecision> decisions = selected.stream()
                .map(value -> new RepositoryContext.SelectionDecision(value.reference(), true,
                        "SELECTED_BY_RANK_AND_DIVERSITY", value.relevanceScore(),
                        value.estimatedTokens())).toList();
        return new EvidenceSelector.SelectionResult(selected, decisions,
                selected.stream().mapToInt(RepositoryEvidence::estimatedTokens).sum());
    }

    private RepositoryEvidence evidence(
            UUID sourceId, String kind, String path, int strength
    ) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, "file:" + path, path, Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), 49, List.of(),
                        new EvidenceScore.MatchStrength(strength, strength)),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE", sourceId.toString(), path, "structure:" + path),
                Map.of("resolvedRevision", "abc123"), 20, List.of());
    }
}
