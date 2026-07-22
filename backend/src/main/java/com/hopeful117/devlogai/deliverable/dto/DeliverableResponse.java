package com.hopeful117.devlogai.deliverable.dto;

import com.hopeful117.devlogai.deliverable.entity.DeliverableType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DeliverableResponse(
        UUID id, UUID projectId, UUID analysisId, DeliverableType type,
        String audience, String style, String language, String additionalGuidance,
        String title, String content, String promptVersion, String promptDigest,
        String provider, String modelIdentifier, Instant generatedAt,
        List<UUID> sourceInsightIds
) { }
