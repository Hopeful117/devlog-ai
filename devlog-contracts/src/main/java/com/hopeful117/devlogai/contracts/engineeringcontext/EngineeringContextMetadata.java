package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;
import java.util.Map;

public record EngineeringContextMetadata(

        int candidateCount,
        int selectedCount,
        boolean truncated,
        int usedTokens,
        String contextDigest,
        List<String> warnings,
        EngineeringContextFreshness freshness,
        int rejectedCount,
        Map<String, Integer> rejectionReasonCounts
) {
    public EngineeringContextMetadata {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        rejectionReasonCounts = rejectionReasonCounts == null
                ? Map.of() : Map.copyOf(rejectionReasonCounts);
    }

    public EngineeringContextMetadata(
            int candidateCount, int selectedCount, boolean truncated,
            int usedTokens, String contextDigest, List<String> warnings,
            EngineeringContextFreshness freshness
    ) {
        this(candidateCount, selectedCount, truncated, usedTokens,
                contextDigest, warnings, freshness, 0, Map.of());
    }
}
