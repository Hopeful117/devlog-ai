package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.repositorycontext.collector.CurrentAnalysisContextCollector;
import com.hopeful117.devlogai.repositorycontext.collector.DeterministicKnowledgeContextCollector;
import com.hopeful117.devlogai.repositorycontext.collector.EvidenceFactory;
import com.hopeful117.devlogai.repositorycontext.collector.GitHistoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.collector.ProjectKnowledgeContextCollector;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.intelligence.DeterministicContextIntelligence;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedFileContentEnricher;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedJavaSymbolEnricher;
import com.hopeful117.devlogai.repositorycontext.ranking.DeterministicEvidenceRanker;
import com.hopeful117.devlogai.repositorycontext.selection.BudgetedDiverseEvidenceSelector;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepositoryContextServiceTest {
    @Test
    void assemblesRankedTraceableRepositoryLayersWithoutRawDiffs() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ProjectCommit commit = ProjectCommit.builder()
                .project(Project.builder().id(projectId).build())
                .source(Source.builder().id(sourceId).build())
                .commitHash("abcdef").subject("Implement repository context")
                .fullMessage("This full message is contextual but is not a source of truth")
                .committedAt(Instant.parse("2026-07-23T10:00:00Z"))
                .filesChanged(4).insertions(50).deletions(3).build();
        commit.addParent(0, "parent");
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of(commit));
        RepositoryContextEngine service = engine(commits, 10, 100, 5, 1000);
        AnalysisContext context = context(projectId, List.of(
                fact(FactType.ADR_DOCUMENT_PRESENT, "ADR changed", "docs/decisions/ADR-037.md"),
                fact(FactType.TECHNOLOGY, "Spring Boot application", "backend/pom.xml"),
                fact(FactType.README_PRESENT, "README updated", "README.md")));

        RepositoryContext first = service.build(context, uncappedIntent(), null, List.of());
        RepositoryContext second = service.build(context, uncappedIntent(), null, List.of());

        assertEquals(first, second);
        assertFalse(first.truncated());
        assertEquals(ContextProfile.ARCHITECTURE_REVIEW, first.profile());
        assertEquals(List.of("architecture-v1"),
                first.activeProfileKeys());
        assertEquals("context-intelligence-v2", first.contextPlanVersion());
        assertTrue(first.selectedByLayer().containsKey(RepositoryContextLayer.CURRENT_ANALYSIS));
        assertTrue(first.selectedByLayer().containsKey(RepositoryContextLayer.GIT_HISTORY));
        assertTrue(first.selectedByLayer().containsKey(RepositoryContextLayer.ADR));
        assertTrue(first.selectedByLayer().containsKey(
                RepositoryContextLayer.PROJECT_DOCUMENTATION));
        assertEquals(first.candidateCount(), first.diagnostics().candidatesByLayer()
                .values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(first.evidence().size(), first.diagnostics().selectedByKind()
                .values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(first.diagnostics().preferredLayerAvailability().stream()
                .anyMatch(value -> value.layer() == RepositoryContextLayer.COMMIT_DIFF
                        && !value.candidateAvailable()
                        && value.reason().equals("NO_CANDIDATE_FOR_PREFERRED_LAYER")));
        RepositoryEvidence history = first.evidence().stream()
                .filter(value -> value.layer() == RepositoryContextLayer.GIT_HISTORY)
                .findFirst().orElseThrow();
        assertEquals("git:" + sourceId + ":abcdef", history.reference());
        assertTrue(history.relatedReferences().contains("git:" + sourceId + ":parent"));
        assertFalse(history.summary().contains("source of truth"));
        assertEquals("GIT", history.provenance().sourceType());
        assertEquals("git-history", history.extractionMetadata().get("collectorId"));
        assertEquals("multi-criteria-v2", history.score().policyVersion());
        assertTrue(history.score().criteria().size() >= 6);
        int weightedScore = history.score().criteria().entrySet().stream()
                .mapToInt(entry -> entry.getValue()
                        * history.score().weights().get(entry.getKey())).sum();
        int totalWeight = history.score().weights().values().stream()
                .mapToInt(Integer::intValue).sum();
        int expectedScore = (int) Math.round((double) weightedScore / totalWeight);
        assertEquals(expectedScore, history.score().finalScore());
        assertTrue(history.rankingReasons().stream()
                .anyMatch(value -> value.startsWith("HISTORICAL_RELEVANCE=")));
        assertTrue(first.usedTokens() <= first.budget().maximumTokens());
        assertEquals(first.candidateCount(), first.selectionDecisions().size());
        assertTrue(first.contextDigest().matches("[0-9a-f]{64}"));
        verify(commits, times(2))
                .findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                        eq(projectId), any(Pageable.class));
    }

    @Test
    void appliesGlobalBudgetAndReportsDiscardedEvidence() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        RepositoryContextEngine service = engine(commits, 2, 50, 1, 1000);
        AnalysisContext context = context(projectId, List.of(
                fact(FactType.TECHNOLOGY, "one", "one.java"),
                fact(FactType.TECHNOLOGY, "two", "two.java"),
                fact(FactType.TECHNOLOGY, "three", "three.java")));

        RepositoryContext result = service.build(context, uncappedIntent(), null, List.of());

        assertEquals(2, result.evidence().size());
        assertTrue(result.truncated());
        assertEquals(2, result.discardedCount());
        assertEquals(List.of("REPOSITORY_CONTEXT_BUDGET_APPLIED"), result.warnings());
        assertEquals(2, result.selectionDecisions().stream()
                .filter(RepositoryContext.SelectionDecision::selected).count());
    }

    @Test
    void enforcesTokenBudgetAndAcceptsAdditionalCollectorWithoutEngineChanges() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        EvidenceFactory factory = new EvidenceFactory();
        RepositoryContextCollector extension = new RepositoryContextCollector() {
            @Override public String collectorId() { return "extension"; }
            @Override public String collectorVersion() { return "v1"; }
            @Override
            public List<RepositoryEvidence> collect(ContextRequest request) {
                return List.of(factory.create(
                        new EvidenceFactory.ContextRequestMetadata(
                                collectorId(), collectorVersion(), "EXTENSION"),
                        new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.COMMIT_DIFF, "EXTENSION_EVIDENCE",
                        "extension:1", "bounded extension evidence", Instant.EPOCH,
                        List.of(), "repository", "extension.txt", "1"),
                        request.budget().maximumSummaryCharacters()));
            }
        };
        RepositoryContextEngine service = engine(
                commits, 10, 100, 0, 20, extension);

        RepositoryContext result = service.build(
                context(projectId, List.of(
                        fact(FactType.TECHNOLOGY, "additional evidence", "src/App.java"))),
                intent(), null, List.of());

        assertTrue(result.usedTokens() <= 20);
        assertTrue(result.selectionDecisions().stream().anyMatch(decision ->
                decision.evidenceReference().equals("extension:1")));
        assertTrue(result.truncated());
    }

    @Test
    void composesMixedFileCandidatesWithEngineeringStoryPrecision() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        EvidenceFactory factory = new EvidenceFactory();
        RepositoryContextCollector structure = new RepositoryContextCollector() {
            @Override public String collectorId() { return "repository-structure"; }
            @Override public String collectorVersion() { return "v2"; }
            @Override
            public List<RepositoryEvidence> collect(ContextRequest request) {
                return List.of(
                        fileEvidence(factory, request, "SOURCE_FILE",
                                "backend/src/main/java/example/RepositoryCandidateService.java"),
                        fileEvidence(factory, request, "TEST_FILE",
                                "backend/src/test/java/example/RepositoryCandidateServiceTest.java"),
                        fileEvidence(factory, request, "CONFIG_FILE", "backend/pom.xml"));
            }
        };
        RepositoryContextEngine service = engine(
                commits, 20, 200, 0, 1000, structure);

        RepositoryContext result = service.build(context(projectId, List.of()),
                engineeringStoryIntent(), null, List.of());

        assertEquals(1, result.diagnostics().candidatesByKind().get("SOURCE_FILE"));
        assertEquals(1, result.diagnostics().candidatesByKind().get("TEST_FILE"));
        assertEquals(1, result.diagnostics().candidatesByKind().get("CONFIG_FILE"));
        assertTrue(result.evidence().stream().anyMatch(value ->
                value.kind().equals("SOURCE_FILE")));
        assertTrue(result.evidence().stream().anyMatch(value ->
                value.kind().equals("TEST_FILE")));
        assertTrue(result.evidence().stream().anyMatch(value ->
                value.kind().equals("CONFIG_FILE")));
        assertTrue(result.evidence().stream().filter(value -> value.kind().endsWith("_FILE"))
                .allMatch(value -> value.extractionMetadata().get("collectorVersion").equals("v2")));
        assertTrue(result.usedTokens() <= result.budget().maximumTokens());
        assertEquals(result.candidateCount(), result.selectionDecisions().size());
    }

    @Test
    void includesEnrichedContentAndMetadataInContextDigest() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        EvidenceFactory factory = new EvidenceFactory();
        RepositoryContextCollector structure = new RepositoryContextCollector() {
            @Override public String collectorId() { return "repository-structure"; }
            @Override public String collectorVersion() { return "v2"; }
            @Override
            public List<RepositoryEvidence> collect(ContextRequest request) {
                return List.of(fileEvidence(factory, request, "SOURCE_FILE",
                        "backend/src/main/java/example/App.java"));
            }
        };
        AnalysisContext context = context(projectId, List.of());

        RepositoryContext first = engine(commits, 20, 200, 0, 1000,
                structure, contentEnricher("class App {}"))
                .build(context, engineeringStoryIntent(), null, List.of());
        RepositoryContext second = engine(commits, 20, 200, 0, 1000,
                structure, contentEnricher("class App { int value; }"))
                .build(context, engineeringStoryIntent(), null, List.of());

        assertNotEquals(first.contextDigest(), second.contextDigest());
        assertEquals("class App {}", first.evidence().getFirst().content().text());
        assertEquals("abc123", first.evidence().getFirst().content().revision());
    }

    @Test
    void includesContentAllocationDecisionInContextDigest() {
        ProjectCommitRepository commits = mock(ProjectCommitRepository.class);
        UUID projectId = UUID.randomUUID();
        when(commits.findByProjectIdOrderByCommittedAtDescCommitHashDesc(
                eq(projectId), any(Pageable.class))).thenReturn(List.of());
        EvidenceFactory factory = new EvidenceFactory();
        RepositoryContextCollector structure = new RepositoryContextCollector() {
            @Override public String collectorId() { return "repository-structure"; }
            @Override public String collectorVersion() { return "v2"; }
            @Override
            public List<RepositoryEvidence> collect(ContextRequest request) {
                return List.of(fileEvidence(factory, request, "SOURCE_FILE",
                        "backend/src/main/java/example/App.java"));
            }
        };
        AnalysisContext context = context(projectId, List.of());

        RepositoryContext first = engine(commits, 20, 200, 0, 1000,
                structure, contentEnricher("class App {}", 1))
                .build(context, engineeringStoryIntent(), null, List.of());
        RepositoryContext second = engine(commits, 20, 200, 0, 1000,
                structure, contentEnricher("class App {}", 2))
                .build(context, engineeringStoryIntent(), null, List.of());

        assertNotEquals(first.contextDigest(), second.contextDigest());
        assertEquals(1, first.evidence().getFirst().content().allocationRank());
        assertEquals(2, second.evidence().getFirst().content().allocationRank());
    }

    private RepositoryEvidence fileEvidence(
            EvidenceFactory factory,
            ContextRequest request,
            String kind,
            String path
    ) {
        String prefix = kind.equals("CONFIG_FILE") ? "config:" : "file:";
        return factory.create(
                new EvidenceFactory.ContextRequestMetadata(
                        "repository-structure", "v2", "REPOSITORY_STRUCTURE"),
                new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.RELATED_SOURCE_CODE, kind,
                        prefix + path, path, Instant.EPOCH, List.of(),
                        "repository", path, "repository-structure:" + kind + ":" + path),
                request.budget().maximumSummaryCharacters());
    }

    private AnalysisContext context(
            UUID projectId,
            List<AnalysisContext.FactSnapshot> facts
    ) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "DevLog", "devlog", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(),
                        AnalysisType.ARCHITECTURE_REVIEW, "architecture-overview", "v1",
                        AnalysisStatus.IN_PROGRESS, Instant.EPOCH, null, Instant.EPOCH),
                mock(com.hopeful117.devlogai.profile.dto.ProjectProfileResponse.class),
                facts, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of());
    }

    private AnalysisContext.FactSnapshot fact(
            FactType type,
            String content,
            String reference
    ) {
        return new AnalysisContext.FactSnapshot(UUID.randomUUID(), type, content, reference,
                List.of(reference), Instant.EPOCH);
    }

    private IntentDefinition intent() {
        return new IntentDefinition("architecture-overview", "v1", "Describe architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1",
                List.of("architecture-v1", "history-v1"));
    }

    private IntentDefinition uncappedIntent() {
        return new IntentDefinition("architecture-overview", "v1", "Describe architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1",
                List.of("architecture-v1"));
    }

    private IntentDefinition engineeringStoryIntent() {
        return new IntentDefinition("engineering-story-preparation", "v1",
                "Produce repository source test configuration candidates",
                List.of(), List.of("deterministic evidence only"),
                Map.of(), "engineering-story-context-v1",
                List.of("engineering-story-v1"));
    }

    private RepositoryContextEngine engine(
            ProjectCommitRepository commits,
            int maximumItems,
            int maximumSummary,
            int maximumHistory,
            int maximumTokens
    ) {
        return engine(commits, maximumItems, maximumSummary,
                maximumHistory, maximumTokens, null);
    }

    private RepositoryContextEngine engine(
            ProjectCommitRepository commits,
            int maximumItems,
            int maximumSummary,
            int maximumHistory,
            int maximumTokens,
            RepositoryContextCollector extension
    ) {
        SelectedFileContentEnricher enricher = mock(SelectedFileContentEnricher.class);
        when(enricher.enrich(any(), any())).thenAnswer(invocation ->
                new SelectedFileContentEnricher.EnrichmentResult(
                        invocation.getArgument(1), List.of()));
        return engine(commits, maximumItems, maximumSummary, maximumHistory,
                maximumTokens, extension, enricher);
    }

    private RepositoryContextEngine engine(
            ProjectCommitRepository commits,
            int maximumItems,
            int maximumSummary,
            int maximumHistory,
            int maximumTokens,
            RepositoryContextCollector extension,
            SelectedFileContentEnricher enricher
    ) {
        EvidenceFactory factory = new EvidenceFactory();
        var collectors = new java.util.ArrayList<RepositoryContextCollector>(List.of(
                new CurrentAnalysisContextCollector(factory),
                new DeterministicKnowledgeContextCollector(factory),
                new GitHistoryContextCollector(commits, factory),
                new ProjectKnowledgeContextCollector(factory)));
        if (extension != null) collectors.add(extension);
        return new RepositoryContextEngine(
                collectors,
                new DeterministicContextIntelligence(),
                new DeterministicEvidenceRanker(),
                new BudgetedDiverseEvidenceSelector(),
                symbolEnricher(),
                enricher,
                new ObjectMapper(), maximumItems, maximumSummary,
                maximumHistory, maximumTokens);
    }

    private SelectedJavaSymbolEnricher symbolEnricher() {
        SelectedJavaSymbolEnricher enricher = mock(SelectedJavaSymbolEnricher.class);
        when(enricher.enrich(any(), any())).thenAnswer(invocation ->
                new SelectedJavaSymbolEnricher.EnrichmentResult(
                        invocation.getArgument(1), List.of()));
        return enricher;
    }

    private SelectedFileContentEnricher contentEnricher(String text) {
        return contentEnricher(text, 1);
    }

    private SelectedFileContentEnricher contentEnricher(String text, int allocationRank) {
        SelectedFileContentEnricher enricher = mock(SelectedFileContentEnricher.class);
        when(enricher.enrich(any(), any())).thenAnswer(invocation -> {
            com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector.SelectionResult
                    selection = invocation.getArgument(1);
            List<RepositoryEvidence> enriched = selection.selected().stream()
                    .map(value -> value.withContent(new RepositoryEvidenceContent(
                            RepositoryEvidenceContent.Status.COMPLETE, text, null,
                            "selected-file-content", "v1", "abc123",
                            "selected-content-allocation", "v1", allocationRank,
                            List.of("FINAL_SCORE=49", "SEMANTIC_MATCH_STRENGTH=7",
                                    "GUIDANCE_MATCH_STRENGTH=3"))))
                    .toList();
            int usedTokens = enriched.stream()
                    .mapToInt(RepositoryEvidence::estimatedTokens).sum();
            return new SelectedFileContentEnricher.EnrichmentResult(
                    new com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector.SelectionResult(
                            enriched, selection.decisions(), usedTokens), List.of());
        });
        return enricher;
    }
}
