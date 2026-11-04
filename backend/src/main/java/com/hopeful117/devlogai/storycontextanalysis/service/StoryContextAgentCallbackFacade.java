package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.ContextLossLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Dedicated SCA transport facade. Domain callback validation remains shared Core logic. */
@Service
@RequiredArgsConstructor
public class StoryContextAgentCallbackFacade {
    private final AiTaskRepository aiTaskRepository;
    private final AiTaskResultService aiTaskResultService;
    private final StoryContextAgentMetrics metrics;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiTaskResultAcknowledgement callback(UUID aiTaskId, AiTaskResultRequest request) {
        request.validateCallbackContract();
        request.interactionTraces().forEach(trace -> {
            if ("PROVIDER_INCOMPLETE".equals(trace.failureCategory())) {
                metrics.increment("sca_provider_incomplete_total");
            } else if ("PARSING_FAILED".equals(trace.validationStatus())) {
                metrics.increment("sca_parsing_failure_total");
            } else if ("VALIDATION_FAILED".equals(trace.validationStatus())) {
                metrics.increment("sca_validation_failure_total");
            }
        });
        AiTask task = aiTaskRepository.findById(aiTaskId)
                .orElseThrow(() -> new EntityNotFoundException("AI task", aiTaskId));
        if (task.getId() == null || !task.getId().equals(aiTaskId)) {
            metrics.increment("sca_digest_mismatch_total");
            throw new IllegalArgumentException("snapshotId must equal aiTaskId");
        }
        try {
            AiTaskResultAcknowledgement acknowledgement =
                    aiTaskResultService.handle(task.getCorrelationId(), request);
            updateLossAccounting(task, request);
            aiTaskRepository.save(task);
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

    @SuppressWarnings("unchecked")
    private void updateLossAccounting(AiTask task, AiTaskResultRequest request) {
        Map<String, Object> snapshot = task.getContextSnapshot();
        if (snapshot == null || !(snapshot.get("lossAccounting") instanceof Map<?, ?> rawLedger)) return;
        Map<String, Object> ledgerMap = new LinkedHashMap<>();
        rawLedger.forEach((key, value) -> {
            if (key instanceof String stringKey) ledgerMap.put(stringKey, value);
        });
        ContextLossLedger ledger = objectMapper.convertValue(ledgerMap, ContextLossLedger.class);
        String status = request.status().name();
        boolean incomplete = request.interactionTraces().stream()
                .anyMatch(trace -> "PROVIDER_INCOMPLETE".equals(trace.failureCategory())
                        || "INCOMPLETE".equals(trace.providerStatus()));
        if (incomplete) status = "INCOMPLETE";
        // Interaction traces prove provider execution, not which projected references
        // were visible. Keep per-reference visibility explicitly unknown until the
        // callback contract carries that attribution.
        int visibleCount = 0;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("traceCount", request.interactionTraces().size());
        details.put("visibilityEvidence", "NOT_EXPOSED_BY_CALLBACK_CONTRACT");
        details.put("validationStatuses", request.interactionTraces().stream()
                .map(trace -> trace.validationStatus()).distinct().sorted().toList());
        details.put("incompleteReasons", request.interactionTraces().stream()
                .map(trace -> trace.incompleteReason()).filter(value -> value != null && !value.isBlank())
                .distinct().sorted().toList());
        ContextLossLedger updated = ledger.withProviderOutcome(status, visibleCount, details, objectMapper);
        snapshot.put("lossAccounting", objectMapper.convertValue(updated, Map.class));
        snapshot.put("providerOutcome", updated.providerOutcome());
    }

}
