package com.hopeful117.devlogai.evaluation.livepreparation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LiveEvaluationPreparationResponse(
        UUID planId,
        String projectSlug,
        LiveEvaluationPreparationStatus status,
        String requestDigest,
        String planDigest,
        Instant createdAt,
        Instant expiresAt,
        Map<String, Object> plan,
        List<String> blockers
) {
    static LiveEvaluationPreparationResponse from(LiveEvaluationPreparation value) {
        Object rawBlockers = value.getPlanSnapshot().get("blockers");
        List<String> blockers = rawBlockers instanceof List<?> list
                ? list.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
        return new LiveEvaluationPreparationResponse(
                value.getId(), value.getProject().getSlug(), value.getStatus(), value.getRequestDigest(),
                value.getPlanDigest(), value.getCreatedAt(), value.getExpiresAt(), value.getPlanSnapshot(), blockers);
    }
}
