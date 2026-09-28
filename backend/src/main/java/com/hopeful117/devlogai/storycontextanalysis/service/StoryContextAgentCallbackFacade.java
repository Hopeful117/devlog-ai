package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Dedicated SCA transport facade. Domain callback validation remains shared Core logic. */
@Service
@RequiredArgsConstructor
public class StoryContextAgentCallbackFacade {
    private final AiTaskRepository aiTaskRepository;
    private final AiTaskResultService aiTaskResultService;
    private final StoryContextAgentMetrics metrics;

    @Transactional
    public AiTaskResultAcknowledgement callback(UUID aiTaskId, AiTaskResultRequest request) {
        AiTask task = aiTaskRepository.findById(aiTaskId)
                .orElseThrow(() -> new EntityNotFoundException("AI task", aiTaskId));
        if (task.getId() == null || !task.getId().equals(aiTaskId)) {
            metrics.increment("sca_digest_mismatch_total");
            throw new IllegalArgumentException("snapshotId must equal aiTaskId");
        }
        try {
            AiTaskResultAcknowledgement acknowledgement =
                    aiTaskResultService.handle(task.getCorrelationId(), request);
            if (acknowledgement.duplicate()) {
                metrics.increment("sca_idempotent_retry_total");
                metrics.increment("sca_duplicate_terminal_total");
            }
            return acknowledgement;
        } catch (RuntimeException exception) {
            String message = exception.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("digest")) {
                metrics.increment("sca_digest_mismatch_total");
            }
            if (message != null && (message.contains("grounding") || message.contains("evidence"))) {
                metrics.increment("sca_grounding_rejection_total");
            }
            throw exception;
        }
    }
}
