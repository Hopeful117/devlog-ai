package com.hopeful117.devlogai.aicatalog.model;

import java.util.List;
import java.util.HashSet;

public record AiProviderCatalogEntry(
        String providerId,
        String displayName,
        List<AiAuthenticationMethod> authenticationMethods,
        List<AiProviderModel> models,
        String recommendedModel,
        List<String> capabilities,
        AiCatalogLifecycleStatus lifecycleStatus
) {
    public AiProviderCatalogEntry {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider identifier must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Provider display name must not be blank");
        }
        authenticationMethods = List.copyOf(authenticationMethods);
        models = List.copyOf(models);
        capabilities = List.copyOf(capabilities);
        if (new HashSet<>(models.stream().map(AiProviderModel::identifier).toList()).size() != models.size()) {
            throw new IllegalArgumentException("Model identifiers must be unique within a provider");
        }
        if (recommendedModel != null
                && models.stream().noneMatch(model -> model.identifier().equals(recommendedModel))) {
            throw new IllegalArgumentException("Recommended model must be present in the provider catalog");
        }
    }
}
