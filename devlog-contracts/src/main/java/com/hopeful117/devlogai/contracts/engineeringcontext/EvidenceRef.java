package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EvidenceRef(
        String reference,
        String resource
) {
    public EvidenceRef {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }
}