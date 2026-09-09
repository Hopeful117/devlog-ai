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
import com.hopeful117.devlogai.repositorycontext.DocumentStatus;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryRevisionScope;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class DocumentBodyCollectorIntegrationTest {

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
    void scopePropagatedToWorkspaceManager() {
        EngineeringStorySnapshot story = createStory(118, "docs/stories/0118/story.md");
        RepositoryRevisionScope scope = createScope("target_commit_456");

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, "target_commit_456")).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), "target_commit_456"));
        when(contentReader.readComplete(any(), anyString(), anyInt())).thenReturn(
                new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE, "# Story", null));
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        collector.collect(request);

        verify(workspaceManager).synchronize(source, "target_commit_456");
    }

    @Test
    void revisionPrecedenceUsesScopeOverStory() {
        EngineeringStorySnapshot story = createStory(118, "docs/stories/0118/story.md");
        RepositoryRevisionScope scope = createScope("scope_revision");

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, "scope_revision")).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), "scope_revision"));
        when(contentReader.readComplete(any(), anyString(), anyInt())).thenReturn(
                new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE, "# Story", null));
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        collector.collect(request);

        verify(workspaceManager).synchronize(source, "scope_revision");
    }

    @Test
    void budgetEnforcedOnDocumentBody() {
        lenient().when(budgetPolicy.maxCharactersPerDocument()).thenReturn(100);
        lenient().when(budgetPolicy.maxSelectedDocuments()).thenReturn(5);

        EngineeringStorySnapshot story = createStory(118, "docs/stories/0118/story.md");
        RepositoryRevisionScope scope = createScope(REVISION);

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), REVISION));

        String longContent = "# Story 0118\n\n" + "A".repeat(200);
        when(contentReader.readComplete(any(), anyString(), anyInt()))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.TRUNCATED,
                        longContent.substring(0, 100), "Truncated by budget"));
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), false));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        List<RepositoryEvidence> result = collector.collect(request);

        assertFalse(result.isEmpty());
        verify(contentReader, atLeastOnce()).readComplete(any(), anyString(), eq(100));
    }

    @Test
    void adrStatusParsedAndStored() {
        EngineeringStorySnapshot story = createStory(67, "docs/decisions/ADR-067.md");
        RepositoryRevisionScope scope = createScope(REVISION);

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), REVISION));
        when(contentReader.readComplete(any(), anyString(), anyInt())).thenReturn(
                new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                        "# ADR-067: Test\n\n## Status\nACCEPTED", null));
        when(adrStatusParser.parse("# ADR-067: Test\n\n## Status\nACCEPTED"))
                .thenReturn(new AdrStatusParser.AdrStatusResult(DocumentStatus.ACCEPTED, null));
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of("67"), java.util.Set.of(), java.util.Set.of(), false));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        List<RepositoryEvidence> result = collector.collect(request);

        assertFalse(result.isEmpty());
        verify(adrStatusParser).parse("# ADR-067: Test\n\n## Status\nACCEPTED");
    }

    @Test
    void supersededAdrDetected() {
        EngineeringStorySnapshot story = createStory(67, "docs/decisions/ADR-067.md");
        RepositoryRevisionScope scope = createScope(REVISION);

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), REVISION));
        when(contentReader.readComplete(any(), anyString(), anyInt())).thenReturn(
                new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                        "# ADR-067: Test\n\n## Status\n**SUPERSEDED** by [ADR-068]", null));
        when(adrStatusParser.parse(anyString()))
                .thenReturn(new AdrStatusParser.AdrStatusResult(DocumentStatus.SUPERSEDED, "68"));
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of("67"), java.util.Set.of(), java.util.Set.of(), false));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        List<RepositoryEvidence> result = collector.collect(request);

        assertFalse(result.isEmpty());
        verify(adrStatusParser).parse(anyString());
    }

    @Test
    void oneHopTraversalCollectsReferencedAdrs() {
        EngineeringStorySnapshot story = createStory(118, "docs/stories/0118/story.md");
        RepositoryRevisionScope scope = createScope(REVISION);

        Source source = Source.builder().id(SOURCE_ID).build();
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(
                new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), REVISION));

        String storyContent = "# Story 0118\n\nSee ADR-067 for architecture decisions.";
        String adrContent = "# ADR-067: Test\n\n## Status\nACCEPTED";
        when(contentReader.readComplete(any(), anyString(), anyInt()))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE, storyContent, null))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE, storyContent, null))
                .thenReturn(new SecureRepositoryContentReader.ReadResult(
                        SecureRepositoryContentReader.ReadResult.Status.COMPLETE, adrContent, null));
        lenient().when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        java.util.Set.of("67"), java.util.Set.of(), java.util.Set.of(), false));
        when(adrStatusParser.parse(adrContent))
                .thenReturn(new AdrStatusParser.AdrStatusResult(DocumentStatus.ACCEPTED, null));

        RepositoryEvidence mockEvidence = mock(RepositoryEvidence.class);
        when(mockEvidence.withContent(any())).thenReturn(mockEvidence);
        when(mockEvidence.withExtractionMetadata(any())).thenReturn(mockEvidence);
        when(evidenceFactory.create(any(), any(), anyInt())).thenReturn(mockEvidence);

        ContextRequest request = createContextRequest(story, scope);
        List<RepositoryEvidence> result = collector.collect(request);

        assertFalse(result.isEmpty());
        verify(adrStatusParser).parse(adrContent);
    }

    private EngineeringStorySnapshot createStory(int storyNumber, String storyPath) {
        return new EngineeringStorySnapshot(
                UUID.randomUUID(), PROJECT_ID, storyNumber,
                "Story " + storyNumber, "IN_PROGRESS", storyPath,
                null, null, Instant.now(), null);
    }

    private RepositoryRevisionScope createScope(String revision) {
        return new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, revision,
                Path.of("/workspace"), "STORY_TARGET_COMMIT");
    }

    private ContextRequest createContextRequest(EngineeringStorySnapshot story,
                                                 RepositoryRevisionScope scope) {
        return createContextRequest(List.of(story), scope);
    }

    private ContextRequest createContextRequest(List<EngineeringStorySnapshot> stories,
                                                 RepositoryRevisionScope scope) {
        return new ContextRequest(
                createAnalysisContext(stories),
                createIntent(),
                null,
                List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                scope);
    }

    private AnalysisContext createAnalysisContext(List<EngineeringStorySnapshot> stories) {
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

    private IntentDefinition createIntent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze",
                List.of(), List.of(), Map.of(), "prompt-v1");
    }
}
