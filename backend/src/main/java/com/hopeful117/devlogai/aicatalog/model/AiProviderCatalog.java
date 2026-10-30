package com.hopeful117.devlogai.aicatalog.model;

import java.util.List;

public record AiProviderCatalog(
        String catalogVersion,
        List<AiProviderCatalogEntry> providers
) {
    public AiProviderCatalog {
        providers = List.copyOf(providers);
    }
}
