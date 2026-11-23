package com.hopeful117.devlogai.evaluation.liveauthorization;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LiveEvaluationAuthorizationResponse(
        UUID authorizationId,
        UUID planId,
        String planDigest,
        LiveEvaluationAuthorizationStatus status,
        String requesterId,
        String approverId,
        Instant createdAt,
        Instant approvedAt,
        Instant consumedAt,
        Instant expiresAt,
        Map<String, Object> artifact
) {
    static LiveEvaluationAuthorizationResponse from(LiveEvaluationAuthorization value) {
        return new LiveEvaluationAuthorizationResponse(value.getId(), value.getPreparation().getId(),
                value.getPlanDigest(), value.getStatus(), value.getRequesterId(), value.getApproverId(),
                value.getCreatedAt(), value.getApprovedAt(), value.getConsumedAt(), value.getExpiresAt(),
                value.getArtifactSnapshot());
    }
}
