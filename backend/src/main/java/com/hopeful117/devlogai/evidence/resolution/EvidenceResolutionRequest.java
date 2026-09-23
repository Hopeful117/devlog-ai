package com.hopeful117.devlogai.evidence.resolution;

import java.util.Objects;
import java.util.UUID;

public record EvidenceResolutionRequest(
        String reference,
        EvidenceResolutionMode mode,
        UUID analysisId
) {
    public EvidenceResolutionRequest(String reference, EvidenceResolutionMode mode) {
        this(reference, mode, null);
    }

    public EvidenceResolutionRequest {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(mode, "mode");
        if (reference.isBlank()) {
            throw new IllegalArgumentException("reference must not be blank");
        }
    }
}
