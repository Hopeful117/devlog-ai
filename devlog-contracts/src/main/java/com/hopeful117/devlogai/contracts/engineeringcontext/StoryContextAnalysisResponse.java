package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.Map;

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
        Map<String, Object> contextFreshness
) {
    public StoryContextAnalysisResponse {
        contextFreshness = contextFreshness != null ? Map.copyOf(contextFreshness) : null;
    }
}
