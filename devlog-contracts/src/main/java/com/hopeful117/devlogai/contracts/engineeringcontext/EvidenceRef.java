package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EvidenceRef(
        String reference,
        String resource,
        CausalEvidenceRole role
) {
    public EvidenceRef(String reference, String resource) {
        this(reference, resource, null);
    }

    public EvidenceRef {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }

    public enum CausalEvidenceRole {
        DIRECT_RELATIONSHIP_STATEMENT,
        MATERIAL_RELATIONSHIP_SUPPORT,
        NON_CAUSAL_CONTEXT,
        CONTRADICTORY_EVIDENCE
    }
}
