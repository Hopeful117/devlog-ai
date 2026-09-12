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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentBodyCollectorTest {

    @Mock private SecureRepositoryContentReader contentReader;
    @Mock private SourceRepository sourceRepository;
    @Mock private WorkspaceManager workspaceManager;
    @Mock private EngineeringStoryRepository storyRepository;
    @Mock private DocumentReferenceExtractor referenceExtractor;
    @Mock private DocumentBudgetPolicy budgetPolicy;

    private DocumentPriorityComparator priorityComparator;
    private DocumentBodyCollector collector;
    private SynchronizedWorkspace workspace;

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final String REVISION = "abc123";
    private static final String STORY_PATH = "docs/stories/0119/story.md";

    @BeforeEach
    void setUp() {
        priorityComparator = spy(new DocumentPriorityComparator());
        collector = new DocumentBodyCollector(
                contentReader, sourceRepository, workspaceManager, storyRepository,
                referenceExtractor, new AdrStatusParser(), budgetPolicy,
                priorityComparator, new EvidenceFactory());
        lenient().when(budgetPolicy.maxCharactersPerDocument()).thenReturn(4000);
        lenient().when(budgetPolicy.maxSelectedDocuments()).thenReturn(5);
        lenient().when(budgetPolicy.maxTotalCharacters()).thenReturn(12000);
    }

    @Test
    void skipsWhenNoRevisionScope() {
        assertTrue(collector.collect(createContextRequest(createStory(), null)).isEmpty());
    }

    @Test
    void skipsWhenNoStories() {
        ContextRequest request = new ContextRequest(
                createAnalysisContext(List.of()), createIntent(), null, List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000), createScope());

        assertTrue(collector.collect(request).isEmpty());
    }

    @Test
    void injectsDocumentPriorityComparatorIntoCollectionOrder() {
        prepareWorkspace(null);
        stubDocuments(
                Map.of(STORY_PATH, "story", adrPath(67), acceptedAdr(67)),
                Set.of(), references(Set.of("67"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(List.of(STORY_PATH, adrPath(67)), paths(result));
        verify(priorityComparator, atLeastOnce()).compare(any(), any());
    }

    @Test
    void enforcesMaximumOfFiveSelectedDocuments() {
        prepareWorkspace(null);
        Set<String> adrNumbers = new LinkedHashSet<>(
                List.of("1", "2", "3", "4", "5", "6"));
        Map<String, String> documents = new java.util.HashMap<>();
        documents.put(STORY_PATH, "S".repeat(100));
        adrNumbers.forEach(number -> documents.put(
                adrPath(Integer.parseInt(number)), acceptedAdr(Integer.parseInt(number))));
        stubDocuments(documents, Set.of(), references(adrNumbers, Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(5, result.size());
        assertEquals(List.of(STORY_PATH, adrPath(1), adrPath(2), adrPath(3), adrPath(4)),
                paths(result));
    }

    @Test
    void enforcesTwelveThousandCharacterAggregateBudget() {
        prepareWorkspace(null);
        Map<String, String> documents = Map.of(
                STORY_PATH, "S".repeat(4000),
                adrPath(1), "A".repeat(4000),
                adrPath(2), "B".repeat(4000),
                adrPath(3), "C".repeat(4000));
        stubDocuments(documents, Set.of(), references(Set.of("1", "2", "3"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(3, result.size());
        assertEquals(12000, totalContentCharacters(result));
        assertFalse(paths(result).contains(adrPath(3)));
    }

    @Test
    void respectsFourThousandCharacterPerDocumentLimit() {
        prepareWorkspace(null);
        stubDocuments(Map.of(STORY_PATH, "S".repeat(4000)), Set.of(),
                references(Set.of(), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(1, result.size());
        assertEquals(4000, result.getFirst().content().text().length());
        assertEquals(com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent.Status.COMPLETE,
                result.getFirst().content().status());
        verify(contentReader, org.mockito.Mockito.times(2))
                .readComplete(workspace, STORY_PATH, 4000);
    }

    @Test
    void constrainsPerDocumentLimitByRemainingAggregateBudget() {
        when(budgetPolicy.maxTotalCharacters()).thenReturn(5000);
        prepareWorkspace(null);
        stubDocuments(Map.of(
                        STORY_PATH, "S".repeat(4000),
                        adrPath(1), "A".repeat(3000)),
                Set.of(), references(Set.of("1"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(List.of(4000), contentLengths(result));
        assertFalse(paths(result).contains(adrPath(1)));
        verify(contentReader).readComplete(workspace, adrPath(1), 1000);
    }

    @Test
    void chargesAggregateBudgetUsingActualRetainedContentLength() {
        when(budgetPolicy.maxTotalCharacters()).thenReturn(5000);
        prepareWorkspace(null);
        stubDocuments(Map.of(
                        STORY_PATH, "S".repeat(1000),
                        adrPath(1), "A".repeat(3000),
                        adrPath(2), "B".repeat(1000)),
                Set.of(), references(Set.of("1", "2"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(List.of(1000, 3000, 1000), contentLengths(result));
        assertEquals(5000, totalContentCharacters(result));
        verify(contentReader, org.mockito.Mockito.times(2))
                .readComplete(workspace, adrPath(1), 4000);
        verify(contentReader).readComplete(workspace, adrPath(2), 1000);
    }

    @Test
    void excludesSkippedInputTooLargeDocumentsWithoutConsumingSlots() {
        when(budgetPolicy.maxSelectedDocuments()).thenReturn(2);
        prepareWorkspace(null);
        stubDocuments(Map.of(
                        STORY_PATH, "story",
                        adrPath(1), acceptedAdr(1),
                        adrPath(2), acceptedAdr(2)),
                Set.of(adrPath(1)), references(Set.of("1", "2"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(2, result.size());
        assertEquals(List.of(STORY_PATH, adrPath(2)), paths(result));
        assertTrue(result.stream().noneMatch(evidence ->
                evidence.content().status()
                        == com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent.Status.SKIPPED));
    }

    @Test
    void excludesUnavailableNullDocumentsWithoutConsumingSlots() {
        when(budgetPolicy.maxSelectedDocuments()).thenReturn(2);
        prepareWorkspace(null);
        stubDocuments(Map.of(
                        STORY_PATH, "story",
                        adrPath(1), acceptedAdr(1),
                        adrPath(2), acceptedAdr(2)),
                Set.of(), Set.of(adrPath(1)),
                references(Set.of("1", "2"), Set.of(), false));

        List<RepositoryEvidence> result = collector.collect(
                createContextRequest(createStory(), createScope()));

        assertEquals(2, result.size());
        assertEquals(List.of(STORY_PATH, adrPath(2)), paths(result));
        assertTrue(result.stream().noneMatch(evidence ->
                evidence.content().status()
                        == com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent.Status.UNAVAILABLE));
    }

    @Test
    void storyEvidenceUsesSourceSynchronizationTimestamp() {
        Instant synchronizedAt = Instant.parse("2026-09-10T10:00:00Z");
        prepareWorkspace(synchronizedAt);
        stubDocuments(Map.of(STORY_PATH, "story"), Set.of(),
                references(Set.of(), Set.of(), false));

        RepositoryEvidence evidence = collector.collect(
                createContextRequest(createStory(), createScope())).getFirst();

        assertEquals(synchronizedAt, evidence.occurredAt());
    }

    @Test
    void storyEvidenceFallsBackToEpochWithoutSynchronizationTimestamp() {
        prepareWorkspace(null);
        stubDocuments(Map.of(STORY_PATH, "story"), Set.of(),
                references(Set.of(), Set.of(), false));

        RepositoryEvidence evidence = collector.collect(
                createContextRequest(createStory(), createScope())).getFirst();

        assertEquals(Instant.EPOCH, evidence.occurredAt());
    }

    private void prepareWorkspace(Instant synchronizedAt) {
        Source source = Source.builder()
                .id(SOURCE_ID)
                .lastSynchronizedAt(synchronizedAt)
                .build();
        workspace = new SynchronizedWorkspace(SOURCE_ID, Path.of("/workspace"), REVISION);
        when(sourceRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(workspaceManager.synchronize(source, REVISION)).thenReturn(workspace);
    }

    private void stubDocuments(
            Map<String, String> documents,
            Set<String> skippedPaths,
            DocumentReferenceExtractor.ExtractedReferences references
    ) {
        stubDocuments(documents, skippedPaths, Set.of(), references);
    }

    private void stubDocuments(
            Map<String, String> documents,
            Set<String> skippedPaths,
            Set<String> unavailablePaths,
            DocumentReferenceExtractor.ExtractedReferences references
    ) {
        when(referenceExtractor.extract(anyString())).thenReturn(references);
        when(contentReader.readComplete(eq(workspace), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(1);
                    int maximum = invocation.getArgument(2);
                    if (skippedPaths.contains(path)) {
                        return new SecureRepositoryContentReader.ReadResult(
                                SecureRepositoryContentReader.ReadResult.Status.SKIPPED,
                                null, "INPUT_TOO_LARGE");
                    }
                    if (unavailablePaths.contains(path)) {
                        return new SecureRepositoryContentReader.ReadResult(
                                SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                                null, "FILE_UNAVAILABLE");
                    }
                    String text = documents.get(path);
                    if (text == null) {
                        return new SecureRepositoryContentReader.ReadResult(
                                SecureRepositoryContentReader.ReadResult.Status.UNAVAILABLE,
                                "", "NOT_FOUND");
                    }
                    boolean skipped = text.length() > maximum;
                    return new SecureRepositoryContentReader.ReadResult(
                            skipped
                                    ? SecureRepositoryContentReader.ReadResult.Status.SKIPPED
                                    : SecureRepositoryContentReader.ReadResult.Status.COMPLETE,
                            skipped ? null : text,
                            skipped ? "INPUT_TOO_LARGE" : null);
                });
    }

    private DocumentReferenceExtractor.ExtractedReferences references(
            Set<String> adrNumbers,
            Set<String> storyNumbers,
            boolean roadmap
    ) {
        return new DocumentReferenceExtractor.ExtractedReferences(
                adrNumbers, storyNumbers, Set.of(), roadmap);
    }

    private String acceptedAdr(int number) {
        return "# ADR-%04d\n\n## Status\n**ACCEPTED**".formatted(number);
    }

    private String adrPath(int number) {
        return "docs/decisions/ADR-%04d.md".formatted(number);
    }

    private List<String> paths(List<RepositoryEvidence> evidence) {
        return evidence.stream().map(item -> item.provenance().originatingFile()).toList();
    }

    private List<Integer> contentLengths(List<RepositoryEvidence> evidence) {
        return evidence.stream().map(item -> item.content().text().length()).toList();
    }

    private int totalContentCharacters(List<RepositoryEvidence> evidence) {
        return evidence.stream().mapToInt(item -> item.content().text().length()).sum();
    }

    private EngineeringStorySnapshot createStory() {
        return new EngineeringStorySnapshot(
                UUID.randomUUID(), PROJECT_ID, 119,
                "Consistent scoped evidence", "IN_PROGRESS", STORY_PATH,
                null, null, Instant.EPOCH, null);
    }

    private RepositoryRevisionScope createScope() {
        return new RepositoryRevisionScope(
                PROJECT_ID, SOURCE_ID, REVISION,
                Path.of("/workspace"), "STORY_TARGET_COMMIT");
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

    private ContextRequest createContextRequest(
            EngineeringStorySnapshot story,
            RepositoryRevisionScope scope
    ) {
        return new ContextRequest(
                createAnalysisContext(List.of(story)), createIntent(), null, List.of(),
                new ContextPlan("v1", List.of(), Map.of(), List.of(), 0, List.of()),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000), scope);
    }

    private IntentDefinition createIntent() {
        return new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze",
                List.of(), List.of(), Map.of(), "prompt-v1");
    }
}
