package com.hopeful117.devlogai.insight.dto.response;

import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightTrustState;
import com.hopeful117.devlogai.insight.entity.InsightType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InsightResponse(

        UUID id,

        UUID projectId,

        UUID analysisId,

        UUID proposalId,

        UUID validationId,

        InsightType type,

        InsightSeverity severity,

        InsightTrustState trustState,

        String title,

        String content,

        String rationale,

        BigDecimal confidence,

        List<String> evidenceReferences,

        String sourceType,

        Instant createdAt,

        Instant updatedAt


) {
}
