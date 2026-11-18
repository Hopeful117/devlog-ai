package com.hopeful117.devlogai.repositorycontext.capture;

import java.util.List;

/**
 * Serialization record for a live retrieval capture (story-0206-live-capture/v1).
 * Fields map 1:1 to the JSON schema consumed by the Python evaluation harness.
 */
public record CaptureRecord(
        String protocolVersion,
        String capturedAt,
        String scenarioId,
        String projectSlug,
        String revision,
        String question,
        int candidateCount,
        int selectedCount,
        int estimatedTokens,
        boolean truncated,
        long latencyMs,
        String contextDigest,
        String condition,
        List<String> expectedEvidence,
        List<CandidateCapture> candidates
) {
    public record CandidateCapture(
            String reference,
            String family,
            String trustTier,
            String status,
            String sourceType,
            int relevanceScore,
            boolean selected,
            String rejectionReason
    ) {}
}
