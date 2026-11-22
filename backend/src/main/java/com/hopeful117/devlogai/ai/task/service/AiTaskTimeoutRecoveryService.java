package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Terminalizes Core tasks that outlive their persisted execution deadline. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskTimeoutRecoveryService {
    private static final List<AiTaskStatus> RECOVERABLE_STATUSES =
            List.of(AiTaskStatus.SUBMITTED, AiTaskStatus.PROCESSING);

    private final AiTaskRepository aiTaskRepository;
    private final Clock clock;
    private final AiTaskTimeoutTransitionService transitionService;

    @Value("${ai-task.recovery-batch-size:100}")
    private int recoveryBatchSize = 100;

    @Scheduled(fixedDelayString = "${ai-task.recovery-delay-ms:30000}")
    public void recoverExpiredTasks() {
        Instant now = clock.instant();
        aiTaskRepository.findIdsWithExpiredDeadline(
                        RECOVERABLE_STATUSES, now, PageRequest.of(0, Math.max(1, recoveryBatchSize)))
                .stream()
                .forEach(taskId -> {
                    try {
                        transitionService.timeoutIfExpired(taskId, now);
                    } catch (RuntimeException exception) {
                        log.error("AI task timeout recovery failed taskId={}", taskId, exception);
                    }
                });
    }

    public void timeoutIfExpired(UUID taskId, Instant now) {
        transitionService.timeoutIfExpired(taskId, now);
    }
}
