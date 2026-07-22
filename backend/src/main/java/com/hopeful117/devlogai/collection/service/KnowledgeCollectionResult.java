package com.hopeful117.devlogai.collection.service;

import java.util.Map;
import java.util.UUID;

public record KnowledgeCollectionResult(
        int sourceCount,
        int factCount,
        int observationCount,
        Map<UUID, String> resolvedRevisions
) {
    public KnowledgeCollectionResult {
        resolvedRevisions = Map.copyOf(resolvedRevisions);
    }
}
