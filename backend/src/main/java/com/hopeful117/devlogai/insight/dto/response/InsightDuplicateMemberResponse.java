package com.hopeful117.devlogai.insight.dto.response;

import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InsightDuplicateMemberResponse(
        UUID insightId,
        UUID proposalId,
        InsightType type,
        InsightSeverity severity,
        String sourceType,
        String title,
        String content,
        String rationale,
        BigDecimal confidence,
        int evidenceReferenceCount,
        Instant createdAt
) {
}
