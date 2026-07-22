package com.hopeful117.devlogai.collection.service;

import java.util.Map;
import java.util.UUID;
import java.util.List;

public record KnowledgeCollectionResult(
        int sourceCount,
        int factCount,
        int observationCount,
        Map<UUID, String> resolvedRevisions,
        List<CollectionDiagnostic> warnings
) {
    public KnowledgeCollectionResult {
        resolvedRevisions = Map.copyOf(resolvedRevisions);
        warnings = List.copyOf(warnings);
    }
}
