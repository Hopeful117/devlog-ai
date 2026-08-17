package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EngineeringEvidence(

        String kind,
        String layer,
        String summary,
        String sourceType,
        String originatingFile,
        String identifier,
        Integer relevanceScore,
        String selectionReason
) {
}
