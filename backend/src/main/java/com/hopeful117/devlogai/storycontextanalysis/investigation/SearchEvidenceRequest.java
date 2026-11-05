package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record SearchEvidenceRequest(
        String actionId,
        String projectSlug,
        String revision,
        String purpose,
        String query,
        String pathPrefix,
        int resultLimit,
        long maximumBytes,
        long maximumTokens
) implements InvestigationAction {
    @Override
    public InvestigationActionKind kind() {
        return InvestigationActionKind.SEARCH_EVIDENCE;
    }
}
