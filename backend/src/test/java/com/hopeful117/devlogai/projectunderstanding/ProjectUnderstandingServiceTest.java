package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.workflow.AnalysisAiTaskTypeResolver;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowServiceImpl;
import com.hopeful117.devlogai.collection.service.KnowledgeCollectionService;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionService;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledgePromptProjectionService;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ProjectUnderstandingServiceTest {
    @Mock ProjectUnderstandingPreparationService preparation;
    @Mock ProjectUnderstandingClaimService claims;
    @Mock AnalysisWorkflowService workflow;
    @Mock AnalysisRepository analyses;
    private ProjectUnderstandingService service;
    private UUID projectId;
    private PreparedProjectUnderstanding prepared;
    private Analysis analysis;

    @BeforeEach
    void setUp() {
        service = new ProjectUnderstandingService(preparation, claims, workflow, analyses);
        projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        IntentDefinition intent = new IntentDefinition(
                "describe-project", "v1", "describe", List.of(), List.of(), Map.of(), "prompt");
        prepared = new PreparedProjectUnderstanding(projectId, sourceId, null, "abc", null,
                intent, Map.of("id", sourceId.toString(), "name", "Core"));
        analysis = Analysis.builder().id(UUID.randomUUID()).selectedSource(
                        Source.builder().id(sourceId).build())
                .selectedSourceSnapshot(prepared.sourceSnapshot()).status(AnalysisStatus.PENDING)
                .intentId(intent.id()).intentVersion(intent.version()).build();
        when(preparation.prepare(eq(projectId), any())).thenReturn(prepared);
    }

    @Test
    void startsANewClaimAndReturnsCreatedOutcome() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.CREATED));
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        when(analyses.findById(analysis.getId())).thenReturn(Optional.of(analysis));

        var response = service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null));

        assertThat(response.outcome()).isEqualTo(ProjectUnderstandingOutcome.CREATED);
        assertThat(response.analysisId()).isEqualTo(analysis.getId());
        verify(workflow).start(analysis.getId());
    }

    @Test
    void reusesAnEquivalentActiveClaimWithoutRestartingIt() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.REUSED));

        var response = service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null));

        assertThat(response.outcome()).isEqualTo(ProjectUnderstandingOutcome.REUSED);
        verifyNoInteractions(workflow);
    }

    @Test
    void reloadsTheWinnerAfterAConcurrentUniqueKeyRace() {
        when(claims.claim(prepared)).thenThrow(new DataIntegrityViolationException("race"));
        when(claims.findWinner(prepared)).thenReturn(Optional.of(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.REUSED)));

        assertThat(service.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null)).outcome())
                .isEqualTo(ProjectUnderstandingOutcome.REUSED);
        verifyNoInteractions(workflow);
    }

    @Test
    void marksANewPendingClaimFailedWhenWorkflowCannotStart() {
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.CREATED));
        doThrow(new IllegalStateException("start failed")).when(workflow).start(analysis.getId());
        ProjectUnderstandingRequest request = new ProjectUnderstandingRequest(
                prepared.sourceId(), null, null);

        assertThatThrownBy(() -> service.execute(projectId, request))
                .isInstanceOf(IllegalStateException.class);
        verify(claims).failPending(analysis.getId());
    }

    @Test
    void executesARefreshThroughTheRealWorkflowSeams() {
        AnalysisService analysisService = mock(AnalysisService.class);
        KnowledgeCollectionService knowledgeCollectionService = mock(KnowledgeCollectionService.class);
        com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService deterministicAnalysisService =
                mock(com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService.class);
        ProjectProfileService projectProfileService = mock(ProjectProfileService.class);
        IntentCatalog intentCatalog = mock(IntentCatalog.class);
        AnalysisContextService analysisContextService = mock(AnalysisContextService.class);
        AiTaskService aiTaskService = mock(AiTaskService.class);
        AIEngineClient aiEngineClient = mock(AIEngineClient.class);
        KnowledgeSelectionService knowledgeSelectionService = mock(KnowledgeSelectionService.class);
        SelectedKnowledgePromptProjectionService promptProjectionService =
                new SelectedKnowledgePromptProjectionService(new ObjectMapper());
        SelectedKnowledge selectedKnowledge = mock(SelectedKnowledge.class);
        AnalysisAiTaskTypeResolver resolver = new AnalysisAiTaskTypeResolver();

        AnalysisWorkflowServiceImpl realWorkflow = new AnalysisWorkflowServiceImpl(
                analysisService,
                resolver,
                knowledgeCollectionService,
                deterministicAnalysisService,
                projectProfileService,
                analysisContextService,
                aiTaskService,
                aiEngineClient,
                intentCatalog,
                knowledgeSelectionService,
                promptProjectionService
        );
        ProjectUnderstandingService refreshService = new ProjectUnderstandingService(
                preparation,
                claims,
                realWorkflow,
                analyses
        );

        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        when(claims.claim(prepared)).thenReturn(
                new ProjectUnderstandingClaim(analysis, ProjectUnderstandingOutcome.CREATED));
        when(intentCatalog.resolve("describe-project", "v1")).thenReturn(prepared.intent());
        when(analysisService.start(analysis.getId())).thenReturn(new com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse(
                analysis.getId(),
                projectId,
                prepared.sourceId(),
                AnalysisType.ARCHITECTURE_REVIEW,
                "describe-project",
                "v1",
                AnalysisStatus.IN_PROGRESS,
                Instant.now(),
                null,
                Instant.now(),
                Instant.now(),
                null,
                prepared.sourceSnapshot()
        ));
        when(deterministicAnalysisService.analyze(analysis.getId()))
                .thenReturn(new com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisResult(3, 2));
        AnalysisContext context = mock(AnalysisContext.class);
        when(analysisContextService.build(analysis.getId())).thenReturn(context);
        when(knowledgeSelectionService.select(eq(context), eq(prepared.intent()), isNull()))
                .thenReturn(selectedKnowledge);
        when(aiTaskService.create(eq(new CreateAiTaskRequest(analysis.getId(), AiTaskType.INSIGHT_GENERATION)), eq(context)))
                .thenReturn(new AiTaskResponse(taskId, analysis.getId(), correlationId,
                        AiTaskType.INSIGHT_GENERATION, AiTaskStatus.CREATED,
                        Map.of("context", "snapshot"), null, 0, null, null,
                        Instant.now(), null, null, null));
        when(aiTaskService.attachSelectedKnowledge(taskId, selectedKnowledge))
                .thenReturn(new AiTaskResponse(taskId, analysis.getId(), correlationId,
                        AiTaskType.INSIGHT_GENERATION, AiTaskStatus.CREATED,
                        Map.of("context", "snapshot"), null, 0, null, null,
                        Instant.now(), null, null, null));
        when(aiEngineClient.submit(any(com.hopeful117.devlogai.ai.engine.dto.PromptRequest.class))).thenReturn(new AiTaskSubmissionResponse(
                correlationId, true, "job-42", Instant.now()
        ));
        when(aiTaskService.submit(eq(taskId), eq(new SubmitAiTaskRequest("job-42"))))
                .thenReturn(new AiTaskResponse(taskId, analysis.getId(), correlationId,
                        AiTaskType.INSIGHT_GENERATION, AiTaskStatus.SUBMITTED,
                        Map.of("context", "snapshot"), "job-42", 0, null, null,
                        Instant.now(), Instant.now(), null, null));
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        when(analyses.findById(analysis.getId())).thenReturn(Optional.of(analysis));

        var response = refreshService.execute(projectId,
                new ProjectUnderstandingRequest(prepared.sourceId(), null, null));

        assertThat(response.outcome()).isEqualTo(ProjectUnderstandingOutcome.CREATED);
        assertThat(response.analysisId()).isEqualTo(analysis.getId());
        assertThat(response.status()).isEqualTo(AnalysisStatus.IN_PROGRESS);
        verify(analysisContextService).build(analysis.getId());
        verify(knowledgeSelectionService).select(eq(context), eq(prepared.intent()), isNull());
        verify(aiTaskService).attachSelectedKnowledge(taskId, selectedKnowledge);
        verify(aiEngineClient).submit(any(com.hopeful117.devlogai.ai.engine.dto.PromptRequest.class));
    }
}
