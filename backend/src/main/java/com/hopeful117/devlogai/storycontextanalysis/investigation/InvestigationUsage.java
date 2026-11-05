package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record InvestigationUsage(
        int steps,
        int retrievalCalls,
        int evidenceItems,
        long bytes,
        long tokens,
        long elapsedMillis,
        int providerOutputTokens
) {
    public static InvestigationUsage empty() {
        return new InvestigationUsage(0, 0, 0, 0, 0, 0, 0);
    }
}
