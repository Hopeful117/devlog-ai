package com.hopeful117.devlogai.ai.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record PromptExecutionMetadata(
        @NotBlank @Size(max = 100) String promptVersion,
        @NotBlank @Size(max = 100) String provider,
        @NotBlank @Size(max = 255) String modelIdentifier,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String promptContentDigest,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String contextDigest,
        @Pattern(regexp = "[0-9a-f]{64}") String selectionDigest,
        @Pattern(regexp = "[0-9a-f]{64}") String projectionDigest,
        @Size(max = 100) String projectionVersion,
        Map<String, Object> scope,
        Map<String, Object> freshness,
        @Pattern(regexp = "[0-9a-f]{64}") String groundingDigest
) {
    public PromptExecutionMetadata(String promptVersion, String provider, String modelIdentifier,
            String promptContentDigest, String contextDigest, String selectionDigest, String projectionDigest) {
        this(promptVersion, provider, modelIdentifier, promptContentDigest, contextDigest, selectionDigest, projectionDigest, null, null, null, null);
    }

    public PromptExecutionMetadata(String promptVersion, String provider, String modelIdentifier,
            String promptContentDigest, String contextDigest) {
        this(promptVersion, provider, modelIdentifier, promptContentDigest, contextDigest, null, null, null, null, null, null);
    }
}
