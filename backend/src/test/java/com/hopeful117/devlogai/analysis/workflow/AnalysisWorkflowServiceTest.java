package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisResult;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisWorkflowServiceTest {

    @Mock
    private AnalysisService analysisService;

    @Mock
    private DeterministicAnalysisService deterministicAnalysisService;

    @Mock
    private AnalysisContextService analysisContextService;

    @Mock
    private AiTaskService aiTaskService;

    @InjectMocks
    private AnalysisWorkflowServiceImpl workflowService;

    @Test
    void shouldPrepareAiTaskInWorkflowOrder() {
        UUID analysisId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AiTaskType taskType = AiTaskType.DECISION_PROPOSAL_GENERATION;
        AnalysisResponse analysis = analysisResponse(analysisId, AnalysisStatus.IN_PROGRESS);
        AnalysisContext context = mock(AnalysisContext.class);
        AiTaskResponse task = aiTaskResponse(taskId, analysisId, correlationId);

        when(analysisService.start(analysisId)).thenReturn(analysis);
        when(deterministicAnalysisService.analyze(analysisId))
                .thenReturn(new DeterministicAnalysisResult(4, 2));
        when(analysisContextService.build(analysisId)).thenReturn(context);
        when(aiTaskService.create(
                new CreateAiTaskRequest(analysisId, taskType),
                context
        )).thenReturn(task);

        AnalysisWorkflowResult result = workflowService.start(analysisId, taskType);

        assertEquals(analysisId, result.analysisId());
        assertEquals(AnalysisStatus.IN_PROGRESS, result.analysisStatus());
        assertEquals(4, result.factCount());
        assertEquals(2, result.observationCount());
        assertEquals(taskId, result.aiTaskId());
        assertEquals(AiTaskStatus.CREATED, result.aiTaskStatus());
        assertEquals(correlationId, result.correlationId());

        InOrder order = inOrder(
                analysisService,
                deterministicAnalysisService,
                analysisContextService,
                aiTaskService
        );
        order.verify(analysisService).start(analysisId);
        order.verify(deterministicAnalysisService).analyze(analysisId);
        order.verify(analysisContextService).build(analysisId);
        order.verify(aiTaskService).create(
                new CreateAiTaskRequest(analysisId, taskType),
                context
        );
        verify(analysisService, never()).fail(any());
    }

    @Test
    void shouldMarkAnalysisFailedWhenDeterministicStepFails() {
        UUID analysisId = UUID.randomUUID();
        RuntimeException failure = new RuntimeException("deterministic failure");
        when(analysisService.start(analysisId))
                .thenReturn(analysisResponse(analysisId, AnalysisStatus.IN_PROGRESS));
        when(deterministicAnalysisService.analyze(analysisId)).thenThrow(failure);

        RuntimeException result = assertThrows(
                RuntimeException.class,
                () -> workflowService.start(
                        analysisId,
                        AiTaskType.INSIGHT_GENERATION
                )
        );

        assertSame(failure, result);
        verify(analysisService).fail(analysisId);
        verifyNoInteractions(analysisContextService, aiTaskService);
    }

    @Test
    void shouldMarkAnalysisFailedWhenContextConstructionFails() {
        UUID analysisId = UUID.randomUUID();
        when(analysisService.start(analysisId))
                .thenReturn(analysisResponse(analysisId, AnalysisStatus.IN_PROGRESS));
        when(deterministicAnalysisService.analyze(analysisId))
                .thenReturn(new DeterministicAnalysisResult(1, 0));
        when(analysisContextService.build(analysisId))
                .thenThrow(new RuntimeException("context failure"));

        assertThrows(
                RuntimeException.class,
                () -> workflowService.start(
                        analysisId,
                        AiTaskType.DOCUMENTATION_GENERATION
                )
        );

        verify(analysisService).fail(analysisId);
        verifyNoInteractions(aiTaskService);
    }

    @Test
    void shouldMarkAnalysisFailedWhenAiTaskCreationFails() {
        UUID analysisId = UUID.randomUUID();
        AiTaskType taskType = AiTaskType.EVENT_PROPOSAL_GENERATION;
        AnalysisContext context = mock(AnalysisContext.class);
        when(analysisService.start(analysisId))
                .thenReturn(analysisResponse(analysisId, AnalysisStatus.IN_PROGRESS));
        when(deterministicAnalysisService.analyze(analysisId))
                .thenReturn(new DeterministicAnalysisResult(1, 1));
        when(analysisContextService.build(analysisId)).thenReturn(context);
        when(aiTaskService.create(
                new CreateAiTaskRequest(analysisId, taskType),
                context
        )).thenThrow(new RuntimeException("task failure"));

        assertThrows(
                RuntimeException.class,
                () -> workflowService.start(analysisId, taskType)
        );

        verify(analysisService).fail(analysisId);
    }

    @Test
    void shouldRejectDuplicateStartWithoutChangingAnalysisToFailed() {
        UUID analysisId = UUID.randomUUID();
        ConflictException conflict = new ConflictException("already started");
        when(analysisService.start(analysisId)).thenThrow(conflict);

        ConflictException result = assertThrows(
                ConflictException.class,
                () -> workflowService.start(
                        analysisId,
                        AiTaskType.INSIGHT_GENERATION
                )
        );

        assertSame(conflict, result);
        verify(analysisService, never()).fail(any());
        verifyNoInteractions(
                deterministicAnalysisService,
                analysisContextService,
                aiTaskService
        );
    }

    @Test
    void shouldPreserveOriginalFailureWhenFailureTransitionAlsoFails() {
        UUID analysisId = UUID.randomUUID();
        RuntimeException original = new RuntimeException("context failure");
        RuntimeException transition = new RuntimeException("transition failure");
        when(analysisService.start(analysisId))
                .thenReturn(analysisResponse(analysisId, AnalysisStatus.IN_PROGRESS));
        when(deterministicAnalysisService.analyze(analysisId))
                .thenReturn(new DeterministicAnalysisResult(0, 0));
        when(analysisContextService.build(analysisId)).thenThrow(original);
        when(analysisService.fail(analysisId)).thenThrow(transition);

        RuntimeException result = assertThrows(
                RuntimeException.class,
                () -> workflowService.start(
                        analysisId,
                        AiTaskType.INSIGHT_GENERATION
                )
        );

        assertSame(original, result);
        assertArrayEquals(new Throwable[]{transition}, result.getSuppressed());
    }

    private AnalysisResponse analysisResponse(UUID id, AnalysisStatus status) {
        return new AnalysisResponse(
                id,
                UUID.randomUUID(),
                AnalysisType.ARCHITECTURE_REVIEW,
                status,
                null,
                null,
                null,
                null
        );
    }

    private AiTaskResponse aiTaskResponse(
            UUID id,
            UUID analysisId,
            UUID correlationId
    ) {
        return new AiTaskResponse(
                id,
                analysisId,
                correlationId,
                AiTaskType.DECISION_PROPOSAL_GENERATION,
                AiTaskStatus.CREATED,
                null,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
