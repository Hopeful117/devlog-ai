package com.hopeful117.devlogai.intent.model;

import java.util.List;
import java.util.Map;
import com.hopeful117.devlogai.proposal.entity.ProposalType;

public record IntentDefinition(
        String id,
        String version,
        String objective,
        ProposalType outputProposalType,
        IntentExecutionMode executionMode,
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
        this(id, version, objective, ProposalType.INSIGHT, IntentExecutionMode.GENERIC,
                supportedInsightTypes, constraints,
                outputSchema, promptTemplate, List.of());
    }

    public IntentDefinition(String id, String version, String objective,
            List<InsightType> supportedInsightTypes, List<String> constraints,
            Map<String, Object> outputSchema, String promptTemplate,
            List<String> contextProfiles) {
        this(id, version, objective, ProposalType.INSIGHT, IntentExecutionMode.GENERIC,
                supportedInsightTypes, constraints, outputSchema, promptTemplate, contextProfiles);
    }

    public String key() { return id + "-" + version; }
}
