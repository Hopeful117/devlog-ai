package com.hopeful117.devlogai.evidence.retrieval;

import com.hopeful117.devlogai.contracts.engineeringcontext.TrustTier;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceCandidate(
        String canonicalReference,
        EvidenceRetrievalFamily family,
        UUID projectId,
        UUID sourceId,
        Instant occurredAt,
        String summary,
        EvidenceCandidateProvenance provenance,
        TrustTier trustTier
) {
    public EvidenceCandidate {
        Objects.requireNonNull(canonicalReference, "canonicalReference");
        if (canonicalReference.isBlank()) {
            throw new IllegalArgumentException("canonicalReference must not be blank");
        }
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(projectId, "projectId");
        if (family == EvidenceRetrievalFamily.GIT_COMMIT && sourceId == null) {
            throw new IllegalArgumentException("Git commit candidates require sourceId");
        }
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(trustTier, "trustTier");
    }
}
