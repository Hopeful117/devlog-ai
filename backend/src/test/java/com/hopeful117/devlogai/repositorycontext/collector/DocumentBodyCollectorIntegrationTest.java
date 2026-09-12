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
import com.hopeful117.devlogai.repositorycontext.DocumentPriorityComparator;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentBodyCollectorIntegrationTest {

    @Mock private SecureRepositoryContentReader contentReader;
    @Mock private SourceRepository sourceRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private EngineeringStoryRepository storyRepository;
    @Mock private DocumentReferenceExtractor referenceExtractor;
    @Mock private DocumentBudgetPolicy budgetPolicy;

    private DocumentBodyCollector collector;
    private SynchronizedWorkspace workspace;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final String REVISION = "abc123";
    private static final String STORY_PATH = "docs/stories/0118/story.md";
    private static final String ADR_PATH = "docs/decisions/ADR-0067.md";

    @BeforeEach
    void setUp() {
        collector = new DocumentBodyCollector(
                contentReader, sourceRepository, workspaceManager, storyRepository,
                referenceExtractor, new AdrStatusParser(), budgetPolicy,
                new DocumentPriorityComparator(), new EvidenceFactory());
        lenient().when(budgetPolicy.maxCharactersPerDocument()).thenReturn(4000);
        lenient().when(budgetPolicy.maxSelectedDocuments()).thenReturn(5);
        lenient().when(budgetPolicy.maxTotalCharacters()).thenReturn(12000);
    }

    @Test
    void scopePropagatedToWorkspaceManager() {
        Source source = prepareWorkspace("target_commit_456");
        stubStoryOnly("# Story 0118");

        collector.collect(createContextRequest(createStory(), createScope("target_commit_456")));

        verify(workspaceManager).synchronize(source, "target_commit_456");
    }

    @Test
    void revisionPrecedenceUsesScopeOverStory() {
        Source source = prepareWorkspace("scope_revision");
        stubStoryOnly("# Story 0118");

        collector.collect(createContextRequest(createStory(), createScope("scope_revision")));

        verify(workspaceManager).synchronize(source, "scope_revision");
    }

    @Test
    void budgetEnforcedOnDocumentBody() {
        when(budgetPolicy.maxCharactersPerDocument()).thenReturn(100);
        prepareWorkspace(REVISION);
        stubStoryOnly("S".repeat(100));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope(REVISION)));

        assertEquals(1, result.size());
        assertEquals(100, result.getFirst().content().text().length());
        verify(contentReader, org.mockito.Mockito.times(2))
                .readComplete(workspace, STORY_PATH, 100);
    }

    @Test
    void adrStatusParsedAndStored() {
        prepareWorkspace(REVISION);
        String adrContent = "# ADR-067: Test\n\n## Status\n**ACCEPTED**";
        stubStoryAndAdr(adrContent);

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope(REVISION)));

        RepositoryEvidence adr = result.stream()
                .filter(evidence -> evidence.kind().equals("ADR_DOCUMENT"))
                .findFirst().orElseThrow();
        assertEquals(DocumentStatus.ACCEPTED.name(),
                adr.extractionMetadata().get("documentStatus"));
    }

    @Test
    void supersededAdrDetected() {
        prepareWorkspace(REVISION);
        String adrContent = "# ADR-067: Test\n\n## Status\n**SUPERSEDED** by [ADR-068]";
        stubStoryAndAdr(adrContent);

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope(REVISION)));

        RepositoryEvidence adr = result.stream()
                .filter(evidence -> evidence.kind().equals("ADR_DOCUMENT"))
                .findFirst().orElseThrow();
        assertEquals(DocumentStatus.SUPERSEDED.name(),
                adr.extractionMetadata().get("documentStatus"));
        assertEquals("ADR-068", adr.extractionMetadata().get("supersededBy"));
    }

    @Test
    void oneHopTraversalCollectsReferencedAdrs() {
        prepareWorkspace(REVISION);
        stubStoryAndAdr("# ADR-067: Test\n\n## Status\n**ACCEPTED**");

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope(REVISION)));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(evidence ->
                evidence.provenance().originatingFile().equals(ADR_PATH)));
        assertFalse(result.stream().anyMatch(evidence -> evidence.content().text().isEmpty()));
    }

    @Test
    void unavailableAdrBodyIsExcludedFromEvidence() {
        prepareWorkspace(REVISION);
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        Set.of("67"), Set.of(), Set.of(), false));
        stubContents(Map.of(
                STORY_PATH, "# Story 0118\n\nSee ADR-067.",
                ADR_PATH, "unused"), Set.of(ADR_PATH));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope(REVISION)));

        assertEquals(1, result.size());
        assertEquals(STORY_PATH, result.getFirst().provenance().originatingFile());
    }

    private Source prepareWorkspace(String revision) {
        Source source = Source.builder().id(SOURCE_ID).build();
        workspace = new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), revision);
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, revision)).thenReturn(workspace);
        return source;
    }

    private void stubStoryOnly(String storyContent) {
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        Set.of(), Set.of(), Set.of(), false));
        stubContents(Map.of(STORY_PATH, storyContent));
    }

    private void stubStoryAndAdr(String adrContent) {
        when(referenceExtractor.extract(anyString())).thenReturn(
                new DocumentReferenceExtractor.ExtractedReferences(
                        Set.of("67"), Set.of(), Set.of(), false));
        stubContents(Map.of(
                STORY_PATH, "# Story 0118\n\nSee ADR-067.",
                ADR_PATH, adrContent));
    }

    private void stubContents(Map<String, String> documents) {
        stubContents(documents, Set.of());
    }

    private void stubContents(Map<String, String> documents, Set<String> unavailablePaths) {
        when(contentReader.readComplete(eq(workspace), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(1);
                    if (unavailablePaths.contains(path)) {
                        return new SecureRepositoryContentReader.ReadResult(
                                SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                                null, "FILE_UNAVAILABLE");
                    }
                    String text = documents.get(path);
                    int maximum = invocation.getArgument(2);
                    boolean skipped = text.length() > maximum;
                    return new SecureRepositoryContentReader.ReadResult(
                            skipped
                                    ? SecureRepositoryContentReader.ReadResult.Status.SKIPPED
                                    : SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                            skipped ? null : text,
                            skipped ? "INPUT_TOO_LARGE" : null);
                });
    }

    private EngineeringStorySnapshot createStory() {
        return new EngineeringStorySnapshot(
                UUID.randomUUID(), PROJECT_ID, 118,
                "Story 118", "IN_PROGRESS", STORY_PATH,
                "story_base", "story_target", Instant.EPOCH, null);
    }

    private RepositoryRevisionScope createScope(String revision) {
        return new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, revision,
                Path.of("/workspace"), "STORY_TARGET_COMMIT");
    }

    private ContextRequest createContextRequest(
            EngineeringStorySnapshot story,
            RepositoryRevisionScope scope
    ) {
        return new ContextRequest(
                createAnalysisContext(List.of(story)), createIntent(), null, List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000), scope);
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
                List.of(), List.of(), null,
                List.of(), List.of(), List.of(), stories, List.of());
    }

    private IntentDefinition createIntent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze",
                List.of(), List.of(), Map.of(), "prompt-v1");
    }
}
