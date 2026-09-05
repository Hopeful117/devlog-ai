package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;

public record ContextSection(
        String name,
        TrustTier trustTier,
        List<EngineeringEvidence> evidence,
        String rationale
) {
    public ContextSection {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}