package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultStatus;
import com.hopeful117.devlogai.ai.engine.dto.PromptExecutionMetadata;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
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
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionService;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledgePromptProjectionService;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyzeStoryContextUseCaseTest {

    private static final String PROJECT_SLUG = "devlog-ai";
    private static final String INTENT_ID = "engineering-story-context-analysis";
    private static final String CONTEXT_DIGEST = "a".repeat(64);
    private static final String CANONICAL_REFERENCE = "src/main/java/Canonical.java";
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
    @Mock private KnowledgeSelectionService knowledgeSelectionService;
    @Mock private SelectedKnowledgePromptProjectionService promptProjectionService;
    @Mock private ProjectProfileService projectProfileService;
    @Mock private AnalysisContextService analysisContextService;
    @Mock private AnalysisRepository analysisRepository;
    @Mock private AnalysisExecutionDiagnosticRepository diagnosticRepository;

    private AnalyzeStoryContextUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new AnalyzeStoryContextUseCase(
                projectRepository,
                storyRepository,
                engineeringContextFacade,
                intentCatalog,
                aiTaskService,
                aiEngineClient,
                aiTaskRepository,
                storyContextAnalysisRepository,
                new ObjectMapper(),
                knowledgeSelectionService,
                promptProjectionService,
                projectProfileService,
                analysisContextService,
                analysisRepository,
                diagnosticRepository
        );
    }

    @Test
    void executeProjectsSelectedKnowledgeAndCanonicalGroundingIntoPrompt() {
        UUID projectId = UUID.randomUUID();
        UUID storyId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Project project = project(projectId);
        EngineeringStory story = story(storyId, project);
        ProjectProfileResponse profile = profile(projectId, analysisId);
        AnalysisContext baselineContext = baselineContext(projectId, analysisId, profile);
        SelectedKnowledge selectedKnowledge = selectedKnowledge(baselineContext, profile);
        Map<String, Object> projectedKnowledge = Map.of(
                "selectedFacts", selectedKnowledge.selectedFacts(),
                "selectedObservations", selectedKnowledge.selectedObservations(),
                "selectedInsights", selectedKnowledge.selectedInsights(),
                "selectionDigest", CONTEXT_DIGEST
        );
        EngineeringContext engineeringContext = engineeringContext();
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
        when(engineeringContextFacade.getEngineeringContext(
                PROJECT_SLUG, INTENT_ID, List.of("src/main/java/Canonical.java"), storyId))
                .thenReturn(engineeringContext);
        when(projectProfileService.getLatestByProject(projectId)).thenReturn(profile);
        when(analysisContextService.build(analysisId)).thenReturn(baselineContext);
        when(knowledgeSelectionService.select(any(), eq(intent), eq(null))).thenReturn(selectedKnowledge);
        when(promptProjectionService.toMap(selectedKnowledge)).thenReturn(projectedKnowledge);
        when(aiTaskService.createForStoryContextAnalysisEntity(
                eq(projectId), eq(AiTaskType.STORY_CONTEXT_ANALYSIS), eq(INTENT_ID), eq("v1"),
                eq("story-context-analysis-prompt-v1"), any(), eq(CONTEXT_DIGEST),
                any(), eq(null)))
                .thenReturn(task);

        UUID result = useCase.execute(
                PROJECT_SLUG, storyId, List.of("src/main/java/Canonical.java"), null);

        assertEquals(task.getId(), result);
        ArgumentCaptor<AnalysisContext> selectionContext = ArgumentCaptor.forClass(AnalysisContext.class);
        verify(knowledgeSelectionService).select(selectionContext.capture(), eq(intent), eq(null));
        assertEquals(INTENT_ID, selectionContext.getValue().analysis().intentId());
        assertEquals(List.of(baselineContext.facts().get(0)), selectionContext.getValue().facts());
        assertEquals(List.of(baselineContext.observations().get(0)), selectionContext.getValue().observations());
        assertEquals(storyId, selectionContext.getValue().engineeringStories().get(0).id());

        ArgumentCaptor<PromptRequest> promptCaptor = ArgumentCaptor.forClass(PromptRequest.class);
        verify(aiEngineClient).submit(promptCaptor.capture());
        PromptRequest request = promptCaptor.getValue();
        assertFalse(((List<?>) request.selectedKnowledge().get("selectedFacts")).isEmpty());
        assertFalse(((List<?>) request.selectedKnowledge().get("selectedObservations")).isEmpty());
        assertFalse(((List<?>) request.selectedKnowledge().get("selectedInsights")).isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projectedStories =
                (List<Map<String, Object>>) request.selectedKnowledge().get("engineeringStories");
        assertEquals(storyId.toString(), projectedStories.get(0).get("id"));
        assertEquals(List.of(CANONICAL_REFERENCE),
                request.groundingContract().get("allowedEvidenceReferences"));
        assertFalse(((List<?>) request.groundingContract().get("allowedEvidenceReferences"))
                .contains(PROVENANCE_IDENTIFIER));
        assertFalse(((List<?>) request.groundingContract().get("allowedEvidenceReferences"))
                .contains(RELATED_REFERENCE));

        @SuppressWarnings("unchecked")
        Map<String, Object> freshness = (Map<String, Object>) task.getContextSnapshot()
                .get("contextFreshness");
        assertEquals("STALE", freshness.get("status"));
        assertEquals("repository-revision", freshness.get("repositoryRevision"));
        verify(aiTaskService).submit(eq(task.getId()), any());
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

    private ProjectProfileResponse profile(UUID projectId, UUID analysisId) {
        return new ProjectProfileResponse(
                UUID.randomUUID(), projectId, analysisId, "v1", "v1", Instant.now(),
                null, Map.of(), null, List.of(), "profile", List.of(), 1);
    }

    private AnalysisContext baselineContext(
            UUID projectId, UUID analysisId, ProjectProfileResponse profile) {
        Instant now = Instant.parse("2026-09-08T10:00:00Z");
        AnalysisContext.FactSnapshot fact = new AnalysisContext.FactSnapshot(
                UUID.randomUUID(), FactType.DOCKER_SERVICE_DEPENDS_ON, "backend -> ai-engine",
                "docker-compose.yml", List.of(CANONICAL_REFERENCE), now);
        AnalysisContext.ObservationSnapshot observation = new AnalysisContext.ObservationSnapshot(
                UUID.randomUUID(), ObservationType.HTTP_SERVICE_COMMUNICATION,
                "Backend communicates with the AI Engine", "rule", "v1",
                List.of(fact.id()), now);
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(
                        projectId, "DevLog AI", PROJECT_SLUG, null, ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(
                        analysisId, AnalysisType.ARCHITECTURE_REVIEW, "project-profile", "v1",
                        AnalysisStatus.COMPLETED, now, now, now),
                profile,
                List.of(fact),
                List.of(observation),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
    }

    private SelectedKnowledge selectedKnowledge(
            AnalysisContext context, ProjectProfileResponse profile) {
        SelectedKnowledge.InsightSnapshot insight = new SelectedKnowledge.InsightSnapshot(
                UUID.randomUUID(), context.analysis().id(), InsightType.ARCHITECTURAL,
                InsightSeverity.INFO, "Core authority", "Java owns grounding authority");
        return new SelectedKnowledge(
                context.project(), context.analysis(), profile,
                context.observations(), context.facts(),
                new SelectedKnowledge.DiagnosticSnapshot(true, false, 0, 0),
                List.of(insight), null,
                new SelectedKnowledge.SelectionMetadata(
                        "knowledge-selection-v5",
                        List.of("ENGINEERING_STORY_RELEVANCE"),
                        3, 0,
                        new SelectedKnowledge.KnowledgeBudget(40, 25, 10, 5, 60),
                        "COMPLETE"),
                CONTEXT_DIGEST
        );
    }

    private EngineeringContext engineeringContext() {
        EngineeringEvidence evidence = new EngineeringEvidence(
                "SOURCE_FILE", "CODE", "Canonical evidence", "REPOSITORY",
                "src/main/java/Canonical.java", PROVENANCE_IDENTIFIER, CANONICAL_REFERENCE,
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
        return AiTask.builder()
                .id(UUID.randomUUID())
                .correlationId(correlationId)
                .taskType(AiTaskType.STORY_CONTEXT_ANALYSIS)
                .intentId(INTENT_ID)
                .intentVersion("v1")
                .status(AiTaskStatus.SUBMITTED)
                .contextDigest(CONTEXT_DIGEST)
                .contextSnapshot(Map.of(
                        "storyId", storyId.toString(),
                        "groundingContract", Map.of(
                                "allowedEvidenceReferences", List.of(allowedReference)),
                        "contextFreshness", freshness
                ))
                .build();
    }

    private AiTaskResultRequest completedRequest(
            UUID correlationId,
            Instant completedAt,
            StoryContextAnalysisResult result
    ) {
        PromptExecutionMetadata execution = new PromptExecutionMetadata(
                "story-context-analysis-prompt-v1", "mock", "deterministic-v1",
                "b".repeat(64), CONTEXT_DIGEST);
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
}
