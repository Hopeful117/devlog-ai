package com.hopeful117.devlogai.analysis.dto.response;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;

import java.time.Instant;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,

        UUID projectId,

        AnalysisType type,

        AnalysisStatus status,

        Instant startedAt,

        Instant completedAt,

        Instant createdAt,

        Instant updatedAt


) {
}
