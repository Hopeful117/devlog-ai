package com.hopeful117.devlogai.evidence.resolution;

import java.util.UUID;

/** Stable, deterministic failure raised by the Core resolution boundary. */
public final class EvidenceResolutionException extends RuntimeException {
    private final EvidenceResolutionFailureCode code;
    private final String reference;
    private final UUID sourceId;

    public EvidenceResolutionException(
            EvidenceResolutionFailureCode code,
            String reference,
            UUID sourceId,
            String message
    ) {
        super(message);
        this.code = code;
        this.reference = reference;
        this.sourceId = sourceId;
    }

    public EvidenceResolutionFailureCode code() {
        return code;
    }

    public String reference() {
        return reference;
    }

    public UUID sourceId() {
        return sourceId;
    }
}
