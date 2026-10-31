package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/** Performs the deterministic, fail-closed budget check before provider submission. */
public final class StoryContextBudgetPreflight {
    private static final int SAFETY_MARGIN_TOKENS = 256;

    private final AiProviderCatalogService catalog;
    private final ObjectMapper objectMapper;

    public StoryContextBudgetPreflight(AiProviderCatalogService catalog, ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.objectMapper = objectMapper;
    }

    public Result check(String provider, String model, Map<String, Object> projection,
                        Map<String, Object> allowedParameters) {
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
        int outputLimit = maxOutputTokens(allowedParameters);
        int inputEstimate;
        try {
            inputEstimate = Math.max(1, objectMapper.writeValueAsString(projection).length() / 4);
        } catch (Exception exception) {
            return Result.rejected("BUDGET_INPUT_MEASUREMENT_UNKNOWN", "Prompt input size could not be measured");
        }
        long required = (long) inputEstimate + outputLimit + SAFETY_MARGIN_TOKENS;
        if (required > capacity) {
            return Result.rejected("BUDGET_CAPACITY_INSUFFICIENT", "Configured model capacity is insufficient");
        }
        return new Result(true, null, null, inputEstimate, outputLimit, capacity);
    }

    private static int maxOutputTokens(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("maxOutputTokens");
        if (value instanceof Number number && number.intValue() >= 256) return number.intValue();
        return 2_000;
    }

    public record Result(boolean accepted, String code, String message, int inputEstimate,
                         int outputLimit, int capacity) {
        static Result rejected(String code, String message) {
            return new Result(false, code, message, -1, -1, -1);
        }
    }
}
