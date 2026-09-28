package com.hopeful117.devlogai.ai.interactiontrace.service;

import com.hopeful117.devlogai.ai.engine.dto.AiInteractionTraceRequest;
import com.hopeful117.devlogai.ai.interactiontrace.entity.AiInteractionTrace;
import com.hopeful117.devlogai.ai.interactiontrace.repository.AiInteractionTraceRepository;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiInteractionTracePersistenceService {
    private final AiInteractionTraceRepository repository;

    @Value("${devlog.ai-interaction-trace.level:NORMAL}")
    private String configuredLevel;

    @Value("${devlog.ai-interaction-trace.diagnostic-retention:7d}")
    private Duration diagnosticRetention;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(AiTask task, List<AiInteractionTraceRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        boolean diagnostic = "DIAGNOSTIC".equalsIgnoreCase(configuredLevel);
        Instant expiresAt = diagnostic ? Instant.now().plus(diagnosticRetention) : null;
        for (AiInteractionTraceRequest request : requests) {
            if (repository.existsById(request.id())) continue;
            repository.save(toEntity(task, request, diagnostic, expiresAt));
        }
    }

    private AiInteractionTrace toEntity(AiTask task, AiInteractionTraceRequest request,
                                        boolean diagnostic, Instant expiresAt) {
        return AiInteractionTrace.builder()
                .id(request.id())
                .aiTask(task)
                .analysis(task.getAnalysis())
                .correlationId(task.getCorrelationId())
                .attempt(request.attempt())
                .interactionType(request.interactionType())
                .traceLevel(diagnostic ? "DIAGNOSTIC" : "NORMAL")
                .provider(request.provider())
                .modelIdentifier(request.modelIdentifier())
                .intent(request.intent())
                .intentVersion(request.intentVersion())
                .promptVersion(request.promptVersion())
                .startedAt(request.startedAt())
                .completedAt(request.completedAt())
                .durationMs(request.durationMs())
                .validationStatus(request.validationStatus())
                .failureCategory(request.failureCategory())
                .retryReason(request.retryReason())
                .selectedKnowledgeFingerprint(request.selectedKnowledgeFingerprint())
                .promptFingerprint(request.promptFingerprint())
                .selectedFactCount(request.selectedFactCount())
                .selectedObservationCount(request.selectedObservationCount())
                .selectedInsightCount(request.selectedInsightCount())
                .selectedEngineeringEventCount(request.selectedEngineeringEventCount())
                .groundingFingerprint(request.groundingFingerprint())
                .inputTokens(request.inputTokens())
                .outputTokens(request.outputTokens())
                .totalTokens(request.totalTokens())
                .traceId(request.traceId())
                .spanId(request.spanId())
                .systemPrompt(diagnostic ? request.systemPrompt() : null)
                .userPrompt(diagnostic ? request.userPrompt() : null)
                .rawModelResponse(diagnostic ? request.rawModelResponse() : null)
                .parsedModelResponse(diagnostic ? request.parsedModelResponse() : null)
                .validationDiagnostics(diagnostic ? request.validationDiagnostics() : null)
                .expiresAt(expiresAt)
                .build();
    }
}
