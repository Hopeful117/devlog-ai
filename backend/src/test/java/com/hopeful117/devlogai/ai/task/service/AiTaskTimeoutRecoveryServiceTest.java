package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiTaskTimeoutRecoveryServiceTest {
    @Mock
    private AiTaskRepository aiTaskRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @InjectMocks
    private AiTaskTimeoutTransitionService service;

    @Test
    void shouldTerminalizeExpiredSubmittedTask() {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-22T20:00:00Z");
        AiTask task = AiTask.builder()
                .status(AiTaskStatus.SUBMITTED)
                .deadlineAt(now.minusSeconds(1))
                .build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.timeoutIfExpired(taskId, now);

        assertEquals(AiTaskStatus.TIMED_OUT, task.getStatus());
        assertEquals("AI_TASK_DEADLINE_EXCEEDED", task.getFailureCode());
        assertEquals("CORE_DEADLINE_EXCEEDED", task.getTerminalReason());
        assertEquals("WATCHDOG_TIMEOUT", task.getRecoveryAction());
        assertEquals(now, task.getCompletedAt());
        verify(aiTaskRepository).save(task);
    }

    @Test
    void shouldNotTimeoutTaskBeforeDeadline() {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-22T20:00:00Z");
        AiTask task = AiTask.builder()
                .status(AiTaskStatus.PROCESSING)
                .deadlineAt(now.plusSeconds(1))
                .build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.timeoutIfExpired(taskId, now);

        assertEquals(AiTaskStatus.PROCESSING, task.getStatus());
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreAlreadyTerminalTaskForIdempotentRecovery() {
        UUID taskId = UUID.randomUUID();
        Instant now = Instant.now(Clock.fixed(Instant.parse("2026-11-22T20:00:00Z"), ZoneOffset.UTC));
        AiTask task = AiTask.builder()
                .status(AiTaskStatus.COMPLETED)
                .deadlineAt(now.minusSeconds(1))
                .build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));

        service.timeoutIfExpired(taskId, now);

        assertEquals(AiTaskStatus.COMPLETED, task.getStatus());
        verify(aiTaskRepository, never()).save(any());
    }

    @Test
    void shouldFailActiveAnalysisWithTimedOutTask() {
        UUID taskId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-22T20:00:00Z");
        Analysis analysis = Analysis.builder()
                .id(analysisId)
                .status(AnalysisStatus.IN_PROGRESS)
                .build();
        AiTask task = AiTask.builder()
                .analysis(analysis)
                .status(AiTaskStatus.PROCESSING)
                .deadlineAt(now.minusSeconds(1))
                .build();
        when(aiTaskRepository.findByIdForUpdate(taskId)).thenReturn(Optional.of(task));
        when(analysisRepository.findByIdForUpdate(analysisId)).thenReturn(Optional.of(analysis));

        service.timeoutIfExpired(taskId, now);

        assertEquals(AiTaskStatus.TIMED_OUT, task.getStatus());
        assertEquals(AnalysisStatus.FAILED, analysis.getStatus());
        assertEquals(now, analysis.getCompletedAt());
        verify(analysisRepository).save(analysis);
    }
}
