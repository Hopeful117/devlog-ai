package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record InvestigationBudget(
        int maximumSteps,
        int maximumRetrievalCalls,
        int maximumEvidenceItems,
        long maximumBytes,
        long maximumTokens,
        long maximumElapsedMillis,
        int maximumProviderOutputTokens
) { }
