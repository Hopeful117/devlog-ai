package com.hopeful117.devlogai.ai.engine.service;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.engine.exception.InvalidAiTaskResultException;
import com.hopeful117.devlogai.ai.engine.repository.AiTaskCallbackRejectionRepository;
import com.hopeful117.devlogai.ai.reference.AiReferenceResolutionException;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.engine.entity.AiTaskCallbackRejection;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTaskCallbackRejectionService {
    private static final int MAX_MESSAGE_LENGTH = 5000;

    private final AiTaskRepository aiTaskRepository;
    private final AiTaskCallbackRejectionRepository rejectionRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID aiTaskId, AiTaskResultRequest request, RuntimeException failure) {
        AiTask task = aiTaskRepository.findByIdForUpdate(aiTaskId).orElse(null);
        String errorCode = errorCode(failure);
        String message = bounded(failure.getMessage());
        String currentStatus = task == null ? null : task.getStatus().name();
        String resultStatus = request == null || request.status() == null
                ? null : request.status().name();
        int httpStatus = httpStatus(failure);
        int traceCount = request == null || request.interactionTraces() == null
                ? 0 : request.interactionTraces().size();

        rejectionRepository.save(AiTaskCallbackRejection.builder()
                .id(UUID.randomUUID())
                .aiTaskId(aiTaskId)
                .correlationId(request == null ? null : request.correlationId())
                .resultStatus(resultStatus)
                .httpStatus(httpStatus)
                .errorCode(errorCode)
                .currentStatus(currentStatus)
                .errorMessage(message)
                .interactionTraceCount(traceCount)
                .providerGenerationCount(request == null ? null : request.investigationProviderGenerationCount())
                .providerElapsedMillis(request == null ? null : request.investigationProviderElapsedMillis())
                .providerOutputTokens(request == null ? null : request.investigationProviderOutputTokens())
                .receivedAt(Instant.now())
                .build());

        if (task != null) {
            task.setLastCallbackFailureCode(errorCode);
            task.setLastCallbackFailureStatus(currentStatus);
            task.setLastCallbackFailureMessage(message);
            task.setLastCallbackFailureAt(Instant.now());
            if (isPermanentCallbackRejection(failure) && !task.getStatus().isTerminal()) {
                task.setStatus(AiTaskStatus.FAILED);
                task.setFailureCode(errorCode);
                task.setFailureMessage(message);
                task.setTerminalReason("PERMANENT_CALLBACK_REJECTION");
                task.setRecoveryAction("CALLBACK_REJECTION");
                task.setCompletedAt(Instant.now());
            }
            aiTaskRepository.save(task);
        }

        log.warn("AI task callback rejected taskId={} correlationId={} code={} httpStatus={} currentStatus={} resultStatus={} traceCount={}",
                aiTaskId, request == null ? null : request.correlationId(), errorCode,
                httpStatus, currentStatus, resultStatus, traceCount);
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof AiTaskResultConflictException conflict) return conflict.getCode();
        if (failure instanceof AiReferenceResolutionException reference) return reference.code();
        if (failure instanceof InvalidAiTaskResultException) return "INVALID_AI_TASK_RESULT";
        return failure.getClass().getSimpleName();
    }

    private int httpStatus(RuntimeException failure) {
        return failure instanceof AiTaskResultConflictException ? 409 : 400;
    }

    private boolean isPermanentCallbackRejection(RuntimeException failure) {
        // State conflicts are retry/duplicate signals. Contract and domain
        // validation failures cannot become valid by repeating the callback.
        return failure instanceof InvalidAiTaskResultException
                || failure instanceof AiReferenceResolutionException
                || (failure instanceof ConflictException
                && !(failure instanceof AiTaskResultConflictException));
    }

    private String bounded(String value) {
        if (value == null) return null;
        return value.length() <= MAX_MESSAGE_LENGTH ? value : value.substring(0, MAX_MESSAGE_LENGTH);
    }
}
