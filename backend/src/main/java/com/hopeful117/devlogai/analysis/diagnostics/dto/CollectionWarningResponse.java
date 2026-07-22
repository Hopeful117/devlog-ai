package com.hopeful117.devlogai.analysis.diagnostics.dto;

import com.hopeful117.devlogai.analysis.diagnostics.entity.WarningSeverity;
import com.hopeful117.devlogai.collection.collector.CollectorType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CollectionWarningResponse(
        UUID id, UUID analysisId, UUID sourceId, CollectorType collectorType,
        String collectorVersion, String code, WarningSeverity severity,
        String message, String evidenceReference, Map<String, Object> metadata,
        Instant occurredAt
) { }
