package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.entity.AiTaskCallbackRejection;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.engine.repository.AiTaskCallbackRejectionRepository;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolutionException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskCallbackRejectionServiceTest {
    @Mock
    private AiTaskRepository aiTaskRepository;

    @Mock
    private AiTaskCallbackRejectionRepository rejectionRepository;

    @InjectMocks
    private AiTaskCallbackRejectionService service;

    @Test
    void shouldTerminalizeReferenceResolutionFailure() {
        UUID taskId = UUID.randomUUID();
        AiTask task = AiTask.builder().status(AiTaskStatus.PROCESSING).build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.record(taskId, null,
                new AiReferenceResolutionException("REFERENCE_NOT_AUTHORIZED", "Reference is not authorized"));

        assertEquals(AiTaskStatus.FAILED, task.getStatus());
        assertEquals("REFERENCE_NOT_AUTHORIZED", task.getFailureCode());
        assertEquals("PERMANENT_CALLBACK_REJECTION", task.getTerminalReason());
        verify(aiTaskRepository).save(task);
        verify(rejectionRepository).save(any(AiTaskCallbackRejection.class));
    }

    @Test
    void shouldKeepStateConflictRetryable() {
        UUID taskId = UUID.randomUUID();
        AiTask task = AiTask.builder().status(AiTaskStatus.SUBMITTED).build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.record(taskId, null,
                new AiTaskResultConflictException("AI_TASK_NOT_READY", AiTaskStatus.SUBMITTED, "Not ready"));

        assertEquals(AiTaskStatus.SUBMITTED, task.getStatus());
        verify(aiTaskRepository).save(task);
    }
}
