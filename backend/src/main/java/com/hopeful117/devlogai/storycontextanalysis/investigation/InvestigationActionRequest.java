package com.hopeful117.devlogai.storycontextanalysis.investigation;

import java.util.UUID;

/** Strict transport request. Project and revision are checked against the task snapshot, never used as authority. */
public record InvestigationActionRequest(
        String kind,
        String actionId,
        String projectSlug,
        String revision,
        String purpose,
        Limits limits,
        String query,
        String path,
        Range range,
        String protocolVersion,
        UUID executionId,
        UUID parentTaskId,
        UUID correlationId,
        String provider,
        UUID credentialBindingId,
        Integer providerOutputTokens,
        Integer providerGenerationCount,
        Long providerElapsedMillis
) {
    public InvestigationActionRequest(String kind, String actionId, String projectSlug, String revision,
                                      String purpose, Limits limits, String query, String path, Range range) {
        this(kind, actionId, projectSlug, revision, purpose, limits, query, path, range,
                null, null, null, null, null, null, 0, 1, 0L);
    }

    /** Compatibility constructor for the original v2 action envelope. */
    public InvestigationActionRequest(String kind, String actionId, String projectSlug, String revision,
                                      String purpose, Limits limits, String query, String path, Range range,
                                      String protocolVersion, UUID executionId, UUID parentTaskId,
                                      UUID correlationId, String provider, UUID credentialBindingId,
                                      int providerOutputTokens) {
        this(kind, actionId, projectSlug, revision, purpose, limits, query, path, range,
                protocolVersion, executionId, parentTaskId, correlationId, provider, credentialBindingId,
                providerOutputTokens, 1, 0L);
    }

    public record Limits(int resultLimit, long maximumBytes, long maximumTokens) { }

    public record Range(int startLine, int endLine) { }
}
