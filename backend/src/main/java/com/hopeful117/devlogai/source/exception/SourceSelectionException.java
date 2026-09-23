package com.hopeful117.devlogai.source.exception;

import java.util.UUID;

/** Deterministic failure when a current-context operation cannot select one source. */
public class SourceSelectionException extends RuntimeException {
    public enum Reason {
        SOURCE_UNAVAILABLE,
        AMBIGUOUS_SOURCE
    }

    private final Reason reason;
    private final UUID projectId;

    public SourceSelectionException(Reason reason, UUID projectId, String message) {
        super(message);
        this.reason = reason;
        this.projectId = projectId;
    }

    public Reason reason() {
        return reason;
    }

    public UUID projectId() {
        return projectId;
    }
}
