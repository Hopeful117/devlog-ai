package com.hopeful117.devlogai.aicatalog.model;

import java.util.List;

public record AiProviderModel(
        String identifier,
        String displayName,
        List<String> capabilities,
        AiProviderModelAvailability availability
) {
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
    }
}
