package com.hopeful117.devlogai.evidence.resolution;

import java.util.Objects;
import java.util.UUID;

public record EvidenceResolutionMetadata(
        String canonicalReference,
        EvidenceResolutionFamily family,
        UUID sourceId,
        String provenance,
        String revision,
        EvidenceResolutionMode mode,
        boolean expandable
) {
    public EvidenceResolutionMetadata {
        Objects.requireNonNull(canonicalReference, "canonicalReference");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(mode, "mode");
    }
}
