package com.hopeful117.devlogai.evidence.retrieval;

import java.util.Objects;

public record EvidenceCandidateProvenance(
        String authority
) {
    public EvidenceCandidateProvenance {
        Objects.requireNonNull(authority, "authority");
        if (authority.isBlank()) {
            throw new IllegalArgumentException("authority must not be blank");
        }
    }
}
