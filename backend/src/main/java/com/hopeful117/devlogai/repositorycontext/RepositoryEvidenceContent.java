package com.hopeful117.devlogai.repositorycontext;

public record RepositoryEvidenceContent(
        Status status,
        String text,
        String reason,
        String policyId,
        String policyVersion,
        String revision,
        String allocationPolicyId,
        String allocationPolicyVersion,
        Integer allocationRank,
        java.util.List<String> allocationReasons
) {
    public RepositoryEvidenceContent {
        allocationReasons = allocationReasons == null
                ? java.util.List.of() : java.util.List.copyOf(allocationReasons);
    }

    public RepositoryEvidenceContent(
            Status status,
            String text,
            String reason,
            String policyId,
            String policyVersion,
            String revision
    ) {
        this(status, text, reason, policyId, policyVersion, revision,
                null, null, null, java.util.List.of());
    }

    public enum Status {
        COMPLETE,
        TRUNCATED,
        SKIPPED,
        UNAVAILABLE
    }
}
