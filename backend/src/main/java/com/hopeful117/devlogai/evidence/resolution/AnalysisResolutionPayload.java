package com.hopeful117.devlogai.evidence.resolution;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnalysisResolutionPayload(
        UUID id, UUID projectId, UUID selectedSourceId, AnalysisType type, String intentId,
        String intentVersion, AnalysisStatus status, String targetRevision, Instant startedAt,
        Instant completedAt, Instant createdAt, Instant updatedAt, Map<String, Object> userGuidance,
        Map<String, Object> selectedSourceSnapshot
) implements EvidenceResolutionPayload {
    public AnalysisResolutionPayload {
        userGuidance = userGuidance == null ? null : Map.copyOf(userGuidance);
        selectedSourceSnapshot = selectedSourceSnapshot == null
                ? null : Map.copyOf(selectedSourceSnapshot);
    }
}
