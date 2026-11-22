package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Applies one timeout transition in an isolated transaction. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskTimeoutTransitionService {
    private final AiTaskRepository aiTaskRepository;
    private final AnalysisRepository analysisRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void timeoutIfExpired(UUID taskId, Instant now) {
        AiTask task = aiTaskRepository.findByIdForUpdate(taskId).orElse(null);
        if (task == null || task.getStatus().isTerminal()
                || task.getDeadlineAt() == null || task.getDeadlineAt().isAfter(now)) {
            return;
        }

        Analysis analysis = task.getAnalysis() == null ? null
                : analysisRepository.findByIdForUpdate(task.getAnalysis().getId()).orElse(null);
        if (analysis != null && (analysis.getStatus() == AnalysisStatus.PENDING
                || analysis.getStatus() == AnalysisStatus.IN_PROGRESS)) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setCompletedAt(now);
            analysisRepository.save(analysis);
        }

        task.setStatus(AiTaskStatus.TIMED_OUT);
        task.setFailureCode("AI_TASK_DEADLINE_EXCEEDED");
        task.setFailureMessage("AI task exceeded its Core-owned execution deadline");
        task.setTerminalReason("CORE_DEADLINE_EXCEEDED");
        task.setRecoveryAction("WATCHDOG_TIMEOUT");
        task.setCompletedAt(now);
        aiTaskRepository.save(task);
        log.warn("AI task timed out taskId={} deadlineAt={}", taskId, task.getDeadlineAt());
    }
}
