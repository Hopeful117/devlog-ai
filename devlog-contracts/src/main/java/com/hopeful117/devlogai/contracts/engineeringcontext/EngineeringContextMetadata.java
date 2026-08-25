package com.hopeful117.devlogai.contracts.engineeringcontext;

import java.util.List;

public record EngineeringContextMetadata(

        int candidateCount,
        int selectedCount,
        boolean truncated,
        int usedTokens,
        String contextDigest,
        List<String> warnings,
        EngineeringContextFreshness freshness
) {
    public EngineeringContextMetadata {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
