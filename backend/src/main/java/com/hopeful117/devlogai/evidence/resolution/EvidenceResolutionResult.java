package com.hopeful117.devlogai.evidence.resolution;

import java.util.Objects;

public record EvidenceResolutionResult(
        EvidenceResolutionMetadata metadata,
        EvidenceResolutionPayload payload
) {
    public EvidenceResolutionResult {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(payload, "payload");
    }
}
