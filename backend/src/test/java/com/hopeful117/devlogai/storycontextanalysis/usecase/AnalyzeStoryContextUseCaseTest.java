package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.engine.dto.AiInteractionTraceRequest;
import com.hopeful117.devlogai.ai.engine.dto.PromptExecutionMetadata;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.reference.AiReferenceRegistry;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextRequestEcho;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.CanonicalContextDigest;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeStoryContextUseCaseTest {

    private static final String PROJECT_SLUG = "devlog-ai";
    private static final String INTENT_ID = "engineering-story-context-analysis";
    private static final String CONTEXT_DIGEST = "a".repeat(64);
    private static final String PROJECTION_DIGEST = "b".repeat(64);
    private static final String CANONICAL_REFERENCE = "src/main/java/Canonical.java";
    private static final String DOCUMENT_REFERENCE =
            "document:00000000-0000-0000-0000-000000000001:docs/stories/0119/story.md@abc123def";
    private static final String PROVENANCE_IDENTIFIER = "fact:historical-provenance";
    private static final String RELATED_REFERENCE = "src/main/java/Related.java";
    private static final String PROJECT_REVISION = "repository-revision";
    private static final Map<String, Object> CALLBACK_FRESHNESS = Map.of(
            "sourceRevision", Map.of("kind", "PROJECT_REVISION", "project", PROJECT_SLUG,
                    "revision", PROJECT_REVISION),
            "state", "STALE");

    @Mock private ProjectRepository projectRepository;
    @Mock private EngineeringStoryRepository storyRepository;
    @Mock private EngineeringContextFacade engineeringContextFacade;
    @Mock private IntentCatalog intentCatalog;
    @Mock private AiTaskService aiTaskService;
    @Mock private AIEngineClient aiEngineClient;
    @Mock private AiTaskRepository aiTaskRepository;
    @Mock private StoryContextAnalysisRepository storyContextAnalysisRepository;
    @Mock private AnalysisRepository analysisRepository;

    private AnalyzeStoryContextUseCase useCase;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        useCase = new AnalyzeStoryContextUseCase(
                projectRepository,
                storyRepository,
                new StoryContextPreparationService(
                        projectRepository,
                        storyRepository,
                        engineeringContextFacade,
                        objectMapper),
                intentCatalog,
                aiTaskService,
                aiEngineClient,
                aiTaskRepository,
                objectMapper,
                new StoryContextDigestService(objectMapper),
                new StoryContextCallbackIdentityValidator(),
                new StoryContextCallbackService(
                        aiTaskRepository,
                        storyRepository,
                        storyContextAnalysisRepository,
                        objectMapper,
                        new StoryContextCallbackIdentityValidator()),
                new StoryContextSubmissionService(
                        intentCatalog,
                        aiTaskService,
                        aiEngineClient,
                        aiTaskRepository,
                        analysisRepository,
                        objectMapper,
                        new StoryContextDigestService(objectMapper)),
                analysisRepository
        );
    }

    @Test
    void executeUsesCanonicalFacadeWithoutBroadeningGrounding() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);
        EngineeringContext engineeringContext = engineeringContext();
        CanonicalEngineeringContext canonicalContext = new CanonicalEngineeringContext(
                engineeringContext, repositoryContext(), CONTEXT_DIGEST, "engineering-context-v2",
                new ContextRequestEcho(PROJECT_SLUG, INTENT_ID,
                        List.of(CANONICAL_REFERENCE), storyId),
                Map.of("status", "STALE", "repositoryRevision", "repository-revision"),
                Map.of("candidateCount", 1, "selectedCount", 1, "discardedCount", 0,
                        "usedTokens", 100, "budget", 10_000), Map.of(DOCUMENT_REFERENCE, "TECHNICAL_EVIDENCE"),
                List.of(new EvidenceRef(DOCUMENT_REFERENCE, "document://story-0119")));
        IntentDefinition intent = intent();
        AiTask task = AiTask.builder()
                .id(UUID.randomUUID())
                .correlationId(UUID.randomUUID())
                .status(AiTaskStatus.CREATED)
                .contextSnapshot(Map.of("storyId", storyId.toString()))
                .build();

        when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(intentCatalog.resolve(INTENT_ID, "v1")).thenReturn(intent);
        when(engineeringContextFacade.getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of("src/main/java/Canonical.java"), storyId))
                .thenReturn(canonicalContext);
        when(analysisRepository.save(any(Analysis.class)))
                .thenAnswer(invocation -> {
                    Analysis saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    return saved;
                });
        when(aiTaskService.createForStoryContextAnalysisEntity(
                any(UUID.class), eq(AiTaskType.STORY_CONTEXT_ANALYSIS), eq(INTENT_ID), eq("v1"),
                eq("story-context-analysis-prompt-v1"), any(), any(String.class),
                 any(), eq(null), any(AiReferenceRegistry.class)))
                .thenReturn(task);

        UUID result = useCase.execute(
                PROJECT_SLUG, storyId, List.of("src/main/java/Canonical.java"), null);

        assertEquals(task.getId(), result);
        verify(engineeringContextFacade).getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of("src/main/java/Canonical.java"), storyId);
        verify(engineeringContextFacade, never()).getEngineeringContext(any(), any(), any(), any());
        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(aiEngineClient).submit(promptCaptor.capture());
        PromptRequest request = promptCaptor.getValue();
        assertEquals("engineering-context-v2",
                ((Map<?, ?>) request.selectedKnowledge().get("policy")).get("compositionVersion"));
        assertTrue(request.selectedKnowledge().keySet().containsAll(List.of(
                "contractVersion", "projectionVersion", "contextDigest", "request", "freshness",
                "context", "groundingCandidates", "accounting", "policy")));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UUID> executionAnalysisId = ArgumentCaptor.forClass(UUID.class);
        verify(aiTaskService).createForStoryContextAnalysisEntity(
                executionAnalysisId.capture(), eq(AiTaskType.STORY_CONTEXT_ANALYSIS), eq(INTENT_ID), eq("v1"),
                eq("story-context-analysis-prompt-v1"), any(), any(String.class),
                any(), eq(null), any(AiReferenceRegistry.class));
        assertFalse(request.groundingContract().containsKey("allowedEvidenceReferences"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typedGrounding =
                (List<Map<String, Object>>) request.groundingContract().get("allowedGroundingReferences");
        assertEquals("REPOSITORY_EVIDENCE", typedGrounding.get(0).get("type"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("ref"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("coreReference"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("taskReference"));
        assertEquals("PROJECT_REVISION", typedGrounding.get(0).get("scope"));
        assertEquals(PROJECT_SLUG, typedGrounding.get(0).get("project"));
        assertEquals("repository-revision", typedGrounding.get(0).get("revision"));
        assertEquals(64, request.contextDigest().length());
        assertNotNull(request.projectionDigest());
        assertEquals(request.projectionDigest(), request.metadata().get("projectionDigest"));
        assertEquals(StoryContextAgentProjectionV1.CONTRACT_VERSION,
                request.metadata().get("contractVersion"));
        assertEquals(StoryContextAgentProjectionV1.PROJECTION_VERSION,
                request.metadata().get("projectionVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> persistedProjection =
                (Map<String, Object>) task.getContextSnapshot().get("projection");
        assertEquals(StoryContextAgentProjectionV1.PROJECTION_VERSION, persistedProjection.get("projectionVersion"));
        assertEquals(request.selectedKnowledge(), persistedProjection);
        assertEquals(StoryContextAgentProjectionV1.CONTRACT_VERSION, persistedProjection.get("contractVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> projectionGrounding = (Map<String, Object>) persistedProjection.get("groundingCandidates");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projectionReferences =
                (List<Map<String, Object>>) projectionGrounding.get("repositoryEvidence");
        assertFalse(projectionReferences.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> snapshotEvidence =
                (List<Map<String, Object>>) ((Map<String, Object>) persistedProjection.get("context"))
                        .get("repositoryEvidence");
        assertEquals(1, snapshotEvidence.size());
        assertEquals(DOCUMENT_REFERENCE, snapshotEvidence.get(0).get("reference"));
        assertEquals(Map.of("sourceType", "REPOSITORY", "repositoryLocation", "devlog://evidence/canonical",
                        "originatingFile", "docs/stories/0119/story.md", "identifier", PROVENANCE_IDENTIFIER),
                snapshotEvidence.get(0).get("provenance"));
        assertEquals(PROJECT_REVISION,
                ((Map<String, Object>) snapshotEvidence.get(0).get("content")).get("revision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> projectionReference =
                (Map<String, Object>) projectionReferences.get(0).get("reference");
        assertEquals(Set.of("type", "ref", "scope"), projectionReference.keySet());
        assertEquals(request.selectedKnowledge().get("requestEcho"), persistedProjection.get("requestEcho"));
        assertEquals(request.selectedKnowledge().get("requestEcho"), task.getContextSnapshot().get("requestEcho"));
        assertEquals(request.groundingContract(), task.getContextSnapshot().get("groundingContract"));
        assertEquals(request.projectionDigest(), projectionDigest(persistedProjection));
        assertEquals(request.projectionDigest(), task.getProjectionDigest());
        assertEquals(null, task.getSelectionDigest());
        assertEquals(null, task.getSelectionVersion());
        assertEquals(request.contextDigest(), task.getContextDigest());
        assertEquals(request.projectionDigest(), task.getContextSnapshot().get("projectionDigest"));
        assertTrue(task.getContextSnapshot().containsKey("groundingContract"));

        @SuppressWarnings("unchecked")
        Map<String, Object> freshness = (Map<String, Object>) task.getContextSnapshot()
                .get("contextFreshness");
        assertEquals("STALE", freshness.get("state"));
        assertEquals(Map.of("kind", "PROJECT_REVISION", "project", PROJECT_SLUG, "revision", PROJECT_REVISION), freshness.get("sourceRevision"));
        assertEquals(request.contextDigest(), task.getContextSnapshot().get("contextDigest"));
        assertFalse(task.getContextSnapshot().containsKey("selectionDigest"));
        assertEquals(task.getProjectionDigest(), task.getContextSnapshot().get("projectionDigest"));
        assertEquals("devlog-ai", ((Map<?, ?>) task.getContextSnapshot().get("scope")).get("projectSlug"));
        assertTrue(task.getContextSnapshot().containsKey("requestEcho"));
        assertTrue(task.getContextSnapshot().containsKey("revisions"));
        assertTrue(task.getContextSnapshot().containsKey("policy"));
        assertTrue(task.getContextSnapshot().containsKey("budgets"));
        assertTrue(task.getContextSnapshot().containsKey("accounting"));
        assertTrue(task.getContextSnapshot().containsKey("truncation"));
        assertTrue(task.getContextSnapshot().containsKey("warnings"));
        assertTrue(task.getContextSnapshot().containsKey("referenceMapping"));
        assertTrue(task.getContextSnapshot().containsKey("projection"));
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) task.getContextSnapshot().get("policy");
        assertEquals(StoryContextAgentProjectionV1.PROJECTION_VERSION, policy.get("contractVersion"));
        assertEquals(request.projectionDigest(), policy.get("projectionDigest"));
        verify(aiTaskService).submit(eq(task.getId()), any());
    }

    @Test
    void projectionDigestCoversCompleteV1PayloadAndIgnoresMapOrder() {
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("contractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION);
        projection.put("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION);
        projection.put("contextDigest", CONTEXT_DIGEST);
        projection.put("requestEcho", new LinkedHashMap<>(Map.of("projectSlug", PROJECT_SLUG)));
        projection.put("context", new LinkedHashMap<>(Map.of("summary", "stable")));
        projection.put("groundingCandidates", List.of(Map.of("reference", CANONICAL_REFERENCE)));
        projection.put("policy", new LinkedHashMap<>(Map.of("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION)));

        String baseline = projectionDigest(projection);
        Map<String, Object> changedContext = new LinkedHashMap<>(projection);
        changedContext.put("context", Map.of("summary", "changed"));
        assertNotEquals(baseline, projectionDigest(changedContext));

        Map<String, Object> reordered = new LinkedHashMap<>();
        projection.forEach((key, value) -> reordered.put(key, value));
        assertEquals(baseline, projectionDigest(reordered));
    }

    @Test
    void questionIsPropagatedThroughCorePreparationAndProjectionV2() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                engineeringContext(), repositoryContext(), CONTEXT_DIGEST, "engineering-context-v2",
                new ContextRequestEcho(PROJECT_SLUG, INTENT_ID, List.of(), storyId),
                Map.of("status", "STALE", "repositoryRevision", PROJECT_REVISION),
                Map.of("candidateCount", 1, "selectedCount", 1, "discardedCount", 0,
                        "usedTokens", 100, "budget", 10_000),
                Map.of(DOCUMENT_REFERENCE, "TECHNICAL_EVIDENCE"),
                List.of(new EvidenceRef(DOCUMENT_REFERENCE, "document://story-0119")));

        when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(engineeringContextFacade.getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of(), storyId, "Which component owns polling?"))
                .thenReturn(canonical);

        PreparedStoryContext prepared = new StoryContextPreparationService(
                projectRepository, storyRepository, engineeringContextFacade, new ObjectMapper())
                .prepare(PROJECT_SLUG, storyId, INTENT_ID, List.of(),
                        "Which component owns polling?");

        assertEquals("Which component owns polling?",
                ((Map<?, ?>) prepared.projection().get("request")).get("question"));
        assertEquals(prepared.projection().get("request"), prepared.projection().get("requestEcho"));
        assertEquals("story-context-agent-projection/v2", prepared.projection().get("contractVersion"));
        verify(engineeringContextFacade).getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of(), storyId, "Which component owns polling?");
    }

    private String projectionDigest(Map<String, Object> projection) {
        Map<String, Object> identity = new LinkedHashMap<>(projection);
        identity.remove("projectionDigest");
        return CanonicalContextDigest.calculate(identity);
    }

    @Test
    void executeFailsClosedWhenCanonicalContextIsUnavailable() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);
        IntentDefinition intent = intent();

        when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(intentCatalog.resolve(INTENT_ID, "v1")).thenReturn(intent);
        when(engineeringContextFacade.getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of(), storyId)).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(PROJECT_SLUG, storyId, List.of(), null));
        verify(engineeringContextFacade, never()).getEngineeringContext(any(), any(), any(), any());
        verify(aiTaskService, never()).createForStoryContextAnalysisEntity(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void executeFailsClosedWhenCanonicalFacadeReturnsNoContext() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);

        when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(intentCatalog.resolve(INTENT_ID, "v1")).thenReturn(intent());
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> useCase.execute(PROJECT_SLUG, storyId, List.of(), null));

        assertEquals("Canonical EngineeringContext is required for Story Context Analysis", error.getMessage());

        verify(engineeringContextFacade).getCanonicalEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of(), storyId);
        verify(engineeringContextFacade, never()).getEngineeringContext(any(), any(), any(), any());
        verify(aiTaskService, never()).createForStoryContextAnalysisEntity(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void differentRequestWithReusedIdempotencyKeyConflictsBeforeConstruction() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);
        AiTask existing = AiTask.builder().id(UUID.randomUUID())
                .submissionDigest("different-request")
                .build();

        when(projectRepository.findBySlug(PROJECT_SLUG)).thenReturn(Optional.of(project));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
        when(intentCatalog.resolve(INTENT_ID, "v1")).thenReturn(intent());
        when(aiTaskRepository.findByIdempotencyKeyHash(any())).thenReturn(Optional.of(existing));

        assertThrows(com.hopeful117.devlogai.shared.exception.ConflictException.class,
                () -> useCase.execute(PROJECT_SLUG, storyId, INTENT_ID, List.of(), null, "same-key"));
        verify(engineeringContextFacade, never()).getCanonicalEngineeringContext(
                any(), any(), any(), any());
        verify(analysisRepository, never()).save(any(Analysis.class));
    }

    @Test
    void handleCallbackPersistsValidGroundedAnalysisAndFreshness() {
        UUID storyId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        EngineeringStory story = story(storyId, project(UUID.randomUUID()));
        Map<String, Object> freshness = Map.of(
                "status", "STALE",
                "repositoryRevision", "repository-revision",
                "contextRevision", "context-revision"
        );
        AiTask task = callbackTask(correlationId, storyId, CANONICAL_REFERENCE, freshness);
        Instant completedAt = Instant.parse("2026-09-09T10:00:00Z");
        AiTaskResultRequest request = completedRequest(
                correlationId, completedAt, storyId, groundedResult(CANONICAL_REFERENCE));

        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

        useCase.handleCallback(correlationId, request);

        ArgumentCaptor<StoryContextAnalysis> analysisCaptor =
                ArgumentCaptor.forClass(StoryContextAnalysis.class);
        verify(storyContextAnalysisRepository).save(analysisCaptor.capture());
        StoryContextAnalysis persisted = analysisCaptor.getValue();
        assertEquals(story, persisted.getStory());
        assertEquals(task, persisted.getAiTask());
        assertEquals(CONTEXT_DIGEST, persisted.getContextDigest());
        assertEquals(freshness, persisted.getContextFreshness());
        assertEquals(AiTaskStatus.COMPLETED, task.getStatus());
        assertEquals(completedAt, task.getCompletedAt());
        verify(aiTaskRepository).save(task);
    }

    @Test
    void handleCallbackRejectsUnauthorizedReferenceWithoutSuccessfulPersistence() {
        UUID storyId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AiTask task = callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of());
        AiTaskResultRequest request = completedRequest(
                correlationId,
                Instant.parse("2026-09-09T10:00:00Z"),
                storyId,
                groundedResult("src/main/java/Fabricated.java")
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> useCase.handleCallback(correlationId, request)
        );

        assertTrue(error.getMessage().contains("unauthorized evidence"));
        assertEquals(AiTaskStatus.SUBMITTED, task.getStatus());
        verify(storyContextAnalysisRepository, never()).save(any());
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void handleCallbackRejectsMalformedInteractionTraceBeforeLookupOrMutation() {
        UUID correlationId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        AiTask task = callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of());
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.parse("2026-09-09T10:00:00Z"), List.of(), null,
                callbackPromptExecution(storyId), null, groundedResult(CANONICAL_REFERENCE),
                java.util.Collections.singletonList(null));

        assertThrows(com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException.class,
                () -> useCase.handleCallback(correlationId, request));

        assertEquals(AiTaskStatus.SUBMITTED, task.getStatus());
        verify(aiTaskRepository, never()).findByCorrelationIdForUpdate(any());
        verify(storyContextAnalysisRepository, never()).save(any());
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void relationshipBearingFindingsRequireRelationType() {
        List<StoryContextAnalysisResult> results = List.of(
                resultWithSections(List.of(new StoryContextAnalysisResult.ArchitectureFinding(
                                "Architecture", "Description", groundingWithoutRelation())),
                        List.of(), List.of(), List.of(), List.of(), List.of()),
                resultWithSections(List.of(), List.of(new StoryContextAnalysisResult.DecisionFinding(
                                "Decision", "Context", "Choice", "Rationale", groundingWithoutRelation())),
                        List.of(), List.of(), List.of(), List.of()),
                resultWithSections(List.of(), List.of(), List.of(new StoryContextAnalysisResult.HistoricalContextItem(
                                "History", "Description", "2026", List.of(), groundingWithoutRelation())),
                        List.of(), List.of(), List.of()),
                resultWithSections(List.of(), List.of(), List.of(), List.of(new StoryContextAnalysisResult.ImpactedComponentFinding(
                                "Component", "Impact", "MODIFIED", groundingWithoutRelation())),
                        List.of(), List.of())
        );

        for (StoryContextAnalysisResult result : results) {
            UUID correlationId = UUID.randomUUID();
            UUID storyId = UUID.randomUUID();
            when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                    .thenReturn(Optional.of(callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of())));

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> useCase.handleCallback(correlationId, completedRequest(
                            correlationId, Instant.parse("2026-09-09T10:00:00Z"), storyId, result))
            );

            assertTrue(error.getMessage().contains("relationType"), error.getMessage());
        }
    }

    @Test
    void nonRelationshipFindingsMayOmitRelationType() {
        Project project = project(UUID.randomUUID());
        EngineeringStory story = story(UUID.randomUUID(), project);
        when(storyRepository.findById(any())).thenReturn(Optional.of(story));

        List<StoryContextAnalysisResult> results = List.of(
                resultWithSections(List.of(), List.of(), List.of(), List.of(),
                        List.of(new StoryContextAnalysisResult.EvidenceFinding(
                                "Evidence", "Description", "SOURCE", groundingWithoutRelation())), List.of()),
                resultWithSections(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(new StoryContextAnalysisResult.ConstraintFinding(
                                "Constraint", "Description", "POLICY", groundingWithoutRelation())))
        );

        for (StoryContextAnalysisResult result : results) {
            UUID correlationId = UUID.randomUUID();
            UUID storyId = UUID.randomUUID();
            when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                    .thenReturn(Optional.of(callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of())));

            useCase.handleCallback(correlationId, completedRequest(
                    correlationId, Instant.parse("2026-09-09T10:00:00Z"), storyId, result));
        }

        verify(storyContextAnalysisRepository, times(2)).save(any());
    }

    @Test
    void handleCallbackRejectsUnauthorizedCausalClaimReference() {
        UUID storyId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AiTask task = callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of());
        StoryContextAnalysisResult.CausalClaim claim = new StoryContextAnalysisResult.CausalClaim(
                "ADR-043", "ExecutionConfiguration",
                StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED,
                StoryContextAnalysisResult.CausalEvidenceBasis.DIRECT_DOCUMENTATION,
                List.of(new EvidenceRef("not-authorized", "devlog://evidence/not-authorized",
                        EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT)),
                "The cited evidence directly states the relationship.");
        AiTaskResultRequest request = completedRequest(
                correlationId,
                Instant.parse("2026-09-09T10:00:00Z"),
                storyId,
                resultWithCausalClaims(claim));
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId)).thenReturn(Optional.of(task));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> useCase.handleCallback(correlationId, request));

        assertTrue(error.getMessage().contains("Causal claim references unauthorized evidence"));
        verify(storyContextAnalysisRepository, never()).save(any());
    }

    @Test
    void causalClaimRejectsAffirmativeBasisForNotEstablished() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoryContextAnalysisResult.CausalClaim(
                        "ADR-043", "ExecutionConfiguration",
                        StoryContextAnalysisResult.CausalClassification.NOT_ESTABLISHED,
                        StoryContextAnalysisResult.CausalEvidenceBasis.DIRECT_DOCUMENTATION,
                List.of(), "Chronology does not establish causality."));
    }

    @Test
    void causalClaimRejectsStrongSupportWithOneEvidenceReference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoryContextAnalysisResult.CausalClaim(
                        "Story-0039", "Account",
                        StoryContextAnalysisResult.CausalClassification.STRONGLY_SUPPORTED,
                        StoryContextAnalysisResult.CausalEvidenceBasis.MATERIAL_CORROBORATION,
                        List.of(new EvidenceRef(CANONICAL_REFERENCE, "devlog://evidence/canonical",
                                EvidenceRef.CausalEvidenceRole.MATERIAL_RELATIONSHIP_SUPPORT)),
                        "One item is insufficient for strong support."));
    }

    private Project project(UUID projectId) {
        return Project.builder()
                .id(projectId)
                .name("DevLog AI")
                .slug(PROJECT_SLUG)
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private EngineeringStory story(UUID storyId, Project project) {
        return EngineeringStory.builder()
                .id(storyId)
                .project(project)
                .storyNumber(116)
                .title("Wire validated knowledge into SCA")
                .status(StoryStatus.COMPLETED)
                .storyPath("docs/stories/0116-wire-validated-knowledge-into-sca/story.md")
                .createdAt(Instant.parse("2026-09-08T10:00:00Z"))
                .build();
    }

    @Test
    void restGuidanceUsesSharedFieldsWhenBuildingPythonPromptContract() {
        var guidance = Map.<String, Object>of(
                "focus", "repository relationships",
                "audience", "maintainers",
                "levelOfDetail", "deep",
                "writingStyle", "concise",
                "outputContext", "implementation review",
                "priorities", List.of("trust", "digests"));

        var mapped = com.hopeful117.devlogai.intent.model.UserGuidance.from(guidance);

        assertEquals("repository relationships", mapped.focus());
        assertEquals("maintainers", mapped.audience());
        assertEquals("deep", mapped.levelOfDetail());
        assertEquals("concise", mapped.writingStyle());
        assertEquals("implementation review", mapped.outputContext());
        assertEquals(List.of("trust", "digests"), mapped.priorities());
    }

    @Test
    void causalClaimRejectsAffirmativeClassificationWithNonCausalContext() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StoryContextAnalysisResult.CausalClaim(
                        "ADR-043", "ExecutionConfiguration",
                        StoryContextAnalysisResult.CausalClassification.EXPLICITLY_DOCUMENTED,
                        StoryContextAnalysisResult.CausalEvidenceBasis.DIRECT_DOCUMENTATION,
                        List.of(
                                new EvidenceRef("direct", "devlog://evidence/direct",
                                        EvidenceRef.CausalEvidenceRole.DIRECT_RELATIONSHIP_STATEMENT),
                                new EvidenceRef("chronology", "devlog://evidence/chronology",
                                        EvidenceRef.CausalEvidenceRole.NON_CAUSAL_CONTEXT)),
                        "The evidence does not establish the relationship."));
    }

    @Test
    void handleCallbackRejectsEmptyCausalClaimsWhenCausalAnswerIsRequired() {
        UUID storyId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AiTask task = callbackTask(correlationId, storyId, CANONICAL_REFERENCE, Map.of(), true);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId)).thenReturn(Optional.of(task));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> useCase.handleCallback(
                        correlationId,
                        completedRequest(correlationId, Instant.parse("2026-09-09T10:00:00Z"), storyId, groundedResult(CANONICAL_REFERENCE))));

        assertTrue(error.getMessage().contains("causalClaims must not be empty"));
        verify(storyContextAnalysisRepository, never()).save(any());
    }

    @Test
    void canonicalRevisionRejectsUnresolvableSentinelsFailClosed() {
        for (String revision : List.of("UNKNOWN", "UNSPECIFIED")) {
            CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                    engineeringContext(), repositoryContext(), CONTEXT_DIGEST, "engineering-context-v2",
                    new ContextRequestEcho(PROJECT_SLUG, INTENT_ID, List.of(CANONICAL_REFERENCE), null),
                    Map.of("sourceRevision", Map.of("kind", "PROJECT_REVISION",
                            "project", PROJECT_SLUG, "revision", revision)),
                    Map.of("candidateCount", 1, "selectedCount", 1, "discardedCount", 0,
                            "usedTokens", 1, "budget", 10), Map.of(), List.of());
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> StoryContextAgentProjectionV1.canonicalRevision(canonical));
            assertTrue(error.getMessage().contains("not resolvable"));
        }
    }

    @Test
    void canonicalRevisionAcceptsRepositoryRevisionFromCanonicalFreshnessSummary() {
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                engineeringContext(), repositoryContext(), CONTEXT_DIGEST, "engineering-context-v2",
                new ContextRequestEcho(PROJECT_SLUG, INTENT_ID, List.of(CANONICAL_REFERENCE), null),
                Map.of("summary", new ProjectFreshnessSummary(
                        ProjectFreshnessSummary.PROJECTION_VERSION, UUID.randomUUID(), List.of(
                                new ProjectFreshnessResponse("v1", UUID.randomUUID(), UUID.randomUUID(),
                                        new ProjectFreshnessResponse.Source(UUID.randomUUID(), "GitHub", "main",
                                                "origin/main", PROJECT_REVISION, PROJECT_REVISION),
                                        null, null, null, null, null)), 0, false)),
                Map.of("candidateCount", 1, "selectedCount", 1, "discardedCount", 0,
                        "usedTokens", 1, "budget", 10), Map.of(), List.of());

        assertEquals(PROJECT_REVISION, StoryContextAgentProjectionV1.canonicalRevision(canonical));
    }

    @Test
    void canonicalRevisionUsesEvidenceSnapshotWhenFreshnessCheckpointIsOlder() {
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                engineeringContext(), repositoryContext(), CONTEXT_DIGEST, "engineering-context-v2",
                new ContextRequestEcho(PROJECT_SLUG, INTENT_ID, List.of(CANONICAL_REFERENCE), null),
                Map.of("sourceRevision", Map.of("kind", "PROJECT_REVISION",
                        "project", PROJECT_SLUG, "revision", "newer-live-revision")),
                Map.of("candidateCount", 1, "selectedCount", 1, "discardedCount", 0,
                        "usedTokens", 1, "budget", 10), Map.of(), List.of());

        assertEquals(PROJECT_REVISION, StoryContextAgentProjectionV1.canonicalRevision(canonical));
    }

    @Test
    void canonicalRevisionRejectsMixedEvidenceRevisions() {
        RepositoryContext base = repositoryContext();
        RepositoryEvidence first = base.evidence().getFirst();
        RepositoryEvidence second = first.withContent(new RepositoryEvidenceContent(
                RepositoryEvidenceContent.Status.COMPLETE, "Other revision", null, null, null,
                "other-revision"));
        RepositoryContext mixed = new RepositoryContext(
                base.contextVersion(), base.profile(), base.activeProfileKeys(), base.contextPlanVersion(),
                base.contextIntelligenceExplanations(), List.of(first, second), base.selectedByLayer(),
                base.diagnostics(), base.budget(), base.usedTokens(), 2, base.discardedCount(),
                base.truncated(), base.selectionDecisions(), base.warnings(), base.contextDigest());
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                engineeringContext(), mixed, CONTEXT_DIGEST, "engineering-context-v2",
                new ContextRequestEcho(PROJECT_SLUG, INTENT_ID, List.of(CANONICAL_REFERENCE), null),
                Map.of("sourceRevision", Map.of("kind", "PROJECT_REVISION",
                        "project", PROJECT_SLUG, "revision", PROJECT_REVISION)),
                Map.of("candidateCount", 2, "selectedCount", 2, "discardedCount", 0,
                        "usedTokens", 2, "budget", 10), Map.of(), List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> StoryContextAgentProjectionV1.canonicalRevision(canonical));

        assertTrue(error.getMessage().contains("mixed project revisions"));
    }

    private EngineeringContext engineeringContext() {
        EngineeringEvidence evidence = new EngineeringEvidence(
                "SOURCE_FILE", "CODE", "Canonical evidence", "REPOSITORY",
                "src/main/java/Canonical.java", PROVENANCE_IDENTIFIER, DOCUMENT_REFERENCE,
                100, "Story relevance", Instant.parse("2026-09-08T10:00:00Z"),
                List.of(RELATED_REFERENCE), Map.of(), null, null,
                "devlog://evidence/canonical", TrustTier.TECHNICAL_EVIDENCE);
        EngineeringContextFreshness freshness = new EngineeringContextFreshness(
                "STALE", "repository-revision", "context-revision", List.of());
        return new EngineeringContext(
                null, INTENT_ID, List.of(evidence),
                new EngineeringContextMetadata(
                        1, 1, false, 1, CONTEXT_DIGEST, List.of(), freshness),
                List.of(), null);
    }

    private RepositoryContext repositoryContext() {
        RepositoryEvidence evidence = new RepositoryEvidence(
                RepositoryContextLayer.PROJECT_DOCUMENTATION, "DOCUMENT", DOCUMENT_REFERENCE,
                "Canonical evidence", Instant.parse("2026-09-08T10:00:00Z"), EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY", "devlog://evidence/canonical", "docs/stories/0119/story.md",
                        PROVENANCE_IDENTIFIER), Map.of(), 100, List.of(),
                new RepositoryEvidenceContent(RepositoryEvidenceContent.Status.COMPLETE,
                        "Canonical evidence", null, null, null, "repository-revision"));
        return new RepositoryContext(
                "engineering-context-v2", ContextProfile.ENGINEERING_STORY, List.of(), "v1", List.of(),
                List.of(evidence), Map.of(), RepositoryContextDiagnostics.empty(), new RepositoryContext.ContextBudget(50, 200, 10, 10000),
                100, 1, 0, false, List.of(), List.of(), "repository-context-digest");
    }

    private IntentDefinition intent() {
        return new IntentDefinition(
                INTENT_ID, "v1", "Analyze Story context", List.of(), List.of(),
                Map.of("type", "object"), "story-context-analysis-prompt-v1");
    }

    private AiTask callbackTask(
            UUID correlationId,
            UUID storyId,
            String allowedReference,
            Map<String, Object> freshness
    ) {
        return callbackTask(correlationId, storyId, allowedReference, freshness, false);
    }

    private AiTask callbackTask(
            UUID correlationId,
            UUID storyId,
            String allowedReference,
            Map<String, Object> freshness,
            boolean causalAnswerRequired
    ) {
        Map<String, Object> groundingContract = new LinkedHashMap<>();
        Map<String, Object> evidenceProvenance = Map.of(
                "source", "REPOSITORY",
                "locator", "devlog://evidence/canonical",
                "path", "docs/stories/0119/story.md",
                "identifier", PROVENANCE_IDENTIFIER);
        Map<String, Object> typedReference = new LinkedHashMap<>();
        typedReference.put("type", "REPOSITORY_EVIDENCE");
        typedReference.put("ref", allowedReference);
        typedReference.put("coreReference", allowedReference);
        typedReference.put("taskReference", allowedReference);
        typedReference.put("scope", "PROJECT_REVISION");
        typedReference.put("project", PROJECT_SLUG);
        typedReference.put("revision", PROJECT_REVISION);
        typedReference.put("provenance", evidenceProvenance);
        typedReference.put("trust", "TECHNICAL_EVIDENCE");
        groundingContract.put("allowedGroundingReferences", List.of(typedReference));
        groundingContract.put("groundingContractVersion", "story-context-grounding/v1");
        groundingContract.put("causalAnswerRequired", causalAnswerRequired);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("storyId", storyId.toString());
        snapshot.put("scope", Map.of(
                "projectSlug", PROJECT_SLUG,
                "storyId", storyId.toString(),
                "intent", INTENT_ID,
                "files", List.of(CANONICAL_REFERENCE)));
        snapshot.put("groundingContract", groundingContract);
        snapshot.put("contextFreshness", freshness);
        snapshot.put("projectionVersion", "sca/v1");
        snapshot.put("freshness", CALLBACK_FRESHNESS);
        snapshot.put("groundingDigest", "d".repeat(64));
        snapshot.put("contextDigest", CONTEXT_DIGEST);
        snapshot.put("selectionDigest", null);
        snapshot.put("projectionDigest", PROJECTION_DIGEST);
        snapshot.put("projection", Map.ofEntries(
                Map.entry("contractVersion", StoryContextAgentProjectionV1.CONTRACT_VERSION),
                Map.entry("projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION),
                Map.entry("contextDigest", CONTEXT_DIGEST),
                Map.entry("request", snapshot.get("scope")),
                Map.entry("requestEcho", snapshot.get("scope")),
                Map.entry("scope", snapshot.get("scope")),
                Map.entry("freshness", CALLBACK_FRESHNESS),
                Map.entry("context", Map.of("project", Map.of(), "sections", List.of(),
                        "repositoryEvidence", List.of(), "relations", Map.of())),
                Map.entry("groundingCandidates", Map.of("repositoryEvidence", List.of())),
                Map.entry("accounting", Map.of("candidateCount", 1, "selectedCount", 1,
                        "discardedCount", 0, "usedTokens", 100, "budget", 10_000,
                        "truncated", false, "warnings", List.of())),
                Map.entry("policy", Map.of("compositionVersion", "engineering-context-v2",
                        "projectionVersion", StoryContextAgentProjectionV1.PROJECTION_VERSION)),
                Map.entry("projectionDigest", PROJECTION_DIGEST)));
        Map<String, Object> selectedSnapshot = new LinkedHashMap<>();
        selectedSnapshot.put("request", snapshot.get("scope"));
        selectedSnapshot.put("freshness", Map.of(
                "sourceRevision", Map.of(
                        "kind", "PROJECT_REVISION",
                        "project", PROJECT_SLUG,
                        "revision", PROJECT_REVISION),
                "state", "STALE"));
        selectedSnapshot.put("repositoryContext", Map.of("evidence", List.of(Map.of(
                "reference", allowedReference,
                "provenance", evidenceProvenance,
                "content", Map.of("status", "COMPLETE", "text", "Canonical evidence", "revision", PROJECT_REVISION)))));
        return AiTask.builder()
                .id(UUID.randomUUID())
                .correlationId(correlationId)
                .taskType(AiTaskType.STORY_CONTEXT_ANALYSIS)
                .intentId(INTENT_ID)
                .intentVersion("v1")
                .status(AiTaskStatus.SUBMITTED)
                .contextDigest(CONTEXT_DIGEST)
                .projectionDigest(PROJECTION_DIGEST)
                .selectionDigest(null)
                .selectedKnowledgeSnapshot(selectedSnapshot)
                .contextSnapshot(snapshot)
                .build();
    }

    private AiTaskResultRequest completedRequest(
            UUID correlationId,
            Instant completedAt,
            UUID storyId,
            StoryContextAnalysisResult result
    ) {
        PromptExecutionMetadata execution = new PromptExecutionMetadata(
                "story-context-analysis-prompt-v1", "mock", "deterministic-v1",
                "c".repeat(64), CONTEXT_DIGEST, null, PROJECTION_DIGEST,
                "sca/v1",
                Map.of("projectSlug", PROJECT_SLUG, "storyId", storyId.toString(),
                        "intent", INTENT_ID, "files", List.of(CANONICAL_REFERENCE)),
                CALLBACK_FRESHNESS,
                "d".repeat(64));
        return new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED, completedAt,
                List.of(), null, execution, null, result);
    }

    private PromptExecutionMetadata callbackPromptExecution(UUID storyId) {
        return new PromptExecutionMetadata(
                "story-context-analysis-prompt-v1", "mock", "deterministic-v1",
                "c".repeat(64), CONTEXT_DIGEST, null, PROJECTION_DIGEST,
                "sca/v1",
                Map.of("projectSlug", PROJECT_SLUG, "storyId", storyId.toString(),
                        "intent", INTENT_ID, "files", List.of(CANONICAL_REFERENCE)),
                CALLBACK_FRESHNESS,
                "d".repeat(64));
    }

    private StoryContextAnalysisResult groundedResult(String reference) {
        StoryContextAnalysisResult.GroundingMetadata grounding =
                new StoryContextAnalysisResult.GroundingMetadata(
                        List.of(new EvidenceRef(reference, "devlog://evidence/canonical")),
                        "FACTUAL_EXTRACTION", true,
                        StoryContextAnalysisResult.RelationType.EXPLICIT);
        StoryContextAnalysisResult.ArchitectureFinding finding =
                new StoryContextAnalysisResult.ArchitectureFinding(
                        "Core authority", "Java owns grounding authority", grounding);
        StoryContextAnalysisResult.Provenance provenance =
                new StoryContextAnalysisResult.Provenance(
                        CONTEXT_DIGEST, "story-context-analysis-prompt-v1", "mock",
                        "deterministic-v1", "b".repeat(64), INTENT_ID, "v1",
                        List.of(), Map.of());
        StoryContextAnalysisResult.OutputClassification classification =
                new StoryContextAnalysisResult.OutputClassification(List.of(
                        new StoryContextAnalysisResult.OutputClassification.ClassificationEntry(
                                "architectureFindings[0]",
                                StoryContextAnalysisResult.OutputClassification.Classification.FACTUAL_EXTRACTION,
                                true,
                                "Directly grounded"
                        )
                ));
        return new StoryContextAnalysisResult(
                new StoryContextAnalysisResult.ObjectiveUnderstanding("Understand the Story"),
                List.of(finding), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), StoryContextAnalysisResult.Confidence.HIGH,
                provenance, classification);
    }

    private StoryContextAnalysisResult.GroundingMetadata groundingWithoutRelation() {
        return new StoryContextAnalysisResult.GroundingMetadata(
                List.of(new EvidenceRef(CANONICAL_REFERENCE, "devlog://evidence/canonical")),
                "FACTUAL_EXTRACTION", true, null);
    }

    private StoryContextAnalysisResult resultWithSections(
            List<StoryContextAnalysisResult.ArchitectureFinding> architecture,
            List<StoryContextAnalysisResult.DecisionFinding> decisions,
            List<StoryContextAnalysisResult.HistoricalContextItem> history,
            List<StoryContextAnalysisResult.ImpactedComponentFinding> impacted,
            List<StoryContextAnalysisResult.EvidenceFinding> evidence,
            List<StoryContextAnalysisResult.ConstraintFinding> constraints
    ) {
        StoryContextAnalysisResult base = groundedResult(CANONICAL_REFERENCE);
        return new StoryContextAnalysisResult(
                 base.objectiveUnderstanding(), architecture, decisions, evidence, history, constraints,
                 impacted, base.uncertainties(), base.missingInformation(), base.implementationQuestions(),
                 base.implementationPreparation(),
                 base.confidence(), base.provenance(), base.outputClassification(), List.of(), null);
    }

    private StoryContextAnalysisResult resultWithCausalClaims(
            StoryContextAnalysisResult.CausalClaim... claims
    ) {
        StoryContextAnalysisResult base = groundedResult(CANONICAL_REFERENCE);
        return new StoryContextAnalysisResult(
                base.objectiveUnderstanding(), base.architectureFindings(), base.decisionFindings(),
                 base.evidenceFindings(), base.historicalContext(), base.constraintFindings(),
                 base.impactedComponentFindings(), base.uncertainties(), base.missingInformation(),
                 base.implementationQuestions(), base.implementationPreparation(), base.confidence(), base.provenance(),
                 base.outputClassification(), List.of(claims), null);
    }
}
