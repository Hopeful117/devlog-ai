package com.hopeful117.devlogai.fact.dto.response;

import com.hopeful117.devlogai.fact.entity.FactType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record FactResponse(
        UUID id,
        UUID analysisId,
        FactType type,
        String content,
        String source,
        Set<String> evidenceReferences,
        Instant detectedAt
) {
}
