package com.hopeful117.devlogai.evidence.retrieval;

/** Stable validation failure for the shared retrieval boundary. */
public final class EvidenceRetrievalException extends RuntimeException {
    private final EvidenceRetrievalFailureCode code;

    public EvidenceRetrievalException(EvidenceRetrievalFailureCode code, String message) {
        super(message);
        this.code = code;
    }

    public EvidenceRetrievalFailureCode code() {
        return code;
    }
}
