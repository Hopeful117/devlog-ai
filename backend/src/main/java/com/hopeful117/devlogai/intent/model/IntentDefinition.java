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
        String promptTemplate,
        List<String> contextProfiles
) {
    public IntentDefinition {
        supportedInsightTypes = List.copyOf(supportedInsightTypes);
        constraints = List.copyOf(constraints);
        outputSchema = Map.copyOf(outputSchema);
        contextProfiles = List.copyOf(contextProfiles);
    }

    public IntentDefinition(
            String id, String version, String objective,
            List<InsightType> supportedInsightTypes, List<String> constraints,
            Map<String, Object> outputSchema, String promptTemplate
    ) {
        this(id, version, objective, supportedInsightTypes, constraints,
                outputSchema, promptTemplate, List.of());
    }

    public String key() { return id + "-" + version; }
}
