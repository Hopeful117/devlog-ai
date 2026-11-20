package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * Application-level response for Story Context Analysis, containing both
 * the AI-produced analysis result and the authoritative deterministic
 * freshness snapshot captured at context construction time.
 *
 * <p>The {@code analysis} field holds the AI-generated analysis payload.
 * The {@code contextFreshness} field holds the Core-owned deterministic
 * freshness metadata. These are independent pieces of information with
 * different ownership.</p>
 */
public record StoryContextAnalysisResponse(
        StoryContextAnalysisResult analysis,
        Map<String, Object> contextFreshness,
        String investigationStopReason,
        Map<String, Object> executionScope,
        Map<String, Object> provenance
) {
    public StoryContextAnalysisResponse {
        // Map.copyOf rejects legitimate null revisions in NO_BASELINE/UNKNOWN snapshots.
        contextFreshness = contextFreshness == null ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(contextFreshness));
        executionScope = immutableNullable(executionScope);
        provenance = immutableNullable(provenance);
    }

    public StoryContextAnalysisResponse(StoryContextAnalysisResult analysis,
                                        Map<String, Object> contextFreshness) {
        this(analysis, contextFreshness, null, null, null);
    }

    public StoryContextAnalysisResponse(StoryContextAnalysisResult analysis,
                                        Map<String, Object> contextFreshness,
                                        String investigationStopReason) {
        this(analysis, contextFreshness, investigationStopReason, null, null);
    }

    private static Map<String, Object> immutableNullable(Map<String, Object> value) {
        return value == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
}
