package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.collection.collector.SecureRepositoryContentReader;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot;
import com.hopeful117.devlogai.repositorycontext.AdrStatusParser;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.DocumentBudgetPolicy;
import com.hopeful117.devlogai.repositorycontext.DocumentReferenceExtractor;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentBodyCollectorTest {

    @Mock private SecureRepositoryContentReader contentReader;
    @Mock private SourceRepository sourceRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private EngineeringStoryRepository storyRepository;
    @Mock private DocumentReferenceExtractor referenceExtractor;
    @Mock private AdrStatusParser adrStatusParser;
    @Mock private DocumentBudgetPolicy budgetPolicy;
    @Mock private EvidenceFactory evidenceFactory;

    private DocumentBodyCollector collector;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final String REVISION = "abc123";

    @BeforeEach
    void setUp() {
        collector = new DocumentBodyCollector(
                contentReader, sourceRepository, workspaceManager, storyRepository,
                referenceExtractor, adrStatusParser, budgetPolicy, evidenceFactory);
        lenient().when(budgetPolicy.maxCharactersPerDocument()).thenReturn(4000);
        lenient().when(budgetPolicy.maxSelectedDocuments()).thenReturn(5);
    }

    @Test
    void skipsWhenNoRevisionScope() {
        EngineeringStorySnapshot story = new EngineeringStorySnapshot(
                UUID.randomUUID(), PROJECT_ID, 118,
                "Story", "IN_PROGRESS", "docs/story.md",
                null, null, Instant.now(), null);

        ContextRequest request = createContextRequest(story, null);

        List<RepositoryEvidence> result = collector.collect(request);

        assertTrue(result.isEmpty(), "Should return empty when no revision scope");
    }

    @Test
    void skipsWhenNoStories() {
        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, REVISION,
                Path.of("/workspace"), "STORY_TARGET_COMMIT");

        AnalysisContext analysisContext = createAnalysisContext(List.of());

        ContextRequest request = new ContextRequest(
                analysisContext,
                createIntent(),
                null, List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                scope);

        List<RepositoryEvidence> result = collector.collect(request);

        assertTrue(result.isEmpty(), "Should return empty when no stories");
    }

    @Test
    void storySummaryUsesTypedAccess() {
        EngineeringStorySnapshot story = new EngineeringStorySnapshot(
                UUID.randomUUID(), PROJECT_ID, 118,
                "OAuth Gateway", "IN_PROGRESS",
                "docs/stories/0118/story.md",
                null, null, Instant.now(), null);

        RepositoryRevisionScope scope = new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, REVISION,
                Path.of("/workspace"), "STORY_TARGET_COMMIT");

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

        SynchronizedWorkspace workspace = new SynchronizedWorkspace(
                SOURCE_ID, Path.of("/workspace"), REVISION);
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(workspace);

        SecureRepositoryContentReader.ReadResult readResult =
                new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                        "# Story 0118", null);
        when(contentReader.readComplete(workspace, "docs/stories/0118/story.md", 4000))
                .thenReturn(readResult);

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(mockEvidence.summary()).thenReturn("OAuth Gateway — IN_PROGRESS");
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false));

        ContextRequest request = createContextRequest(story, scope);

        List<RepositoryEvidence> result = collector.collect(request);

        assertFalse(result.isEmpty());
        RepositoryEvidence evidence = result.getFirst();
        assertTrue(evidence.summary().contains("OAuth Gateway"),
                "Summary should use typed title access");
        assertTrue(evidence.summary().contains("IN_PROGRESS"),
                "Summary should use typed status access");
    }

    private AnalysisContext createAnalysisContext(
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringStorySnapshot> stories) {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(PROJECT_ID, "Test", "test", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(),
                        AnalysisType.STORY_CONTEXT_ANALYSIS,
                        "engineering-story-context-analysis", "v1",
                        AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(),
                null,
                List.of(), List.of(), List.of(), stories, List.of());
    }

    private ContextRequest createContextRequest(EngineeringStorySnapshot story,
                                                 RepositoryRevisionScope scope) {
        return new ContextRequest(
                createAnalysisContext(List.of(story)),
                createIntent(),
                null,
                List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                scope);
    }

    private IntentDefinition createIntent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze",
                List.of(), List.of(), Map.of(), "prompt-v1");
    }
}
