package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
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
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextMetadata;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringEvidence;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
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
import java.util.Optional;
import java.util.UUID;
import java.lang.reflect.Method;

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
                engineeringContextFacade,
                intentCatalog,
                aiTaskService,
                aiEngineClient,
                aiTaskRepository,
                storyContextAnalysisRepository,
                objectMapper,
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
                engineeringContext, null, CONTEXT_DIGEST, "engineering-context-v2", null,
                Map.of("status", "STALE", "repositoryRevision", "repository-revision"),
                Map.of("evidenceCount", 1), Map.of(DOCUMENT_REFERENCE, "TECHNICAL_EVIDENCE"),
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
                ((Map<?, ?>) request.selectedKnowledge().get("canonicalContext")).get("contextVersion"));
        assertTrue(request.selectedKnowledge().keySet().containsAll(List.of(
                "project", "analysis", "projectProfile", "selectedFacts",
                "selectedObservations", "diagnostics", "selectedInsights",
                "selectionMetadata", "repositoryContext", "engineeringStories")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projectedEvidence = request.selectedKnowledge().get("repositoryContext") instanceof Map<?, ?> repository
                && repository.get("evidence") instanceof List<?> evidence
                ? (List<Map<String, Object>>) evidence : List.of();
        assertTrue(projectedEvidence.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projectedStories =
                (List<Map<String, Object>>) request.selectedKnowledge().get("engineeringStories");
        assertEquals(storyId.toString(), projectedStories.get(0).get("id"));
        ArgumentCaptor<UUID> executionAnalysisId = ArgumentCaptor.forClass(UUID.class);
        verify(aiTaskService).createForStoryContextAnalysisEntity(
                executionAnalysisId.capture(), eq(AiTaskType.STORY_CONTEXT_ANALYSIS), eq(INTENT_ID), eq("v1"),
                eq("story-context-analysis-prompt-v1"), any(), any(String.class),
                any(), eq(null), any(AiReferenceRegistry.class));
        @SuppressWarnings("unchecked")
        List<String> allowedEvidenceReferences = (List<String>) request.groundingContract()
                .get("allowedEvidenceReferences");
        assertEquals(List.of(DOCUMENT_REFERENCE), allowedEvidenceReferences);
        assertFalse(allowedEvidenceReferences.contains(CANONICAL_REFERENCE));
        assertFalse(((List<?>) request.groundingContract().get("allowedEvidenceReferences"))
                .contains(PROVENANCE_IDENTIFIER));
        assertFalse(((List<?>) request.groundingContract().get("allowedEvidenceReferences"))
                .contains(RELATED_REFERENCE));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> typedGrounding =
                (List<Map<String, Object>>) request.groundingContract().get("allowedGroundingReferences");
        assertEquals("REPOSITORY_EVIDENCE", typedGrounding.get(0).get("type"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("ref"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("coreReference"));
        assertEquals(DOCUMENT_REFERENCE, typedGrounding.get(0).get("taskReference"));
        assertTrue(((Map<?, ?>) typedGrounding.get(0).get("scope")).containsKey("project"));
        assertTrue(((Map<?, ?>) typedGrounding.get(0).get("scope")).containsKey("revision"));
        assertEquals(64, request.contextDigest().length());
        assertNotNull(request.projectionDigest());
        assertEquals(request.projectionDigest(), request.metadata().get("projectionDigest"));
        assertEquals(StoryContextAgentProjection.PROJECTION_VERSION,
                request.metadata().get("contractVersion"));
        assertEquals(StoryContextAgentProjection.PROJECTION_VERSION,
                request.metadata().get("projectionVersion"));
        assertEquals(StoryContextAgentProjection.PROJECTION_VERSION,
                task.getContextSnapshot().get("projectionVersion"));
        @SuppressWarnings("unchecked")
        Map<String, Object> persistedProjection =
                (Map<String, Object>) task.getContextSnapshot().get("projection");
        assertEquals(StoryContextAgentProjection.PROJECTION_VERSION, persistedProjection.get("version"));
        assertEquals(request.selectedKnowledge(), persistedProjection.get("selectedKnowledge"));
        assertEquals(request.groundingContract(), persistedProjection.get("groundingContract"));
        assertEquals(request.groundingContract(), task.getContextSnapshot().get("groundingContract"));
        assertEquals(request.projectionDigest(),
                projectionDigest((Map<String, Object>) persistedProjection.get("selectedKnowledge"),
                        (Map<String, Object>) persistedProjection.get("groundingContract")));
        assertEquals(request.projectionDigest(), task.getProjectionDigest());
        assertEquals(null, task.getSelectionDigest());
        assertEquals(null, task.getSelectionVersion());
        assertEquals(request.contextDigest(), task.getContextDigest());
        assertEquals(request.projectionDigest(), task.getContextSnapshot().get("projectionDigest"));
        assertTrue(task.getContextSnapshot().containsKey("groundingContract"));

        @SuppressWarnings("unchecked")
        Map<String, Object> freshness = (Map<String, Object>) task.getContextSnapshot()
                .get("contextFreshness");
        assertEquals("STALE", freshness.get("status"));
        assertEquals("repository-revision", freshness.get("repositoryRevision"));
        assertEquals(CONTEXT_DIGEST, task.getContextSnapshot().get("contextDigest"));
        assertEquals(null, task.getContextSnapshot().get("selectionDigest"));
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
        assertEquals(StoryContextAgentProjection.PROJECTION_VERSION, policy.get("contractVersion"));
        assertEquals(request.projectionDigest(), policy.get("projectionDigest"));
        verify(aiTaskService).submit(eq(task.getId()), any());
    }

    @Test
    void projectionDigestChangesForGroundingAndSelectedKnowledgeAndIgnoresMapOrder() throws Exception {
        Map<String, Object> selected = new LinkedHashMap<>();
        Map<String, Object> selectedKnowledge = new LinkedHashMap<>();
        selectedKnowledge.put("answer", "one");
        selectedKnowledge.put("context", "stable");
        selected.put("selectedKnowledge", selectedKnowledge);
        Map<String, Object> grounding = new LinkedHashMap<>();
        grounding.put("allowedEvidenceReferences", List.of(DOCUMENT_REFERENCE));

        String baseline = projectionDigest(selected, grounding);
        Map<String, Object> changedGrounding = new LinkedHashMap<>(grounding);
        changedGrounding.put("causalAnswerRequired", true);
        assertNotEquals(baseline, projectionDigest(selected, changedGrounding));

        Map<String, Object> changedSelected = new LinkedHashMap<>(selected);
        changedSelected.put("selectedKnowledge", new LinkedHashMap<>(Map.of("answer", "two")));
        assertNotEquals(baseline, projectionDigest(changedSelected, grounding));

        Map<String, Object> reorderedSelected = new LinkedHashMap<>();
        Map<String, Object> reorderedKnowledge = new LinkedHashMap<>();
        reorderedKnowledge.put("context", "stable");
        reorderedKnowledge.put("answer", "one");
        reorderedSelected.put("selectedKnowledge", reorderedKnowledge);
        Map<String, Object> reorderedGrounding = new LinkedHashMap<>();
        reorderedGrounding.put("allowedEvidenceReferences", List.of(DOCUMENT_REFERENCE));
        assertEquals(baseline, projectionDigest(reorderedSelected, reorderedGrounding));
    }

    private String projectionDigest(Map<String, Object> selected, Map<String, Object> grounding)
            throws Exception {
        Method method = AnalyzeStoryContextUseCase.class.getDeclaredMethod(
                "digestProjection", Map.class, Map.class);
        method.setAccessible(true);
        return (String) method.invoke(useCase, selected, grounding);
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
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(invocation -> {
            Analysis saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
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
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(invocation -> {
            Analysis saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

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
                correlationId, completedAt, groundedResult(CANONICAL_REFERENCE));

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
                            correlationId, Instant.parse("2026-09-09T10:00:00Z"), result))
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
                    correlationId, Instant.parse("2026-09-09T10:00:00Z"), result));
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
    void restGuidanceUsesSharedFieldsWhenBuildingPythonPromptContract() throws Exception {
        Method mapper = AnalyzeStoryContextUseCase.class.getDeclaredMethod("mapGuidance", Map.class);
        mapper.setAccessible(true);

        var guidance = Map.<String, Object>of(
                "focus", "repository relationships",
                "audience", "maintainers",
                "levelOfDetail", "deep",
                "writingStyle", "concise",
                "outputContext", "implementation review",
                "priorities", List.of("trust", "digests"));

        var mapped = (com.hopeful117.devlogai.intent.model.UserGuidance)
                mapper.invoke(useCase, guidance);

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
                        completedRequest(correlationId, Instant.parse("2026-09-09T10:00:00Z"), groundedResult(CANONICAL_REFERENCE))));

        assertTrue(error.getMessage().contains("causalClaims must not be empty"));
        verify(storyContextAnalysisRepository, never()).save(any());
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
        groundingContract.put("allowedEvidenceReferences", List.of(allowedReference));
        groundingContract.put("causalAnswerRequired", causalAnswerRequired);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("storyId", storyId.toString());
        snapshot.put("groundingContract", groundingContract);
        snapshot.put("contextFreshness", freshness);
        snapshot.put("contextDigest", CONTEXT_DIGEST);
        snapshot.put("selectionDigest", null);
        snapshot.put("projectionDigest", PROJECTION_DIGEST);
        return AiTask.builder()
                .id(UUID.randomUUID())
                .correlationId(correlationId)
                .taskType(AiTaskType.STORY_CONTEXT_ANALYSIS)
                .intentId(INTENT_ID)
                .intentVersion("v1")
                .status(AiTaskStatus.SUBMITTED)
                .contextDigest(CONTEXT_DIGEST)
                .projectionDigest(PROJECTION_DIGEST)
                .contextSnapshot(snapshot)
                .build();
    }

    private AiTaskResultRequest completedRequest(
            UUID correlationId,
            Instant completedAt,
            StoryContextAnalysisResult result
    ) {
        PromptExecutionMetadata execution = new PromptExecutionMetadata(
                "story-context-analysis-prompt-v1", "mock", "deterministic-v1",
                "c".repeat(64), CONTEXT_DIGEST, null, PROJECTION_DIGEST);
        return new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED, completedAt,
                List.of(), null, execution, null, result);
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
                base.implementationQuestions(), base.confidence(), base.provenance(),
                base.outputClassification(), List.of(claims));
    }
}
