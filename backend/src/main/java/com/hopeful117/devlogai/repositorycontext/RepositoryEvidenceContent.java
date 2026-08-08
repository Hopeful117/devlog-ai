package com.hopeful117.devlogai.repositorycontext;

public record RepositoryEvidenceContent(
        Status status,
        String text,
        String reason,
        String policyId,
        String policyVersion,
        String revision
) {
    public enum Status {
        COMPLETE,
        TRUNCATED,
        SKIPPED,
        UNAVAILABLE
    }
}
