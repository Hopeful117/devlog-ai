package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.fact.entity.Fact;
import com.hopeful117.devlogai.fact.repository.FactRepository;
import com.hopeful117.devlogai.observation.entity.Observation;
import com.hopeful117.devlogai.observation.repository.ObservationRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.entity.ValidatableProposal;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
                .analysis(task.getAnalysis())
                .build();
        AiProposalResult proposal = new AiProposalResult(
                ProposalType.INSIGHT,
                mock(JsonNode.class),
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
                mock(JsonNode.class),
                BigDecimal.ONE,
                List.of(factId),
                List.of(),
                List.of()
        );
        when(aiTaskRepository.findByCorrelationIdForUpdate(correlationId))
                .thenReturn(Optional.of(task));
        when(factRepository.findAllById(any())).thenReturn(List.of(foreignFact));

        assertThrows(
                InvalidAiTaskResultException.class,
                () -> service.handle(
                        correlationId,
                        completedRequest(
                                correlationId,
                                Instant.now(),
                                List.of(proposal)
                        )
                )
        );

        verify(proposalRepository, never()).saveAll(any());
        verify(aiTaskRepository, never()).save(any());
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
                null
        );
    }
}
