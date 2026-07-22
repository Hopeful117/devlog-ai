package com.hopeful117.devlogai.collection.collector;

import java.util.List;
import java.util.Map;

public record CollectionResult(
        CollectorType collectorType,
        String collectorVersion,
        List<CollectedFact> facts,
        List<CollectionWarning> warnings,
        Map<String, String> executionMetadata
) {
    public CollectionResult {
        facts = List.copyOf(facts);
        warnings = List.copyOf(warnings);
        executionMetadata = Map.copyOf(executionMetadata);
    }

    public static CollectionResult of(
            CollectorType type,
            String version,
            List<CollectedFact> facts,
            List<CollectionWarning> warnings
    ) {
        return new CollectionResult(type, version, facts, warnings,
                Map.of("factCount", Integer.toString(facts.size())));
    }
}
