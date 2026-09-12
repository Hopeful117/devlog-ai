package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.collection.collector.CollectorLimits;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot;
import com.hopeful117.devlogai.repositorycontext.AdrStatusParser;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.DocumentBudgetPolicy;
import com.hopeful117.devlogai.repositorycontext.DocumentPriorityComparator;
import com.hopeful117.devlogai.repositorycontext.DocumentReferenceExtractor;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextEngine;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;
import com.hopeful117.devlogai.repositorycontext.collector.DocumentBodyCollector;
import com.hopeful117.devlogai.repositorycontext.collector.EvidenceFactory;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedFileContentEnricher;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedJavaSymbolEnricher;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextIntelligence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import com.hopeful117.devlogai.repositorycontext.ranking.EvidenceRanker;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeSelectionServiceRepositoryContextPropagationTest {

    private static final String REVISION = "abc123def";

    @TempDir
    Path workspacePath;

    @Test
    void scopeSurvivesSelectionEngineAndDocumentCollector() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String storyPath = "docs/stories/0119/story.md";
        Path storyFile = workspacePath.resolve(storyPath);
        Files.createDirectories(storyFile.getParent());
        Files.writeString(storyFile, "# Story 0119\n\nRepository-scoped evidence.");

        Source source = Source.builder()
                .id(sourceId)
                .lastSynchronizedAt(Instant.parse("2026-09-10T10:00:00Z"))
                .build();
        SourceRepository sourceRepository = mock(SourceRepository.class);
        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        EngineeringStoryRepository storyRepository = mock(EngineeringStoryRepository.class);
        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, REVISION));

        DocumentBodyCollector documentCollector = new DocumentBodyCollector(
                new SecureRepositoryContentReader(new CollectorLimits()),
                sourceRepository,
                workspaceManager,
                storyRepository,
                new DocumentReferenceExtractor(),
                new AdrStatusParser(),
                new DocumentBudgetPolicy(5, 4000, 12000),
                new DocumentPriorityComparator(),
                new EvidenceFactory());

        ContextPlan contextPlan = new ContextPlan(
                "context-plan-v1",
                List.of(new ContextProfileDefinition(
                        "project-state", ContextProfile.PROJECT_STATE, "v1",
                        Map.of(), List.of(), 0, 1)),
                Map.of(), List.of(), 0, List.of());
        ContextIntelligence contextIntelligence = mock(ContextIntelligence.class);
        when(contextIntelligence.plan(any(), any())).thenReturn(contextPlan);
        EvidenceRanker ranker = (evidence, request) -> evidence;
        EvidenceSelector selector = (ranked, request) -> new EvidenceSelector.SelectionResult(
                ranked, List.of(), ranked.stream().mapToInt(value -> value.estimatedTokens()).sum());
        SelectedJavaSymbolEnricher symbolEnricher = mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation ->
                new SelectedJavaSymbolEnricher.EnrichmentResult(
                        invocation.getArgument(1), List.of()));
        SelectedFileContentEnricher contentEnricher = mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenAnswer(invocation ->
                new SelectedFileContentEnricher.EnrichmentResult(
                        invocation.getArgument(1), List.of()));

        RepositoryContextEngine repositoryContextEngine = new RepositoryContextEngine(
                List.of(documentCollector), contextIntelligence, ranker, selector,
                symbolEnricher, contentEnricher, new ObjectMapper(),
                60, 500, 20, 6000);
        AnalysisExecutionDiagnosticRepository diagnostics =
                mock(AnalysisExecutionDiagnosticRepository.class);
        InsightRepository insights = mock(InsightRepository.class);
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(
                AnalysisExecutionDiagnostic.builder()
                        .analysisId(analysisId)
                        .collectionComplete(true)
                        .build()));
        when(insights.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(com.hopeful117.devlogai.insight.entity.InsightStatus.ACTIVE)))
                .thenReturn(List.of());

        KnowledgeSelectionServiceImpl selectionService = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, new ObjectMapper(), repositoryContextEngine, 15);
        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                projectId, sourceId, REVISION, workspacePath, "STORY_TARGET_COMMIT");

        SelectedKnowledge selected = selectionService.select(
                analysisContext(projectId, analysisId, storyPath), intent(), null, scope);

        assertNotNull(selected.repositoryContext());
        assertFalse(selected.repositoryContext().evidence().isEmpty());
        var evidence = selected.repositoryContext().evidence().stream()
                .filter(item -> item.kind().equals("STORY_DOCUMENT"))
                .findFirst()
                .orElseThrow();
        assertEquals("REPOSITORY_DOCUMENT", evidence.provenance().sourceType());
        assertNotNull(evidence.content());
        assertFalse(evidence.content().text().isBlank());
        assertTrue(evidence.reference().contains(sourceId.toString()));
        assertTrue(evidence.reference().contains(storyPath));
        assertTrue(evidence.reference().endsWith("@" + REVISION));
    }

    private AnalysisContext analysisContext(UUID projectId, UUID analysisId, String storyPath) {
        Instant now = Instant.parse("2026-09-10T10:00:00Z");
        ProjectProfileResponse profile = new ProjectProfileResponse(
                UUID.randomUUID(), projectId, analysisId, "v1", "v1", now, null,
                Map.of(), new ProjectProfileResponse.Completeness(
                        ProfileCompletenessStatus.COMPLETE, true, false, 0, 0, 1, 0, 0),
                List.of(), "profile", List.of(), 0);
        EngineeringStorySnapshot story = new EngineeringStorySnapshot(
                UUID.randomUUID(), projectId, 119, "Story 0119", "IN_PROGRESS",
                storyPath, null, REVISION, now, null);
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(
                        projectId, "Project", "project", null, ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(
                        analysisId, AnalysisType.STORY_CONTEXT_ANALYSIS,
                        "engineering-story-context-analysis", "v1",
                        AnalysisStatus.IN_PROGRESS, now, null, now),
                profile,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null, List.of(), List.of(), List.of(),
                List.of(story), List.of());
    }

    private IntentDefinition intent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze Story",
                List.of(), List.of(), Map.of("type", "object"), "prompt-v1");
    }
}
