package com.hopeful117.devlogai.contracts.storycontextanalysis;

public record EvidenceRef(
        String reference,
        String resource
) {
    public EvidenceRef {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("EvidenceRef reference must not be blank");
        }
        resource = resource == null || resource.isBlank() ? null : resource;
    }
}