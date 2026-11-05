package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.ContextLossLedger;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

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
        accountFinalInvestigationGeneration(task, request);
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
    private void accountFinalInvestigationGeneration(AiTask task, AiTaskResultRequest request) {
        if (task.getStatus().isTerminal()) {
            return;
        }
        Map<String, Object> snapshot = task.getContextSnapshot();
        if (snapshot == null || !(snapshot.get("investigation") instanceof Map<?, ?> raw)) return;
        Map<String, Object> investigation = new LinkedHashMap<>();
        raw.forEach((key, value) -> { if (key instanceof String name) investigation.put(name, value); });
        if (!Boolean.TRUE.equals(investigation.get("initialized"))) return;
        Map<String, Object> usage = map(investigation.get("usage"));
        Map<String, Object> budget = map(investigation.get("budget"));
        int accounted = Math.toIntExact(number(investigation, "accountedProviderGenerationCount"));
        if (accounted < 0 || accounted > request.interactionTraces().size()) {
            throw new ConflictException("Investigation provider generation accounting boundary is invalid");
        }
        if (request.investigationProviderGenerationCount() == null
                || request.investigationProviderGenerationCount() != request.interactionTraces().size()) {
            throw new ConflictException("Investigation provider generation count is incomplete");
        }
        long accountedProviderElapsed = number(investigation, "accountedProviderElapsedMillis");
        long accountedProviderOutput = number(usage, "providerOutputTokens");
        if (request.investigationProviderElapsedMillis() == null
                || request.investigationProviderOutputTokens() == null
                || request.investigationProviderElapsedMillis() < accountedProviderElapsed
                || request.investigationProviderOutputTokens() < accountedProviderOutput) {
            throw new ConflictException("Investigation provider accounting totals are incomplete");
        }
        long outputTokens = 0;
        long elapsed = 0;
        for (int index = accounted; index < request.interactionTraces().size(); index++) {
            var trace = request.interactionTraces().get(index);
            if (trace.outputTokens() == null) {
                throw new ConflictException("Investigation provider output token measurement is missing");
            }
            outputTokens = add(outputTokens, trace.outputTokens());
            elapsed = add(elapsed, trace.durationMs());
        }
        if (outputTokens != request.investigationProviderOutputTokens() - accountedProviderOutput
                || elapsed != request.investigationProviderElapsedMillis() - accountedProviderElapsed) {
            throw new ConflictException("Investigation provider accounting does not match interaction traces");
        }
        long providerOutput = request.investigationProviderOutputTokens();
        long elapsedTotal = add(number(usage, "elapsedMillis"), elapsed);
        if (providerOutput > number(budget, "maximumProviderOutputTokens")
                || elapsedTotal > number(budget, "maximumElapsedMillis")) {
            throw new ConflictException("Investigation final provider generation exceeds the remaining budget");
        }
        usage.put("providerOutputTokens", providerOutput);
        usage.put("elapsedMillis", elapsedTotal);
        investigation.put("accountedProviderGenerationCount", request.interactionTraces().size());
        investigation.put("accountedProviderElapsedMillis", request.investigationProviderElapsedMillis());
        if (request.investigationStopReason() == null
                || !Set.of("COMPLETED", "SUFFICIENT_EVIDENCE", "BUDGET_EXHAUSTED",
                "NO_RELEVANT_EVIDENCE", "REVISION_INVALID", "ACTION_REJECTED",
                "PROVIDER_INCOMPLETE", "VALIDATION_FAILED").contains(request.investigationStopReason())) {
            throw new ConflictException("Investigation final stop reason is required");
        }
        investigation.put("stopReason", request.investigationStopReason());
        investigation.put("usage", usage);
        snapshot.put("investigation", investigation);
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> { if (key instanceof String name) result.put(name, item); });
        return result;
    }

    private long number(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof Number number ? number.longValue() : 0;
    }

    private long add(long left, long right) {
        try { return Math.addExact(left, right); }
        catch (ArithmeticException exception) { throw new ConflictException("Investigation budget accounting overflowed"); }
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
