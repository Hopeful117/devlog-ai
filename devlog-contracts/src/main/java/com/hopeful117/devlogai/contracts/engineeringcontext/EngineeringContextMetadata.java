package com.hopeful117.devlogai.contracts.engineeringcontext;

public record EngineeringContextMetadata(

        int candidateCount,
        int selectedCount,
        boolean truncated,
        int usedTokens,
        String contextDigest
) {
}
