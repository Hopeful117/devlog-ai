package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.mapper.AiTaskMapper;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskServiceTest {

    @Mock
    private AiTaskRepository aiTaskRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AnalysisContextService analysisContextService;

    @Mock
    private AiTaskMapper aiTaskMapper;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AiTaskServiceImpl aiTaskService;

    @Test
    void shouldCreateTaskWithImmutableContextSnapshot() {
        UUID analysisId = UUID.randomUUID();
        CreateAiTaskRequest request = new CreateAiTaskRequest(
                analysisId,
                AiTaskType.DECISION_PROPOSAL_GENERATION
        );
        Analysis analysis = new Analysis();
        AnalysisContext context = mock(AnalysisContext.class);
        Map<String, Object> snapshot = Map.of("analysis", Map.of("id", analysisId));
        AiTask task = new AiTask();
        AiTaskResponse response = mock(AiTaskResponse.class);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(analysisContextService.build(analysisId)).thenReturn(context);
        when(objectMapper.convertValue(context, Map.class)).thenReturn(snapshot);
        when(aiTaskMapper.toEntity(request)).thenReturn(task);
        when(aiTaskRepository.save(task)).thenReturn(task);
        when(aiTaskMapper.toResponse(task)).thenReturn(response);

        AiTaskResponse result = aiTaskService.create(request);

        assertSame(response, result);
        assertSame(analysis, task.getAnalysis());
        assertNotNull(task.getCorrelationId());
        assertEquals(AiTaskStatus.CREATED, task.getStatus());
        assertSame(snapshot, task.getContextSnapshot());
        assertEquals(0, task.getAttemptCount());
        assertNull(task.getExternalJobId());
        assertNull(task.getFailureCode());
        assertNull(task.getFailureMessage());
        assertNull(task.getSubmittedAt());
        assertNull(task.getStartedAt());
        assertNull(task.getCompletedAt());
        verify(analysisContextService).build(analysisId);
        verify(aiTaskRepository).save(task);
    }

    @Test
    void shouldRejectCreationWhenAnalysisDoesNotExist() {
        UUID analysisId = UUID.randomUUID();
        CreateAiTaskRequest request = new CreateAiTaskRequest(
                analysisId,
                AiTaskType.INSIGHT_GENERATION
        );
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> aiTaskService.create(request));

        verifyNoInteractions(analysisContextService, objectMapper, aiTaskMapper);
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void shouldFindTaskByCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        AiTask task = new AiTask();
        AiTaskResponse response = mock(AiTaskResponse.class);
        when(aiTaskRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.of(task));
        when(aiTaskMapper.toResponse(task)).thenReturn(response);

        assertSame(response, aiTaskService.getByCorrelationId(correlationId));
    }

    @Test
    void shouldRejectUnknownCorrelationId() {
        UUID correlationId = UUID.randomUUID();
        when(aiTaskRepository.findByCorrelationId(correlationId))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> aiTaskService.getByCorrelationId(correlationId)
        );
        verifyNoInteractions(aiTaskMapper);
    }

    @Test
    void shouldReturnTasksUsingRepositoryDeterministicOrder() {
        UUID analysisId = UUID.randomUUID();
        AiTask first = new AiTask();
        AiTask second = new AiTask();
        AiTaskResponse firstResponse = mock(AiTaskResponse.class);
        AiTaskResponse secondResponse = mock(AiTaskResponse.class);
        when(aiTaskRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(List.of(first, second));
        when(aiTaskMapper.toResponse(first)).thenReturn(firstResponse);
        when(aiTaskMapper.toResponse(second)).thenReturn(secondResponse);

        List<AiTaskResponse> result = aiTaskService.getByAnalysisId(analysisId);

        assertEquals(List.of(firstResponse, secondResponse), result);
    }

    @Test
    void shouldFollowSuccessfulLifecycle() {
        UUID id = UUID.randomUUID();
        AiTask task = AiTask.builder()
                .status(AiTaskStatus.CREATED)
                .attemptCount(0)
                .build();
        AiTaskResponse response = mock(AiTaskResponse.class);
        when(aiTaskRepository.findById(id)).thenReturn(Optional.of(task));
        when(aiTaskRepository.save(task)).thenReturn(task);
        when(aiTaskMapper.toResponse(task)).thenReturn(response);

        assertSame(
                response,
                aiTaskService.submit(id, new SubmitAiTaskRequest("engine-job-42"))
        );
        assertEquals(AiTaskStatus.SUBMITTED, task.getStatus());
        assertEquals("engine-job-42", task.getExternalJobId());
        assertEquals(1, task.getAttemptCount());
        assertNotNull(task.getSubmittedAt());

        assertSame(response, aiTaskService.startProcessing(id));
        assertEquals(AiTaskStatus.PROCESSING, task.getStatus());
        assertNotNull(task.getStartedAt());

        assertSame(response, aiTaskService.complete(id));
        assertEquals(AiTaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getCompletedAt());
        verify(aiTaskRepository, times(3)).save(task);
    }

    @Test
    void shouldRecordProcessingFailure() {
        UUID id = UUID.randomUUID();
        AiTask task = AiTask.builder().status(AiTaskStatus.PROCESSING).build();
        AiTaskResponse response = mock(AiTaskResponse.class);
        when(aiTaskRepository.findById(id)).thenReturn(Optional.of(task));
        when(aiTaskRepository.save(task)).thenReturn(task);
        when(aiTaskMapper.toResponse(task)).thenReturn(response);

        AiTaskResponse result = aiTaskService.fail(
                id,
                new FailAiTaskRequest("ENGINE_TIMEOUT", "AI Engine did not respond")
        );

        assertSame(response, result);
        assertEquals(AiTaskStatus.FAILED, task.getStatus());
        assertEquals("ENGINE_TIMEOUT", task.getFailureCode());
        assertEquals("AI Engine did not respond", task.getFailureMessage());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void shouldRecordSubmissionFailureFromCreatedState() {
        UUID id = UUID.randomUUID();
        AiTask task = AiTask.builder().status(AiTaskStatus.CREATED).build();
        AiTaskResponse response = mock(AiTaskResponse.class);
        when(aiTaskRepository.findById(id)).thenReturn(Optional.of(task));
        when(aiTaskRepository.save(task)).thenReturn(task);
        when(aiTaskMapper.toResponse(task)).thenReturn(response);

        AiTaskResponse result = aiTaskService.failSubmission(
                id,
                new FailAiTaskRequest(
                        "AI_ENGINE_SUBMISSION_FAILED",
                        "Connection refused"
                )
        );

        assertSame(response, result);
        assertEquals(AiTaskStatus.FAILED, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertEquals("AI_ENGINE_SUBMISSION_FAILED", task.getFailureCode());
        assertEquals("Connection refused", task.getFailureMessage());
        assertNotNull(task.getCompletedAt());
    }

    @Test
    void shouldRejectInvalidTransitionWithoutPersisting() {
        UUID id = UUID.randomUUID();
        AiTask task = AiTask.builder().status(AiTaskStatus.SUBMITTED).build();
        when(aiTaskRepository.findById(id)).thenReturn(Optional.of(task));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> aiTaskService.complete(id)
        );

        assertTrue(exception.getMessage().contains("SUBMITTED"));
        assertTrue(exception.getMessage().contains("COMPLETED"));
        verify(aiTaskRepository, never()).save(any());
        verifyNoInteractions(aiTaskMapper);
    }
}
