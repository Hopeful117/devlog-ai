package com.hopeful117.devlogai.temporal.signal;

/**
 * Deterministic temporal signals for V1 Insight temporal assessment.
 *
 * Repository-state comparison is the ONLY conclusion-producing signal.
 * These signals describe what was observed, not an independent algorithm.
 */
public enum DeterministicTemporalSignal {

    EVIDENCE_FILE_DELETED_SINCE_BASELINE(
            "Evidence file was present at baseline but is absent at currentKnownRevision",
            true),

    ALL_EVIDENCE_VERIFIED_PRESENT(
            "All evaluable evidence references verified present at both baseline and currentKnownRevision",
            false);

    private final String description;
    private final boolean indicatesSuspicion;

    DeterministicTemporalSignal(String description, boolean indicatesSuspicion) {
        this.description = description;
        this.indicatesSuspicion = indicatesSuspicion;
    }

    public String getDescription() { return description; }
    public boolean indicatesSuspicion() { return indicatesSuspicion; }
}
