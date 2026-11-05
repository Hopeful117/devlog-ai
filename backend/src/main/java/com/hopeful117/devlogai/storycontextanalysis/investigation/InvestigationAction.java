package com.hopeful117.devlogai.storycontextanalysis.investigation;

public sealed interface InvestigationAction
        permits SearchEvidenceRequest, ReadEvidenceRangeRequest {
    String actionId();

    String projectSlug();

    String revision();

    String purpose();

    int resultLimit();

    long maximumBytes();

    long maximumTokens();

    InvestigationActionKind kind();
}
