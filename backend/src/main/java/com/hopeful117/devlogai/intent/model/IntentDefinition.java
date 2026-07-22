package com.hopeful117.devlogai.intent.model;

import java.util.List;
import java.util.Map;

public record IntentDefinition(
        String id,
        String version,
        String objective,
        List<InsightType> supportedInsightTypes,
        List<String> constraints,
        Map<String, Object> outputSchema,
        String promptTemplate
) {
    public IntentDefinition {
        supportedInsightTypes = List.copyOf(supportedInsightTypes);
        constraints = List.copyOf(constraints);
        outputSchema = Map.copyOf(outputSchema);
    }

    public String key() { return id + "-" + version; }
}
