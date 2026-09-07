package com.hopeful117.devlogai.ai.engine.service;

import tools.jackson.databind.ObjectMapper;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskResultServiceTest {

    @Mock
    private AiTaskRepository aiTaskRepository;

    @Mock
    private ValidatableProposalRepository proposalRepository;

    @Mock
    private FactRepository factRepository;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AiProposalContractValidator proposalContractValidator;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AnalyzeStoryContextUseCase analyzeStoryContextUseCase;

    @InjectMocks
    private AiTaskResultServiceImpl service;

    @Test
    void shouldCreateProposalsAndCompleteSubmittedTask() {
        UUID correlationId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        UUID observationId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-07-21T20:00:00Z");
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        Fact fact = Fact.builder().id(factId).analysis(task.getAnalysis()).build();
        Observation observation = Observation.builder()
                .id(observationId)
                .ruleId("TEST_RULE").ruleVersion("1")
                .analysis(task.getAnalysis())
                .build();
        AiProposalResult proposal = new AiProposalResult(
                ProposalType.INSIGHT,
                java.util.Map.of("title", "Test insight"),
                new BigDecimal("0.8500"),
                List.of(factId),
                List.of(observationId),
                List.of("src/main/java/App.java:12")
        );
        AiTaskResultRequest request = completedRequest(
                correlationId,
                completedAt,
                List.of(proposal)
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(factRepository.findAllById(any())).thenReturn(List.of(fact));
        when(observationRepository.findAllById(any()))
                .thenReturn(List.of(observation));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(1L);

        AiTaskResultAcknowledgement result = service.handle(
                correlationId,
                request
        );

        assertTrue(result.acknowledged());
        assertFalse(result.duplicate());
        assertEquals(AiTaskStatus.COMPLETED, result.taskStatus());
        assertEquals(1, result.proposalCount());
        assertEquals(AiTaskStatus.COMPLETED, task.getStatus());
        assertEquals(completedAt, task.getStartedAt());
        assertEquals(completedAt, task.getCompletedAt());
        assertEquals("describe-project-prompt-v1", task.getPromptVersion());
        assertEquals("mock", task.getProvider());
        assertEquals("deterministic-v1", task.getModelIdentifier());
        assertEquals("a".repeat(64), task.getPromptContentDigest());
        assertEquals("b".repeat(64), task.getContextDigest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ValidatableProposal>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(proposalRepository).saveAll(captor.capture());
        ValidatableProposal saved = captor.getValue().getFirst();
        assertSame(task, saved.getAiTask());
        assertSame(task.getAnalysis(), saved.getAnalysis());
        assertSame(task.getAnalysis().getProject(), saved.getProject());
        assertEquals(0, saved.getSourceIndex());
        assertEquals(ProposalStatus.PROPOSED, saved.getStatus());
        assertEquals(new BigDecimal("0.8500"), saved.getConfidence());
        assertEquals(List.of(factId), saved.getSupportingFactIds());
        assertEquals(List.of(observationId), saved.getSupportingObservationIds());
        verify(aiTaskRepository).save(task);
    }

    @Test
    void shouldAcknowledgeDuplicateWithoutCreatingProposalsAgain() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.COMPLETED);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(2L);

        AiTaskResultAcknowledgement result = service.handle(
                correlationId,
                completedRequest(correlationId, Instant.now(), List.of())
        );

        assertTrue(result.acknowledged());
        assertTrue(result.duplicate());
        assertEquals(2, result.proposalCount());
        verify(proposalRepository, never()).saveAll(any());
        verify(aiTaskRepository, never()).save(any());
        verifyNoInteractions(factRepository, observationRepository);
    }

    @Test
    void shouldRecordFailedResultWithoutCreatingProposals() {
        UUID correlationId = UUID.randomUUID();
        Instant completedAt = Instant.now();
        AiTask task = task(correlationId, AiTaskStatus.PROCESSING);
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId,
                "job-42",
                AiTaskResultStatus.FAILED,
                completedAt,
                List.of(),
                new AiTaskResultError("MODEL_ERROR", "Provider failed")
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(0L);

        AiTaskResultAcknowledgement result = service.handle(
                correlationId,
                request
        );

        assertEquals(AiTaskStatus.FAILED, result.taskStatus());
        assertEquals(AiTaskStatus.FAILED, task.getStatus());
        assertEquals("MODEL_ERROR", task.getFailureCode());
        assertEquals("Provider failed", task.getFailureMessage());
        assertEquals(completedAt, task.getCompletedAt());
        verify(proposalRepository, never()).saveAll(any());
        verify(aiTaskRepository).save(task);
    }

    @Test
    void shouldReportCreatedTaskAsTransientNotReadyConflict() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.CREATED);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        AiTaskResultRequest request = completedRequest(
                correlationId, Instant.now(), List.of());

        AiTaskResultConflictException result = assertThrows(
                AiTaskResultConflictException.class,
                () -> service.handle(correlationId, request)
        );

        assertEquals("AI_TASK_NOT_READY", result.getCode());
        assertEquals(AiTaskStatus.CREATED, result.getCurrentStatus());
        verifyNoInteractions(proposalRepository, factRepository, observationRepository);
    }

    @Test
    void shouldReportDifferentTerminalResultAsNonTransientConflict() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.FAILED);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        AiTaskResultRequest request = completedRequest(
                correlationId, Instant.now(), List.of());

        AiTaskResultConflictException result = assertThrows(
                AiTaskResultConflictException.class,
                () -> service.handle(correlationId, request)
        );

        assertEquals("AI_TASK_TERMINAL_CONFLICT", result.getCode());
        assertEquals(AiTaskStatus.FAILED, result.getCurrentStatus());
    }

    @Test
    void shouldRejectReferencesFromAnotherAnalysis() {
        UUID correlationId = UUID.randomUUID();
        UUID factId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        Fact foreignFact = Fact.builder()
                .id(factId)
                .analysis(Analysis.builder().id(UUID.randomUUID()).build())
                .build();
        AiProposalResult proposal = new AiProposalResult(
                ProposalType.CHALLENGE,
                java.util.Map.of("title", "Test challenge"),
                BigDecimal.ONE,
                List.of(factId),
                List.of(),
                List.of()
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(factRepository.findAllById(any())).thenReturn(List.of(foreignFact));
        AiTaskResultRequest request = completedRequest(
                correlationId, Instant.now(), List.of(proposal));

        assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(correlationId, request)
        );

        verify(proposalRepository, never()).saveAll(any());
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void shouldPersistSynthesisWhenPresent() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        task.setSelectedKnowledgeSnapshot(Map.of(
                "selectedFacts", List.of(Map.of(
                        "evidenceReferences", List.of("src/main.java:10")))));
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "Architecture Overview",
                List.of(new AnalysisSynthesisResult.SynthesisSection("Components", "REST API")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of("src/main.java:10")
        );
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptMetadata(), synthesis);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(factRepository.findAllById(any())).thenReturn(List.of());
        when(observationRepository.findAllById(any())).thenReturn(List.of());
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(0L);
        when(objectMapper.convertValue(any(), eq(java.util.Map.class)))
                .thenReturn(java.util.Map.of("title", "Architecture Overview"));

        AiTaskResultAcknowledgement result = service.handle(correlationId, request);

        assertTrue(result.acknowledged());
        assertEquals(AiTaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getSynthesisSnapshot());
        assertEquals("Architecture Overview", task.getSynthesisSnapshot().get("title"));
        verify(aiTaskRepository).save(task);
    }

    @Test
    void shouldRejectV2CompletionWithoutSynthesis() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptMetadata(), null);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        InvalidAiTaskResultException ex = assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(correlationId, request)
        );
        assertTrue(ex.getMessage().contains("synthesis"));
        verify(proposalRepository, never()).saveAll(any());
    }

    @Test
    void shouldRejectSynthesisWithBlankTitle() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "  ",
                List.of(new AnalysisSynthesisResult.SynthesisSection("Components", "REST API")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of()
        );
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptMetadata(), synthesis);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        InvalidAiTaskResultException ex = assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(correlationId, request)
        );
        assertTrue(ex.getMessage().contains("blank"));
        verify(proposalRepository, never()).saveAll(any());
    }

    @Test
    void shouldRejectSynthesisWithEmptySections() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "Architecture",
                List.of(),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of()
        );
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptMetadata(), synthesis);
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        InvalidAiTaskResultException ex = assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(correlationId, request)
        );
        assertTrue(ex.getMessage().contains("section"));
        verify(proposalRepository, never()).saveAll(any());
    }

    @Test
    void shouldValidateSynthesisGroundingBeforePersistence() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        task.setSelectedKnowledgeSnapshot(Map.of(
                "selectedFacts", List.of(Map.of(
                        "evidenceReferences", List.of("allowed.java:1")))));
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "Architecture",
                List.of(new AnalysisSynthesisResult.SynthesisSection("Components", "REST API")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of("invented.java:1")
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        service.handle(
                correlationId,
                new AiTaskResultRequest(correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                        Instant.now(), List.of(), null, promptMetadata(), synthesis));

        verify(proposalContractValidator).validateSynthesis(task, synthesis, false);
    }

    @Test
    void shouldRejectSynthesisForV1Intent() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v1");
        AnalysisSynthesisResult synthesis = new AnalysisSynthesisResult(
                "Architecture",
                List.of(new AnalysisSynthesisResult.SynthesisSection("Components", "REST API")),
                AnalysisSynthesisResult.ArchitectureDeltaConclusion.NO_MATERIAL_DELTA,
                List.of()
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        assertThrows(InvalidAiTaskResultException.class, () -> service.handle(
                correlationId,
                new AiTaskResultRequest(correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                        Instant.now(), List.of(), null, promptMetadata(), synthesis)));
    }

    @Test
    void shouldRejectTerminalV2TaskThatHasNoPersistedSynthesis() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.COMPLETED);
        task.setIntentId("architecture-overview");
        task.setIntentVersion("v2");
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        AiTaskResultConflictException error = assertThrows(
                AiTaskResultConflictException.class,
                () -> service.handle(correlationId, completedRequest(
                        correlationId, Instant.now(), List.of())));

        assertEquals("AI_TASK_INVALID_TERMINAL_RESULT", error.getCode());
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void shouldDelegateStoryContextAnalysisCallbackToUseCaseAndPreservePromptExecutionMetadata() {
        UUID correlationId = UUID.randomUUID();
        Instant completedAt = Instant.parse("2026-09-06T10:00:00Z");
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("engineering-story-context-analysis");
        task.setIntentVersion("v1");
        task.setContextSnapshot(Map.of(
                "analysisId", task.getAnalysis().getId().toString(),
                "intentId", "engineering-story-context-analysis",
                "intentVersion", "v1",
                "storyId", UUID.randomUUID().toString()
        ));

        PromptExecutionMetadata promptExec = new PromptExecutionMetadata(
                "story-context-prompt-v1", "openai", "gpt-4.1-mini",
                "digest-abc", "context-digest-xyz");
        StoryContextAnalysisResult analysisResult = new StoryContextAnalysisResult(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                completedAt, List.of(), null, promptExec, null, analysisResult);

        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(0L);

        AiTaskResultAcknowledgement result = service.handle(correlationId, request);

        assertTrue(result.acknowledged());
        assertFalse(result.duplicate());
        verify(analyzeStoryContextUseCase).handleCallback(correlationId, request);
        verify(proposalRepository, never()).saveAll(any());
    }

    @Test
    void shouldRejectStoryContextAnalysisCallbackWithoutAnalysisResult() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("engineering-story-context-analysis");
        task.setIntentVersion("v1");

        PromptExecutionMetadata promptExec = new PromptExecutionMetadata(
                "story-context-prompt-v1", "mock", "model",
                "a".repeat(64), "b".repeat(64));
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptExec, null, null);

        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));

        InvalidAiTaskResultException ex = assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(correlationId, request)
        );
        assertTrue(ex.getMessage().contains("analysisResult"));
        verifyNoInteractions(analyzeStoryContextUseCase);
    }

    @Test
    void shouldHandleDuplicateStoryContextAnalysisCallbackAsNoOp() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = task(correlationId, AiTaskStatus.COMPLETED);
        task.setIntentId("engineering-story-context-analysis");
        task.setIntentVersion("v1");

        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(0L);

        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.COMPLETED,
                Instant.now(), List.of(), null, promptMetadata(), null, null);

        AiTaskResultAcknowledgement result = service.handle(correlationId, request);

        assertTrue(result.acknowledged());
        assertTrue(result.duplicate());
        assertEquals(AiTaskStatus.COMPLETED, result.taskStatus());
        verifyNoInteractions(analyzeStoryContextUseCase);
    }

    @Test
    void shouldHandleFailedStoryContextAnalysisCallback() {
        UUID correlationId = UUID.randomUUID();
        Instant completedAt = Instant.now();
        AiTask task = task(correlationId, AiTaskStatus.SUBMITTED);
        task.setIntentId("engineering-story-context-analysis");
        task.setIntentVersion("v1");

        PromptExecutionMetadata promptExec = new PromptExecutionMetadata(
                "story-context-prompt-v1", "mock", "model",
                "a".repeat(64), "b".repeat(64));
        AiTaskResultRequest request = new AiTaskResultRequest(
                correlationId, "job-42", AiTaskResultStatus.FAILED,
                completedAt, List.of(),
                new AiTaskResultError("PROVIDER_TIMEOUT", "LLM did not respond"),
                promptExec, null, null);

        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAiTaskId(task.getId())).thenReturn(0L);

        AiTaskResultAcknowledgement result = service.handle(correlationId, request);

        assertEquals(AiTaskStatus.FAILED, result.taskStatus());
        assertEquals(AiTaskStatus.FAILED, task.getStatus());
        assertEquals("PROVIDER_TIMEOUT", task.getFailureCode());
        assertEquals("LLM did not respond", task.getFailureMessage());
        assertEquals(completedAt, task.getCompletedAt());
        verify(analyzeStoryContextUseCase).handleCallback(correlationId, request);
    }

    private AiTask task(UUID correlationId, AiTaskStatus status) {
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Analysis analysis = Analysis.builder()
                .id(UUID.randomUUID())
                .project(project)
                .build();
        return AiTask.builder()
                .id(UUID.randomUUID())
                .analysis(analysis)
                .correlationId(correlationId)
                .externalJobId("job-42")
                .status(status)
                .build();
    }

    private AiTaskResultRequest completedRequest(
            UUID correlationId,
            Instant completedAt,
            List<AiProposalResult> proposals
    ) {
        return new AiTaskResultRequest(
                correlationId,
                "job-42",
                AiTaskResultStatus.COMPLETED,
                completedAt,
                proposals,
                null,
                promptMetadata(),
                null
        );
    }

    private PromptExecutionMetadata promptMetadata() {
        return new PromptExecutionMetadata(
                "describe-project-prompt-v1", "mock", "deterministic-v1",
                "a".repeat(64), "b".repeat(64));
    }
}
