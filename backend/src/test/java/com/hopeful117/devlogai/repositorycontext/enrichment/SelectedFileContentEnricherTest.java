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
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelectedFileContentEnricherTest {
    @TempDir
    Path workspacePath;

    @Test
    void enrichesOnlySourceAndTestWithinAllBudgets() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.createDirectories(workspacePath.resolve("src/main/java"));
        Files.createDirectories(workspacePath.resolve("src/test/java"));
        Files.writeString(workspacePath.resolve("src/main/java/App.java"),
                "public class Application {}");
        Files.writeString(workspacePath.resolve("src/test/java/AppTest.java"),
                "class ApplicationTest {}");
        Files.writeString(workspacePath.resolve("pom.xml"), "<secret>never</secret>");

        RepositoryContentPolicy policy = new RepositoryContentPolicy();
        policy.setMaxEnrichedFiles(2);
        policy.setMaxCharactersPerFile(8);
        policy.setMaxTotalCharacters(12);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, "abc123"));
        SelectedFileContentEnricher enricher = new SelectedFileContentEnricher(
                policy, new SelectedContentAllocationPolicy(),
                new SecureRepositoryContentReader(new CollectorLimits()),
                sources, workspaces);
        List<RepositoryEvidence> selected = List.of(
                evidence(sourceId, "SOURCE_FILE", "src/main/java/App.java"),
                evidence(sourceId, "TEST_FILE", "src/test/java/AppTest.java"),
                evidence(sourceId, "CONFIG_FILE", "pom.xml"));

        var result = enricher.enrich(request(projectId, 1000), selection(selected));

        RepositoryEvidence sourceEvidence = result.selection().selected().get(0);
        RepositoryEvidence testEvidence = result.selection().selected().get(1);
        RepositoryEvidence configEvidence = result.selection().selected().get(2);
        assertEquals(RepositoryEvidenceContent.Status.TRUNCATED,
                sourceEvidence.content().status());
        assertEquals(8, sourceEvidence.content().text().length());
        assertEquals(RepositoryEvidenceContent.Status.TRUNCATED,
                testEvidence.content().status());
        assertEquals(4, testEvidence.content().text().length());
        assertNull(configEvidence.content());
        assertTrue(result.selection().usedTokens() <= 1000);
        assertEquals(result.selection().selected().stream()
                        .mapToInt(RepositoryEvidence::estimatedTokens).sum(),
                result.selection().usedTokens());
        assertTrue(result.warnings().contains("CONTENT_ENRICHMENT_TRUNCATED"));
        verify(workspaces).synchronize(source, "abc123");
    }

    @Test
    void preservesPathEvidenceWhenWorkspaceIsUnavailable() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        RepositoryContentPolicy policy = new RepositoryContentPolicy();
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.empty());
        SelectedFileContentEnricher enricher = new SelectedFileContentEnricher(
                policy, new SelectedContentAllocationPolicy(),
                new SecureRepositoryContentReader(new CollectorLimits()),
                sources, workspaces);
        RepositoryEvidence original = evidence(
                sourceId, "SOURCE_FILE", "src/main/java/App.java");

        var result = enricher.enrich(request(projectId, 1000),
                selection(List.of(original)));

        RepositoryEvidence returned = result.selection().selected().getFirst();
        assertEquals(original.reference(), returned.reference());
        assertEquals(original.summary(), returned.summary());
        assertEquals(RepositoryEvidenceContent.Status.UNAVAILABLE,
                returned.content().status());
        assertEquals("WORKSPACE_UNAVAILABLE", returned.content().reason());
    }

    @Test
    void marksAdditionalEligibleEvidenceWhenFileLimitIsReached() throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.createDirectories(workspacePath.resolve("src/main/java"));
        Files.writeString(workspacePath.resolve("src/main/java/App.java"), "class App {}");
        Files.writeString(workspacePath.resolve("src/main/java/Other.java"), "class Other {}");
        RepositoryContentPolicy policy = new RepositoryContentPolicy();
        policy.setMaxEnrichedFiles(1);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, "abc123"));
        SelectedFileContentEnricher enricher = new SelectedFileContentEnricher(
                policy, new SelectedContentAllocationPolicy(),
                new SecureRepositoryContentReader(new CollectorLimits()),
                sources, workspaces);

        var result = enricher.enrich(request(projectId, 1000), selection(List.of(
                evidence(sourceId, "SOURCE_FILE", "src/main/java/App.java"),
                evidence(sourceId, "SOURCE_FILE", "src/main/java/Other.java"))));

        assertEquals(RepositoryEvidenceContent.Status.COMPLETE,
                result.selection().selected().get(0).content().status());
        assertEquals(RepositoryEvidenceContent.Status.SKIPPED,
                result.selection().selected().get(1).content().status());
        assertEquals("ENRICHED_FILE_LIMIT",
                result.selection().selected().get(1).content().reason());
        assertTrue(result.warnings().contains("CONTENT_ENRICHMENT_LIMIT_APPLIED"));
    }

    @Test
    void enrichesStrongerEqualScoreMatchBeforeAlphabeticalDistractor()
            throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.createDirectories(workspacePath.resolve("src/aaa"));
        Files.createDirectories(workspacePath.resolve("src/zzz"));
        Files.writeString(workspacePath.resolve("src/aaa/Distractor.java"),
                "class Distractor {}");
        Files.writeString(workspacePath.resolve("src/zzz/Central.java"),
                "class Central {}");
        RepositoryContentPolicy policy = new RepositoryContentPolicy();
        policy.setMaxEnrichedFiles(1);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, "abc123"));
        SelectedFileContentEnricher enricher = new SelectedFileContentEnricher(
                policy, new SelectedContentAllocationPolicy(),
                new SecureRepositoryContentReader(new CollectorLimits()),
                sources, workspaces);

        var result = enricher.enrich(request(projectId, 1000), selection(List.of(
                evidence(sourceId, "SOURCE_FILE", "src/aaa/Distractor.java",
                        49, 100),
                evidence(sourceId, "SOURCE_FILE", "src/zzz/Central.java",
                        49, 200))));

        RepositoryEvidence distractor = result.selection().selected().get(0);
        RepositoryEvidence central = result.selection().selected().get(1);
        assertEquals(RepositoryEvidenceContent.Status.SKIPPED,
                distractor.content().status());
        assertEquals("ENRICHED_FILE_LIMIT", distractor.content().reason());
        assertEquals(RepositoryEvidenceContent.Status.COMPLETE,
                central.content().status());
        assertEquals("class Central {}", central.content().text());
        assertEquals(1, central.content().allocationRank());
        assertTrue(central.content().allocationReasons().contains(
                "SEMANTIC_MATCH_STRENGTH=200"));
    }

    @Test
    void reservesRemainingTokenBudgetForEveryAvailableContentSlot()
            throws IOException {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Files.createDirectories(workspacePath.resolve("src"));
        Files.writeString(workspacePath.resolve("src/First.java"), "x".repeat(4000));
        Files.writeString(workspacePath.resolve("src/Second.java"), "y".repeat(4000));
        RepositoryContentPolicy policy = new RepositoryContentPolicy();
        policy.setMaxEnrichedFiles(2);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = source(projectId, sourceId);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(workspaces.synchronize(source, "abc123")).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, "abc123"));
        SelectedFileContentEnricher enricher = new SelectedFileContentEnricher(
                policy, new SelectedContentAllocationPolicy(),
                new SecureRepositoryContentReader(new CollectorLimits()),
                sources, workspaces);
        List<RepositoryEvidence> selected = List.of(
                evidence(sourceId, "SOURCE_FILE", "src/First.java", 49, 200),
                evidence(sourceId, "SOURCE_FILE", "src/Second.java", 49, 100));
        int initialTokens = selection(selected).usedTokens();

        var result = enricher.enrich(request(projectId, initialTokens + 300),
                selection(selected));

        RepositoryEvidence first = result.selection().selected().get(0);
        RepositoryEvidence second = result.selection().selected().get(1);
        assertTrue(!first.content().text().isEmpty());
        assertTrue(!second.content().text().isEmpty());
        assertEquals(RepositoryEvidenceContent.Status.TRUNCATED,
                first.content().status());
        assertEquals(RepositoryEvidenceContent.Status.TRUNCATED,
                second.content().status());
        assertTrue(result.selection().usedTokens() <= initialTokens + 300);
        assertTrue(!result.warnings().contains(
                "CONTENT_ALLOCATION_METADATA_BUDGET_EXHAUSTED"));
    }

    private Source source(UUID projectId, UUID sourceId) {
        return Source.builder().id(sourceId).active(true)
                .type(SourceType.GIT_REPOSITORY)
                .project(Project.builder().id(projectId).build()).build();
    }

    private ContextRequest request(UUID projectId, int maximumTokens) {
        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "DevLog", "devlog",
                        null, ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(),
                        AnalysisType.ARCHITECTURE_REVIEW, "story", "v1",
                        AnalysisStatus.IN_PROGRESS, Instant.EPOCH, null, Instant.EPOCH),
                null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        return new ContextRequest(context, null, null, List.of(), null,
                new RepositoryContext.ContextBudget(60, 500, 20, maximumTokens));
    }

    private EvidenceSelector.SelectionResult selection(
            List<RepositoryEvidence> selected
    ) {
        List<RepositoryContext.SelectionDecision> decisions = selected.stream()
                .map(value -> new RepositoryContext.SelectionDecision(
                        value.reference(), true, "SELECTED_BY_RANK_AND_DIVERSITY",
                        value.relevanceScore(), value.estimatedTokens()))
                .toList();
        int tokens = selected.stream().mapToInt(RepositoryEvidence::estimatedTokens).sum();
        return new EvidenceSelector.SelectionResult(selected, decisions, tokens);
    }

    private RepositoryEvidence evidence(UUID sourceId, String kind, String path) {
        return evidence(sourceId, kind, path, 0, 0);
    }

    private RepositoryEvidence evidence(
            UUID sourceId,
            String kind,
            String path,
            int finalScore,
            int semanticStrength
    ) {
        EvidenceScore score = new EvidenceScore("test", Map.of(), Map.of(), finalScore,
                List.of(), new EvidenceScore.MatchStrength(semanticStrength, 0));
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, (kind.equals("CONFIG_FILE") ? "config:" : "file:") + path,
                path, Instant.EPOCH, score, List.of(),
                new RepositoryEvidence.EvidenceProvenance("REPOSITORY_STRUCTURE",
                        sourceId.toString(), path, "structure:" + path),
                Map.of("collectorId", "repository-structure",
                        "collectorVersion", "v2", "resolvedRevision", "abc123"),
                Math.max(1, (path.length() * 2 + 3) / 4),
                List.of("COLLECTED_NOT_RANKED"));
    }
}
