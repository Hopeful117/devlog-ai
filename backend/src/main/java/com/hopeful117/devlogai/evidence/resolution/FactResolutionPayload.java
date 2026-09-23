package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.fact.entity.FactType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record FactResolutionPayload(
        UUID id,
        UUID analysisId,
        FactType type,
        String content,
        String source,
        String fingerprint,
        Set<String> evidenceReferences,
        Instant detectedAt
) implements EvidenceResolutionPayload {
    public FactResolutionPayload {
        evidenceReferences = Set.copyOf(evidenceReferences);
    }
}
