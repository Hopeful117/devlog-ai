package com.hopeful117.devlogai.evidence.resolution;

import java.util.Objects;
import java.util.UUID;

public record ParsedEvidenceReference(
        String canonicalReference,
        EvidenceResolutionFamily family,
        UUID sourceId,
        String revision,
        String pathOrIdentity
) {
    public ParsedEvidenceReference {
        Objects.requireNonNull(canonicalReference, "canonicalReference");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(pathOrIdentity, "pathOrIdentity");
    }
}
