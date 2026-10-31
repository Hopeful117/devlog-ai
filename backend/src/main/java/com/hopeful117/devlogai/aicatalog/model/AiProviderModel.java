package com.hopeful117.devlogai.aicatalog.model;

import java.util.List;

public record AiProviderModel(
        String identifier,
        String displayName,
        List<String> capabilities,
        AiProviderModelAvailability availability,
        Integer contextCapacityTokens
) {
    public AiProviderModel(String identifier, String displayName, List<String> capabilities,
                           AiProviderModelAvailability availability) {
        this(identifier, displayName, capabilities, availability, null);
    }
    public AiProviderModel {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Model identifier must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Model display name must not be blank");
        }
        capabilities = List.copyOf(capabilities);
        if (availability == null) {
            throw new IllegalArgumentException("Model availability must not be null");
        }
        if (contextCapacityTokens != null && contextCapacityTokens < 1) {
            throw new IllegalArgumentException("Model context capacity must be positive");
        }
    }
}
