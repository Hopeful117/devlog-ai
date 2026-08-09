package com.hopeful117.devlogai.projectunderstanding.dto;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;

import java.util.Map;
import java.util.UUID;

public record ProjectUnderstandingResponse(
        UUID analysisId,
        AnalysisStatus status,
        UUID sourceId,
        String targetRevision,
        String intentId,
        String intentVersion,
        ProjectUnderstandingOutcome outcome,
        Map<String, Object> sourceSnapshot
) { }
