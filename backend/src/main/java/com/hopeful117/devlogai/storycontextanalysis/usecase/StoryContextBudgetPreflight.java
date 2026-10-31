package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicy;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicyResolver;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextComplexityClass;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.Map;

/** Performs the deterministic, fail-closed budget check before provider submission. */
public final class StoryContextBudgetPreflight {
    private static final int SAFETY_MARGIN_TOKENS = 256;
    private static final int PROMPT_POLICY_OVERHEAD_TOKENS = 4_096;
    public static final String PROMPT_POLICY_VERSION = "story-context-prompt-budget-v1";

    private final AiProviderCatalogService catalog;
    private final ObjectMapper objectMapper;
    private final StoryContextCompletionPolicyResolver policyResolver;

    public StoryContextBudgetPreflight(AiProviderCatalogService catalog, ObjectMapper objectMapper,
                                       StoryContextCompletionPolicyResolver policyResolver) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.policyResolver = policyResolver;
    }

    public Result check(PromptRequest prompt) {
        if (prompt == null) {
            return Result.rejected("BUDGET_INPUT_MEASUREMENT_UNKNOWN", "Prompt budget input is unavailable");
        }
        Map<String, Object> projection = prompt.selectedKnowledge();
        String catalogProvider = "chatgpt".equals(prompt.provider()) ? "openai_chatgpt" : prompt.provider();
        StoryContextComplexityClass complexity = complexity(prompt);
        Optional<StoryContextCompletionPolicy> policy = policyResolver.resolve(
                catalogProvider, prompt.modelIdentifier(), prompt.intent().id(), complexity);
        if (policy.isEmpty()) {
            return Result.rejected("BUDGET_COMPLETION_POLICY_UNKNOWN",
                    "No active measured Story Context completion policy is configured");
        }
        Result base = checkCapacity(catalogProvider, prompt.modelIdentifier(), prompt.allowedParameters(),
                projection, prompt, complexity, policy.get());
        return base;
    }

    private Result checkCapacity(String provider, String model, Map<String, Object> allowedParameters,
                                 Map<String, Object> projection, PromptRequest prompt,
                                 StoryContextComplexityClass complexity,
                                 StoryContextCompletionPolicy policy) {
        if (provider == null || provider.isBlank() || model == null || model.isBlank()) {
            return Result.rejected("BUDGET_CAPACITY_UNKNOWN", "Model capacity is not configured");
        }
        Integer capacity;
        try {
            capacity = catalog.contextCapacityTokens(provider, model);
        } catch (EntityNotFoundException exception) {
            return Result.rejected("BUDGET_CAPACITY_UNKNOWN", "Model capacity is not configured");
        }
        if (capacity == null) {
            return Result.rejected("BUDGET_CAPACITY_UNKNOWN", "Model capacity is not configured");
        }
        OutputLimit output = maxOutputTokens(allowedParameters, policy);
        if (!output.valid()) {
            return Result.rejected(output.code(), output.message(), -1, output.value(), capacity,
                    policy, complexity, output.parameters(), output.source());
        }
        if (output.value() < policy.getMinimumOutputTokens()) {
            return Result.rejected("BUDGET_COMPLETION_ALLOWANCE_INSUFFICIENT",
                    "Configured output allowance is below the Story Context completion policy",
                    -1, output.value(), capacity, policy, complexity,
                    output.parameters(), output.source());
        }
        int inputEstimate;
        try {
            int serializedCharacters = objectMapper.writeValueAsString(
                    prompt.withAllowedParameters(output.parameters())).length();
            inputEstimate = Math.max(1,
                    (serializedCharacters + 3) / 4 + PROMPT_POLICY_OVERHEAD_TOKENS);
        } catch (Exception exception) {
            return Result.rejected("BUDGET_INPUT_MEASUREMENT_UNKNOWN", "Prompt input size could not be measured",
                    -1, output.value(), capacity, policy, complexity,
                    output.parameters(), output.source());
        }
        int effectiveOutput = Math.min(output.value(), Math.min(policy.getTargetOutputTokens(),
                policy.getMaximumOutputTokens()));
        long required = (long) inputEstimate + effectiveOutput + SAFETY_MARGIN_TOKENS;
        if (required > capacity) {
            String code = inputEstimate + SAFETY_MARGIN_TOKENS >= capacity
                    ? "BUDGET_CAPACITY_INSUFFICIENT"
                    : "BUDGET_OUTPUT_ALLOWANCE_INSUFFICIENT";
            return Result.rejected(code, "Configured budget is insufficient for provider invocation",
                    inputEstimate, effectiveOutput, capacity, policy, complexity,
                    output.parameters(), output.source());
        }
        return new Result(true, projectionReduced(projection) ? "PROJECTION_REDUCED" : "AUTHORIZED",
                null, null, inputEstimate, effectiveOutput, capacity,
                policy.getMinimumOutputTokens(), policy.getPolicyVersion(), complexity,
                policy.getMeasurementCertainty(), policy.getTargetOutputTokens(),
                output.parameters(), output.source());
    }

    private static OutputLimit maxOutputTokens(Map<String, Object> parameters,
                                               StoryContextCompletionPolicy policy) {
        Object value = parameters == null ? null : parameters.get("maxOutputTokens");
        if (value == null) {
            int target = policy.getTargetOutputTokens();
            return new OutputLimit(true, target, null, null,
                    Map.of("maxOutputTokens", target), "CORE_POLICY");
        }
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            return new OutputLimit(false, -1, "BUDGET_OUTPUT_ALLOWANCE_INVALID",
                    "Configured output allowance is invalid", Map.of(), "USER_CONFIGURATION");
        }
        Number number = (Number) value;
        long tokens = number.longValue();
        if (tokens < 256 || tokens > 32_768) {
            return new OutputLimit(false, (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, tokens)),
                    "BUDGET_OUTPUT_ALLOWANCE_INVALID", "Configured output allowance is outside the supported range",
                    Map.of(), "USER_CONFIGURATION");
        }
        int effective = Math.min((int) tokens, policy.getTargetOutputTokens());
        String source = tokens < policy.getTargetOutputTokens()
                ? "CORE_POLICY_CAPPED_BY_USER" : "CORE_POLICY";
        return new OutputLimit(true, effective, null, null,
                Map.of("maxOutputTokens", effective), source);
    }

    private static boolean projectionReduced(Map<String, Object> projection) {
        Object accounting = projection == null ? null : projection.get("accounting");
        if (!(accounting instanceof Map<?, ?> values)) return false;
        return Boolean.TRUE.equals(values.get("truncated"));
    }

    private StoryContextComplexityClass complexity(PromptRequest prompt) {
        if (projectionReduced(prompt.selectedKnowledge())) {
            return StoryContextComplexityClass.REDUCED;
        }
        Object fields = prompt.expectedOutputContract().get("requiredAnalysisFields");
        if (fields instanceof java.util.Collection<?> collection && collection.size() >= 10) {
            return StoryContextComplexityClass.COMPLEX;
        }
        return StoryContextComplexityClass.STANDARD;
    }

    public record Result(boolean accepted, String outcome, String code, String message, int inputEstimate,
                         int outputLimit, int capacity, int requiredOutputLimit, String policyVersion,
                         StoryContextComplexityClass complexityClass, String measurementCertainty,
                         int targetOutputLimit, Map<String, Object> effectiveParameters,
                         String outputLimitSource) {
        static Result rejected(String code, String message) {
            return new Result(false, "REJECTED", code, message, -1, -1, -1, 0,
                    null, null, null, -1, Map.of(), "UNKNOWN");
        }

        static Result rejected(String code, String message, int inputEstimate,
                               int outputLimit, int capacity, StoryContextCompletionPolicy policy,
                               StoryContextComplexityClass complexity,
                               Map<String, Object> effectiveParameters, String outputLimitSource) {
            return new Result(false, "REJECTED", code, message, inputEstimate, outputLimit, capacity,
                    policy.getMinimumOutputTokens(), policy.getPolicyVersion(), complexity,
                    policy.getMeasurementCertainty(), policy.getTargetOutputTokens(),
                    effectiveParameters, outputLimitSource);
        }
    }

    private record OutputLimit(boolean valid, int value, String code, String message,
                               Map<String, Object> parameters, String source) { }
}
