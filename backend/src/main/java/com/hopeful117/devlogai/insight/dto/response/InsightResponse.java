package com.hopeful117.devlogai.insight.dto.response;

import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;

import java.time.Instant;
import java.util.UUID;

public record InsightResponse(

        UUID id,

        UUID projectId,

        UUID analysisId,

        InsightType type,

        InsightSeverity severity,

        String title,

        String content,

        Instant createdAt,

        Instant updatedAt


) {
}
