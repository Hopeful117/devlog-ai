package com.hopeful117.devlogai.story.service;

import java.util.List;
import java.util.UUID;

public record StoryCatalogSynchronizationResult(
        UUID projectId,
        UUID sourceId,
        String revision,
        int createdCount,
        int updatedCount,
        int unchangedCount,
        int skippedCount,
        List<String> warnings
) {
    public StoryCatalogSynchronizationResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
