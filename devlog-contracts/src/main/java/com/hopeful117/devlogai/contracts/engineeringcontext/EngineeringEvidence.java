package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EngineeringEvidence(

        String kind,
        String layer,
        String summary,
        String sourceType,
        String originatingFile,
        String identifier,
        Integer relevanceScore,
        String selectionReason,
        java.time.Instant occurredAt,
        java.util.List<String> relatedReferences,
        java.util.Map<String, String> extractionMetadata,
        EngineeringEvidenceContent content,
        EngineeringEvidenceSymbols symbols,
        String resource
) {
    public EngineeringEvidence {
        relatedReferences = relatedReferences == null
                ? java.util.List.of() : java.util.List.copyOf(relatedReferences);
        extractionMetadata = extractionMetadata == null
                ? java.util.Map.of() : java.util.Map.copyOf(extractionMetadata);
    }
}
