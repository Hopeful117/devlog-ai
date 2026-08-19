package com.hopeful117.devlogai.repositoryevidence;

public class RepositoryEvidenceResolutionException extends RuntimeException {

    public enum Reason { LINEAGE_UNAVAILABLE, DATA_INTEGRITY_ERROR }

    private final Reason reason;

    public RepositoryEvidenceResolutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}