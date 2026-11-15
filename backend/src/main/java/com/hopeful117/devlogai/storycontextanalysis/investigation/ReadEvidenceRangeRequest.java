package com.hopeful117.devlogai.storycontextanalysis.investigation;

public record ReadEvidenceRangeRequest(
        String actionId,
        String projectSlug,
        String revision,
        String purpose,
        String path,
        int startLine,
        int endLine,
        int resultLimit,
        long maximumBytes,
        long maximumTokens
) implements InvestigationAction {
    @Override
    public InvestigationActionKind kind() {
        return InvestigationActionKind.READ_EVIDENCE_RANGE;
    }
}
