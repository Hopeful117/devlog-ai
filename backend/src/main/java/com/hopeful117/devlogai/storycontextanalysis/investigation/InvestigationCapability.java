package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record InvestigationCapability(
        InvestigationActionKind kind,
        int maximumResultLimit,
        long maximumBytes,
        long maximumTokens
) { }
